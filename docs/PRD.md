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

## 3. Timer State Machine Requirements

### 3.1 Phase Definitions
| Phase | Primary Color | Hex Palette | Default Duration | Completion Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **Work** | Crimson Red | `#FF2A4B` / `#8A0F24` | 60 minutes (configurable) | Emits alert, transitions automatically to **Break**. |
| **Break** | Light Sky Blue | `#38D6FF` / `#0A5C8A` | 15 minutes (configurable) | Emits alert, transitions to **Readiness**. |
| **Readiness** | Amber Yellow | `#FFD426` / `#8A6800` | Manual continue (Optional grace timer: off by default, configurable 1-15 min) | Waits for user action to start **Work**. If optional grace timer is enabled and expires, emits a gentle reminder alert. |

### 3.2 Controls and Interactions
- **Center Tap**: Starts, pauses, or resumes the active countdown; when in **Readiness**, a single tap begins the next **Work** cycle.
- **Long Press on Timer**: Advances immediately to the next phase or resets the current cycle.
- **Touch Drag Across Screen**: Deforms the 3D/2D mesh surface with a spring-damped wave ripple centered at the pointer location.
- **Hardware Stem Button (While App Is Open)**: Pressing a secondary hardware button while the app window is focused triggers the primary phase action without altering system-wide button assignments outside the app.
- **Swipe from Left Edge**: Opens the Configuration UI drawer.

## 4. Visual and Typography Requirements

- **Uncluttered Layout**: The main view contains only the animated mesh background, the large central timer digits (`MM:SS`), and a minimal single-word phase pill that fades to low opacity during active focus.
- **3D Mesh Background**:
  - Renders a perspective-projected 3D triangular/quad lattice driven by multi-frequency sine and simplex wave functions.
  - Reacts to finger touch like a taut fabric sheet.
  - Stops all animation loops immediately when the watch enters Ambient / Always-On Display (AOD) mode or when the screen turns off.
  - Can be disabled in settings, switching to a lightweight 2D mesh with subtle drift animation and phase colors.
- **Customizable Fonts**:
  - Users can select from curated Google Fonts (such as *Space Grotesk*, *Outfit*, *JetBrains Mono*, *Syne*, and *Fraunces*) via Android's Downloadable Fonts provider with graceful system fallbacks when offline.

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
