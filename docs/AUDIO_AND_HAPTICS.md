# Audio Synthesis, System Ringtones & Haptics

## 1. Avoiding Harsh Machine-Generated Beeps

Standard square-wave buzzer tones feel jarring after a one-hour focus session or a quiet 15-minute anti-vertigo break. FocusMesh Wear provides two sources of rich, organic alerting audio:

1. **Built-In Acoustic Harmonic Presets (`AcousticsGenerator`)**:
   - Synthesizes 16-bit 44.1kHz PCM audio buffers in memory using additive harmonic synthesis with natural exponential decay envelopes, inharmonic overtones, and warm low-pass filtering:
     - **Tibetan Singing Bowl**: Fundamental 432 Hz with beating fifth and octave partials and a long 2.8-second resonant tail.
     - **Warm Rosewood Marimba**: Double-note ascending fifth (C5 -> G5) with woody second and fourth harmonic emphasis and gentle percussive attack.
     - **Crystal Wind Chime**: Three-note pentatonic arpeggio with bell-like shimmer partials.
     - **Soft Zen Gong**: Low 220 Hz fundamental with warm sub-harmonic bloom.
   - Played directly via `AudioTrack` configured with `AudioAttributes.USAGE_ALARM` and `AudioAttributes.CONTENT_TYPE_SONIFICATION`.
2. **Native Watch Alarm Tones (`RingtoneManager`)**:
   - Users can select **System Watch Alarm** in the settings drawer, which resolves the watch's configured default alarm ringtone (`RingtoneManager.TYPE_ALARM`) on One UI Watch and plays it with `USAGE_ALARM` attributes.

## 2. Independent Volume Control

The settings drawer includes a dedicated volume slider (`0%` to `100%`).
- For built-in acoustic presets, the user's volume multiplier is applied directly to the `AudioTrack` gain (`AudioTrack.setVolume(volume)`) while ensuring the hardware `STREAM_ALARM` channel is audible.
- When the user adjusts the volume slider in the Configuration drawer, a short preview chime plays so they can gauge loudness immediately.

## 3. Haptic Alert Patterns (`VibrationAttributes.USAGE_ALARM`)

On the Samsung Galaxy Watch Ultra, the linear resonant actuator (LRA) supports crisp amplitude-controlled waveforms via `VibrationEffect.createWaveform(timings, amplitudes, repeat)`.

Each transition has a distinct tactile signature so the user can tell which phase just ended without looking at the watch:
- **Work Completed -> Entering Break**: Three slow, decelerating pulses that feel like exhaling (`[0, 180, 140, 260, 180, 400] ms`, amplitudes `[0, 220, 0, 180, 0, 130]`).
- **Break Completed -> Entering Readiness**: Two crisp, uplifting double-taps (`[0, 90, 70, 120, 200, 90, 70, 150] ms`, amplitudes `[0, 200, 0, 255, 0, 200, 0, 255]`).
- **Readiness Grace Expired**: A single gentle nudge (`[0, 140, 100, 140] ms`, amplitudes `[0, 150, 0, 180]`).
