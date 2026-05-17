from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import re
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from .config import load_settings
from .db import get_conn
from .gamification import achievement_progress_rows, tier_for_level, tier_snapshot
from .models import (
    ActivateRequest,
    CheckRequest,
    DeviceReactivationRequest,
    LeaderboardRequest,
    ResetRequest,
    TrainingHistoryRequest,
    TrainingSessionCreateRequest,
    UserBootstrapRequest,
    UserProfileUpdateRequest,
    UserStatisticsRequest,
)
from .security import normalize_serial


app = FastAPI(title="BoxingFitness API Service", version="1.2.0")

SUPPORTED_LANGUAGES = {"zh", "en", "fr", "th"}
SUPPORTED_WINDOWS = {"all", "day", "week", "month"}
MAX_TRAINING_SESSION_SECONDS = 600
SUPPORTED_LEADERBOARD_KEYS = {
    "daily_best_hits",
    "total_hits",
    "total_duration",
    "total_calories",
    "total_fat_burn",
}
HEX_COLOR_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")
LEADERBOARD_ESTIMATED_KCAL_PER_HIT = 0.34
LEADERBOARD_ESTIMATED_FAT_PER_KCAL = 0.11


@dataclass
class RequestContext:
    serial: str
    install_id: str
    device_hash: str
    activation_token: str
    app_version: str | None
    ip: str
    license_row: dict[str, Any]
    activation_row: dict[str, Any]
    user_row: dict[str, Any] | None


APP_USER_SELECT_COLUMNS = """
id, serial, nickname, language_code, country_code, avatar_color,
current_tier, highest_tier, tier_updated_at,
created_at, updated_at, last_seen_at
"""


def utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").strip()
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else ""


def utc_from_epoch_ms(value: int | None) -> datetime | None:
    if value is None or value <= 0:
        return None
    return datetime.fromtimestamp(value / 1000.0, tz=timezone.utc).replace(tzinfo=None)


def decimal_to_float(value: Any, digits: int = 3) -> float:
    if value is None:
        return 0.0
    if isinstance(value, Decimal):
        return round(float(value), digits)
    return round(float(value), digits)


def clamp_language(value: str | None, fallback: str = "zh") -> str:
    normalized = (value or "").strip().lower()
    return normalized if normalized in SUPPORTED_LANGUAGES else fallback


def clamp_window(value: str | None) -> str:
    normalized = (value or "all").strip().lower()
    if normalized not in SUPPORTED_WINDOWS:
        raise HTTPException(status_code=400, detail="window must be one of: all, day, week, month")
    return normalized


def normalize_leaderboard_key(board_key: str | None, mode_seconds: int | None) -> str:
    normalized = (board_key or "").strip().lower()
    if normalized in SUPPORTED_LEADERBOARD_KEYS:
        return normalized
    return "total_hits"


def estimated_session_calories(total_hits: int) -> float:
    return round(max(total_hits, 0) * LEADERBOARD_ESTIMATED_KCAL_PER_HIT, 1)


def estimated_session_fat_burn(total_hits: int) -> float:
    return round(estimated_session_calories(total_hits) * LEADERBOARD_ESTIMATED_FAT_PER_KCAL, 1)


def leaderboard_score_value(board_key: str, total_hits: int, duration_seconds: int) -> int:
    if board_key == "total_duration":
        return int(round(max(duration_seconds, 0) / 60.0))
    if board_key == "total_calories":
        return int(round(estimated_session_calories(total_hits)))
    if board_key == "total_fat_burn":
        return int(round(estimated_session_fat_burn(total_hits)))
    return max(total_hits, 0)


def clamp_avatar_color(value: str | None, fallback: str) -> str:
    normalized = (value or "").strip()
    if HEX_COLOR_RE.fullmatch(normalized):
        return normalized.upper()
    return fallback


def leaderboard_cutoff(window: str) -> datetime | None:
    now = utc_now()
    if window == "day":
        return now - timedelta(days=1)
    if window == "week":
        return now - timedelta(days=7)
    if window == "month":
        return now - timedelta(days=30)
    return None


def default_nickname(serial: str) -> str:
    return f"Player-{serial[-4:]}"


