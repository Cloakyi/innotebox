package de.notizen.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Formatierungsleiste über der unteren Aktionsleiste.
 *
 * Sie nimmt dem Textfeld ausdrücklich nicht den Fokus (`canFocus = false`).
 * Täte sie es, verschwände sie beim ersten Antippen mitsamt der Tastatur, und
 * die Auswahl, auf die sie wirken soll, gleich mit. Das ist der Fehler, den
 * solche Leisten typischerweise haben.
 *
 * Sie erscheint nur, während im Notiztext geschrieben wird. Dauerhaft sichtbar
 * wäre sie in einer Notizen-App Beiwerk, das ständig Platz kostet und selten
 * gebraucht wird.
 *
 * Alle Farben kommen aus der Notiz, nicht aus dem Theme, die Leiste sitzt auf
 * dem Blatt (siehe docs/ENTSCHEIDUNGEN.md, Farbregeln).
 */
@Composable
fun Formatierungsleiste(
    aktiv: Set<Auszeichnung.Art>,
    stufe: Auszeichnung.Stufe,
    farbe: Color,
    onArt: (Auszeichnung.Art) -> Unit,
    onStufe: (Auszeichnung.Stufe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Knopf(Icons.Outlined.FormatBold, "Fett", Auszeichnung.Art.FETT in aktiv, farbe) {
            onArt(Auszeichnung.Art.FETT)
        }
        Knopf(Icons.Outlined.FormatItalic, "Kursiv", Auszeichnung.Art.KURSIV in aktiv, farbe) {
            onArt(Auszeichnung.Art.KURSIV)
        }
        Knopf(
            symbol = Icons.Outlined.FormatUnderlined,
            beschreibung = "Unterstrichen",
            an = Auszeichnung.Art.UNTERSTRICHEN in aktiv,
            farbe = farbe,
        ) { onArt(Auszeichnung.Art.UNTERSTRICHEN) }
        Knopf(
            symbol = Icons.Outlined.FormatStrikethrough,
            beschreibung = "Durchgestrichen",
            an = Auszeichnung.Art.DURCHGESTRICHEN in aktiv,
            farbe = farbe,
        ) { onArt(Auszeichnung.Art.DURCHGESTRICHEN) }

        Trenner(farbe)

        // Ein zweites Antippen der aktiven Stufe führt zurück auf normal --
        // sonst gäbe es keinen Weg zurück außer einem dritten Knopf, der
        // nichts anderes bedeutet als "keine Überschrift".
        Knopf(
            symbol = Icons.Outlined.Title,
            beschreibung = "Überschrift",
            an = stufe == Auszeichnung.Stufe.UEBERSCHRIFT,
            farbe = farbe,
        ) {
            onStufe(
                if (stufe == Auszeichnung.Stufe.UEBERSCHRIFT) {
                    Auszeichnung.Stufe.NORMAL
                } else {
                    Auszeichnung.Stufe.UEBERSCHRIFT
                },
            )
        }
        Knopf(
            symbol = Icons.Outlined.FormatSize,
            beschreibung = "Zwischenüberschrift",
            an = stufe == Auszeichnung.Stufe.ZWISCHENUEBERSCHRIFT,
            farbe = farbe,
        ) {
            onStufe(
                if (stufe == Auszeichnung.Stufe.ZWISCHENUEBERSCHRIFT) {
                    Auszeichnung.Stufe.NORMAL
                } else {
                    Auszeichnung.Stufe.ZWISCHENUEBERSCHRIFT
                },
            )
        }
    }
}

@Composable
private fun Knopf(
    symbol: ImageVector,
    beschreibung: String,
    an: Boolean,
    farbe: Color,
    onKlick: () -> Unit,
) {
    IconButton(
        onClick = onKlick,
        colors = IconButtonDefaults.iconButtonColors(
            // Die aktive Auszeichnung bekommt eine Fläche, keine zweite
            // Textfarbe: Auf einer eingefärbten Notiz gibt es keine zweite
            // lesbare Farbe (siehe docs/ENTSCHEIDUNGEN.md).
            containerColor = if (an) farbe.copy(alpha = 0.20f) else Color.Transparent,
            contentColor = farbe,
        ),
        modifier = Modifier.size(42.dp),
    ) {
        Icon(symbol, contentDescription = beschreibung, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun Trenner(farbe: Color) {
    Spacer(Modifier.width(4.dp))
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(farbe.copy(alpha = 0.25f)),
    )
    Spacer(Modifier.width(4.dp))
}
