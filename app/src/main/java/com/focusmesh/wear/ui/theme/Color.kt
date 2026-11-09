package com.focusmesh.wear.ui.theme

import androidx.compose.ui.graphics.Color
import com.focusmesh.wear.domain.model.TimerPhase

// Holds the primary highlight, secondary contour, and deep background colors for a timer phase.
data class PhasePalette(
    val primary: Color,
    val secondary: Color,
    val deepBackground: Color
)

// Work (Red): High-energy crimson and deep ruby tones for the 60-minute focus block.
val WorkPalette = PhasePalette(
    primary = Color(0xFFFF3355),
    secondary = Color(0xFF9E1128),
    deepBackground = Color(0xFF28050B)
)

// Break (Light Blue): Calm sky cyan and ocean blue tones for the 15-minute no-screen break.
val BreakPalette = PhasePalette(
    primary = Color(0xFF38D6FF),
    secondary = Color(0xFF0A668E),
    deepBackground = Color(0xFF041C28)
)

// Readiness (Yellow): Warm amber and gold tones indicating readiness to manually begin the next cycle.
val ReadinessPalette = PhasePalette(
    primary = Color(0xFFFFD426),
    secondary = Color(0xFF9A7200),
    deepBackground = Color(0xFF261C03)
)

fun paletteForPhase(phase: TimerPhase): PhasePalette = when (phase) {
    TimerPhase.WORK -> WorkPalette
    TimerPhase.BREAK -> BreakPalette
    TimerPhase.READINESS -> ReadinessPalette
}