def avatar_color_for_serial(serial: str) -> str:
    colors = ["#145DA0", "#0E8F6A", "#A73A54", "#D97D00", "#5C3D99", "#2C6E49"]
    return colors[int(serial[-2:]) % len(colors)]


def masked_serial(serial: str) -> str:
    return serial if len(serial) <= 4 else "*******" + serial[-4:]


def write_log(
    conn,
    *,
    serial: str | None,
    install_id: str | None,
    device_hash: str | None,
    event_type: str,
    result: str,
    reason: str | None,
    ip: str | None,
) -> None:
    return None


def blocked(reason: str, message: str, status_code: int = 403) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"status": "blocked", "reason": reason, "message": message},
    )


def serialize_profile(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "user_id": row["id"],
        "serial": row["serial"],
        "serial_masked": masked_serial(row["serial"]),
        "nickname": row["nickname"],
        "language_code": row["language_code"],
        "country_code": row["country_code"],
        "avatar_color": row["avatar_color"],
        "current_tier": int(row.get("current_tier") or 1),
        "highest_tier": int(row.get("highest_tier") or 1),
        "created_at": row["created_at"].isoformat() if row.get("created_at") else None,
        "last_seen_at": row["last_seen_at"].isoformat() if row.get("last_seen_at") else None,
    }


def serialize_statistics(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "total_sessions": int(row.get("total_sessions") or 0),
        "total_hits": int(row.get("total_hits") or 0),
        "best_30_hits": int(row.get("best_30_hits") or 0),
        "best_60_hits": int(row.get("best_60_hits") or 0),
        "average_30_frequency": decimal_to_float(row.get("average_30_frequency")),
        "average_60_frequency": decimal_to_float(row.get("average_60_frequency")),
        "personal_best_hits": int(row.get("personal_best_hits") or 0),
        "best_burst_record": int(row.get("best_burst_record") or 0),
        "best_average_frequency": decimal_to_float(row.get("best_average_frequency")),
        "active_days": int(row.get("active_days") or 0),
        "current_streak": int(row.get("current_streak") or 0),
        "longest_streak": int(row.get("longest_streak") or 0),
    }


def serialize_tier(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "level": int(payload["level"]),
        "key": payload["key"],
        "best_hits": int(payload["best_hits"]),
        "total_hits": int(payload.get("total_hits") or 0),
        "next_level": payload.get("next_level"),
        "next_key": payload.get("next_key"),
        "next_hits": payload.get("next_hits"),
        "next_total_hits": payload.get("next_total_hits"),
        "progress_hits": int(payload.get("progress_hits") or 0),
        "progress_target_hits": int(payload.get("progress_target_hits") or 0),
        "best_weight_percent": int(payload.get("best_weight_percent") or 30),
        "total_weight_percent": int(payload.get("total_weight_percent") or 70),
    }


def serialize_achievement(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "key": payload["key"],
        "metric": payload["metric"],
        "goal": int(payload["goal"]),
        "progress": int(payload["progress"]),
        "unlocked": bool(payload["unlocked"]),
        "unlocked_at": payload.get("unlocked_at").isoformat() if payload.get("unlocked_at") else None,
        "sort_order": int(payload.get("sort_order") or 0),
    }


def serialize_history_row(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "session_id": row["id"],
        "mode_seconds": int(row["mode_seconds"]),
        "total_hits": int(row["total_hits"]),
        "average_frequency": decimal_to_float(row["average_frequency"]),
        "best_burst_count": int(row["best_burst_count"]),
        "best_burst_start_sec": decimal_to_float(row["best_burst_start_sec"]),
        "started_at": row["started_at"].isoformat() if row.get("started_at") else None,
        "ended_at": row["ended_at"].isoformat() if row.get("ended_at") else None,
    }


