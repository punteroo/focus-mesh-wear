package com.focusmesh.wear.domain.model

// Defines the active timer operating mode:
// - STANDARD: Classic sequential cycle (Work expires -> Break paused -> Break expires -> Readiness -> Work).
// - FLEXIBLE: Dual-pool mode allowing the user to swap freely between Work and Break mid-session while
//   pausing and preserving each timer's remaining countdown.
enum class RoutineMode {
    STANDARD,
    FLEXIBLE;

    fun next(): RoutineMode = when (this) {
        STANDARD -> FLEXIBLE
        FLEXIBLE -> STANDARD
    }
}
