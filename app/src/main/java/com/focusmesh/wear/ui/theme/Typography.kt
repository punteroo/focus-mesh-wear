package com.focusmesh.wear.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.focusmesh.wear.domain.model.FontChoice

// Configures the Google Play Services downloadable font provider on Wear OS.
// Note: In a production release with custom certs, replace empty list with com.google.android.gms.fonts certificates.
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = emptyList()
)

// Resolves a Compose FontFamily from the selected Google Font name, falling back cleanly if offline.
fun resolveGoogleFontFamily(fontChoice: FontChoice): FontFamily {
    return resolveGoogleFontFamilyByName(fontChoice.googleFontName)
}

// Allows resolving arbitrary Google Font family names while providing distinct system fallback styles.
fun resolveGoogleFontFamilyByName(fontName: String): FontFamily {
    val googleFont = GoogleFont(fontName)
    return FontFamily(
        Font(
            googleFont = googleFont,
            fontProvider = googleFontProvider,
            weight = FontWeight.Medium
        ),
        Font(
            googleFont = googleFont,
            fontProvider = googleFontProvider,
            weight = FontWeight.Bold
        )
    )
}
