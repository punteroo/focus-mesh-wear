package com.focusmesh.wear.domain.model

// Represents a mode-specific timer preset with a validated custom name (max 16 chars, [A-Za-z0-9 -_]).
// Default presets ("Work" at 60m/15m for Standard and "Work" at 120m/45m for Flexible) cannot be deleted.
data class TimerPreset(
    val id: String,
    val mode: RoutineMode,
    val name: String,
    val workDurationSeconds: Int,
    val breakDurationSeconds: Int,
    val isDefault: Boolean = false
) {
    companion object {
        const val MAX_NAME_LENGTH = 16
        const val DEFAULT_STANDARD_ID = "default_standard_work"
        const val DEFAULT_FLEXIBLE_ID = "default_flexible_work"

        private val allowedCharactersRegex = Regex("[^A-Za-z0-9 _-]")

        // Sanitizes a preset name to at most 16 characters containing only [A-Za-z0-9 -_].
        fun sanitizeName(raw: String, fallback: String = "Work"): String {
            val cleaned = raw.replace(allowedCharactersRegex, "").trim().take(MAX_NAME_LENGTH)
            return cleaned.ifEmpty { fallback.take(MAX_NAME_LENGTH) }
        }

        fun defaultStandardPreset(): TimerPreset = TimerPreset(
            id = DEFAULT_STANDARD_ID,
            mode = RoutineMode.STANDARD,
            name = "Work",
            workDurationSeconds = 60 * 60,
            breakDurationSeconds = 15 * 60,
            isDefault = true
        )

        fun defaultFlexiblePreset(): TimerPreset = TimerPreset(
            id = DEFAULT_FLEXIBLE_ID,
            mode = RoutineMode.FLEXIBLE,
            name = "Work",
            workDurationSeconds = 120 * 60,
            breakDurationSeconds = 45 * 60,
            isDefault = true
        )
    }
}
