package de.notizen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.notizen.core.data.model.NoteColor

/** Flaeche und Textfarbe einer Notizkarte im aktuellen Theme. */
data class NotizFarbe(val container: Color, val onContainer: Color)

/**
 * Die vier Tonwerte einer Palettenfarbe.
 *
 * Die Referenzfarben aus Abschnitt 5a der Spezifikation (Google-Kalender-Palette)
 * sind AUSGANGSPUNKTE, nicht fertige Kartenfarben. Ungemildertes #F6BF26 als
 * Flaeche im Dark Mode blendet und macht Text unlesbar.
 *
 * Die Werte hier sind aus den Referenzfarben BERECHNET, nicht geschaetzt: der
 * Farbton bleibt, Helligkeit und Saettigung werden auf feste Rollen gesetzt.
 * Die Saettigung bleibt dabei bewusst hoch -- zu blasse Pastelltoene sehen alle
 * gleich aus, und eine Palette, in der man Rot nicht von Orange unterscheidet,
 * ist keine.
 *
 * Der schlechteste Kontrast liegt bei 5,5:1, gefordert sind 4,5:1.
 * `NoteColorContrastTest` prueft das bei jedem Build -- wer hier Werte aendert,
 * bekommt es dort gesagt.
 */
internal data class Tonwerte(
    val containerLight: Long,
    val onContainerLight: Long,
    val containerDark: Long,
    val onContainerDark: Long,
)

internal val TONWERTE: Map<NoteColor, Tonwerte> = mapOf(
    NoteColor.RED          to Tonwerte(0xFFF8BCBC, 0xFF570202, 0xFF671B1B, 0xFFF4C0C0),
    NoteColor.PINK         to Tonwerte(0xFFF4C4C0, 0xFF4C120E, 0xFF67211B, 0xFFF4C4C0),
    NoteColor.ORANGE       to Tonwerte(0xFFF8CABC, 0xFF551704, 0xFF672D1B, 0xFFF4CCC0),
    NoteColor.YELLOW       to Tonwerte(0xFFF8E8BC, 0xFF564004, 0xFF67531B, 0xFFF4E6C0),
    NoteColor.GREEN        to Tonwerte(0xFFC5EFDB, 0xFF14462E, 0xFF1C6644, 0xFFC5EFDB),
    NoteColor.DARK_GREEN   to Tonwerte(0xFFBCF8D9, 0xFF07522B, 0xFF1B673F, 0xFFC0F4D9),
    NoteColor.TEAL         to Tonwerte(0xFFBCF8F2, 0xFF02574E, 0xFF1B675F, 0xFFC0F4EF),
    NoteColor.BLUE         to Tonwerte(0xFFBCE4F8, 0xFF023B57, 0xFF1B4E67, 0xFFC0E3F4),
    NoteColor.DARK_BLUE    to Tonwerte(0xFFC8CEEC, 0xFF171E42, 0xFF222B60, 0xFFC8CEEC),
    NoteColor.PURPLE       to Tonwerte(0xFFCACFEA, 0xFF191F40, 0xFF242D5E, 0xFFCACFEA),
    NoteColor.DARK_PURPLE  to Tonwerte(0xFFE8C2F2, 0xFF3E104A, 0xFF571B67, 0xFFE8C2F2),
    NoteColor.BROWN        to Tonwerte(0xFFE3D6D1, 0xFF382721, 0xFF523931, 0xFFE3D6D1),
    NoteColor.DARK_BROWN   to Tonwerte(0xFFE4D4D0, 0xFF382521, 0xFF523730, 0xFFE4D4D0),
    NoteColor.GREY         to Tonwerte(0xFFDADADA, 0xFF2D2D2D, 0xFF414141, 0xFFDADADA),
    NoteColor.BLACK        to Tonwerte(0xFFCCCCCC, 0xFF1F1F1F, 0xFF1C1C1C, 0xFFC7C7C7),
)

/**
 * Die Farbe dieser Notiz im gerade aktiven Theme.
 *
 * DEFAULT hat bewusst KEINEN festen Wert: die Karte uebernimmt die Flaeche des
 * Themes. Bei aktivem Dynamic Color wechselt sie damit mit dem Hintergrundbild
 * -- das ist gewollt (Spezifikation Abschnitt 5a).
 *
 * Ob hell oder dunkel gilt, wird aus der Theme-Flaeche abgelesen statt aus
 * `isSystemInDarkTheme()`. Nur so stimmt es auch, wenn der Nutzer das Theme in
 * den Einstellungen fest vorgibt.
 */
