package de.notizen.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import de.notizen.app.R

/**
 * Zentrale Typografie der App.
 *
 * Google Sans Flex, ein Variable Font unter der SIL Open Font License.
 * EINE Datei, Gewichte ueber die wght-Achse -- keine neun Einzelschnitte.
 *
 * Die mitgelieferte Datei hat sechs Achsen (am 2026-08-20 aus der fvar-Tabelle
 * ausgelesen):
 *
 *   opsz   6 .. 144   (Standard 18)   optische Groesse
 *   wdth  25 .. 151   (Standard 100)  Breite
 *   wght   1 .. 1000  (Standard 400)  Gewicht   <- das benutzen wir
 *   GRAD   0 .. 100   (Standard 0)    Grade
 *   ROND   0 .. 100   (Standard 0)    Rundung
 *   slnt -10 .. 0     (Standard 0)    Neigung
 *
 * Alle unten verwendeten Gewichte (300-700) liegen im gueltigen Bereich.
 * Die uebrigen Achsen bleiben vorerst auf ihrem Standardwert; opsz waere der
 * naechste sinnvolle Schritt, wenn die Typenskala feiner abgestimmt wird.
 */
private fun googleSansFlex(weight: FontWeight) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

private val NotizenFontFamily = FontFamily(
    googleSansFlex(FontWeight.Light),
    googleSansFlex(FontWeight.Normal),
    googleSansFlex(FontWeight.Medium),
    googleSansFlex(FontWeight.SemiBold),
    googleSansFlex(FontWeight.Bold),
)

private val Default = Typography()

/**
 * Material-3-Typenskala, durchgaengig auf die App-Schrift gesetzt.
 * Groessen und Zeilenhoehen bleiben die von Material vorgegebenen.
 */
val NotizenTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = NotizenFontFamily),
    displayMedium = Default.displayMedium.copy(fontFamily = NotizenFontFamily),
    displaySmall = Default.displaySmall.copy(fontFamily = NotizenFontFamily),
    headlineLarge = Default.headlineLarge.copy(fontFamily = NotizenFontFamily),
    headlineMedium = Default.headlineMedium.copy(fontFamily = NotizenFontFamily),
    headlineSmall = Default.headlineSmall.copy(fontFamily = NotizenFontFamily),
    titleLarge = Default.titleLarge.copy(fontFamily = NotizenFontFamily),
    titleMedium = Default.titleMedium.copy(fontFamily = NotizenFontFamily),
    titleSmall = Default.titleSmall.copy(fontFamily = NotizenFontFamily),
    bodyLarge = Default.bodyLarge.copy(fontFamily = NotizenFontFamily),
    bodyMedium = Default.bodyMedium.copy(fontFamily = NotizenFontFamily),
    bodySmall = Default.bodySmall.copy(fontFamily = NotizenFontFamily),
    labelLarge = Default.labelLarge.copy(fontFamily = NotizenFontFamily),
    labelMedium = Default.labelMedium.copy(fontFamily = NotizenFontFamily),
    labelSmall = Default.labelSmall.copy(fontFamily = NotizenFontFamily),
)
