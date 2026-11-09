package com.focusmesh.wear.domain.model

// Curated Google Fonts selectable at runtime for the central countdown display.
// Each entry specifies the exact family name queried from the Google Play Services font provider.
enum class FontChoice(
    val displayName: String,
    val googleFontName: String
) {
    SPACE_GROTESK("Space Grotesk", "Space Grotesk"),
    OUTFIT("Outfit", "Outfit"),
    JETBRAINS_MONO("JetBrains Mono", "JetBrains Mono"),
    SYNE("Syne", "Syne"),
    FRAUNCES("Fraunces", "Fraunces"),
    PLUS_JAKARTA_SANS("Plus Jakarta Sans", "Plus Jakarta Sans");

    fun next(): FontChoice {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
