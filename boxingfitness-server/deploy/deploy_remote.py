from __future__ import annotations

import argparse
import io
import os
import posixpath
import secrets
import shlex
import tarfile
from pathlib import Path

import paramiko


EXCLUDE_DIRS = {".git", "__pycache__", ".venv", ".auth-deploy-venv"}
EXCLUDE_FILES = {".DS_Store"}


def run_command(
    client: paramiko.SSHClient,
    command: str,
    *,
    sudo_password: str | None = None,
    check: bool = True,
) -> tuple[int, str, str]:
    wrapped = command
    if sudo_password is not None:
        wrapped = f"echo {shlex.quote(sudo_password)} | sudo -S -p '' bash -lc {shlex.quote(command)}"
    stdin, stdout, stderr = client.exec_command(wrapped)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if check and exit_code != 0:
        raise RuntimeError(f"command failed ({exit_code}): {command}\nSTDOUT:\n{out}\nSTDERR:\n{err}")
    return exit_code, out, err


def iter_project_files(local_root: Path):
    for current_root, dirnames, filenames in os.walk(local_root):
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]
        relative = Path(current_root).relative_to(local_root)
        for filename in filenames:
            if filename in EXCLUDE_FILES:
                continue
            local_path = Path(current_root) / filename
            archive_name = filename if str(relative) == "." else posixpath.join(*relative.parts, filename)
            yield local_path, archive_name


def build_archive(local_root: Path) -> bytes:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        for local_path, archive_name in iter_project_files(local_root):
            tar.add(local_path, arcname=archive_name)
    return buffer.getvalue()


def upload_archive_via_ssh(
    client: paramiko.SSHClient,
    archive_bytes: bytes,
    remote_archive: str,
) -> None:
    stdin, stdout, stderr = client.exec_command(f"cat > {shlex.quote(remote_archive)}")
    stdin.channel.sendall(archive_bytes)
    stdin.channel.shutdown_write()
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if exit_code != 0:
        raise RuntimeError(f"archive upload failed\nSTDOUT:\n{out}\nSTDERR:\n{err}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--db-host", required=True)
    parser.add_argument("--db-name", default="BoxingFitness")
    parser.add_argument("--db-user", required=True)
    parser.add_argument("--db-password", required=True)
    parser.add_argument("--server-name", default=None, help="IP or domain used by nginx server_name")
    parser.add_argument("--admin-token", default=None)
    parser.add_argument("--code-pepper", default=None)
    parser.add_argument("--local-root", default=None)
    parser.add_argument("--remote-dir", default="/opt/boxingfitness-auth")
    args = parser.parse_args()

    local_root = Path(args.local_root) if args.local_root else Path(__file__).resolve().parents[1]
    server_name = args.server_name or args.host
    admin_token = args.admin_token or secrets.token_hex(24)
    code_pepper = args.code_pepper or secrets.token_hex(32)
    remote_tmp = f"/home/{args.username}/boxingfitness-auth-upload"
    remote_archive = f"/home/{args.username}/boxingfitness-auth-upload.tar.gz"

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(args.host, username=args.username, password=args.password, timeout=20)
    try:
        archive_bytes = build_archive(local_root)
        run_command(
            client,
            f"rm -rf {shlex.quote(remote_tmp)} {shlex.quote(remote_archive)} && mkdir -p {shlex.quote(remote_tmp)}",
        )
        upload_archive_via_ssh(client, archive_bytes, remote_archive)
        run_command(
            client,
            f"tar -xzf {shlex.quote(remote_archive)} -C {shlex.quote(remote_tmp)}",
        )

        run_command(
            client,
            f"""
            apt-get update
            apt-get install -y python3 python3-venv python3-pip mysql-client nginx curl
            mkdir -p /var/log/boxingfitness-auth
            mkdir -p /etc/nginx/snippets
            cp -r {shlex.quote(remote_tmp)}/. {shlex.quote(args.remote_dir)}/
            chown -R {shlex.quote(args.username)}:{shlex.quote(args.username)} {shlex.quote(args.remote_dir)} /var/log/boxingfitness-auth
            """.strip(),
            sudo_password=args.password,
        )

        run_command(
            client,
            f"""
            cd {shlex.quote(args.remote_dir)}
            python3 -m venv .venv
            .venv/bin/python -m pip install --upgrade pip
            .venv/bin/python -m pip install -r requirements.txt
            """.strip(),
        )

        env_content = f"""APP_NAME=boxingfitness-auth
APP_HOST=127.0.0.1
APP_PORT=8012
DB_HOST={args.db_host}
DB_PORT=3306
DB_NAME={args.db_name}
DB_USER={args.db_user}
DB_PASSWORD={args.db_password}
CODE_PEPPER={code_pepper}
ADMIN_TOKEN={admin_token}
DEFAULT_PRODUCT_CODE=BF01
UPLOAD_ROOT={args.remote_dir}/uploads
LOG_DIR=/var/log/boxingfitness-auth
"""
        run_command(
            client,
            f"cat > /etc/boxingfitness-auth.env <<'EOF'\n{env_content}EOF",
            sudo_password=args.password,
        )

        run_command(
            client,
            f"mysql -h {shlex.quote(args.db_host)} -u {shlex.quote(args.db_user)} -p{shlex.quote(args.db_password)} -e \"CREATE DATABASE IF NOT EXISTS {args.db_name} DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;\"",
            sudo_password=None,
        )
        run_command(
            client,
            f"mysql -h {shlex.quote(args.db_host)} -u {shlex.quote(args.db_user)} -p{shlex.quote(args.db_password)} {shlex.quote(args.db_name)} < {shlex.quote(args.remote_dir)}/sql/schema.sql",
        )

        run_command(
            client,
            f"""
            cp {shlex.quote(args.remote_dir)}/deploy/systemd/boxingfitness-auth.service /etc/systemd/system/boxingfitness-auth.service
            cp {shlex.quote(args.remote_dir)}/deploy/nginx/boxingfitness-auth-location.conf /etc/nginx/snippets/boxingfitness-auth-location.conf
            if [ -f /etc/nginx/sites-available/default ]; then
              python3 {shlex.quote(args.remote_dir)}/deploy/insert_nginx_location.py /etc/nginx/sites-available/default /etc/nginx/snippets/boxingfitness-auth-location.conf
            else
              sed -i 's#server_name .*#server_name {server_name};#' {shlex.quote(args.remote_dir)}/deploy/nginx/boxingfitness-auth.conf
              cp {shlex.quote(args.remote_dir)}/deploy/nginx/boxingfitness-auth.conf /etc/nginx/sites-available/boxingfitness-auth.conf
              ln -sf /etc/nginx/sites-available/boxingfitness-auth.conf /etc/nginx/sites-enabled/boxingfitness-auth.conf
            fi
            systemctl daemon-reload
            systemctl enable boxingfitness-auth
            systemctl restart boxingfitness-auth
            nginx -t
            systemctl reload nginx
            """.strip(),
            sudo_password=args.password,
        )

        _, app_health, _ = run_command(client, "curl -s http://127.0.0.1:8012/health")
        _, nginx_health, _ = run_command(client, "curl -s http://127.0.0.1/boxingfitness/health")

        print("DEPLOY_OK")
        print(f"server=http://{args.host}")
        print(f"admin_token={admin_token}")
        print(f"code_pepper={code_pepper}")
        print(f"app_health={app_health.strip()}")
        print(f"nginx_health={nginx_health.strip()}")
    finally:
        client.close()


if __name__ == "__main__":
    main()


