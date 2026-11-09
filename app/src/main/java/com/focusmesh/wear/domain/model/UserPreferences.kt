package com.focusmesh.wear.domain.model

// Immutable snapshot of user-configurable modes, per-mode presets, background mode, typography,
// audio settings, and interactive tutorial completion state.
data class UserPreferences(
    val routineMode: RoutineMode = RoutineMode.STANDARD,
    val standardPresets: List<TimerPreset> = listOf(TimerPreset.defaultStandardPreset()),
    val flexiblePresets: List<TimerPreset> = listOf(TimerPreset.defaultFlexiblePreset()),
    val activeStandardPresetId: String = TimerPreset.DEFAULT_STANDARD_ID,
    val activeFlexiblePresetId: String = TimerPreset.DEFAULT_FLEXIBLE_ID,
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
    val ongoingPillEnabled: Boolean = true,
    // Tracks whether the interactive onboarding tutorial has been completed at least once.
    val hasCompletedTutorial: Boolean = false
) {
    fun presetsForMode(mode: RoutineMode = routineMode): List<TimerPreset> = when (mode) {
        RoutineMode.STANDARD -> standardPresets.ifEmpty { listOf(TimerPreset.defaultStandardPreset()) }
        RoutineMode.FLEXIBLE -> flexiblePresets.ifEmpty { listOf(TimerPreset.defaultFlexiblePreset()) }
    }

    fun activePresetForMode(mode: RoutineMode = routineMode): TimerPreset {
        val list = presetsForMode(mode)
        val targetId = when (mode) {
            RoutineMode.STANDARD -> activeStandardPresetId
            RoutineMode.FLEXIBLE -> activeFlexiblePresetId
        }
        return list.firstOrNull { it.id == targetId } ?: list.first()
    }

    companion object {
        // Formats a total number of seconds as HH:MM:SS for the settings summary chips.
        fun formatSecondsAsHms(totalSeconds: Int): String {
            val clamped = totalSeconds.coerceAtLeast(1)
            val hours = clamped / 3600
            val minutes = (clamped % 3600) / 60
            val seconds = clamped % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

        // Formats a compact human-readable duration (e.g. "60m", "120m", "1h 15m", "45s") for preset subtitles.
        fun formatCompactDuration(totalSeconds: Int): String {
            val clamped = totalSeconds.coerceAtLeast(1)
            val hours = clamped / 3600
            val minutes = (clamped % 3600) / 60
            val seconds = clamped % 60
            return when {
                hours > 0 && seconds == 0 && minutes == 0 -> "${hours * 60}m"
                hours > 0 && seconds == 0 -> "${hours}h ${minutes}m"
                hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, seconds)
                minutes > 0 && seconds == 0 -> "${minutes}m"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
    }
}
