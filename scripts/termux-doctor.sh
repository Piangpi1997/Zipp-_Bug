#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "== Zip_Bug Termux Doctor =="

java -version 2>&1 | head -1 || true
gradle --version 2>/dev/null | grep -m1 Gradle || true

echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
command -v aapt2 || true

if [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  echo "SDK 34: OK"
else
  echo "SDK 34: NOT FOUND"
fi
