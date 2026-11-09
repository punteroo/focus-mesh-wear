package com.focusmesh.wear.ui.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.focusmesh.wear.R
import com.focusmesh.wear.domain.model.FontChoice

// Configures the Google Play Services downloadable font provider on Wear OS with official certificates.
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Resolves a Compose FontFamily from the selected Google Font name, pairing it with a distinct
// device font fallback so each typography option looks immediately different even before GMS download completes.
fun resolveGoogleFontFamily(fontChoice: FontChoice): FontFamily {
    val googleFont = GoogleFont(fontChoice.googleFontName)
    val deviceFallbackName = when (fontChoice) {
        FontChoice.SPACE_GROTESK -> "sans-serif-condensed"
        FontChoice.OUTFIT -> "sans-serif-light"
        FontChoice.JETBRAINS_MONO -> "monospace"
        FontChoice.SYNE -> "sans-serif-black"
        FontChoice.FRAUNCES -> "serif"
        FontChoice.PLUS_JAKARTA_SANS -> "sans-serif-medium"
    }
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
        ),
        Font(
            familyName = DeviceFontFamilyName(deviceFallbackName),
            weight = FontWeight.Medium
        ),
        Font(
            familyName = DeviceFontFamilyName(deviceFallbackName),
            weight = FontWeight.Bold
        )
    )
}
