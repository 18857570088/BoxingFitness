from __future__ import annotations

import argparse
import base64
import io
import os
import posixpath
import secrets
import shlex
import tarfile
import textwrap
from pathlib import Path

import paramiko


EXCLUDE_DIRS = {".git", "__pycache__", ".venv", ".auth-deploy-venv"}
EXCLUDE_FILES = {".DS_Store"}


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


def shell_quote(value: str) -> str:
    return shlex.quote(value)


def make_script(
    *,
    username: str,
    remote_dir: str,
    db_host: str,
    db_name: str,
    db_user: str,
    db_password: str,
    admin_token: str,
    code_pepper: str,
    server_name: str,
    sudo_password: str,
    archive_b64: str,
) -> str:
    remote_tmp = f"/home/{username}/boxingfitness-auth-upload"
    remote_archive_b64 = f"/home/{username}/boxingfitness-auth-upload.b64"
    remote_archive = f"/home/{username}/boxingfitness-auth-upload.tar.gz"

    env_text = f"""APP_NAME=boxingfitness-auth
APP_HOST=127.0.0.1
APP_PORT=8012
DB_HOST={db_host}
DB_PORT=3306
DB_NAME={db_name}
DB_USER={db_user}
DB_PASSWORD={db_password}
CODE_PEPPER={code_pepper}
ADMIN_TOKEN={admin_token}
DEFAULT_PRODUCT_CODE=BF01
UPLOAD_ROOT={remote_dir}/uploads
LOG_DIR=/var/log/boxingfitness-auth
"""

    qpass = shell_quote(sudo_password)
    q_remote_dir = shell_quote(remote_dir)
    q_remote_tmp = shell_quote(remote_tmp)
    q_remote_archive_b64 = shell_quote(remote_archive_b64)
    q_remote_archive = shell_quote(remote_archive)
    q_db_host = shell_quote(db_host)
    q_db_name = shell_quote(db_name)
    q_db_user = shell_quote(db_user)
    q_db_password = shell_quote(db_password)
    q_server_name = shell_quote(server_name)

    script = f"""set -euo pipefail
SUDO_PASS={qpass}

sudo_run() {{
  echo "$SUDO_PASS" | sudo -S -p '' bash -lc "$1"
}}

rm -rf {q_remote_tmp} {q_remote_archive_b64} {q_remote_archive}
mkdir -p {q_remote_tmp}

cat > {q_remote_archive_b64} <<'__ARCHIVE__'
{archive_b64}
__ARCHIVE__

base64 -d {q_remote_archive_b64} > {q_remote_archive}
tar -xzf {q_remote_archive} -C {q_remote_tmp}

sudo_run "apt-get update"
sudo_run "DEBIAN_FRONTEND=noninteractive apt-get install -f -y"
sudo_run "DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv python3-pip mysql-client nginx curl"
sudo_run "mkdir -p /var/log/boxingfitness-auth"
sudo_run "mkdir -p /etc/nginx/snippets"
sudo_run "cp -r {q_remote_tmp}/. {q_remote_dir}/"
sudo_run "chown -R {shell_quote(username)}:{shell_quote(username)} {q_remote_dir} /var/log/boxingfitness-auth"

cd {q_remote_dir}
python3 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

cat > /tmp/boxingfitness-auth.env <<'__ENV__'
{env_text}
__ENV__
sudo_run "cp /tmp/boxingfitness-auth.env /etc/boxingfitness-auth.env"

mysql -h {q_db_host} -u {q_db_user} -p{q_db_password} -e "CREATE DATABASE IF NOT EXISTS {db_name} DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
mysql -h {q_db_host} -u {q_db_user} -p{q_db_password} {q_db_name} < {q_remote_dir}/sql/schema.sql

sudo_run "cp {remote_dir}/deploy/systemd/boxingfitness-auth.service /etc/systemd/system/boxingfitness-auth.service"
sudo_run "cp {remote_dir}/deploy/nginx/boxingfitness-auth-location.conf /etc/nginx/snippets/boxingfitness-auth-location.conf"
if [ -f /etc/nginx/sites-available/default ]; then
  sudo_run "python3 {remote_dir}/deploy/insert_nginx_location.py /etc/nginx/sites-available/default /etc/nginx/snippets/boxingfitness-auth-location.conf"
else
  sudo_run "sed -i 's#server_name .*#server_name {server_name};#' {remote_dir}/deploy/nginx/boxingfitness-auth.conf"
  sudo_run "cp {remote_dir}/deploy/nginx/boxingfitness-auth.conf /etc/nginx/sites-available/boxingfitness-auth.conf"
  sudo_run "ln -sf /etc/nginx/sites-available/boxingfitness-auth.conf /etc/nginx/sites-enabled/boxingfitness-auth.conf"
fi
sudo_run "systemctl daemon-reload"
sudo_run "systemctl enable boxingfitness-auth"
sudo_run "systemctl restart boxingfitness-auth"
sudo_run "nginx -t"
sudo_run "systemctl reload nginx"

APP_HEALTH=$(curl -s http://127.0.0.1:8012/health)
NGINX_HEALTH=$(curl -s http://127.0.0.1/boxingfitness/health)

echo "__DEPLOY_OK__"
echo "server=http://{server_name}"
echo "admin_token={admin_token}"
echo "code_pepper={code_pepper}"
echo "app_health=$APP_HEALTH"
echo "nginx_health=$NGINX_HEALTH"
"""
    return script


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--db-host", required=True)
    parser.add_argument("--db-name", default="BoxingFitness")
    parser.add_argument("--db-user", required=True)
    parser.add_argument("--db-password", required=True)
    parser.add_argument("--server-name", default=None)
    parser.add_argument("--admin-token", default=None)
    parser.add_argument("--code-pepper", default=None)
    parser.add_argument("--local-root", default=None)
    parser.add_argument("--remote-dir", default="/opt/boxingfitness-auth")
    args = parser.parse_args()

    local_root = Path(args.local_root) if args.local_root else Path(__file__).resolve().parents[1]
    archive_bytes = build_archive(local_root)
    archive_b64 = "\n".join(textwrap.wrap(base64.b64encode(archive_bytes).decode("ascii"), 120))
    admin_token = args.admin_token or secrets.token_hex(24)
    code_pepper = args.code_pepper or secrets.token_hex(32)
    server_name = args.server_name or args.host

    script = make_script(
        username=args.username,
        remote_dir=args.remote_dir,
        db_host=args.db_host,
        db_name=args.db_name,
        db_user=args.db_user,
        db_password=args.db_password,
        admin_token=admin_token,
        code_pepper=code_pepper,
        server_name=server_name,
        sudo_password=args.password,
        archive_b64=archive_b64,
    )

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(args.host, username=args.username, password=args.password, timeout=20)
    try:
        stdin, stdout, stderr = client.exec_command("bash -s", timeout=900)
        stdin.write(script)
        stdin.channel.shutdown_write()
        exit_code = stdout.channel.recv_exit_status()
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        if exit_code != 0:
            raise RuntimeError(f"deploy failed ({exit_code})\nSTDOUT:\n{out}\nSTDERR:\n{err}")
        print(out)
        if err.strip():
            print("STDERR:")
            print(err)
    finally:
        client.close()


if __name__ == "__main__":
    main()


