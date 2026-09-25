package de.notizen.app.ui.suche

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import de.notizen.core.data.repository.TREFFER_AUF
import de.notizen.core.data.repository.TREFFER_ZU

/**
 * Macht aus dem markierten Ausschnitt von `snippet()` formatierten Text.
 *
 * SQLite fasst die Fundstellen in zwei Steuerzeichen ein. Hier werden sie
 * herausgeschnitten und durch echte Formatierung ersetzt -- die Steuerzeichen
 * selbst dürfen niemals auf dem Bildschirm landen.
 *
 * Robust gegen unvollständige Markierungen: `snippet()` kürzt den Ausschnitt
 * auf eine feste Zahl Token, und dabei kann die schließende Markierung
 * wegfallen. Dann wird bis zum Ende hervorgehoben, statt den Rest zu
 * verschlucken oder ein Steuerzeichen durchzureichen.
 */
fun hervorgehoben(ausschnitt: String, stil: SpanStyle): AnnotatedString {
    val bau = AnnotatedString.Builder()
    var offen = false

    ausschnitt.forEach { zeichen ->
        when (zeichen) {
            TREFFER_AUF -> if (!offen) {
                bau.pushStyle(stil)
                offen = true
            }

            TREFFER_ZU -> if (offen) {
                bau.pop()
                offen = false
            }

            else -> bau.append(zeichen)
        }
    }

    if (offen) bau.pop()
    return bau.toAnnotatedString()
}
