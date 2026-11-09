package com.focusmesh.wear.domain.model

// Represents the live state of the timer engine observed by both the Compose UI and OngoingActivity service.
// In FLEXIBLE mode, both savedWorkRemainingMillis and savedBreakRemainingMillis are tracked simultaneously so
// swapping between Work and Break pauses and preserves each pool's remaining time.
data class TimerState(
    val routineMode: RoutineMode = RoutineMode.STANDARD,
    val activePresetId: String = TimerPreset.DEFAULT_STANDARD_ID,
    val activePresetName: String = "Work",
    val phase: TimerPhase = TimerPhase.WORK,
    val isRunning: Boolean = false,
    val remainingMillis: Long = 3600L * 1000L,
    val totalPhaseMillis: Long = 3600L * 1000L,
    val savedWorkRemainingMillis: Long = 3600L * 1000L,
    val savedWorkTotalMillis: Long = 3600L * 1000L,
    val savedBreakRemainingMillis: Long = 900L * 1000L,
    val savedBreakTotalMillis: Long = 900L * 1000L,
    // Tracks whether the current READINESS phase is waiting indefinitely for manual input or counting down a grace window.
    val isAwaitingManualContinue: Boolean = false
) {
    // Formats the remaining milliseconds as HH:MM:SS when 1 hour or longer, or MM:SS when under 1 hour.
    fun formattedTime(): String = formatMillis(remainingMillis)

    // Formats the standby pool time in Flexible mode (Break pool when in Work, or Work pool when in Break).
    fun formattedStandbyPoolTime(): String {
        val targetMillis = if (phase == TimerPhase.WORK) {
            savedBreakRemainingMillis
        } else {
            savedWorkRemainingMillis
        }
        return formatMillis(targetMillis)
    }

    // Formats a compact 5-character readout for the 96x96 watch face ongoing activity icon.
    fun compactPillTime(): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format("%d:%02dh", hours, minutes)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Formats the remaining time in minute granularity for Wear OS Always-On Display (Ambient Mode)
    // where the display hardware refreshes once per minute via onUpdateAmbient().
    fun formattedAmbientTime(): String {
        val clamped = remainingMillis.coerceAtLeast(0L)
        if (clamped == 0L) return "0m"
        val totalMinutes = (clamped + 59_999L) / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            String.format("%dh %02dm", hours, minutes)
        } else {
            String.format("%dm", minutes)
        }
    }

    // Computes normalized progress between 0.0f and 1.0f for perimeter ring and mesh scaling.
    fun progressFraction(): Float {
        if (totalPhaseMillis <= 0L) return 1.0f
        return (remainingMillis.toFloat() / totalPhaseMillis.toFloat()).coerceIn(0.0f, 1.0f)
    }

    companion object {
        fun formatMillis(millis: Long): String {
            val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
