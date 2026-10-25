#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Wrapper for the controlled Termux build script.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/build-termux.sh" --debug "$@"
