package com.focusmesh.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.focusmesh.wear.domain.model.TimerPhase

// Holds the primary highlight, secondary contour, and deep background colors for a timer state.
data class PhasePalette(
    val primary: Color,
    val secondary: Color,
    val deepBackground: Color
)

// Paused (Orange): Always displayed when a timer is paused, shifting into the active phase color when started.
val PausedPalette = PhasePalette(
    primary = Color(0xFFFF6D1A),
    secondary = Color(0xFFA83A00),
    deepBackground = Color(0xFF280C02)
)

// Work (Red): Clear, vibrant scarlet and bright crimson tones for the 60-minute focus block.
val WorkPalette = PhasePalette(
    primary = Color(0xFFFF4D5A),
    secondary = Color(0xFFD92B3A),
    deepBackground = Color(0xFF3D0C14)
)

// Break (Light Blue): Calm sky cyan and ocean blue tones for the 15-minute no-screen break.
val BreakPalette = PhasePalette(
    primary = Color(0xFF38D6FF),
    secondary = Color(0xFF0A668E),
    deepBackground = Color(0xFF041C28)
)

// Readiness (Yellow): Warm lemon-gold tones indicating readiness to begin the next cycle.
val ReadinessPalette = PhasePalette(
    primary = Color(0xFFFFDD26),
    secondary = Color(0xFF9A7A00),
    deepBackground = Color(0xFF261E02)
)

fun paletteForPhase(phase: TimerPhase): PhasePalette = when (phase) {
    TimerPhase.WORK -> WorkPalette
    TimerPhase.BREAK -> BreakPalette
    TimerPhase.READINESS -> ReadinessPalette
}

// Resolves the active visual palette: always Orange while paused, shifting into the phase color when running
// or when holding in the Readiness manual-continue state.
fun paletteForTimerState(
    phase: TimerPhase,
    isRunning: Boolean,
    isAwaitingManualContinue: Boolean
): PhasePalette {
    if (!isRunning && !isAwaitingManualContinue) {
        return PausedPalette
    }
    return paletteForPhase(phase)
}
