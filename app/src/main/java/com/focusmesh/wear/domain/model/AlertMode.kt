package com.focusmesh.wear.domain.model

// Defines which physical outputs are triggered when a phase timer finishes.
// SOUND_AND_HAPTICS is enabled by default so both acoustic chimes and LRA vibrations fire.
enum class AlertMode(
    val emitsSound: Boolean,
    val emitsHaptics: Boolean
) {
    SOUND_AND_HAPTICS(emitsSound = true, emitsHaptics = true),
    SOUND_ONLY(emitsSound = true, emitsHaptics = false),
    HAPTICS_ONLY(emitsSound = false, emitsHaptics = true),
    NONE(emitsSound = false, emitsHaptics = false);

    fun next(): AlertMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