def compute_statistics(conn, user_id: int) -> dict[str, Any]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT
              COUNT(*) AS total_sessions,
              COALESCE(SUM(total_hits), 0) AS total_hits,
              COALESCE(MAX(CASE WHEN mode_seconds = 30 THEN total_hits END), 0) AS best_30_hits,
              COALESCE(MAX(CASE WHEN mode_seconds = 60 THEN total_hits END), 0) AS best_60_hits,
              COALESCE(AVG(CASE WHEN mode_seconds = 30 THEN average_frequency END), 0) AS average_30_frequency,
              COALESCE(AVG(CASE WHEN mode_seconds = 60 THEN average_frequency END), 0) AS average_60_frequency,
              COALESCE(MAX(total_hits), 0) AS personal_best_hits,
              COALESCE(MAX(best_burst_count), 0) AS best_burst_record,
              COALESCE(MAX(average_frequency), 0) AS best_average_frequency
            FROM training_sessions
            WHERE user_id = %s
            """,
            (user_id,),
        )
        row = cur.fetchone() or {}
    return serialize_statistics(row)


def fetch_activity_dates(conn, user_id: int) -> list[date]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT DISTINCT DATE(ended_at) AS active_date
            FROM training_sessions
            WHERE user_id = %s
            ORDER BY active_date DESC
            """,
            (user_id,),
        )
        rows = cur.fetchall() or []
    return [row["active_date"] for row in rows if row.get("active_date")]


def streak_summary(dates: list[date]) -> dict[str, int]:
    if not dates:
        return {"active_days": 0, "current_streak": 0, "longest_streak": 0}

    ordered = sorted(set(dates))
    longest = 1
    current = 1
    for index in range(1, len(ordered)):
        if (ordered[index] - ordered[index - 1]).days == 1:
            current += 1
            longest = max(longest, current)
        else:
            current = 1

    today = utc_now().date()
    date_set = set(ordered)
    if today in date_set:
        cursor = today
    elif (today - ordered[-1]).days == 1:
        cursor = ordered[-1]
    else:
        return {"active_days": len(date_set), "current_streak": 0, "longest_streak": longest}

    current_streak = 0
    while cursor in date_set:
        current_streak += 1
        cursor = cursor.fromordinal(cursor.toordinal() - 1)

    return {
        "active_days": len(date_set),
        "current_streak": current_streak,
        "longest_streak": longest,
    }


def sync_user_progress(conn, user_row: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any], list[dict[str, Any]], bool]:
    statistics = compute_statistics(conn, user_row["id"])
    streaks = streak_summary(fetch_activity_dates(conn, user_row["id"]))
    statistics.update(streaks)

    metrics = {
        "total_sessions": statistics["total_sessions"],
        "total_hits": statistics["total_hits"],
        "personal_best_hits": statistics["personal_best_hits"],
        "best_burst_record": statistics["best_burst_record"],
        "longest_streak": statistics["longest_streak"],
        "active_days": statistics["active_days"],
        "best_30_hits": statistics["best_30_hits"],
        "best_60_hits": statistics["best_60_hits"],
    }
    tier = tier_snapshot(statistics["best_30_hits"], statistics["total_hits"])
    previous_tier = int(user_row.get("current_tier") or 1)
    promoted = tier["level"] > previous_tier

    achievement_rows = achievement_progress_rows(metrics)
    unlocked_map: dict[str, datetime | None] = {}
    valid_achievement_keys = {item["key"] for item in achievement_rows}
    now = utc_now()
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT achievement_key, unlocked_at
            FROM user_achievements
            WHERE user_id = %s
            """,
            (user_row["id"],),
        )
        for row in cur.fetchall() or []:
            unlocked_map[row["achievement_key"]] = row.get("unlocked_at")

        stale_keys = [key for key in unlocked_map if key not in valid_achievement_keys]
        if stale_keys:
            placeholders = ", ".join(["%s"] * len(stale_keys))
            cur.execute(
                f"""
                DELETE FROM user_achievements
                WHERE user_id = %s AND achievement_key IN ({placeholders})
                """,
                (user_row["id"], *stale_keys),
            )
            for key in stale_keys:
                unlocked_map.pop(key, None)

        for item in achievement_rows:
            existing_unlocked_at = unlocked_map.get(item["key"])
            should_unlock = item["unlocked"] and existing_unlocked_at is None
            unlocked_at = existing_unlocked_at or (now if should_unlock else None)
            if item["key"] in unlocked_map:
                cur.execute(
                    """
                    UPDATE user_achievements
                    SET unlocked_at = %s,
                        progress_value = %s,
                        goal_value = %s
                    WHERE user_id = %s AND achievement_key = %s
                    """,
                    (unlocked_at, item["progress"], item["goal"], user_row["id"], item["key"]),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO user_achievements
                    (user_id, achievement_key, unlocked_at, progress_value, goal_value, created_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (user_row["id"], item["key"], unlocked_at, item["progress"], item["goal"], now, now),
                )
            item["unlocked_at"] = unlocked_at

        cur.execute(
            """
            UPDATE app_users
            SET current_tier = %s,
                highest_tier = GREATEST(highest_tier, %s),
                tier_updated_at = %s,
                last_seen_at = %s
            WHERE id = %s
            """,
            (
                tier["level"],
                tier["level"],
                now,
                now,
                user_row["id"],
            ),
        )

    return statistics, tier, [serialize_achievement(item) for item in achievement_rows], promoted


def fetch_app_user(conn, user_id: int) -> dict[str, Any]:
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT {APP_USER_SELECT_COLUMNS}
            FROM app_users
            WHERE id = %s
            """,
            (user_id,),
        )
        return cur.fetchone()


