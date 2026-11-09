package com.focusmesh.wear.domain.model

// Immutable snapshot of user-configurable timer durations (with hour, minute, second granularity),
// background mode, typography, and audio settings.
data class UserPreferences(
    val workDurationSeconds: Int = 3600,
    val breakDurationSeconds: Int = 900,
    // Readiness requires manual continuation by default; when graceEnabled is true, a grace countdown runs.
    val readinessGraceEnabled: Boolean = false,
    val readinessGraceSeconds: Int = 300,
    // Selects between 3D animated mesh, 2D subtle mesh, or solid color background.
    val backgroundMode: BackgroundMode = BackgroundMode.MESH_3D,
    // Controls whether finger touch creates a propagating spring wave across the mesh vertices.
    val touchWaveEnabled: Boolean = true,
    val fontChoice: FontChoice = FontChoice.SPACE_GROTESK,
    val alertMode: AlertMode = AlertMode.SOUND_AND_HAPTICS,
    val soundOption: SoundOption = SoundOption.SINGING_BOWL,
    // Normalized volume level between 0.0f and 1.0f applied to the alarm audio stream output.
    val alertVolume: Float = 0.85f,
    // Controls whether the bottom Watch Face OngoingActivity pill ("Now" icon) is registered.
    val ongoingPillEnabled: Boolean = true
) {
    companion object {
        // Formats a total number of seconds as HH:MM:SS for the settings summary chips.
        fun formatSecondsAsHms(totalSeconds: Int): String {
            val clamped = totalSeconds.coerceAtLeast(1)
            val hours = clamped / 3600
            val minutes = (clamped % 3600) / 60
            val seconds = clamped % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
