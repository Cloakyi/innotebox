package de.notizen.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Weisse Schrift auf einem Hintergrundbild muss lesbar bleiben.
 *
 * Fuer die Palette garantiert das `NoteColorContrastTest` -- dort sind beide
 * Farben bekannt. Bei einem Hintergrundbild ist die untere Farbe **beliebig**,
 * also kann man nur den schlechtesten Fall absichern: ein vollstaendig weisses
 * Bild. Haelt der Abdunkler dort die 4,5:1, haelt er sie ueberall.
 *
 * Ohne diesen Test waere [BILD_ABDUNKLUNG] eine Zahl, die jemand huebsch fand.
 */
class HintergrundScrimTest {

    /** WCAG 2.1, relative Luminanz. */
    private fun luminanz(r: Int, g: Int, b: Int): Double {
        fun kanal(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * kanal(r) + 0.7152 * kanal(g) + 0.0722 * kanal(b)
    }

    private fun kontrast(a: Double, b: Double) = (max(a, b) + 0.05) / (min(a, b) + 0.05)

    /**
     * Schwarz mit Deckung [deckung] ueber einem Grauwert.
     *
     * Alpha-Ueberblendung rechnet in sRGB, nicht in Luminanz -- deshalb hier
     * genauso, sonst faellt das Ergebnis zu guenstig aus.
     */
    private fun ueberblendet(untergrund: Int, deckung: Float): Int =
        (untergrund * (1f - deckung)).toInt()

    @Test
    fun `weisse Schrift haelt 4,5 zu 1 selbst auf einem rein weissen Bild`() {
        val flaeche = ueberblendet(untergrund = 255, deckung = BILD_ABDUNKLUNG)
        val verhaeltnis = kontrast(
            luminanz(255, 255, 255),
            luminanz(flaeche, flaeche, flaeche),
        )
        assertTrue(
            "Weiss auf abgedunkeltem Weiss erreicht nur %.2f:1".format(verhaeltnis),
            verhaeltnis >= 4.5,
        )
    }

    @Test
    fun `der schlechteste Fall ist wirklich das weisse Bild`() {
        // Je heller der Untergrund, desto schlechter der Kontrast zu weisser
        // Schrift. Der Test oben prueft nur einen Punkt -- diese Zeile belegt,
        // dass es der richtige Punkt ist.
        val verhaeltnisse = (0..255 step 15).map { grau ->
            val flaeche = ueberblendet(grau, BILD_ABDUNKLUNG)
            kontrast(luminanz(255, 255, 255), luminanz(flaeche, flaeche, flaeche))
        }
        assertTrue(
            "Ein dunklerer Untergrund muesste besseren Kontrast geben",
            verhaeltnisse == verhaeltnisse.sortedDescending(),
        )
    }
}
