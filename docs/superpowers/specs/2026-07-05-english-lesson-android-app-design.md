# English Lesson Android App Design

## Goal

Build a first Android APK for elementary English reading practice. Lesson 1 uses the existing desk/table illustration and eight sentences. The app supports sequential playback and tap-to-read playback, and future lessons can be added without changing the core player.

## First Version Scope

- Offline APK with bundled image and bundled MP3 audio.
- MiniMax TTS is used only during local asset generation.
- No API key or remote TTS call is included in the APK.
- Standard American English pronunciation is used for all sentence audio.
- The first lesson contains one full-page image, eight sentences, eight audio files, and eight tappable regions.

## App Structure

- `lessons/index.json` lists available lessons.
- Each lesson folder contains `lesson.json`, one image, and an `audio/` directory.
- `lesson.json` stores lesson title, image path, sentence text, audio path, and normalized tap region coordinates.
- The Android player reads the lesson manifest from assets, renders the image, and maps taps to sentence entries.

## User Flow

- The first screen shows available lessons; initially there is only Lesson 1.
- Opening a lesson shows the image and a compact control bar.
- Tapping a picture region plays that sentence and highlights the region.
- Play All reads sentences 1 through 8 in order.
- Stop cancels current playback and clears the sequence.

## Testing

- Build a debug APK locally.
- Install and run it on a local Android Emulator.
- Verify that the first lesson loads, Play All advances through all eight files, and each tap region plays the matching sentence.
