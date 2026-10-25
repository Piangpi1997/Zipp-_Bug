# ZIP_BUG Companion HTTP Engine (`:8090`)

`scripts/engine-http-server.py` provides the minimal, authenticated HTTP engine compatible with `EngineConnectionClient`.

## Addressing Modes

1. **Same-Device Loopback (`http://127.0.0.1:8090`)**:
   - Refers to the Android device itself.
   - Works when `scripts/engine-http-server.py` is started inside Termux on the same phone, OR when a USB-connected PC forwards port `8090` via:
     ```bash
     adb reverse tcp:8090 tcp:8090
     ```
2. **PC / Companion LAN IP (`http://<pc-lan-ip>:8090`, e.g. `http://192.168.1.50:8090`)**:
   - Connects over Wi-Fi from the Android device to a PC on the same local network.

## Security Model

- **Mandatory Bearer Authentication**: Every endpoint (`/health`, `/api/jobs`, `/api/jobs/<id>/logs`, `/api/jobs/<id>/cancel`, `/api/jobs/<id>/artifact`) validates `Authorization: Bearer <token>` using constant-time comparison (`hmac.compare_digest`). Missing or invalid tokens return `HTTP 401 Unauthorized`.
- **No Arbitrary Shell Endpoint**: Only allow-listed tools (`python`, `java`, `node`, `npm`, `ffmpeg`, `yt-dlp`, `gradle`, `git`, `aapt2`, `edge-tts`) are permitted, executed with `shell=False`.
- **Canonical Path Verification**: Artifact downloads are restricted to files inside the job's canonical working directory.
