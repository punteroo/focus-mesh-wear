# FocusMesh Wear

FocusMesh Wear is a standalone Wear OS 5 application designed for the **Samsung Galaxy Watch Ultra** (One UI Watch 6 / Android 14 API 34). It implements a continuous focus and anti-vertigo recovery routine built around a minimal, uncluttered timer interface over an interactive 3D mesh background.

## Core Routine Philosophy

Extended screen exposure can trigger digitally induced dizziness and visual vertigo. FocusMesh Wear structures work around three distinct phases:

1. **Work (Red - Default 60 minutes)**: Deep focus session.
2. **Break (Light Blue - Default 15 minutes)**: Mandatory zero-screen recovery window to let the vestibular and visual systems rest.
3. **Readiness (Yellow - Manual Continue + Optional Grace Timer)**: Transition state reached after a break finishes. By default, the watch stays in Readiness indefinitely until manually advanced so you never feel rushed back to a screen. An optional, off-by-default grace countdown timer can be enabled in settings if you want a gentle reminder after a set number of minutes.

## Key Features

- **Uncluttered Full-Screen Timer**: Displays the active timer directly in the center of the watch face with minimal text.
- **Interactive 3D & 2D Mesh Background**:
  - **3D Mode (Default)**: A perspective-projected 3D wireframe and shaded surface mesh animated in real time, colored dynamically by the active phase (Red, Light Blue, Yellow).
  - **Touch Wave Physics**: Touching or dragging across the screen displaces the mesh like a fabric sheet or propagating liquid wave using spring-damped grid physics.
  - **2D Fallback Mode**: Can be toggled in settings to replace the 3D perspective mesh with a flat 2D mesh featuring subtle drift animations.
  - **Battery-Safe Ambient / Sleep Mode**: When the watch enters Always-On Display (AOD) or goes to sleep, mesh animations stop completely and render a static low-power outline.
- **Do Not Disturb (DND) Bypass Alerts**:
  - Routes audio alerts through `AudioAttributes.USAGE_ALARM` (`STREAM_ALARM`) and haptic alerts through `VibrationAttributes.USAGE_ALARM` paired with `AlarmManager.setExactAndAllowWhileIdle()`.
  - Continues to emit audible chimes and tactile vibrations even when Samsung Modes and Routines or Do Not Disturb are active.
- **Non-Robotic Acoustic Sounds & Custom Volume**:
  - Includes multi-harmonic acoustic chimes synthesized with natural exponential decay (Singing Bowl, Warm Marimba, Crystal Chime, Soft Gong) alongside access to native watch alarm tones via `RingtoneManager`.
  - Configurable alert mode: **Sound + Haptics** (default), **Sound Only**, **Haptics Only**, or **Silent**.
- **One UI Watch Island & Ongoing Activity**:
  - Integrates with the Wear OS `OngoingActivity` API so the active countdown appears as a floating status pill/island on the Galaxy Watch Ultra home watch face.
- **Left-Swipe Configuration Drawer**:
  - Swiping from the left edge of the main timer opens the configuration drawer without accidentally dismissing the app, allowing customization of Google Fonts, 3D/2D mesh mode, alert mode, volume, sound selection, and optional Readiness grace duration.
- **In-App Hardware Button Action**:
  - While the app is open and focused, pressing a secondary hardware watch stem button advances or toggles the timer without overriding your system-wide Galaxy Watch Ultra Quick Button binding outside the app.
- **Bilingual Support**:
  - Full localization in **English** (`values/strings.xml`) and **Spanish** (`values-es/strings.xml`).

## Documentation Index

- [Product Requirements Document (PRD)](docs/PRD.md)
- [Technical Architecture & Gesture Design](docs/ARCHITECTURE.md)
- [Audio Synthesis, System Ringtones & Haptics](docs/AUDIO_AND_HAPTICS.md)
- [First-Time Wear OS Setup & Galaxy Watch Ultra Deployment](docs/DEPLOYMENT_AND_TESTING.md)

## Repository Structure

```text
focus-mesh-wear/
|-- app/
|   |-- build.gradle.kts
|   `-- src/main/
|       |-- AndroidManifest.xml
|       |-- java/com/focusmesh/wear/
|       |   |-- alerts/       # DND-bypassing alarm audio, organic tone synthesis, and haptics
|       |   |-- data/         # Jetpack DataStore persistence for user preferences
|       |   |-- domain/model/ # Phase state machine, preferences, font, and sound models
|       |   |-- service/      # Foreground timer service, exact alarms, and OngoingActivity island
|       |   `-- ui/           # Compose screens, left-swipe pager, 3D/2D wave mesh, and themes
|       `-- res/
|           |-- values/       # English strings
|           `-- values-es/    # Spanish strings
|-- docs/                     # Detailed product and engineering documentation
|-- build.gradle.kts
|-- gradle.properties
`-- settings.gradle.kts
```
