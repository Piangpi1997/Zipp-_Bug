#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Zip_Bug // Antigravity Studio — On-Device Termux Build Script
# Expected Project Location: ~/OpenDots/Zip_Bug_Antigravity

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== ZIP_BUG ANTIGRAVITY ON-DEVICE TERMUX BUILD ==="
echo "Project root: $PROJECT_ROOT"
cd "$PROJECT_ROOT"

# 1. Environment Verification
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "$HOME/android-sdk" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    echo "Detected ANDROID_HOME at $ANDROID_HOME"
  else
    echo "ERROR: ANDROID_HOME is not set and $HOME/android-sdk not found."
    echo "Set export ANDROID_HOME=\$HOME/android-sdk in ~/.bashrc"
    exit 2
  fi
fi

if ! [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  echo "WARNING: Android platform 34 jar not detected at $ANDROID_HOME/platforms/android-34/android.jar"
fi

# 2. Configure Termux aapt2 override
mkdir -p "$HOME/.gradle"
AAPT2_PATH="$(command -v aapt2 2>/dev/null || true)"
if [ -n "$AAPT2_PATH" ]; then
  if ! grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
    echo "android.aapt2FromMavenOverride=$AAPT2_PATH" >> "$HOME/.gradle/gradle.properties"
    echo "Configured aapt2 override: $AAPT2_PATH"
  fi
else
  echo "WARNING: Native aapt2 not found in PATH. Install with: pkg install aapt2"
fi

# 3. Execute Build
echo "Starting Gradle clean assembleDebug..."
if [ -x "./gradlew" ]; then
  BUILD_CMD="./gradlew"
elif command -v gradle >/dev/null; then
  BUILD_CMD="gradle"
else
  echo "ERROR: Neither ./gradlew nor system gradle found."
  exit 1
fi

$BUILD_CMD --no-daemon clean assembleDebug

# 4. Verify Built APK Artifact
APK_OUTPUT="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_OUTPUT" ]; then
  echo "ERROR: Build completed but output APK not found at $APK_OUTPUT"
  exit 1
fi

APK_SIZE="$(wc -c < "$APK_OUTPUT" | tr -d ' ')"
echo "Build SUCCEEDED!"
echo "APK Artifact: $APK_OUTPUT ($APK_SIZE bytes)"

if command -v sha256sum >/dev/null; then
  SHA256="$(sha256sum "$APK_OUTPUT" | cut -d' ' -f1)"
  echo "APK SHA-256: $SHA256"
fi

# 5. Export to Shared Download Storage if available
TARGET_DOWNLOAD="/storage/emulated/0/Download/Zip_Bug-Antigravity-RC.apk"
TARGET_DIR="/storage/emulated/0/Download"

if [ -d "$TARGET_DIR" ] && [ -w "$TARGET_DIR" ]; then
  cp -f "$APK_OUTPUT" "$TARGET_DOWNLOAD"
  echo "Successfully exported APK to: $TARGET_DOWNLOAD"
else
  echo "Note: $TARGET_DIR not directly writable. (Run 'termux-setup-storage' to grant shared storage access)"
  echo "APK is available in project output: $PROJECT_ROOT/$APK_OUTPUT"
fi

echo "=== BUILD COMPLETE ==="
