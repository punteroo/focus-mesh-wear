package com.focusmesh.wear.domain.model

// Immutable snapshot of user-configurable timer, visual, typography, and audio settings.
data class UserPreferences(
    val workDurationMinutes: Int = 60,
    val breakDurationMinutes: Int = 15,
    // Readiness requires manual continuation by default; when graceEnabled is true, a grace countdown runs.
    val readinessGraceEnabled: Boolean = false,
    val readinessGraceMinutes: Int = 5,
    // Controls whether the background renders the full 3D perspective animated mesh or the subtle 2D fallback mesh.
    val mesh3dEnabled: Boolean = true,
    // Controls whether finger touch creates a propagating spring wave across the mesh vertices.
    val touchWaveEnabled: Boolean = true,
    val fontChoice: FontChoice = FontChoice.SPACE_GROTESK,
    val alertMode: AlertMode = AlertMode.SOUND_AND_HAPTICS,
    val soundOption: SoundOption = SoundOption.SINGING_BOWL,
    // Normalized volume level between 0.0f and 1.0f applied to the alarm audio stream output.
    val alertVolume: Float = 0.85f
)
