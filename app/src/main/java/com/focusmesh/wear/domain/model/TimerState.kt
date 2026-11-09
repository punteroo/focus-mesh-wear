package com.focusmesh.wear.domain.model

// Represents the live state of the timer engine observed by both the Compose UI and OngoingActivity service.
data class TimerState(
    val phase: TimerPhase = TimerPhase.WORK,
    val isRunning: Boolean = false,
    val remainingMillis: Long = 60L * 60L * 1000L,
    val totalPhaseMillis: Long = 60L * 60L * 1000L,
    // Tracks whether the current READINESS phase is waiting indefinitely for manual input or counting down a grace window.
    val isAwaitingManualContinue: Boolean = false
) {
    // Formats the remaining milliseconds as MM:SS for the central watch display.
    fun formattedTime(): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Computes normalized progress between 0.0f and 1.0f for subtle mesh intensity scaling.
    fun progressFraction(): Float {
        if (totalPhaseMillis <= 0L) return 1.0f
        return (remainingMillis.toFloat() / totalPhaseMillis.toFloat()).coerceIn(0.0f, 1.0f)
    }
}
