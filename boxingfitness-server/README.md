# Reflex Ball Activation Server

Minimal short-term activation service for:

- 11-digit serial numbers
- 8-digit activation codes
- one-code-one-device binding
- device reset / transfer support

Recommended stack:

- FastAPI
- Uvicorn
- MySQL
- Nginx
- systemd

## Endpoints

- `GET /health`
- `POST /api/v1/activate`
- `POST /api/v1/check`
- `POST /api/v1/admin/reset`

## Local structure

- `app/`: API service
- `sql/schema.sql`: MySQL schema
- `scripts/generate_codes.py`: generate serial + activation code CSV
- `scripts/reset_serial.py`: manual reset helper
- `deploy/`: systemd / nginx templates

