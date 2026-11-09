package com.focusmesh.wear.domain.model

// Represents the three core phases of the anti-vertigo focus routine.
// WORK is an active focus block rendered in red.
// BREAK is a mandatory zero-screen recovery block rendered in light blue.
// READINESS is a yellow hold state waiting for manual continuation or an optional grace timer.
enum class TimerPhase {
    WORK,
    BREAK,
    READINESS;

    // Computes the next logical phase in the continuous cycle.
    fun nextPhase(): TimerPhase = when (this) {
        WORK -> BREAK
        BREAK -> READINESS
        READINESS -> WORK
    }
}
