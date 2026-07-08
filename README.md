# English Reader Android

An Android app for elementary English reading practice.

Lesson 1 teaches:

- this / that / these / those
- desk / table
- sentence reading with tap-to-read and play-all playback

## Build

```bash
cd android/EnglishReader
gradle :app:assembleDebug --no-daemon
```

The debug APK is generated at:

```text
android/EnglishReader/app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Update Flow

The app checks the latest public GitHub Release for this repository:

```text
https://github.com/zhaohongxin0/english-reader-android
```

When a newer release tag is available, for example `v1.2`, the app downloads the
first `.apk` asset from that release and opens the Android system installer.

Android does not allow silent APK installation from a normal app. On the first
update, the phone must grant "install unknown apps" permission to English Reader.

## Publish A New APK

1. Increase `versionCode` and `versionName` in `android/EnglishReader/app/build.gradle`.
2. Build the APK.
3. Create or update a GitHub Release whose tag matches `versionName`, for example `v1.2`.
4. Upload the APK asset to that release.

The helper script does steps 2-4:

```bash
bash tools/publish_github_release.sh
```
