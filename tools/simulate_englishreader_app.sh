#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT/tools/run_englishreader_emulator.sh"
"$ROOT/tools/install_englishreader_apk.sh"
