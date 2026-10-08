#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

[ -n "${ANDROID_HOME:-}" ] || {
  echo "Set ANDROID_HOME"
  exit 2
}

mkdir -p "$HOME/.gradle"

if command -v aapt2 >/dev/null; then
  if ! grep -q '^android.aapt2FromMavenOverride=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
    echo "android.aapt2FromMavenOverride=$(command -v aapt2)" >> "$HOME/.gradle/gradle.properties"
  fi
fi

gradle --no-daemon clean assembleDebug

echo "APK: app/build/outputs/apk/debug/app-debug.apk"
