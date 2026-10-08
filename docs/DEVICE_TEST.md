# Zip_Bug // Antigravity Studio — On-Device Physical Proof Guide

This document contains the exact, linear sequence to verify Zip_Bug Antigravity on a physical Android phone running Termux.

---

## 1. Linear On-Device Execution Sequence

### Phase A: Update Repository
Inside Termux:
```bash
cd ~/OpenDots/Zip_Bug_Antigravity
git fetch origin
git checkout feat/antigravity-studio-v2
git pull --ff-only origin feat/antigravity-studio-v2
git log -1 --oneline
```
*Expected Commit*: Matches latest commit on `feat/antigravity-studio-v2`.

---

### Phase B: Configure Termux Environment
Inside Termux:
```bash
# 1. Allow external app command execution
mkdir -p ~/.termux
grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings

# 2. Configure Android SDK 34 environment
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export ANDROID_SDK_ROOT=$ANDROID_HOME' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc

# 3. Grant shared storage access
termux-setup-storage
```

---

### Phase C: Verify Native Tools
Ensure all native dependencies are installed and available in `$PATH`:
```bash
command -v python
command -v java
command -v gradle
command -v ffmpeg
command -v aapt2
command -v yt-dlp
command -v edge-tts
```
If any tool is missing, install it:
```bash
pkg update -y
pkg install -y python openjdk-17 gradle ffmpeg aapt2
pip install --upgrade yt-dlp edge-tts
```

---

### Phase D: Build RC APK on Device
From `~/OpenDots/Zip_Bug_Antigravity`:
```bash
chmod +x scripts/build-termux.sh
./scripts/build-termux.sh
```
*Expected Output*:
- `BUILD SUCCESSFUL`
- Output APK created at `app/build/outputs/apk/debug/app-debug.apk`
- Exported copy at `/storage/emulated/0/Download/Zip_Bug-Antigravity-RC.apk`
- Real APK SHA-256 and byte size printed to console.

---

### Phase E: Install & Screen Navigation Check
1. Open your Android File Manager or Downloads app.
2. Install `/storage/emulated/0/Download/Zip_Bug-Antigravity-RC.apk`.
3. Launch **Zip_Bug Antigravity Studio**.
4. When prompted, grant **com.termux.permission.RUN_COMMAND** (*Allow Zip_Bug to run commands in Termux*). If denied, verify the app shows `FAIL` rather than `READY`.
5. Navigate through all app screens:
   - **Home**: System overview & stats.
   - **AI**: Provider selection & health check.
   - **Studio**: LikeFigma canvas, multi-screen navigation, export XML & Compose.
   - **Terminal**: Terminal log view & command buttons.
   - **Projects**: Project listing, APK scanner, Signing Diagnostics.
   - **Settings**: Termux home, project root, encrypted API key storage.
   - **Device & Engine Proof**: Pre-flight status and 5-step guided tests.
   - **Signing Diagnostics**: Compare installed APK against newest build artifact.

---

### Phase F: In-App 5-Step Guided Proof
1. Open **Terminal** tab → Tap **Device & Engine Proof**.
2. Tap **Run Guided 5-Step Proof**:
   - `TEST 1: Python` (`python --version`) → `PASS`
   - `TEST 2: Java` (`java -version`) → `PASS`
   - `TEST 3: Gradle` (`gradle --version`) → `PASS`
   - `TEST 4: FFmpeg` (`ffmpeg -version`) → `PASS`
   - `TEST 5: aapt2` (`aapt2 version`) → `PASS`
3. Tap **Copy Log** to copy the formatted execution report to your clipboard.

---

### Phase G: OpenRouter Physical Test
1. Navigate to **AI** tab.
2. Provider: select `OpenRouter • Free`.
3. Model: enter or confirm `openrouter/free`.
4. Ensure your OpenRouter API key is saved in Settings (stored in hardware-backed Android Keystore).
5. Tap **Health Check**:
   - Verify status transitions to `READY`.
6. Set Mode to `Chat`, enter prompt:
   ```text
   Return exactly:
   ZIP_BUG_OPENROUTER_OK
   ```
7. Tap **Send** and confirm the exact response from the OpenRouter endpoint.

---

### Phase H: App Creator Physical Flow
1. In the **AI** tab, switch Mode to **App Creator**.
2. Enter prompt:
   ```text
   Create a simple Android Kotlin/XML app with one screen, a title, one orange button, and dark background.
   ```
3. Tap **Send**.
4. Verify complete workflow stages:
   - `STAGE 1 (AI Response)`: Receives structured JSON spec.
   - `STAGE 2 (Validation)`: AppCreatorParser validates JSON.
   - `STAGE 3 (App Spec Review)`: Review sheet opens with permissions, dependencies, and file list.
   - `STAGE 4 (Generate Project)`: Tap **Generate Project**; verifies project is saved in Room database and disk directory.
   - `STAGE 5 (Build Project)`: Tap **Build APK**; dispatches `./gradlew assembleDebug` via Termux bridge.
   - `STAGE 6 (Artifact Inspection)`: Inspects newly built APK SHA-256 and signer diagnostics.

---

## 2. Evidence to Return

To complete RC1 certification, paste the following real outputs from your phone:
1. **Device Verification Log** (from the **Copy Log** button in Device & Engine Proof).
2. **Terminal Build Output** from `./scripts/build-termux.sh`.
3. **OpenRouter Response** (`ZIP_BUG_OPENROUTER_OK`).
