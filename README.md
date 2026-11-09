# FocusMesh Wear

FocusMesh Wear is a standalone Wear OS 5 application designed for the **Samsung Galaxy Watch Ultra** (One UI Watch 6 / Android 14 API 34). It implements a continuous focus and anti-vertigo recovery routine built around a minimal, uncluttered timer interface over an interactive 3D mesh background.

## Core Routine Philosophy

Extended screen exposure can trigger digitally induced dizziness and visual vertigo. FocusMesh Wear structures work around three distinct phases:

1. **Work (Red - Default 60 minutes)**: Deep focus session.
2. **Break (Light Blue - Default 15 minutes)**: Mandatory zero-screen recovery window to let the vestibular and visual systems rest.
3. **Readiness (Yellow - Manual Continue + Optional Grace Timer)**: Transition state reached after a break finishes. By default, the watch stays in Readiness indefinitely until manually advanced so you never feel rushed back to a screen. An optional, off-by-default grace countdown timer can be enabled in settings if you want a gentle reminder after a set number of minutes.

## Key Features

- **Uncluttered Full-Screen Timer with Custom `HH:MM:SS` Durations**:
  - Displays the active timer directly in the center of the watch face with minimal text.
  - Full 3-column **Hour : Minute : Second** drum picker in settings for Work, Break, and optional Readiness Grace durations.
  - When a Work session finishes, the Break timer enters the **Paused (Orange)** state by default so you can start your no-screen break manually when ready.
- **3-Way Background Mode (`3D Mesh`, `2D Mesh`, `Solid Color`) & Battery Saver Detection**:
  - **Full-Screen 3D Mode (Default)**: An overscanned perspective-projected 3D wireframe and shaded surface mesh that fills 100% of the circular watch screen edge-to-edge.
  - **2D Subtle Mode**: A full-screen 2D lattice with gentle vertex drift.
  - **Solid Color Mode & Automatic Battery Saver**: Can be selected manually in settings or triggered automatically when the watch enters **Battery Saver** (`PowerManager.isPowerSaveMode`), dropping all mesh calculations and touch interactions in favor of a solid state-colored background.
  - **Paused Orange to Active Phase Color Shift**: Whenever the timer is paused, the background and UI glow in **Paused (Orange)** with a slow breathing rhythm. Starting the timer triggers a concentric shockwave, accelerates wave flow, and smoothly shifts the color into **Work (Red)**, **Break (Light Blue)**, or **Readiness (Yellow)**.
  - **Touch Wave Physics**: Touching or dragging across the screen displaces the mesh like a fabric sheet or propagating liquid wave using spring-damped grid physics, while only tapping directly on the central time readout pauses or starts the timer.
  - **Battery-Safe Ambient / Sleep Mode**: When the watch enters Always-On Display (AOD) or goes to sleep, mesh animations stop completely.
- **Do Not Disturb (DND) Bypass Alerts**:
  - Routes audio alerts through `AudioAttributes.USAGE_ALARM` (`STREAM_ALARM`) and haptic alerts through `VibrationAttributes.USAGE_ALARM` paired with `AlarmManager.setExactAndAllowWhileIdle()`.
  - Continues to emit audible chimes and tactile vibrations even when Samsung Modes and Routines or Do Not Disturb are active.
- **Non-Robotic Acoustic Sounds & Custom Volume**:
  - Includes multi-harmonic acoustic chimes synthesized with natural exponential decay (Singing Bowl, Warm Marimba, Crystal Chime, Soft Gong) alongside single-shot playback of native watch alarm tones via `RingtoneManager`.
  - Configurable alert mode: **Sound + Haptics** (default), **Sound Only**, **Haptics Only**, or **Silent**.
- **One UI Watch Island & Ongoing Activity**:
  - Integrates with the Wear OS `OngoingActivity` API using a dynamically rendered countdown `Icon` so the active remaining time and progress ring appear inside the floating indicator at the bottom of the Galaxy Watch Ultra home watch face.
- **Right-Edge-Only Configuration Drawer & Live Font Preview**:
  - Swiping left starting specifically from the **right edge** of the watch (rightmost 16% bezel zone) opens the configuration drawer, leaving the rest of the screen completely free for 360-degree mesh interaction.
  - Includes an inline live typography preview for switching Google Fonts.
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
