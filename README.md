# FocusMesh Wear

FocusMesh Wear is a standalone Wear OS 5 application designed for the **Samsung Galaxy Watch Ultra** (One UI Watch 6 / Android 14 API 34). It implements a continuous focus and anti-vertigo recovery routine built around a minimal, uncluttered timer interface over an interactive 3D mesh background.

## Core Routine Philosophy

Extended screen exposure can trigger digitally induced dizziness and visual vertigo. FocusMesh Wear structures work around three distinct phases:

1. **Work (Red - Default 60 minutes)**: Deep focus session.
2. **Break (Light Blue - Default 15 minutes)**: Mandatory zero-screen recovery window to let the vestibular and visual systems rest.
3. **Readiness (Yellow - Manual Continue + Optional Grace Timer)**: Transition state reached after a break finishes. By default, the watch stays in Readiness indefinitely until manually advanced so you never feel rushed back to a screen. An optional, off-by-default grace countdown timer can be enabled in settings if you want a gentle reminder after a set number of minutes.

## Key Features

- **Interchangeable Standard & Flexible Mode Screens with Per-Mode Presets**:
  - **Vertical Swipe Between Modes**: Swipe up or pull down vertically on the main timer screen to transition between the **Standard Mode** screen and the **Flexible Mode** screen.
  - **Standard Mode**: Classic sequential cycle with a protected default `"Work"` preset (`60m active - 15m break`). When Work finishes, Break enters the **Paused (Orange)** state so you can start your break when ready.
  - **Flexible Mode**: Features a protected default `"Work"` preset (`120m active - 45m break`), dual concentric bezel progress rings (outer ring for the active pool, inner ring for the standby pool), an inverted 3D mesh horizon, and a prominent `FLEXIBLE` mode pill showing the standby pool's remaining time. Single-tap on the clock pauses/starts as normal; **long-tapping the clock** swaps between **Work** and **Break**, pausing the outgoing pool where the swap occurred and **automatically unpausing** the incoming pool.
  - **Customizable Per-Mode Presets**: Create, edit, and delete custom presets for each mode in Settings (**Standard Mode Presets** and **Flexible Mode Presets**) with custom names (up to 16 characters, `[A-Za-z0-9 -_]`) and any `HH:MM:SS` Work/Break duration. Tap the preset name above the clock on the main screen to switch presets (which resets the timers). Default presets are protected from deletion.
- **Interactive 4-Step Vector Tutorial**:
  - Re-openable from Settings at any time, featuring animated vector hand icons and motion trails teaching right-edge Settings swipe, clock tap, Wear OS watch face complications, and vertical swiping + long-tap phase swapping in Flexible Mode.
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
- **Native Watch Face Complication & Configurable "Now" Pill**:
  - Includes a native Wear OS complication provider (`FocusTimerComplicationService`) supporting circular gauge (`RANGED_VALUE`), compact (`SHORT_TEXT`), and banner (`LONG_TEXT`) watch face slots with live-ticking `CountDownTimeReference` support.
  - Also supports the bottom One UI Watch `OngoingActivity` pill with a high-contrast dark circular backdrop and stacked digits, which can be toggled on or off in Settings (**Watch Face Pill**) so you can use a watch face complication, the bottom pill, or both.
- **Native Wear OS Configuration Drawer, Live Font Preview & About Screen**:
  - Swiping left starting specifically from the **right edge** of the watch (rightmost 16% bezel zone) opens the configuration drawer, leaving the rest of the screen completely free for 360-degree mesh interaction.
  - Built with native Wear OS Compose components (`ScalingLazyColumn` with rotary bezel scroll, `ListHeader`, `ToggleChip` with `Switch` and `RadioButton` selection screens, `InlineSlider`, `TitleCard`, and 3-column `Picker` with native circular `Button` controls), including live per-font typography previews and an **About** screen with developer credits, social links, and open-source repository details.
- **In-App Hardware Button Action**:
  - While the app is open and focused, pressing a secondary hardware watch stem button advances or toggles the timer without overriding your system-wide Galaxy Watch Ultra Quick Button binding outside the app.
- **Bilingual Support**:
  - Full localization in **English** (`values/strings.xml`) and **Spanish** (`values-es/strings.xml`).

## Author & Credits

- **Developed by**: Lucas "puntero" Maza (with the aid and help of Gemini 4 Argon)
- **GitHub**: https://github.com/punteroo
- **Discord**: `puntero`
- **Twitter / X**: `desacierto_`
- **Open Source Repository**: https://github.com/punteroo/focus-mesh-wear

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
