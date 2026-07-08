#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
APK="$ROOT/android/EnglishReader/app/build/outputs/apk/debug/app-debug.apk"
SERIAL="${ANDROID_SERIAL:-}"

cd "$ROOT/android/EnglishReader"
gradle :app:assembleDebug --no-daemon

if [ -z "$SERIAL" ]; then
  SERIAL="$("$ADB" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1; exit }')"
fi

if [ -z "$SERIAL" ]; then
  echo "No running Android emulator found. Run tools/run_englishreader_emulator.sh first." >&2
  exit 1
fi

"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP
"$ADB" -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell cmd media_session volume --stream 3 --set 15 >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell am start -n com.zhaolq.englishreader/.MainActivity

if [ "${OPEN_LESSON:-1}" = "1" ]; then
  sleep 1
  "$ADB" -s "$SERIAL" shell input tap 540 460
fi

echo "Installed and opened EnglishReader on $SERIAL."
