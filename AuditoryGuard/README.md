# AuditoryGuard

A real-time road hazard detection Android app that runs in the background and alerts the driver using distinct audio tones.

**This is an educational Proof of Concept (POC). It does not replace safe driving practices.**

## Features

- **Object Detection**: Detects Person, Car, and Red Traffic Light using on-device MediaPipe + EfficientDet
- **Audio Alerts**:
  - Person: High-pitch rapid double-beep
  - Car: Medium-pitch repeating beep
  - Red Light: Low-pitch long tone
- **Smart Filtering**: Confidence > 0.80, spatial proximity filter (>2% of image area), 4-second cooldown per class
- **Background Operation**: Runs as a Foreground Service (works with screen off/locked)
- **Low Latency**: Optimized for < 500ms response using CameraX and 640x480 resolution

## Tech Stack

- **Language**: Kotlin
- **Camera**: CameraX
- **AI**: MediaPipe Tasks Vision (efficientdet_lite2.tflite)
- **Audio**: SoundPool (low latency)
- **Background**: Foreground Service + Notification
- **Min SDK**: 28 (Android 9+)

## Project Structure

```
AuditoryGuard/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/auditoryguard/
│   │   │   ├── MainActivity.kt
│   │   │   ├── HazardDetector.kt
│   │   │   ├── AlertManager.kt
│   │   │   ├── ForegroundService.kt
│   │   │   ├── CameraHelper.kt
│   │   │   └── Utils.kt
│   │   ├── assets/efficientdet_lite2.tflite
│   │   └── res/raw/{alert_person.mp3, alert_car.mp3, alert_light.mp3}
├── build.gradle.kts
├── app/build.gradle.kts
├── gradlew
└── settings.gradle.kts
```

## Setup & Run

1. Open the `AuditoryGuard` folder in **Android Studio**
2. Sync Gradle
3. Build and run on a physical Android device (API 28+ recommended)
4. Grant Camera permission
5. Press **"Start AuditoryGuard"**

The app will run in the background and play audio alerts when hazards are detected.

## Important Notes

- All processing is **on-device** (no cloud, no internet required)
- No video is recorded or stored
- Uses rear camera only
- Battery usage is significant — the app shows warnings if CPU is high

## Disclaimer

This tool is for **educational and demonstration purposes only**. It is not intended for real-world driving assistance. Always prioritize safe driving and follow traffic laws.

---

Built from `spec.md` using opencode.

Last updated: June 2026
