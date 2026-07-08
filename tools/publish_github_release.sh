#!/usr/bin/env bash
set -euo pipefail

REPO="zhaohongxin0/english-reader-android"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_DIR="$ROOT/android/EnglishReader"
BUILD_FILE="$PROJECT_DIR/app/build.gradle"

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*"\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -n 1)"
if [[ -z "$VERSION_NAME" ]]; then
  echo "Could not read versionName from $BUILD_FILE" >&2
  exit 1
fi

TAG="v$VERSION_NAME"
APK_SOURCE="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_DIR="$ROOT/output/releases"
APK_NAME="english-reader-$TAG.apk"
APK_PATH="$RELEASE_DIR/$APK_NAME"

cd "$PROJECT_DIR"
gradle :app:assembleDebug --no-daemon

mkdir -p "$RELEASE_DIR"
cp "$APK_SOURCE" "$APK_PATH"

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" "$APK_PATH" --repo "$REPO" --clobber
else
  gh release create "$TAG" "$APK_PATH" \
    --repo "$REPO" \
    --title "English Reader $TAG" \
    --notes "English Reader Android APK $TAG"
fi

echo "Published $APK_NAME to https://github.com/$REPO/releases/tag/$TAG"
