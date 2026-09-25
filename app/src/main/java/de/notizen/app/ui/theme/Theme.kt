package de.notizen.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

// Fallback-Schema, erzeugt mit material-color-utilities.
// Seed  #001835  (HCT: Farbton 260,4 / Chroma 26,1 / Helligkeit 8,2)
// Variante: VIBRANT  -- NICHT TonalSpot. TonalSpot entsaettigt die Flaechen zu
// einem neutralen Schwarzgrau (#0C0E12) und zerstoert den Marineblau-Charakter.
// Kontraststufe: 0.0 (Standard)
// Diese Werte NICHT von Hand aendern -- bei Bedarf neu generieren.

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AAFFF),
    onPrimary = Color(0xFF002E5D),
    primaryContainer = Color(0xFF5DA2FF),
    onPrimaryContainer = Color(0xFF002348),
    inversePrimary = Color(0xFF005FB3),
    secondary = Color(0xFF739AFF),
    onSecondary = Color(0xFF001C51),
    secondaryContainer = Color(0xFF0040A1),
    onSecondaryContainer = Color(0xFFC0CFFF),
    tertiary = Color(0xFFEEACFF),
    onTertiary = Color(0xFF621C7A),
    tertiaryContainer = Color(0xFFE699FD),
    onTertiaryContainer = Color(0xFF570E6F),
    error = Color(0xFFFF716C),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF9F0519),
    onErrorContainer = Color(0xFFFFA8A3),
    background = Color(0xFF000C2C),
    onBackground = Color(0xFFDEE5FF),
    surface = Color(0xFF000C2C),
    onSurface = Color(0xFFDEE5FF),
    surfaceVariant = Color(0xFF092355),
    onSurfaceVariant = Color(0xFF9CAAD5),
    surfaceTint = Color(0xFF7AAFFF),
    inverseSurface = Color(0xFFFAF8FF),
    inverseOnSurface = Color(0xFF46547A),
    outline = Color(0xFF67749C),
    outlineVariant = Color(0xFF39476C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF0E2960),
    surfaceDim = Color(0xFF000C2C),
    surfaceContainer = Color(0xFF011742),
    surfaceContainerHigh = Color(0xFF051D4B),
    surfaceContainerHighest = Color(0xFF092355),
    surfaceContainerLow = Color(0xFF001137),
    surfaceContainerLowest = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF005BAC),
    onPrimary = Color(0xFFEEF2FF),
    primaryContainer = Color(0xFF5DA2FF),
    onPrimaryContainer = Color(0xFF002348),
    inversePrimary = Color(0xFF2F91FF),
    secondary = Color(0xFF2856B7),
    onSecondary = Color(0xFFF1F2FF),
    secondaryContainer = Color(0xFFC2D1FF),
    onSecondaryContainer = Color(0xFF0241A2),
    tertiary = Color(0xFF833E9A),
    onTertiary = Color(0xFFFFEDFE),
    tertiaryContainer = Color(0xFFE699FD),
    onTertiaryContainer = Color(0xFF570E6F),
    error = Color(0xFFB31B25),
    onError = Color(0xFFFFEFEE),
    errorContainer = Color(0xFFFB5151),
    onErrorContainer = Color(0xFF570008),
    background = Color(0xFFF6F6FF),
    onBackground = Color(0xFF1F2D51),
    surface = Color(0xFFF6F6FF),
    onSurface = Color(0xFF1F2D51),
    surfaceVariant = Color(0xFFD2DCFF),
    onSurfaceVariant = Color(0xFF4D5A81),
    surfaceTint = Color(0xFF005BAC),
    inverseSurface = Color(0xFF000C2C),
    inverseOnSurface = Color(0xFF8E9CC6),
    outline = Color(0xFF68769E),
    outlineVariant = Color(0xFF9EACD7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F6FF),
    surfaceDim = Color(0xFFC6D3FF),
    surfaceContainer = Color(0xFFE2E7FF),
    surfaceContainerHigh = Color(0xFFDAE2FF),
    surfaceContainerHighest = Color(0xFFD2DCFF),
    surfaceContainerLow = Color(0xFFEEF0FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

@Composable
fun NotizenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotizenTypography, // Google Sans Flex, siehe Type.kt
        content = content,
    )
}
