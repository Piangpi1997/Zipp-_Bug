# Zip_Bug // Antigravity Studio — On-Device Physical Proof Guide

This guide tracks the current implementation on `feat/antigravity-studio-v2`. It must be run against the latest branch HEAD before any physical result is classified as VERIFIED.

## 1. Sync the exact branch first

Inside Termux:

```bash
cd ~/OpenDots/Zip_Bug_Antigravity
git fetch origin
git checkout feat/antigravity-studio-v2
git pull --ff-only origin feat/antigravity-studio-v2
git log -1 --oneline
```

The commit shown here must match the current GitHub branch HEAD. Do not continue a release proof from an older RC snapshot.

## 2. Configure Termux

```bash
mkdir -p ~/.termux
grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || \
  echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings

export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

termux-setup-storage
```

Zip_Bug also requires Android permission `com.termux.permission.RUN_COMMAND`. If permission is denied, the app must report a permission failure rather than READY/SUCCESS.

## 3. Verify native tools

```bash
command -v python
command -v java
command -v gradle
command -v ffmpeg
command -v aapt2
command -v yt-dlp
command -v edge-tts
```

If required packages are missing, install them before continuing. Do not mark a dependency PASS just because a UI control exists.

## 4. Controlled debug APK build

From the repository root:

```bash
chmod +x scripts/build-termux.sh

./scripts/build-termux.sh \
  --debug \
  --export /storage/emulated/0/Download/Zip_Bug-debug.apk \
  --timeout-seconds 1200
```

The controlled script now reports explicit stages:

- `VALIDATION`
- `DEPENDENCIES`
- `BUILD`
- `PACKAGING`

A successful build must end with real `ZIPBUG_STAGE|...|PASS|...` records, an artifact proof record, and `ZIPBUG_RESULT|SUCCESS|debug`.

The exported APK must have non-zero size and matching SHA-256 with the built artifact. When `apksigner` is available, signature verification must also pass.

### Release signing boundary

`--release` is intentionally blocked until signing credentials are explicitly configured and reviewed. Zip_Bug must never silently create, replace, or guess a release keystore/password.

## 5. Install one signer lineage only

Debug APKs built on Windows, GitHub Actions, and Termux may use different debug certificates.

Before updating an already installed Zip_Bug package, compare signers. If signer hashes differ, Android will reject the update. Do not uninstall an app containing important local data until required secrets/settings are backed up.

For a clean physical proof, install one chosen APK lineage and keep that signer consistent for subsequent updates.

## 6. Screen/navigation proof

Open Zip_Bug and verify these real screens/actions:

- **Home** — grouped Build / Design / Media / Tools dashboard.
- **Command Help** — searchable `/help` registry with examples.
- **App Creator** — `/appcreator`; legacy `/apcreator` must still resolve.
- **Studio** — `/likefigma`.
- **AI** — provider/model selection and real provider response.
- **Terminal** — `/termux`, controlled `/apkbuilder --debug`, build stages, cancel, stdout/stderr/exitCode.
- **Projects** — imported mini-apps and APK artifact inspection.
- **Settings** — encrypted API keys, reveal/copy, encrypted backup/restore, Termux paths.
- **Device & Engine Proof** — guided native-tool checks.
- **Signing Diagnostics** — installed-vs-artifact signer comparison.

Reserved/non-working commands such as `/aizipper` must be shown as unavailable with an explanation, not simulated as working.

## 7. In-app guided engine proof

Open **Terminal → Device & Engine Proof** and run the guided checks:

1. Python: `python --version`
2. Java: `java -version`
3. Gradle: `gradle --version`
4. FFmpeg: `ffmpeg -version`
5. aapt2: `aapt2 version`

Each proof must include the actual execution state and exit result. Copy the formatted proof log after completion.

## 8. OpenRouter physical test

In **Settings**, save an OpenRouter key securely. Do not paste the key into Terminal logs, AI prompts, source ZIPs, or screenshots.

In **AI**:

- Provider: `OpenRouter • Free`
- Model: `openrouter/free`

Send:

```text
Return exactly:
ZIP_BUG_OPENROUTER_OK
```

A network/provider response is VERIFIED only when the real endpoint returns successfully.

## 9. App Creator physical flow

The AI App Creator flow is split deliberately between generation and build boundaries:

1. AI mode `App Creator` returns a structured project spec.
2. Parser validates the JSON.
3. Review sheet displays permissions, dependencies, and file plan.
4. Generate Project writes the project to Zip_Bug-managed storage and records it.
5. **Do not claim an APK build from app-private storage.** A generated Android project must first be exported/transferred to a Termux-accessible workspace.
6. From that Termux project root, run the controlled debug workflow:
   ```text
   /apkbuilder --debug /data/data/com.termux/files/home/<project-path>
   ```
7. Only after exit code 0 plus artifact validation may the APK be reported as built.
8. Inspect APK metadata, SHA-256, and signer before installation.

## 10. Zippy Cut / Long Recap physical proof

Use media you own, are authorized to process, or that is publicly accessible.

1. Open **Home → Zippy Cut** or run any of:
   - `/zippycut`
   - `/longrecap`
   - `/recap`
2. Create a new recap project and give it a name.
3. Use the Android file picker to select one local video or audio source.
4. Confirm the project survives closing and reopening Zip_Bug.
5. Choose script language (Myanmar or English) independently from UI language.
6. Generate one real AI script using an existing configured provider. Verify four persisted parts are produced and editable:
   - Part 1
   - Part 2
   - Part 3
   - Part 4
7. Generate one TTS preview/full voiceover. For Myanmar, select an available Burmese Edge-TTS voice. Verify the output audio file exists and is non-empty before accepting SUCCESS.
8. Build a timeline with at least one clip. Verify order and trim bounds, then enable only effects reported as supported.
9. Start the final render and capture the real persistent render states/logs. The expected pipeline is:
   `VALIDATION → MEDIA PREPARATION → CLIP PROCESSING → VOICEOVER → SUBTITLES → EFFECTS → FINAL ENCODE → OUTPUT VERIFICATION`
10. Do not accept SUCCESS unless the render process exits 0 and the final output file exists with size > 0 bytes.
11. Use **Open** to play the resulting video and **Share** to confirm the exported file is accessible.
12. Close and reopen Zip_Bug and confirm project, script parts, clip state, TTS reference, and render output are still attached to the project.

Return these Zippy Cut proof items:

- project name/id and selected source type (do not include secrets);
- AI script completion state and Part 1–4 presence;
- TTS job status and verified audio output path/URI;
- final render job status, exit code, and stage log;
- final video path/URI, byte size, and duration if available;
- any failure diagnosis shown by the app.

Physical Zippy Cut / Long Recap remains **PENDING** until this end-to-end flow produces a verified playable output on the phone.

## 11. Evidence required for DEVICE VERIFIED

Return all of the following:

1. Device Verification Log from the physical phone.
2. Controlled Termux build output showing stage records, exit code 0, and artifact proof.
3. OpenRouter live response `ZIP_BUG_OPENROUTER_OK`.
4. If App Creator is included in the claim, the Termux-accessible project path and its controlled APK build/artifact proof.

Until those outputs exist, physical Android and Termux status remains **PENDING**.
