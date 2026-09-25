package de.notizen.app.ui.theme

import de.notizen.core.data.model.NoteColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Die Notiz-Palette muss lesbar sein -- in BEIDEN Themes.
 *
 * Kontrastverhaeltnis mindestens 4,5:1 zwischen
 * Text und Flaeche, geprueft ueber die gesamte Palette. Genau dafuer ist dieser
 * Test da. Wer die Tonwerte anfasst, bekommt es hier gesagt statt erst am
 * Geraet -- und dort faellt schlechter Kontrast erfahrungsgemaess nur dem auf,
 * der ohnehin gut sieht.
 */
class NoteColorContrastTest {

    /** WCAG 2.1, relative Luminanz. */
    private fun luminanz(argb: Long): Double {
        fun kanal(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * kanal(r) + 0.7152 * kanal(g) + 0.0722 * kanal(b)
    }

    private fun kontrast(a: Long, b: Long): Double {
        val la = luminanz(a)
        val lb = luminanz(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun `jede Farbe erreicht mindestens 4,5 zu 1 in beiden Themes`() {
        val maengel = mutableListOf<String>()

        TONWERTE.forEach { (farbe, t) ->
            val hell = kontrast(t.containerLight, t.onContainerLight)
            val dunkel = kontrast(t.containerDark, t.onContainerDark)
            if (hell < 4.5) maengel += "%s hell: %.2f:1".format(farbe.name, hell)
            if (dunkel < 4.5) maengel += "%s dunkel: %.2f:1".format(farbe.name, dunkel)
        }

        assertTrue(
            "Diese Kombinationen sind zu kontrastarm:\n" + maengel.joinToString("\n"),
            maengel.isEmpty(),
        )
    }

    @Test
    fun `die Palette ist vollstaendig`() {
        val fehlend = NoteColor.entries.filter { it != NoteColor.DEFAULT && it !in TONWERTE }
        assertTrue("ohne Tonwerte: $fehlend", fehlend.isEmpty())

        assertEquals(
            "DEFAULT bekommt bewusst keine festen Werte -- es ist die Theme-Flaeche",
            NoteColor.entries.size - 1,
            TONWERTE.size,
        )
    }

    @Test
    fun `Grau und Schwarz sind unterscheidbar`() {
        val grau = TONWERTE.getValue(NoteColor.GREY)
        val schwarz = TONWERTE.getValue(NoteColor.BLACK)

        assertNotEquals(
            "zwei Palettenplaetze fuer dieselbe Farbe waeren einer zu viel",
            grau.containerDark,
            schwarz.containerDark,
        )
        assertTrue(
            "Schwarz muss im Dark Mode dunkler sein als Grau",
            luminanz(schwarz.containerDark) < luminanz(grau.containerDark),
        )
    }

    @Test
    fun `im Dark Mode ist die Flaeche dunkler als der Text`() {
        TONWERTE.forEach { (farbe, t) ->
            assertTrue(
                "${farbe.name}: im Dark Mode muss die Flaeche dunkler sein als der Text",
                luminanz(t.containerDark) < luminanz(t.onContainerDark),
            )
            assertTrue(
                "${farbe.name}: im Light Mode muss die Flaeche heller sein als der Text",
                luminanz(t.containerLight) > luminanz(t.onContainerLight),
            )
        }
    }
}
