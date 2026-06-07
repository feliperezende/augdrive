# AuditoryGuard — Agent Guide

## Project Overview

Real-time road hazard detection Android app that runs in the background and alerts the driver using distinct audio tones. Educational POC, not a replacement for safe driving.

- **Language**: Kotlin 1.9.24
- **Build System**: Gradle (Kotlin DSL), Android Plugin 8.5.0
- **Min SDK**: 28 (Android 9+)
- **Compile SDK**: 34
- **AI**: MediaPipe Tasks Vision 0.10.14 (`efficientdet_lite2.tflite`)
- **Camera**: CameraX 1.3.4
- **Audio**: ToneGenerator via `AudioManager.STREAM_MUSIC`
- **Concurrency**: kotlinx-coroutines-android 1.8.1
- **Background**: Foreground Service + Notification (`foregroundServiceType="camera"`)

## Directory Structure

```
AuditoryGuard/                       # Android Studio project root
├── app/src/main/
│   ├── java/com/example/auditoryguard/   # Source code
│   │   ├── MainActivity.kt              # UI, permissions, service binding, camera handoff
│   │   ├── ForegroundService.kt         # Foreground service with notification
│   │   ├── HazardDetector.kt            # MediaPipe inference + frame scheduling
│   │   ├── RiskAnalyzer.kt              # Temporal/spatial risk scoring
│   │   ├── RiskModels.kt                # RiskEvent, RiskEventType, TrackedDetection
│   │   ├── AlertManager.kt              # ToneGenerator audio alerts
│   │   ├── CameraHelper.kt              # CameraX configuration
│   │   ├── OverlayView.kt               # Bounding box & log rendering
│   │   ├── Utils.kt                     # Image conversion + constants
│   │   └── AuditoryGuardApp.kt          # Application class
│   ├── assets/efficientdet_lite2.tflite
│   ├── res/layout/activity_main.xml     # Landscape sidebar + preview layout
│   ├── res/values/strings.xml
│   └── res/raw/{alert_person.mp3, alert_car.mp3, alert_light.mp3}
├── app/src/main/AndroidManifest.xml
├── build.gradle.kts
├── app/build.gradle.kts
├── gradlew
├── settings.gradle.kts
└── .ai/
    ├── instructions/
    │   ├── context-builder.md
    │   └── knowledge-graph-builder.md
    └── artifacts/
        ├── project-context.yaml
        └── project-knowledge-graph.yaml
```

## Build & Run

1. Open the `AuditoryGuard` folder in **Android Studio**.
2. Sync Gradle.
3. Build and run on a **physical Android device** (API 28+ recommended).
4. Grant Camera and Notification permissions on first launch.
5. Press **"Start AuditoryGuard"**.

## Architecture Summary

```
CameraX → CameraHelper → HazardDetector → MediaPipe ObjectDetector
                                              ↓
OverlayView ← DetectionListener          RiskAnalyzer ← TrackedDetection history
                                              ↓
                                       processRiskEvents
                                              ↓
                                    AlertManager (ToneGenerator)
```

- **HazardDetector**: Runs inference on `Dispatchers.Default`; skips 2 of every 3 frames; drops frames if inference is in flight; confidence threshold 0.65; min area ratio 0.01 (1%)
- **RiskAnalyzer**: 2-second sliding history; rule-based scoring for pedestrian-in-path, vehicle-closing, red-light-ahead; pixel-level red/green/yellow analysis inside traffic-light bounding boxes
- **AlertManager**: Temporarily sets `STREAM_MUSIC` to max volume, plays tone, restores volume after 700–800ms
- **CameraHelper**: Uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` at 640×480; does not bind Preview in service context (no LifecycleOwner)
- **MainActivity**: Binds to ForegroundService; handles camera handoff between activity preview (onResume) and background service (onPause)
- **OverlayView**: Renders bounding boxes, labels, and a 6-entry scrolling log panel

## AI Instructions System

The `.ai/instructions/` directory contains project-specific skill definitions for context-building and knowledge-graph generation.

### Context Builder

**Purpose**: Extract a verified representation of project knowledge from available evidence.

**Input**: Source code, documentation, configuration, schemas.  
**Output**: `.ai/artifacts/project-context.yaml`

Runs in 12 phases:
1. Artifact Inventory      7. Inference Generation     11. Confidence Review
2. Source Coverage         8. Unknown Detection        12. Task Readiness
3. Domain Discovery        9. Conflict Detection
4. Entity Extraction      10. Evidence Catalog
5. Fact Extraction
6. Business Rule Extraction

Core principle: **Evidence first** — classify everything as Fact, Inference, or Unknown. No guessing.

### Knowledge Graph Builder

**Purpose**: Transform the verified project context into a navigable model of system behavior.

**Input**: `.ai/artifacts/project-context.yaml`  
**Output**: `.ai/artifacts/project-knowledge-graph.yaml`

Runs in 10 phases:
1. Domain Mapping          6. State Machine Construction
2. Relationship Extraction 7. Ownership Graph
3. Dependency Graph        8. Impact Analysis Graph
4. Entity Lifecycle        9. Bottleneck Detection
5. Flow Discovery         10. Risk Graph

Core rule: Every relationship edge must carry `classification` (`fact` or `inferred`) and `confidence`.

## Conventions

- **No cloud / no internet** — all processing is on-device.
- **No video recording** — only real-time detection.
- **Rear camera only**.
- **Landscape orientation only** (`screenOrientation="landscape"`).
- Uses physical Android device for testing (emulator camera support is unreliable).
- Gradle tasks should be run from the `AuditoryGuard/` subfolder, not the repo root.

## Important Notes

- This is an **educational Proof of Concept**.
- Battery usage is significant; the app shows warnings if CPU is high.
- The ML model (`efficientdet_lite2.tflite`) is bundled in `assets/`.
- Alert audio files live in `res/raw/`.
- Service returns `START_STICKY` to restart if killed by the system.
- `POSSIBLE_BRAKING` risk type is declared in `RiskEventType` but not currently generated by `RiskAnalyzer`.
