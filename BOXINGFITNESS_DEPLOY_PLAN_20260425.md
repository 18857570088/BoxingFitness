# BoxingFitness Independent Deployment Plan

## Project Identity

- Project name: `BoxingFitness`
- New local project root: `D:\2026\202604\0425`
- Android project copy: `D:\2026\202604\0425\boxingfitness-android`
- Server project copy: `D:\2026\202604\0425\boxingfitness-server`
- Deploy assets copy: `D:\2026\202604\0425\boxingfitness-deploy`

## Chosen Names

- Database name: `BoxingFitness`
- Default product code: `BF01`
- App display name: `BoxingFitness`
- Android `applicationId`: `com.zclei.boxingfitness`
- Android APK filename: `boxingfitness.apk`
- API base URL: `http://152.136.62.157/boxingfitness/api/v1/`

## Server Layout

- Remote service directory: `/opt/boxingfitness-auth`
- Upload root: `/opt/boxingfitness-auth/uploads`
- Audio upload dir: `/opt/boxingfitness-auth/uploads/audio_samples`
- Standard-count upload dir: `/opt/boxingfitness-auth/uploads/standard_count_samples`
- Log dir: `/var/log/boxingfitness-auth`
- Environment file: `/etc/boxingfitness-auth.env`
- systemd service: `boxingfitness-auth.service`
- Nginx snippet: `boxingfitness-auth-location.conf`
- Nginx entry path: `/boxingfitness/`
- Service port: `8012`

## Local Files Already Prepared

- Android app id / app name / APK name / API path updated:
  - `D:\2026\202604\0425\boxingfitness-android\app\build.gradle.kts`
  - `D:\2026\202604\0425\boxingfitness-android\app\src\main\res\values\strings.xml`
  - `D:\2026\202604\0425\boxingfitness-android\app\src\main\java\com\zclei\BoxingFitness\auth\ActivationService.kt`
  - `D:\2026\202604\0425\boxingfitness-android\app\src\main\java\com\zclei\BoxingFitness\cloud\CloudSyncService.kt`

- Independent server defaults prepared:
  - `D:\2026\202604\0425\boxingfitness-server\app\config.py`
  - `D:\2026\202604\0425\boxingfitness-server\.env.example`
  - `D:\2026\202604\0425\boxingfitness-server\sql\schema.sql`
  - `D:\2026\202604\0425\boxingfitness-server\deploy\systemd\boxingfitness-auth.service`
  - `D:\2026\202604\0425\boxingfitness-server\deploy\nginx\boxingfitness-auth-location.conf`
  - `D:\2026\202604\0425\boxingfitness-server\deploy\nginx\boxingfitness-auth.conf`
  - `D:\2026\202604\0425\boxingfitness-server\deploy\deploy_remote_single.py`
  - `D:\2026\202604\0425\boxingfitness-server\deploy\deploy_remote.py`

- Independent deploy examples prepared:
  - `D:\2026\202604\0425\boxingfitness-deploy\boxingfitness.env.example`
  - `D:\2026\202604\0425\boxingfitness-deploy\boxingfitness_create_database.sql`
  - `D:\2026\202604\0425\boxingfitness-deploy\boxingfitness_full_schema_ordered_20260415.sql`
  - `D:\2026\202604\0425\boxingfitness-deploy\boxingfitness_schema_from_reflex_auth_20260415.sql`
  - `D:\2026\202604\0425\boxingfitness-deploy\boxingfitness-auth.service.example`
  - `D:\2026\202604\0425\boxingfitness-deploy\nginx-boxingfitness-location.conf.example`

## Remote Deployment Command Template

The repository copy is ready, but actual remote deployment still requires:

- SSH username/password
- database username/password

Template command:

```powershell
python D:\2026\202604\0425\boxingfitness-server\deploy\deploy_remote_single.py `
  --host 152.136.62.157 `
  --username <ssh-user> `
  --password <ssh-password> `
  --db-host 152.136.62.157 `
  --db-name BoxingFitness `
  --db-user <db-user> `
  --db-password <db-password> `
  --server-name 152.136.62.157 `
  --local-root D:\2026\202604\0425\boxingfitness-server `
  --remote-dir /opt/boxingfitness-auth
```

## Notes

- This keeps the same auth / leaderboard / training-record architecture as the current project.
- The new project is isolated by database, remote directory, env file, upload path, log path, systemd service, and Nginx path.
- Kotlin package names were intentionally left as `com.zclei.BoxingFitness` to avoid a large refactor; the installable Android identity is isolated by `applicationId = com.zclei.boxingfitness`.




