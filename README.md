# AuditoryGuard

A real-time road hazard detection Android app that runs in the background and alerts the driver using distinct audio tones.

**This is an educational Proof of Concept (POC). It does not replace safe driving practices.**

## Features

- **Object Detection**: Detects Person, Car, and Red Traffic Light using on-device MediaPipe + EfficientDet
- **Risk Analysis**: Rule-based temporal and spatial analysis for pedestrian-in-path, vehicle-closing, and red-light-ahead scenarios
- **Audio Alerts** (ToneGenerator):
  - Pedestrian in path: High-pitch emergency ringback tone
  - Vehicle closing: Medium-pitch alert call guard tone
  - Red light ahead: Low-pitch autoredial lite tone
- **Smart Filtering**: Confidence ≥ 0.65, spatial proximity filter (>1% of image area), 4-second cooldown per risk type
- **Frame Optimization**: Skips 2 of every 3 frames and drops frames while inference is in flight to maintain low latency
- **Background Operation**: Runs as a Foreground Service with `foregroundServiceType="camera"` (works with screen off/locked)
- **Camera Handoff**: Seamlessly transfers camera between activity preview and background service
- **Low Latency**: Optimized for sub-500ms response using CameraX at 640×480 resolution

## Tech Stack

- **Language**: Kotlin 1.9.24
- **Build**: Gradle Android Plugin 8.5.0
- **Camera**: CameraX 1.3.4
- **AI**: MediaPipe Tasks Vision 0.10.14 (`efficientdet_lite2.tflite`)
- **Audio**: ToneGenerator via `AudioManager.STREAM_MUSIC`
- **Concurrency**: kotlinx-coroutines-android 1.8.1
- **Background**: Foreground Service + Notification
- **Min SDK**: 28 (Android 9+)
- **Compile SDK**: 34
- **Target SDK**: 34

## Architecture

```
CameraX → CameraHelper → HazardDetector → MediaPipe ObjectDetector
                                              ↓
OverlayView ← DetectionListener          RiskAnalyzer ← TrackedDetection history
                                              ↓
                                       processRiskEvents
                                              ↓
                                    AlertManager (ToneGenerator)
```

- **CameraHelper**: Configures CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST` at 640×480
- **HazardDetector**: Runs inference on `Dispatchers.Default` with frame skipping and in-flight dropping
- **RiskAnalyzer**: 2-second sliding window history; analyzes pixel colors for red-light detection
- **AlertManager**: Temporarily maxes volume, plays tone, then restores original volume
- **OverlayView**: Draws bounding boxes, labels, and a scrolling 6-entry log panel

## Project Structure

```
AuditoryGuard/
├── app/src/main/
│   ├── java/com/example/auditoryguard/
│   │   ├── MainActivity.kt          # UI, permissions, service binding, camera handoff
│   │   ├── ForegroundService.kt     # Foreground service with notification
│   │   ├── HazardDetector.kt        # MediaPipe inference + frame scheduling
│   │   ├── RiskAnalyzer.kt          # Temporal/spatial risk scoring logic
│   │   ├── RiskModels.kt            # RiskEvent, RiskEventType, TrackedDetection
│   │   ├── AlertManager.kt          # ToneGenerator audio alerts
│   │   ├── CameraHelper.kt          # CameraX configuration
│   │   ├── OverlayView.kt           # Bounding box & log rendering
│   │   ├── Utils.kt                 # Image conversion + constants
│   │   └── AuditoryGuardApp.kt      # Application class
│   ├── assets/efficientdet_lite2.tflite
│   ├── res/layout/activity_main.xml # Landscape sidebar + preview layout
│   ├── res/values/strings.xml
│   └── res/raw/{alert_person.mp3, alert_car.mp3, alert_light.mp3}
├── app/src/main/AndroidManifest.xml
├── build.gradle.kts
├── app/build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── .ai/artifacts/
    ├── project-context.yaml
    └── project-knowledge-graph.yaml
```

## Setup & Run

1. Open the `AuditoryGuard` folder in **Android Studio**
2. Sync Gradle
3. Build and run on a **physical Android device** (API 28+ recommended)
4. Grant **Camera** and **Notifications** permissions on first launch
5. Press **"Start AuditoryGuard"**

The app will run in the background and play audio alerts when hazards are detected.

## Important Notes

- All processing is **on-device** (no cloud, no internet required)
- No video is recorded or stored
- Uses **rear camera only**
- Locked to **landscape** orientation (`screenOrientation="landscape"`)
- Battery usage is significant; the app shows warnings if CPU is high
- `START_STICKY` ensures the service restarts if killed by the system

## AI Knowledge Base

This project includes an AI instructions system under `.ai/instructions/`:

- **Context Builder** (`context-builder.md`): Extracts verified project facts, business rules, inferences, and conflicts into `.ai/artifacts/project-context.yaml`
- **Knowledge Graph Builder** (`knowledge-graph-builder.md`): Models relationships, dependencies, lifecycles, flows, state machines, and risks into `.ai/artifacts/project-knowledge-graph.yaml`

## Disclaimer

This tool is for **educational and demonstration purposes only**. It is not intended for real-world driving assistance. Always prioritize safe driving and follow traffic laws.

---

Built from `spec.md` using opencode.

Last updated: June 2026
