# AndroTask

A no-root Android macro recorder app powered by Accessibility Services. Record, edit, and replay touch gestures — no programming knowledge required.

## Features

- **Record** touch, swipe, pinch, and long-press gestures
- **Playback** macros with adjustable speed and loop count
- **Edit** individual steps (delay, coordinates, action type)
- **Trigger** macros via floating button, schedule, or notification tile
- **No root required** — uses Android Accessibility Services
- **No Android Studio** — builds entirely from CLI (Termux / Debian)

## Requirements

- Android 8.0+ (API 26+)
- JDK 17
- Gradle 8.x

## Build from CLI

```bash
git clone https://github.com/BorgorNinja/AndroTask.git
cd AndroTask
chmod +x gradlew
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Accessibility Permission

AndroTask requires Accessibility Service to be enabled manually:

> Settings > Accessibility > AndroTask > Enable

This grants the app the ability to dispatch touch events on your behalf — no root needed.

## License

MIT
