#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD_NAME="${AVD_NAME:-EnglishReader_Visual}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG="$ROOT/logs/englishreader_emulator.log"
PID_FILE="$ROOT/tmp/android-test/emulator.pid"

mkdir -p "$(dirname "$LOG")" "$(dirname "$PID_FILE")"

if pgrep -f "qemu-system.*@${AVD_NAME}" >/dev/null; then
  echo "Emulator ${AVD_NAME} is already running."
else
  nohup "$SDK/emulator/emulator" "@${AVD_NAME}" -no-snapshot -no-boot-anim -gpu swiftshader_indirect >"$LOG" 2>&1 &
  echo "$!" >"$PID_FILE"
  echo "Started emulator ${AVD_NAME}; log: $LOG"
fi

"$ADB" wait-for-device
for _ in $(seq 1 120); do
  if "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -qx 1; then
    "$ADB" shell cmd media_session volume --stream 3 --set 15 >/dev/null 2>&1 || true
    echo "Emulator boot completed."
    "$ADB" devices -l
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for emulator boot." >&2
exit 1
