# Product Requirements Document (PRD)

## 1. Purpose and Problem Statement

Knowledge workers who spend long hours looking at monitors frequently experience digital eye strain, cybersickness, and visually induced dizziness or vertigo. Traditional Pomodoro apps enforce rigid short cycles (25/5 minutes), auto-advance without user consent, rely on standard notification channels that get muted by Do Not Disturb (DND) routines, and clutter small watch screens with tiny buttons and text.

**FocusMesh Wear** solves this on the **Samsung Galaxy Watch Ultra** by providing an uncluttered, glanceable 60/15-minute focus-and-recovery timer that:
1. Reliably pierces through Do Not Disturb and Samsung Modes/Routines when a phase finishes.
2. Uses high-contrast peripheral color states (Red, Light Blue, Yellow) rendered via an interactive 3D mesh so the user can tell the current phase without reading small text.
3. Holds in a **Readiness (Yellow)** state after each break so the user can resume work when physically ready, with an optional configurable grace timer.

## 2. Target Hardware and Platform

- **Primary Device**: Samsung Galaxy Watch Ultra (47mm cushion case, 480x480 circular Super AMOLED display, 3,000 nits peak brightness).
- **Operating System**: Wear OS 5 / One UI Watch 6 (Android 14, API Level 34).
- **Compatibility**: Runs standalone on any Wear OS 3.0+ watch (API 30+).

## 3. Timer State Machine & Operating Modes

### 3.1 Operating Modes & Default Presets
- **Standard Mode (`RoutineMode.STANDARD`)**:
  - Classic sequential cycle: **Work** expires -> transitions to **Break** in the **Paused** state -> **Break** expires -> **Readiness** -> **Work**.
  - Default protected preset: `"Work"` (`60m` active - `15m` break).
- **Flexible Mode (`RoutineMode.FLEXIBLE`)**:
  - Dual-pool mode allowing the user to swap freely between **Work** and **Break** mid-session while pausing and preserving each timer's remaining countdown where the swap occurred.
  - Default protected preset: `"Work"` (`120m` active - `45m` break).
- **Per-Mode Custom Presets**:
  - Each mode maintains its own list of presets with custom names (`<= 16` chars, `[A-Za-z0-9 -_]`) and `HH:MM:SS` Work/Break durations.
  - Default presets can be edited and selected, but cannot be deleted. User-created presets can be edited and deleted.
  - Tapping the preset label above the timer on the main screen opens a native Wear OS Preset Selector; swapping presets immediately resets the timers.

### 3.2 Phase and State Definitions
| State / Phase | Primary Color | Hex Palette | Default Duration (`HH:MM:SS`) | Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Paused** | Vibrant Orange | `#FF6D1A` / `#A83A00` | Indefinite while paused | Always shown when a timer is paused; shifts smoothly into the active phase color when started. |
| **Work** | Clear Scarlet Red | `#FF4D5A` / `#D92B3A` | `01:00:00` (Standard) / `02:00:00` (Flexible) | Displays the active preset name above the clock. |
| **Break** | Light Sky Blue | `#38D6FF` / `#0A668E` | `00:15:00` (Standard) / `00:45:00` (Flexible) | Starts paused by default; in Flexible Mode, discounts time from the Break pool and preserves remaining Break time when swapping back to Work. |
| **Readiness** | Amber Yellow | `#FFDD26` / `#9A7A00` | Manual continue (Optional grace timer: off by default, configurable `HH:MM:SS`) | Waits for user action to replenish pools and start **Work**. |

### 3.3 Controls, Gestures, and Interactive Tutorial
- **Preset Header Tap (Above Clock)**: Opens the native Wear OS Preset Selector for the current mode.
- **Center Time Element Tap**: Tapping directly on the central time readout starts, pauses, or resumes the active countdown; when in **Readiness**, a single tap begins the next **Work** cycle.
- **Center Time Element Long Press**: Advances to the next phase in Standard Mode, or swaps between Work and Break in Flexible Mode.
- **Swipe Up Starting from Bottom Edge**: Starting a swipe on the bottom 18% edge of the watch screen and dragging up opens the **Flexible Mode** controls to toggle Flexible Mode or swap between Work and Break.
- **Swipe Left Starting from Right Edge**: Starting a swipe on the rightmost 16% edge of the watch screen and dragging left opens the Configuration UI drawer.
- **Interactive 4-Step Vector Tutorial**: Shown on first launch and re-openable from Settings at any time, using animated vector hand icons and motion arrows to teach right-edge settings swipe, clock tap, Wear OS complications, and bottom-edge Flexible Mode.

## 4. Visual and Typography Requirements

- **Uncluttered Layout**: The main view contains only the background, the large central timer digits (`HH:MM:SS` or `MM:SS`), and a minimal single-word phase header with a breathing status dot.
- **3-Way Background Mode & Battery Saver**:
  - Users can choose between **3D Animated Mesh**, **2D Subtle Mesh**, and **Solid Color**.
  - When **Solid Color** is selected OR when watch **Battery Saver** (`PowerManager.isPowerSaveMode`) is active, the mesh lattice and touch wave interaction are completely dropped in favor of a solid state-colored background.
  - Also stops all animations immediately when the watch enters Ambient / Always-On Display (AOD) mode.
- **Customizable Fonts with Live Settings Preview**:
  - Users can select from curated Google Fonts (*Space Grotesk*, *Outfit*, *JetBrains Mono*, *Syne*, *Fraunces*, *Plus Jakarta Sans*) with an immediate inline preview sample inside the settings menu.

## 5. Audio, Haptics, and System Integration

- **Do Not Disturb Bypass**:
  - Uses `AudioAttributes.USAGE_ALARM` on the `STREAM_ALARM` channel and `VibrationAttributes.USAGE_ALARM` so alerts sound and vibrate even when DND or Work/Sleep modes are active.
- **Alert Mode Selection**:
  - Configurable between **Sound + Haptics** (default), **Sound Only**, **Haptics Only**, and **Silent (Visual Only)**.
- **Organic Sound Selection & Volume**:
  - Includes warm, non-robotic acoustic tones (Singing Bowl, Warm Marimba, Crystal Chime, Soft Gong) plus native watch alarm ringtones, paired with an independent volume slider (0% to 100%).
- **One UI Watch Island / Ongoing Activity**:
  - Publishes an `OngoingActivity` with an animated status icon and live countdown chronometer so One UI Watch 6 displays the active timer island on the watch face and recents screen.

## 6. Localization

- Full UI string support for **English** (default) and **Spanish** (`es`).
