from __future__ import annotations

import argparse
from pathlib import Path


def insert_include(site_path: Path, snippet_path: str) -> bool:
    include_line = f"    include {snippet_path};"
    text = site_path.read_text(encoding="utf-8")
    if include_line.strip() in text:
        return False

    lines = text.splitlines()
    server_start = next((i for i, line in enumerate(lines) if line.strip().startswith("server {")), None)
    if server_start is None:
        raise RuntimeError(f"No server block found in {site_path}")

    depth = 0
    insert_at: int | None = None
    fallback_at: int | None = None
    for i in range(server_start, len(lines)):
        line = lines[i]
        if i > server_start and depth == 1 and line.lstrip().startswith("location "):
            insert_at = i
            break
        depth += line.count("{")
        depth -= line.count("}")
        if i > server_start and depth == 0:
            fallback_at = i
            break

    target = insert_at if insert_at is not None else fallback_at
    if target is None:
        raise RuntimeError(f"Could not find insertion point in {site_path}")

    backup_path = site_path.with_name(site_path.name + ".bak-BoxingFitness")
    if not backup_path.exists():
        backup_path.write_text(text, encoding="utf-8")

    lines.insert(target, include_line)
    site_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return True


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("site_path", type=Path)
    parser.add_argument("snippet_path")
    args = parser.parse_args()
    changed = insert_include(args.site_path, args.snippet_path)
    print("inserted" if changed else "already-present")


if __name__ == "__main__":
    main()

