# Zip_Bug // Antigravity Studio — On-Device Termux Verification Guide

This document details the exact, reproducible testing procedure to verify Zip_Bug Antigravity on a physical Android device paired with Termux.

---

## 1. Prerequisites & Termux Environment Setup

### 1.1 Install Termux
Install the official Termux release from **F-Droid** or the **Termux GitHub repository** (Google Play versions are obsolete and restricted).

### 1.2 Enable External App Commands
Zip_Bug communicates with Termux using `com.termux.permission.RUN_COMMAND`. For third-party apps to dispatch commands, external execution must be explicitly allowed.

Inside Termux, execute:
```bash
mkdir -p ~/.termux
grep -q "allow-external-apps=true" ~/.termux/termux.properties 2>/dev/null || echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

### 1.3 Configure Android SDK Environment
Ensure `ANDROID_HOME` points to the mobile Android SDK root:
```bash
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export ANDROID_SDK_ROOT=$ANDROID_HOME' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc
```

Verify that the Android 34 platform jar exists:
```bash
ls -la $ANDROID_HOME/platforms/android-34/android.jar
```

### 1.4 Install Native Packages
Install the required compilation and media packages:
```bash
pkg update -y
pkg install -y git python openjdk-17 gradle ffmpeg aapt2
```

### 1.5 Install Python Audio / Video Packages
Install Edge-TTS and yt-dlp:
```bash
pip install --upgrade yt-dlp edge-tts
```

### 1.6 Storage Permission
Grant Termux access to shared Android storage:
```bash
termux-setup-storage
```

---

## 2. Canonical Repository Clone

Clone or synchronize the project at the standard location:
```bash
mkdir -p ~/OpenDots
cd ~/OpenDots
git clone -b feat/antigravity-studio-v2 https://github.com/Piangpi1997/Zipp-_Bug.git Zip_Bug_Antigravity
cd Zip_Bug_Antigravity
```

Verify the working branch and commit:
```bash
git branch --show-current
git log -1 --oneline
```
Expected output:
- Branch: `feat/antigravity-studio-v2`
- Commit HEAD: `77112c7` (or newer)

---

## 3. The 5-Step Command Proof Suite

Run each command manually or trigger them via **Zip_Bug → Terminal → Device & Engine Proof → Run Guided 5-Step Proof**.

### Test 1: Python
```bash
python --version
```
- **PASS**: `Python 3.11.x` or `Python 3.12.x` (exit code `0`)
- **FAIL**: `bash: command not found: python` (exit code `127`)
- *Resolution*: `pkg install python`

### Test 2: Java
```bash
java -version
```
- **PASS**:
  ```text
  openjdk version "17.0.x" ...
  OpenJDK Runtime Environment ...
  ```
  (exit code `0`)
- **FAIL**: `bash: java: command not found` or version `< 17`
- *Resolution*: `pkg install openjdk-17`

### Test 3: Gradle
```bash
gradle --version
```
- **PASS**:
  ```text
  ------------------------------------------------------------
  Gradle 8.9 (or newer)
  ------------------------------------------------------------
  ```
  (exit code `0`)
- **FAIL**: `bash: gradle: command not found`
- *Resolution*: `pkg install gradle`

### Test 4: FFmpeg
```bash
ffmpeg -version
```
- **PASS**: `ffmpeg version 6.x` or `7.x ...` (exit code `0`)
- **FAIL**: `bash: ffmpeg: command not found`
- *Resolution*: `pkg install ffmpeg`

### Test 5: aapt2 Native Binary
```bash
aapt2 version
```
- **PASS**: `Android Asset Packaging Tool (aapt) 2.19-...` (exit code `0`)
- **FAIL**: `bash: aapt2: command not found`
- *Resolution*: `pkg install aapt2` and ensure `~/.gradle/gradle.properties` contains:
  ```properties
  android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
  ```

---

## 4. On-Device APK Build Procedure

From within `~/OpenDots/Zip_Bug_Antigravity`, execute the automated build script:
```bash
chmod +x scripts/build-termux.sh
./scripts/build-termux.sh
```

### Expected Successful Output:
```text
=== ZIP_BUG ANTIGRAVITY ON-DEVICE TERMUX BUILD ===
Project root: /data/data/com.termux/files/home/OpenDots/Zip_Bug_Antigravity
Detected ANDROID_HOME at /data/data/com.termux/files/home/android-sdk
Configured aapt2 override: /data/data/com.termux/files/usr/bin/aapt2
Starting Gradle clean assembleDebug...
...
BUILD SUCCESSFUL in ...
Build SUCCEEDED!
APK Artifact: app/build/outputs/apk/debug/app-debug.apk (6999640 bytes)
APK SHA-256: ...
Successfully exported APK to: /storage/emulated/0/Download/Zip_Bug-Antigravity-RC.apk
=== BUILD COMPLETE ===
```

---

## 5. In-App Verification Screens

1. **Device & Engine Proof**:
   - Navigate to **Terminal** tab → Tap **Device & Engine Proof**.
   - Check pre-flight table (15 checks).
   - Tap **Run Guided 5-Step Proof**: All 5 tests must display `PASS` and `ALL 5 TESTS PASSED`.
   - Tap **Copy Log** to export verification output to clipboard.
2. **Signing Diagnostics**:
   - Navigate to **Projects** tab → Tap **Signing Diagnostics**.
   - If previous installation exists with identical key: `SAFE UPDATE (MATCH)`.
   - If debug keystores differ: `SIGNER CONFLICT` with clear explanation.
3. **AI Health Check**:
   - Navigate to **AI Studio** tab → Provider `OpenRouter • Free` → Model `openrouter/free`.
   - Tap **Health Check**: Badge must transition to `READY` or report clear categorized HTTP status.
