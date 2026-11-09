# Technical Architecture

## 1. High-Level Package Structure

The codebase is organized by responsibility into four primary packages under `com.focusmesh.wear`:

1. `domain.model`: Immutable data structures defining the state machine (`TimerPhase`, `TimerState`), user configuration (`UserPreferences`), alert policies (`AlertMode`), font descriptors (`FontChoice`), and sound profiles (`SoundOption`).
2. `data`: Asynchronous persistence layer backed by Jetpack DataStore (`PreferencesRepository`).
3. `alerts`: Audio and haptic output engine (`AlertController`, `AcousticsGenerator`) engineered to bypass Do Not Disturb via Android alarm streams.
4. `service`: Background execution engine (`TimerForegroundService`, `TimerAlarmReceiver`) coordinating `AlarmManager` exact wakeups and the Wear OS `OngoingActivity` watch face indicator.
5. `ui`: Jetpack Compose for Wear OS presentation layer (`MainActivity`, `TimerViewModel`, `TimerScreen`, `SettingsDrawerScreen`, `mesh`, and `theme`).

## 2. Right-Edge-Only Configuration Drawer vs. Unrestricted Mesh Touch

To allow the user to drag their finger freely in any direction across the 3D/2D mesh without accidentally scrolling into the Settings page:
- `HorizontalPager` has `userScrollEnabled = false` while on `Page 0` (`TimerScreen`).
- `TimerScreen` inspects the initial pointer-down coordinate (`down.position.x`):
  - If `down.position.x >= size.width * 0.84f` (the touch started in the rightmost 16% bezel zone) and drags left by at least `14%` of screen width, `onOpenSettingsFromRightEdge()` animates the `HorizontalPager` to `Page 1` (`SettingsDrawerScreen`).
  - If `down.position.x < size.width * 0.84f` (the touch started anywhere else on the screen), 100% of pointer movement is routed to `WavePhysicsState.applyTouchImpulse()`.
- When `SettingsDrawerScreen` (`Page 1`) is open, `userScrollEnabled` is enabled so swiping back (left-to-right) or pressing Back returns to `TimerScreen` (`Page 0`).

## 3. 3-Way Background Engine (`3D Mesh`, `2D Mesh`, `Solid Color`) & Battery Saver

The background renderer (`MeshBackgroundCanvas` and `WavePhysicsState`) supports three explicit modes and automatic power-save fallback:

- **Full-Bleed Grid Topology**: A `17x17` vertex lattice spanning an overscanned world domain (`[-2.25, 2.25]` in 3D mode, `[-1.35, 1.35]` in 2D mode) so projected triangles and wireframe lines extend completely beyond the circular watch bezel on all sides.
- **Paused Orange & Active Phase Color Transitions**:
  - When the timer is paused, `paletteForTimerState` selects `PausedPalette` (**Vibrant Orange**) and slows the mesh wave speed to a calm idle breathing state (`activeEnergy = 0.38f`).
  - Starting the timer triggers a concentric ring shockwave (`triggerStateTransitionWave`), scales the timer digits with a spring animation, accelerates the mesh waves (`activeEnergy = 1.0f`), and smoothly transitions the mesh and perimeter progress arc from **Orange** into **Work (Red)**, **Break (Light Blue)**, or **Readiness (Yellow)**.
- **Solid Color Mode & Automatic Battery Saver**:
  - `MainActivity` observes `PowerManager.isPowerSaveMode` and listens to `PowerManager.ACTION_POWER_SAVE_MODE_CHANGED`.
  - When `BackgroundMode.SOLID_COLOR` is selected OR when watch Battery Saver is active, the frame clock loop, vertex projection, and touch wave input are completely bypassed, rendering a solid state-colored background and perimeter progress ring.
- **Ambient Mode (AOD) Conservation & Minute-Granularity Display**:
  - `MainActivity` observes lifecycle and ambient state changes via `AmbientLifecycleObserver`.
  - When `isAmbient` is true, the mesh frame clock ticker and infinite UI breathing animations pause completely, and `TimerScreen` switches from second-granularity (`HH:MM:SS` / `MM:SS`) to minute-granularity (`formattedAmbientTime()`, e.g., `1h 00m` or `59m`) so the display never appears frozen on a stale second while the Wear OS display hardware is in 1-minute refresh mode.
  - On `onEnterAmbient()`, `onUpdateAmbient()`, and `onExitAmbient()`, `MainActivity` invokes `TimerViewModel.onAmbientUpdate()` to synchronously recalculate `remainingMillis` against `SystemClock.elapsedRealtime()` before the system latches the AOD display buffer.

## 4. Do Not Disturb Bypass & Exact Timing Pipeline

Wear OS aggressively throttles background coroutines and standard `Handler` timers when the watch screen turns off and the CPU enters deep sleep (Doze mode).

To guarantee exact phase completion alerts:
1. When a timed phase (`WORK`, `BREAK`, or `READINESS` with grace enabled) is running, `TimerForegroundService` calculates `triggerAtMillis = SystemClock.elapsedRealtime() + remainingMillis`.
2. It schedules a wakeup alarm via `AlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)`.
3. When the alarm fires, `TimerAlarmReceiver` acquires a brief partial wake lock and notifies `TimerForegroundService` to transition the phase and invoke `AlertController.triggerPhaseAlert()`.
4. `AlertController` plays audio using `AudioAttributes.USAGE_ALARM` and triggers vibration using `VibrationAttributes.USAGE_ALARM`, both of which are exempted from Do Not Disturb suppression by Android's notification policy service.

## 5. One UI Watch Island (`OngoingActivity`)

Samsung One UI Watch 6 surfaces active background tasks as an animated pill ("island") on the watch face when an app registers an `OngoingActivity` attached to an active foreground notification:
- `TimerForegroundService` builds a `Status.Builder()` containing a `StopwatchPart` or `TimerPart` (`Status.TimerPart(timeZeroMillis)`) and attaches it to `OngoingActivity.Builder(context, NOTIFICATION_ID, notificationBuilder)`.
- Calling `ongoingActivity.apply(context)` registers the timer with the Wear OS launcher so the user can see remaining time on their watch face and return to FocusMesh with a single tap.

## 6. Hardware Button Handling

- Inside `MainActivity.onKeyDown(keyCode, event)`, the activity inspects `KeyEvent.KEYCODE_STEM_PRIMARY`, `KeyEvent.KEYCODE_STEM_1`, and `KeyEvent.KEYCODE_STEM_2`.
- Because `onKeyDown` only executes when `MainActivity` is in the foreground and holds window focus, it never overrides the user's system-wide Samsung Quick Button assignment when the watch is on the home screen or in another app.