def fetch_history(conn, user_id: int, limit: int) -> list[dict[str, Any]]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT
              id,
              mode_seconds,
              total_hits,
              average_frequency,
              best_burst_count,
              best_burst_start_sec,
              started_at,
              ended_at
            FROM training_sessions
            WHERE user_id = %s
            ORDER BY ended_at DESC, id DESC
            LIMIT %s
            """,
            (user_id, limit),
        )
        rows = cur.fetchall() or []
    return [serialize_history_row(row) for row in rows]


def fetch_leaderboard(
    conn,
    *,
    board_key: str,
    window: str,
    limit: int,
    my_user_id: int | None,
) -> tuple[list[dict[str, Any]], dict[str, Any] | None]:
    cutoff = leaderboard_cutoff(window)
    cutoff_sql = ""
    cutoff_params: list[Any] = []
    if cutoff is not None:
        cutoff_sql = " AND ts.ended_at >= %s"
        cutoff_params.append(cutoff)

    with conn.cursor() as cur:
        if board_key == "daily_best_hits":
            cur.execute(
                f"""
                SELECT
                  ts.user_id,
                  u.nickname,
                  u.serial,
                  u.country_code,
                  u.current_tier,
                  DATE(ts.ended_at) AS day_key,
                  COALESCE(SUM(ts.total_hits), 0) AS total_hits,
                  COALESCE(SUM(ts.mode_seconds), 0) AS duration_seconds,
                  MAX(ts.ended_at) AS last_ended_at
                FROM training_sessions ts
                JOIN app_users u ON u.id = ts.user_id
                WHERE ts.total_hits > 0{cutoff_sql}
                GROUP BY
                  ts.user_id,
                  u.nickname,
                  u.serial,
                  u.country_code,
                  u.current_tier,
                  DATE(ts.ended_at)
                """,
                tuple(cutoff_params),
            )
            daily_rows = cur.fetchall() or []
            best_by_user: dict[int, dict[str, Any]] = {}
            for row in daily_rows:
                user_id = int(row["user_id"])
                current = best_by_user.get(user_id)
                current_hits = int(current["total_hits"] or 0) if current else -1
                candidate_hits = int(row["total_hits"] or 0)
                candidate_day = str(row["day_key"])
                current_day = str(current["day_key"]) if current else ""
                if candidate_hits > current_hits or (candidate_hits == current_hits and candidate_day > current_day):
                    best_by_user[user_id] = row
            ranked_rows = list(best_by_user.values())
            ranked_rows.sort(
                key=lambda item: (
                    -int(item["total_hits"] or 0),
                    str(item["day_key"]),
                    int(item["user_id"]),
                ),
            )
        else:
            cur.execute(
                f"""
                SELECT
                  ts.user_id,
                  u.nickname,
                  u.serial,
                  u.country_code,
                  u.current_tier,
                  COALESCE(SUM(ts.total_hits), 0) AS total_hits,
                  COALESCE(SUM(ts.mode_seconds), 0) AS duration_seconds,
                  MAX(ts.ended_at) AS last_ended_at
                FROM training_sessions ts
                JOIN app_users u ON u.id = ts.user_id
                WHERE ts.total_hits > 0{cutoff_sql}
                GROUP BY
                  ts.user_id,
                  u.nickname,
                  u.serial,
                  u.country_code,
                  u.current_tier
                """,
                tuple(cutoff_params),
            )
            aggregate_rows = cur.fetchall() or []
            ranked_rows = sorted(
                [
                    {**row, "score_value": leaderboard_score_value(board_key, int(row["total_hits"] or 0), int(row["duration_seconds"] or 0))}
                    for row in aggregate_rows
                    if leaderboard_score_value(board_key, int(row["total_hits"] or 0), int(row["duration_seconds"] or 0)) > 0
                ],
                key=lambda item: (
                    -int(item["score_value"] or 0),
                    -(int(item["total_hits"] or 0)),
                    int(item["user_id"]),
                ),
            )

    top_entries: list[dict[str, Any]] = []
    my_entry: dict[str, Any] | None = None
    for index, row in enumerate(ranked_rows, start=1):
        tier_level = int(row.get("current_tier") or 1)
        score_value = int(row.get("total_hits") or 0) if board_key == "daily_best_hits" else int(row.get("score_value") or 0)
        ended_at = str(row.get("day_key")) if board_key == "daily_best_hits" else row["last_ended_at"].isoformat() if row.get("last_ended_at") else None
        entry = {
            "rank": index,
            "user_id": int(row["user_id"]),
            "nickname": row["nickname"],
            "serial_masked": masked_serial(row["serial"]),
            "country_code": row["country_code"],
            "tier_level": tier_level,
            "tier_key": tier_for_level(tier_level).key,
            "best_hits": score_value,
            "average_frequency": 0.0,
            "best_burst_count": 0,
            "best_burst_start_sec": 0.0,
            "ended_at": ended_at,
            "is_me": my_user_id is not None and int(row["user_id"]) == my_user_id,
        }
        if index <= limit:
            top_entries.append(entry)
        if my_user_id is not None and int(row["user_id"]) == my_user_id:
            my_entry = entry

    return top_entries, my_entry


def ensure_app_user(
    conn,
    *,
    serial: str,
    preferred_language: str | None = None,
) -> dict[str, Any]:
    language_code = clamp_language(preferred_language, "zh")
    now = utc_now()
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT {APP_USER_SELECT_COLUMNS}
            FROM app_users
            WHERE serial = %s
            FOR UPDATE
            """,
            (serial,),
        )
        row = cur.fetchone()
        if row:
            cur.execute(
                """
                UPDATE app_users
                SET last_seen_at = %s,
                    language_code = COALESCE(NULLIF(%s, ''), language_code)
                WHERE id = %s
                """,
                (now, preferred_language, row["id"]),
            )
            cur.execute(
                f"""
                SELECT {APP_USER_SELECT_COLUMNS}
                FROM app_users
                WHERE id = %s
                """,
                (row["id"],),
            )
            return cur.fetchone()

        cur.execute(
            """
            INSERT INTO app_users
            (serial, nickname, language_code, avatar_color, created_at, updated_at, last_seen_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """,
            (
                serial,
                default_nickname(serial),
                language_code,
                avatar_color_for_serial(serial),
                now,
                now,
                now,
            ),
        )
        user_id = cur.lastrowid
        cur.execute(
            f"""
            SELECT {APP_USER_SELECT_COLUMNS}
            FROM app_users
            WHERE id = %s
            """,
            (user_id,),
        )
        return cur.fetchone()


