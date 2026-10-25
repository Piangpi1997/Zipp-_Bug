#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Safely cleans generated build outputs without touching user source files or signing credentials.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "== Zip_Bug Termux Clean =="
rm -rf "$PROJECT_ROOT/app/build" "$PROJECT_ROOT/build"
echo "Cleaned build directories in $PROJECT_ROOT"
