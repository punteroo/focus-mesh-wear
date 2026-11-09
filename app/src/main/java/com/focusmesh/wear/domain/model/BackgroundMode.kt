package com.focusmesh.wear.domain.model

// Defines the visual rendering mode for the timer background.
// MESH_3D renders the perspective-projected 3D wave surface.
// MESH_2D renders a flat orthographic lattice with subtle vertex drift.
// SOLID_COLOR drops all mesh calculation and renders a solid state-colored background with no touch interaction.
enum class BackgroundMode {
    MESH_3D,
    MESH_2D,
    SOLID_COLOR;

    fun next(): BackgroundMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
