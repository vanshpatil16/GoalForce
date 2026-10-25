# Attention OS session API

This is a local-network FastAPI service for syncing user-created focus-session
summaries and basic focus preferences. It stores the goal, planned and actual
focus duration, start time, completion status, optional self-reported
distraction count, preferred duration, and reminder preference in SQLite.
App-usage records, package names, notification content, and monitoring
preferences remain on the Android device and are not accepted by these APIs.

## Run locally

From the `backend` directory in PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
$env:ATTENTION_OS_DB_PATH = "$PWD\data\attention_os.sqlite3"
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

The health endpoint is `http://127.0.0.1:8000/healthz`. The interactive API
documentation is available at `/docs` while the local service is running.

## Connect an Android phone on the same Wi-Fi

Router port forwarding is not needed for a phone and laptop on the same
trusted Wi-Fi network. Keep the router's internet/WAN port forwarding disabled.

1. Start the API with `uvicorn app.main:app --host 0.0.0.0 --port 8000`.
   Binding to `0.0.0.0` lets other devices on the local network connect.
2. On the laptop, run `ipconfig` and find the IPv4 address of the Wi-Fi
   adapter, for example `192.168.1.20`. Do not use `127.0.0.1`; that address
   points back to the phone when entered on the phone.
3. If Windows Firewall blocks the connection, in an elevated PowerShell window
   allow port 8000 only on the Private profile and local subnet:

   ```powershell
   New-NetFirewallRule -DisplayName 'Attention OS FastAPI (Private Wi-Fi)' `
     -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 `
     -Profile Private -RemoteAddress LocalSubnet
   ```

4. On the phone, open `http://192.168.1.20:8000/healthz` in its browser (replace
   the IP). It should display `{"status":"ok"}`.
5. Build/install the LAN-testing debug APK, open Settings → Session sync
   backend, enter `http://192.168.1.20:8000`, and save it. The app sends session
   summaries only when a focus session ends.

For this debug-only LAN test, HTTP is limited in the Dart client to private
IPv4 addresses. Android cleartext traffic is enabled only by the debug manifest;
release builds do not enable cleartext traffic and require HTTPS. Use a trusted
private network because HTTP does not encrypt traffic. Do not port-forward
port 8000 on your router or use this HTTP setup over public/shared Wi-Fi.

## Run API tests

```powershell
python -m pytest -q
```

## Container deployment

Build with `docker build -t attention-os-api .` from this directory. The
container listens on port `8000`. Mount a **persistent** volume at `/data`;
SQLite data will otherwise be lost when a hosting instance is recreated.
Terminate HTTPS at the hosting platform's TLS proxy. Do not expose the
prototype over public HTTP.

For a local container smoke test:

```powershell
docker volume create attention-os-data
docker run --rm -p 8000:8000 -v attention-os-data:/data attention-os-api
```

Each app installation supplies a cryptographically random 256-bit bearer
token. The API stores only its SHA-256 hash and scopes session reads/writes to
that token. This is a prototype device credential, not a user-account system.
For a public production deployment, add account recovery, abuse/rate limiting,
operational backups, and a documented data-retention/deletion policy before
collecting real personal data.

## API

- `GET /healthz` — health probe; no session data.
- `GET /v1/sessions` — list sessions for the bearer token.
- `POST /v1/sessions` — create or idempotently update a session owned by the
  bearer token.
- `GET /v1/preferences` — read the device's focus duration and reminder setting.
- `PUT /v1/preferences` — save those settings for the authenticated device.

The Android app supports local private-IPv4 HTTP only in a LAN-enabled debug
build; release builds require HTTPS. Leave the backend URL unset for offline
use. This prototype is intended for trusted local-network testing, not public
internet exposure.
