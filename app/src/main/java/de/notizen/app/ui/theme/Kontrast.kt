package de.notizen.app.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Kontrastrechnung nach WCAG 2.1, fuer Farben, die der Nutzer frei waehlt.
 *
 * Die Notizpalette ist einmal durchgerechnet und getestet (`NoteColorPalette`,
 * `NoteColorContrastTest`); dort steht jede Kombination fest. Ordnerfarben
 *  kommen aus der Tagpalette und landen als SCHRIFT auf der
 * Seitenspalte, und ob Gelb auf Hellblau lesbar ist, weiss man erst, wenn man
 * es ausrechnet. Deshalb hier zur Laufzeit, mit derselben Formel wie im Test.
 */

/** WCAG 2.1, relative Luminanz eines ARGB-Werts. Das Alpha wird ignoriert. */
fun luminanz(argb: Int): Double {
    fun kanal(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return 0.2126 * kanal(r) + 0.7152 * kanal(g) + 0.0722 * kanal(b)
}

/** Das Kontrastverhaeltnis zweier Farben, immer >= 1. */
fun kontrast(a: Int, b: Int): Double {
    val la = luminanz(a)
    val lb = luminanz(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * [vorne] mit [deckung] ueber [hinten], in sRGB gemischt, wie es der Bildschirm
 * tut. Fuer die Blase hinter einer gewaehlten Zeile: die Ordnerfarbe halb
 * durchsichtig ueber der Spalte.
 */
fun ueberblendet(vorne: Int, hinten: Int, deckung: Float): Int {
    fun kanal(schiebung: Int): Int {
        val v = (vorne shr schiebung) and 0xFF
        val h = (hinten shr schiebung) and 0xFF
        return (v * deckung + h * (1f - deckung)).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (kanal(16) shl 16) or (kanal(8) shl 8) or kanal(0)
}

/** Das geforderte Mindestverhaeltnis fuer Text (WCAG AA). */
const val MINDESTKONTRAST = 4.5

/**
 * Die Farbe, oder eine hellere bzw. dunklere Tonstufe davon, so dass sie auf
 * [hintergrund] lesbar ist.
 *
 * Reicht der Kontrast, kommt die Farbe unveraendert zurueck: Wer Rot gewaehlt
 * hat, soll Rot sehen. Reicht er nicht, wird sie schrittweise Richtung Weiss
 * (dunkler Hintergrund) oder Schwarz (heller Hintergrund) gemischt, bis das
 * Mindestverhaeltnis steht. Der Farbton bleibt dabei erkennbar; nur die
 * Helligkeit wandert. Am Ende der Leiter stehen Weiss und Schwarz, und die
 * sind auf jedem Hintergrund lesbar, der selbst nicht Mittelgrau ist.
 */
fun lesbareFarbe(farbe: Int, hintergrund: Int, mindestens: Double = MINDESTKONTRAST): Int {
    if (kontrast(farbe, hintergrund) >= mindestens) return farbe
    val ziel = if (luminanz(hintergrund) < 0.5) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    var anteil = 0f
    var kandidat = farbe
    while (anteil < 1f) {
        anteil = (anteil + 0.05f).coerceAtMost(1f)
        kandidat = ueberblendet(ziel, farbe, anteil)
        if (kontrast(kandidat, hintergrund) >= mindestens) return kandidat
    }
    return kandidat
}

/**
 * Wie deckend die Ordnerfarbe hinter einer gewaehlten Zeile in der Seitenspalte
 * liegt. Niedrig genug, dass die Schrift in derselben Farbe darauf
 * lesbar bleibt; hoch genug, dass man die Farbe als Blase erkennt.
 */
const val BLASEN_DECKUNG = 0.22f