def authorize_request(
    conn,
    *,
    serial: str,
    install_id: str,
    device_hash: str,
    activation_token: str,
    app_version: str | None,
    ip: str,
    event_type: str,
) -> tuple[RequestContext | None, JSONResponse | None]:
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT {APP_USER_SELECT_COLUMNS}
            FROM app_users
            WHERE serial = %s
            """,
            (serial,),
        )
        user_row = cur.fetchone()

    return (
        RequestContext(
            serial=serial,
            install_id=install_id,
            device_hash=device_hash,
            activation_token=activation_token,
            app_version=app_version,
            ip=ip,
            license_row={"serial": serial, "status": "free"},
            activation_row={
                "install_id": install_id,
                "device_hash": device_hash,
                "activation_token": activation_token,
                "status": "free",
            },
            user_row=user_row,
        ),
        None,
    )


@app.get("/health")
def health() -> dict[str, Any]:
    settings = load_settings()
    return {"status": "ok", "service": settings.app_name}


@app.post("/api/v1/activate")
def activate(payload: ActivateRequest, request: Request):
    serial = normalize_serial(payload.serial)
    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.code.strip() or "free-use"

    with get_conn() as conn:
        try:
            ensure_app_user(conn, serial=serial)
            conn.commit()
            return {
                "status": "ok",
                "license_state": "free",
                "message": "Activation check has been removed.",
                "serial": serial,
                "activation_token": token,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/reactivate-by-device")
def reactivate_by_device(payload: DeviceReactivationRequest, request: Request):
    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    serial = normalize_serial("26" + device_hash[-9:]) if len(device_hash) >= 9 and device_hash[-9:].isdigit() else "26000000000"
    token = "free-use"

    with get_conn() as conn:
        try:
            ensure_app_user(conn, serial=serial)
            conn.commit()
            return {
                "status": "ok",
                "license_state": "free",
                "message": "Activation check has been removed.",
                "serial": serial,
                "activation_token": token,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/check")
def check(payload: CheckRequest, request: Request):
    serial = normalize_serial(payload.serial)

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="check",
            )
            if failure is not None:
                return failure

            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="check",
                result="ok",
                reason="active",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "license_state": "free",
                "message": "Activation check has been removed.",
                "serial": serial,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/user/bootstrap")
def user_bootstrap(payload: UserBootstrapRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    language_code = clamp_language(payload.language_code, "zh")
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="bootstrap",
            )
            if failure is not None:
                return failure

            user_row = ensure_app_user(conn, serial=serial, preferred_language=language_code)
            statistics, tier, achievements, promoted = sync_user_progress(conn, user_row)
            user_row = fetch_app_user(conn, user_row["id"])
            history = fetch_history(conn, user_row["id"], 10)
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="bootstrap",
                result="ok",
                reason="ready",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "User profile ready.",
                "profile": serialize_profile(user_row),
                "statistics": statistics,
                "history": history,
                "achievements": achievements,
                "tier": serialize_tier(tier),
                "promoted": promoted,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/user/profile/update")
def update_user_profile(payload: UserProfileUpdateRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    nickname = (payload.nickname or "").strip() or None
    language_code = clamp_language(payload.language_code, "zh")
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="profile_update",
            )
            if failure is not None:
                return failure

            user_row = context.user_row or ensure_app_user(conn, serial=serial, preferred_language=language_code)
            country_code = (payload.country_code or "").strip().upper() or user_row["country_code"]
            avatar_color = clamp_avatar_color(payload.avatar_color, user_row["avatar_color"])
            with conn.cursor() as cur:
                cur.execute(
                    """
                    UPDATE app_users
                    SET nickname = %s,
                        language_code = %s,
                        country_code = %s,
                        avatar_color = %s,
                        last_seen_at = %s
                    WHERE id = %s
                    """,
                    (
                        nickname or user_row["nickname"],
                        language_code,
                        country_code,
                        avatar_color,
                        utc_now(),
                        user_row["id"],
                    ),
                )
                cur.execute(
                    f"""
                    SELECT {APP_USER_SELECT_COLUMNS}
                    FROM app_users
                    WHERE id = %s
                    """,
                    (user_row["id"],),
                )
                updated_user = cur.fetchone()

            statistics, tier, achievements, promoted = sync_user_progress(conn, updated_user)
            updated_user = fetch_app_user(conn, updated_user["id"])
            history = fetch_history(conn, updated_user["id"], 10)
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="profile_update",
                result="ok",
                reason="updated",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "Profile updated.",
                "profile": serialize_profile(updated_user),
                "statistics": statistics,
                "history": history,
                "achievements": achievements,
                "tier": serialize_tier(tier),
                "promoted": promoted,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/user/statistics")
def user_statistics(payload: UserStatisticsRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="statistics",
            )
            if failure is not None:
                return failure

            user_row = context.user_row or ensure_app_user(conn, serial=serial)
            statistics, tier, achievements, promoted = sync_user_progress(conn, user_row)
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="statistics",
                result="ok",
                reason="ready",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "Statistics ready.",
                "statistics": statistics,
                "achievements": achievements,
                "tier": serialize_tier(tier),
                "promoted": promoted,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/training/history")
def training_history(payload: TrainingHistoryRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="history",
            )
            if failure is not None:
                return failure

            user_row = context.user_row or ensure_app_user(conn, serial=serial)
            statistics, tier, achievements, promoted = sync_user_progress(conn, user_row)
            history = fetch_history(conn, user_row["id"], payload.limit)
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="history",
                result="ok",
                reason=f"limit_{payload.limit}",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "History ready.",
                "history": history,
                "statistics": statistics,
                "achievements": achievements,
                "tier": serialize_tier(tier),
                "promoted": promoted,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/training/session")
def create_training_session(payload: TrainingSessionCreateRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if payload.mode_seconds <= 0 or payload.mode_seconds > MAX_TRAINING_SESSION_SECONDS:
        raise HTTPException(status_code=400, detail="mode_seconds must be between 1 and 600")

    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    ip = client_ip(request)
    started_at = utc_from_epoch_ms(payload.started_at_epoch_ms)
    ended_at = utc_from_epoch_ms(payload.ended_at_epoch_ms) or utc_now()

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="session_create",
            )
            if failure is not None:
                return failure

            user_row = context.user_row or ensure_app_user(conn, serial=serial)
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO training_sessions
                    (user_id, serial, mode_seconds, total_hits, average_frequency, best_burst_count,
                     best_burst_start_sec, started_at, ended_at, device_hash, app_version, created_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        user_row["id"],
                        serial,
                        payload.mode_seconds,
                        payload.total_hits,
                        payload.average_frequency,
                        payload.best_burst_count,
                        payload.best_burst_start_sec,
                        started_at,
                        ended_at,
                        device_hash,
                        app_version,
                        utc_now(),
                    ),
                )
                session_id = cur.lastrowid

            statistics, tier, achievements, promoted = sync_user_progress(conn, user_row)
            user_row = fetch_app_user(conn, user_row["id"])
            history = fetch_history(conn, user_row["id"], 10)
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="session_create",
                result="ok",
                reason=f"mode_{payload.mode_seconds}",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "Training session saved.",
                "session_id": session_id,
                "profile": serialize_profile(user_row),
                "statistics": statistics,
                "history": history,
                "achievements": achievements,
                "tier": serialize_tier(tier),
                "promoted": promoted,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/leaderboard")
def leaderboard(payload: LeaderboardRequest, request: Request):
    try:
        serial = normalize_serial(payload.serial)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    board_key = normalize_leaderboard_key(payload.board_key, payload.mode_seconds)
    window = clamp_window(payload.window)
    install_id = payload.install_id.strip()
    device_hash = payload.device_hash.strip()
    token = payload.activation_token.strip()
    app_version = (payload.app_version or "").strip() or None
    ip = client_ip(request)

    with get_conn() as conn:
        try:
            context, failure = authorize_request(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                activation_token=token,
                app_version=app_version,
                ip=ip,
                event_type="leaderboard",
            )
            if failure is not None:
                return failure

            user_row = context.user_row or ensure_app_user(conn, serial=serial)
            sync_user_progress(conn, user_row)
            user_row = fetch_app_user(conn, user_row["id"])
            top_entries, my_entry = fetch_leaderboard(
                conn,
                board_key=board_key,
                window=window,
                limit=payload.limit,
                my_user_id=user_row["id"],
            )
            write_log(
                conn,
                serial=serial,
                install_id=install_id,
                device_hash=device_hash,
                event_type="leaderboard",
                result="ok",
                reason=f"{board_key}_{window}",
                ip=ip,
            )
            conn.commit()
            return {
                "status": "ok",
                "message": "Leaderboard ready.",
                "board_key": board_key,
                "mode_seconds": 0,
                "window": window,
                "top": top_entries,
                "me": my_entry,
            }
        except Exception:
            conn.rollback()
            raise


@app.post("/api/v1/admin/reset")
def admin_reset(
    payload: ResetRequest,
    request: Request,
    x_admin_token: str | None = Header(default=None),
):
    settings = load_settings()
    if x_admin_token != settings.admin_token:
        raise HTTPException(status_code=401, detail="invalid admin token")

    return {
        "status": "removed",
        "message": "Serial and activation reset has been removed.",
    }
