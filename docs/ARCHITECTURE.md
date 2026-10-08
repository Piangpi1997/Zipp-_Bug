# Architecture

## Android side
Owns UI/UX, AI orchestration, project metadata, visual studio, safe ZIP import and sandboxed localhost runtime.

## Termux side
Owns heavy CLI execution: Gradle, Java, Python, ffmpeg, yt-dlp, git, aapt2 and zipalign.

The Android app must not expose arbitrary raw-shell execution to imported mini-apps.

## Mini-app contract
```json
{
  "name": "Demo",
  "slug": "demo",
  "version": "1.0.0",
  "kind": "web-mini-app",
  "entry": "www/index.html",
  "permissions": [],
  "requiredTools": []
}
```

## AI provider layer
Provider-neutral interface. API keys are entered by the user in Settings and encrypted with Android Keystore.

## Visual studio
LIKEFIGMA produces a provider-independent screen schema. Future generators target Android XML, Jetpack Compose and HTML/CSS.

## Build truth model
Each action moves through real states: queued -> running -> success/failed. Success requires a real process/provider response and recorded evidence such as exit code, artifact path or HTTP status.
