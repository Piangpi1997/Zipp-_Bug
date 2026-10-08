# Zip_Bug Professional Build Plan

## Phase A — Foundation — DONE
- Material 3 Zippy UI.
- Room project catalog.
- Encrypted BYO AI keys.
- Safe ZIP import and app manifest validation.
- Local web runtime on :3131.
- Termux allow-list bridge.
- Figma-like visual editor foundation.
- Social Recap workspace.

## Phase B — Real Build Engine — ACTIVE
- [x] Environment doctor for Java 17 / Gradle 8.9 / SDK 34 / aapt2.
- [x] Termux result PendingIntent integration.
- [x] Real stdout/stderr/internal error/exit-code capture.
- [x] Build jobs persisted in Room.
- [x] Real `/apkbuilder` command.
- [x] Post-build APK copy job to configured shared storage.
- [ ] APK artifact discovery for nonstandard module/output names.
- [ ] On-device APK signature verification before install handoff.
- [ ] Install handoff with package/version/signature comparison.
- [ ] Build cancellation, timeout and job history UI.

## Phase C — App Creator — ACTIVE
- [x] Native Kotlin/XML starter source generator.
- [x] HTML/CSS/JavaScript + JSON mini-app generator.
- [x] Source ZIP export through Android Storage Access Framework.
- [x] Generator and command-parser unit tests.
- [ ] INDEX -> formal app specification.
- [ ] AI spec -> reviewed file plan.
- [ ] Java/XML target.
- [ ] Jetpack Compose target.
- [ ] File tree/editor and diff preview.
- [ ] Compiler feedback -> AI repair loop.
- [ ] Full slash-command routing: /apcreator /likefigma /aizipper /aicreator.

## Phase D — Media Lab — ACTIVE
- [x] Authorized URL field and explicit authorization notice.
- [x] Real yt-dlp/ffmpeg audio-preparation job through Termux.
- [x] Real media stdout/stderr/exit-code UI.
- [x] Recap / Recap+Dialogue / Dub prompt builder.
- [x] Real Edge-TTS Termux setup and generation jobs.
- [x] Burmese Nilar/Thiha neural voice presets.
- [ ] Transcription pipeline.
- [ ] Transcript -> configured AI Provider one-tap recap.
- [ ] 1–18 clip timeline, freeze+zoom and transitions.
- [ ] Subtitle generation and final dub mux.

## Phase E — Professional Release
Plugin SDK, git workspaces, signed release profiles, tests, migrations, backup/restore, crash logs, accessibility, localization and release checklist.

## Engineering rule
UI state must be evidence-driven. `SUCCESS` is only emitted after a real provider response or process exit result proves success.