@Composable
@ReadOnlyComposable
fun NoteColor.alsNotizFarbe(): NotizFarbe {
    val schema = MaterialTheme.colorScheme
    if (this == NoteColor.DEFAULT) {
        return NotizFarbe(schema.surface, schema.onSurface)
    }
    val t = TONWERTE[this] ?: return NotizFarbe(schema.surface, schema.onSurface)
    val dunkel = schema.surface.luminance() < 0.5f
    return if (dunkel) {
        NotizFarbe(Color(t.containerDark), Color(t.onContainerDark))
    } else {
        NotizFarbe(Color(t.containerLight), Color(t.onContainerLight))
    }
}

/** Anzeigename fuer die Farbauswahl. */
fun NoteColor.anzeigename(): String = when (this) {
    NoteColor.DEFAULT -> "Standard"
    NoteColor.RED -> "Rot"
    NoteColor.PINK -> "Rosa"
    NoteColor.ORANGE -> "Orange"
    NoteColor.YELLOW -> "Gelb"
    NoteColor.GREEN -> "Grün"
    NoteColor.DARK_GREEN -> "Dunkelgrün"
    NoteColor.TEAL -> "Türkis"
    NoteColor.BLUE -> "Blau"
    NoteColor.DARK_BLUE -> "Dunkelblau"
    NoteColor.PURPLE -> "Lila"
    NoteColor.DARK_PURPLE -> "Dunkellila"
    NoteColor.BROWN -> "Braun"
    NoteColor.DARK_BROWN -> "Dunkelbraun"
    NoteColor.GREY -> "Grau"
    NoteColor.BLACK -> "Schwarz"
}

/**
 * Wie Geändertes im Transkript hervorgehoben wird.
 *
 * **Ein Textmarker, keine Textfarbe.** Der erste Versuch färbte die Schrift ein
 * — auf einer eingefärbten Notiz war das Ergebnis nur noch fett, weil eine
 * zweite lesbare Textfarbe auf einer farbigen Fläche schlicht nicht zu haben
 * ist: Der Kontrast der Palette ist ausschließlich für `onContainer` gegen
 * `container` nachgerechnet (`NoteColorContrastTest`), für alles andere gilt er
 * nicht.
 *
 * Ein hinterlegter Streifen löst das: Die Schrift behält ihre geprüfte Farbe,
 * die Markierung entsteht aus der Fläche dahinter. Das funktioniert auf jeder
 * Notizfarbe gleich gut und ist auch bei vielen Wörtern auf einen Blick zu
 * sehen — genau das, was Fettschrift nicht leistet.
 */
@Composable
@ReadOnlyComposable
fun markiererZu(farbe: NoteColor): Color =
    if (farbe == NoteColor.DEFAULT) {
        MaterialTheme.colorScheme.primary.copy(alpha = MARKIERER_DECKUNG)
    } else {
        farbe.alsNotizFarbe().onContainer.copy(alpha = MARKIERER_DECKUNG)
    }

/**
 * Deckkraft des Markierers.
 *
 * Kräftig genug, um beim Überfliegen aufzufallen, schwach genug, dass die
 * Schrift darüber nicht leidet.
 */
private const val MARKIERER_DECKUNG = 0.22f

/**
 * Die Farbe des Favoritensterns.
 *
 * Fest und nicht aus dem Theme: „Favorit" ist überall dieselbe Aussage, und ein
 * Stern, der auf jeder Notiz anders aussieht, ist kein Erkennungszeichen mehr.
 * Gold ist dafür die Farbe, die niemand erklären muss.
 *
 * Eine der wenigen erlaubten festen Farben im UI-Code — dieselbe Ausnahme wie
 * für die Palette selbst, und aus demselben Grund.
 */
val FAVORIT_GOLD = Color(0xFFFFC107)

/**
 * Deckung des Abdunklers ueber einem Hintergrundbild.
 *
 * **Der Wert ist berechnet, nicht gewaehlt.** Ein Hintergrundbild kann jede
 * Farbe haben, also auch reines Weiss -- und genau das ist der schlechteste
 * Fall fuer weisse Schrift. Bei 0,55 Schwarz darueber ergibt sich selbst dann
 * noch ein Kontrast von rund 4,75:1, die geforderten 4,5:1 sind also auch im
 * ungeguenstigsten Bild gehalten. `HintergrundScrimTest` rechnet das nach;
 * wer den Wert senkt, bekommt es dort gesagt.
 */
const val BILD_ABDUNKLUNG = 0.55f

/**
 * Flaeche und Schriftfarbe einer Notiz mit Hintergrundbild.
 *
 * `container` ist durchsichtig, weil das Bild dahinterliegt und nicht
 * uebermalt werden darf. Die Schrift ist immer weiss -- ueber einem beliebigen
 * Foto gibt es keine Palettenfarbe, fuer die man Lesbarkeit zusagen koennte.
 */
val BILD_FARBE = NotizFarbe(container = Color.Transparent, onContainer = Color.White)
