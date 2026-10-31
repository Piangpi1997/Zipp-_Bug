#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "== Zip_Bug Termux Doctor =="

check_tool() {
  local name="$1"
  local fix="$2"
  if command -v "$name" >/dev/null 2>&1; then
    echo "[PASS] $name: $(command -v "$name")"
  else
    echo "[MISS] $name -> Fix: $fix"
  fi
}

check_tool "java" "pkg install openjdk-17"
check_tool "gradle" "pkg install gradle"
check_tool "python" "pkg install python"
check_tool "node" "pkg install nodejs"
check_tool "npm" "pkg install nodejs"
check_tool "aapt2" "pkg install aapt2"
check_tool "ffmpeg" "pkg install ffmpeg"
check_tool "yt-dlp" "pip install --upgrade yt-dlp"
check_tool "edge-tts" "pip install --upgrade edge-tts"

echo "ANDROID_HOME=${ANDROID_HOME:-unset}"
if [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  echo "[PASS] Android SDK 34: $ANDROID_HOME/platforms/android-34/android.jar"
elif [ -f "$HOME/android-sdk/platforms/android-34/android.jar" ]; then
  echo "[PASS] Android SDK 34: $HOME/android-sdk/platforms/android-34/android.jar"
else
  echo "[MISS] Android SDK 34 -> Place android-34 platform in \$HOME/android-sdk/platforms/android-34/android.jar"
fi

if grep -q '^allow-external-apps=true' "$HOME/.termux/termux.properties" 2>/dev/null; then
  echo "[PASS] allow-external-apps=true"
else
  echo "[MISS] allow-external-apps=true -> Run: mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"
fi
