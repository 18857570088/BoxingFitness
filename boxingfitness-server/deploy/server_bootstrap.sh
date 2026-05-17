#!/usr/bin/env bash
set -euo pipefail

sudo apt-get update
sudo apt-get install -y python3 python3-venv python3-pip mysql-client nginx

sudo mkdir -p /var/log/boxingfitness-auth
sudo mkdir -p /etc/nginx/snippets
sudo chown -R ubuntu:ubuntu /opt/boxingfitness-auth /var/log/boxingfitness-auth

cd /opt/boxingfitness-auth
python3 -m venv .venv
. .venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

sudo cp deploy/systemd/boxingfitness-auth.service /etc/systemd/system/boxingfitness-auth.service
sudo cp deploy/nginx/boxingfitness-auth-location.conf /etc/nginx/snippets/boxingfitness-auth-location.conf
if [ -f /etc/nginx/sites-available/default ]; then
  sudo python3 deploy/insert_nginx_location.py /etc/nginx/sites-available/default /etc/nginx/snippets/boxingfitness-auth-location.conf
else
  sudo cp deploy/nginx/boxingfitness-auth.conf /etc/nginx/sites-available/boxingfitness-auth.conf
  sudo ln -sf /etc/nginx/sites-available/boxingfitness-auth.conf /etc/nginx/sites-enabled/boxingfitness-auth.conf
fi
sudo systemctl daemon-reload
sudo systemctl enable boxingfitness-auth
sudo systemctl restart boxingfitness-auth
sudo nginx -t
sudo systemctl reload nginx
