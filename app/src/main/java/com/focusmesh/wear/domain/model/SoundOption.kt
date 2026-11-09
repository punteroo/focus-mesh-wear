package com.focusmesh.wear.domain.model

// Non-robotic acoustic alert options plus native Wear OS system alarm integration.
enum class SoundOption {
    SINGING_BOWL,
    WARM_MARIMBA,
    CRYSTAL_CHIME,
    SOFT_GONG,
    SYSTEM_ALARM;

    fun next(): SoundOption {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
