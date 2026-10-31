# Zip_Bug Studio — Antigravity Android Studio

Zip_Bug is a **real Android development/runtime studio**. It is not a fake demo shell and it does not invent build, install, certification, engine, or provider success.

## Working foundation
- **BYO AI Providers** — OpenAI, Gemini and Claude with user-owned API keys.
- **Encrypted secrets** — provider keys are encrypted with Android Keystore / AES-GCM.
- **App Creator** — generates real Kotlin/XML Android source ZIPs.
- **Web Mini-App Creator** — generates real `app.json + HTML + CSS + JavaScript` ZIPs.
- **LIKEFIGMA Studio** — versioned and migration-aware project schema; multi-selection, touch drag/resize, grid snapping, align/distribute, layer order, undo/redo, debounced autosave/recovery, JSON import, and validated Android XML / Compose export.
- **AI ZIP Import** — safe `app.json + www/` import with canonical-path and extraction limits.
- **Local Runtime** — localhost web runtime on `127.0.0.1:3131`.
- **Termux Engine** — allow-listed Java/Python/Gradle/ffmpeg/yt-dlp/git/aapt2/zipalign commands.
- **Real command results** — Termux PendingIntent callback captures stdout, stderr, exit code and internal error data.
- **Persistent jobs** — Room stores queued/running/success/failed engine jobs.
- **APK Builder** — `/apkbuilder` invokes real `gradle --no-daemon assembleDebug`; only exit code 0 is success.
- **APK export** — after a successful build, Termux performs a separate real copy job to the configured shared-storage destination.
- **Termux Doctor** — checks Java, Gradle, Python, aapt2, SDK 34, ffmpeg and yt-dlp using a real Python process in Termux.
- **Social Recap Lab** — real authorized yt-dlp/ffmpeg audio-preparation job with captured logs, plus recap/dialogue/dub prompt workflow.
- **TTS Studio** — real Termux Edge-TTS setup/generation jobs with Burmese Nilar/Thiha voice presets and factual exit-code status.
- **CI** — Studio Node tests plus Java 17 + Gradle 8.9 + SDK 34 Android tests, debug APK build and APK signature verification.

## Command surface
- `/termux <approved-tool> [args]`
- `/apkbuilder [termux-project-path]`
- Planned orchestration commands: `/apcreator`, `/likefigma`, `/aizipper`, `/aicreator`

## Non-negotiable truth rules
- No fake install.
- No fake bug/fake success.
- No fake certification.
- No pretend build result.
- A success badge requires real process/provider evidence.
- API keys never belong in source control.
- Imported ZIPs never receive unrestricted shell access.

See `PLAN.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, and `docs/TERMUX_ENGINE.md`.
