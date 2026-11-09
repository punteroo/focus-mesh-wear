package com.focusmesh.wear.domain.model

// Represents the live state of the timer engine observed by both the Compose UI and OngoingActivity service.
data class TimerState(
    val phase: TimerPhase = TimerPhase.WORK,
    val isRunning: Boolean = false,
    val remainingMillis: Long = 3600L * 1000L,
    val totalPhaseMillis: Long = 3600L * 1000L,
    // Tracks whether the current READINESS phase is waiting indefinitely for manual input or counting down a grace window.
    val isAwaitingManualContinue: Boolean = false
) {
    // Formats the remaining milliseconds as HH:MM:SS when 1 hour or longer, or MM:SS when under 1 hour.
    fun formattedTime(): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
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

    // Computes normalized progress between 0.0f and 1.0f for perimeter ring and mesh scaling.
    fun progressFraction(): Float {
        if (totalPhaseMillis <= 0L) return 1.0f
        return (remainingMillis.toFloat() / totalPhaseMillis.toFloat()).coerceIn(0.0f, 1.0f)
    }
}
