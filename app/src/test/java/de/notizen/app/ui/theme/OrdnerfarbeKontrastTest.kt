package de.notizen.app.ui.theme

import de.notizen.app.ui.tags.TAG_FARBEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordnerfarben als Schrift auf der Seitenspalte, in beiden Themes.
 *
 * Die Palette ist die der Tags, und die wurde fuer Chips gewaehlt, nicht fuer
 * Schrift: Gelb auf Hellblau ist als Punkt in Ordnung und als Wort nicht zu
 * lesen. `lesbareFarbe` muss jede Farbe der Palette auf beiden Hintergruenden
 * auf 4,5:1 bringen, und eine Farbe, die es schon ist, unveraendert lassen.
 *
 * Die Hintergruende sind `surfaceContainer` aus `Theme.kt` (die Spalte), einmal
 * pur und einmal mit der Blase, also der Ordnerfarbe halb durchsichtig darueber.
 */
class OrdnerfarbeKontrastTest {

    private val spalteHell = 0xFFE2E7FF.toInt()
    private val spalteDunkel = 0xFF011742.toInt()

    @Test
    fun `jede Ordnerfarbe wird auf beiden Spalten lesbar`() {
        val maengel = mutableListOf<String>()
        for (farbe in TAG_FARBEN) {
            for ((name, spalte) in listOf("hell" to spalteHell, "dunkel" to spalteDunkel)) {
                val blase = ueberblendet(farbe, spalte, BLASEN_DECKUNG)
                val schrift = lesbareFarbe(farbe, blase)
                val k = kontrast(schrift, blase)
                if (k < MINDESTKONTRAST) maengel += "%08X auf %s: %.2f:1".format(farbe, name, k)
            }
        }
        assertTrue("Zu kontrastarm:\n" + maengel.joinToString("\n"), maengel.isEmpty())
    }

    @Test
    fun `eine Farbe mit genug Kontrast bleibt, wie sie ist`() {
        // Indigo auf der hellen Spalte: 5,5:1, bleibt. (Das dunkle Rot der
        // Palette liegt bei 4,4:1 und wird deshalb abgedunkelt; das war beim
        // Bau die erste Ueberraschung und der Grund fuer diese Rechnung.)
        val indigo = 0xFF3F51B5.toInt()
        assertEquals(indigo, lesbareFarbe(indigo, spalteHell))
        val hellblau = 0xFF7986CB.toInt()
        assertEquals(hellblau, lesbareFarbe(hellblau, spalteDunkel))
    }

    @Test
    fun `auf dunklem Grund wird aufgehellt, auf hellem abgedunkelt`() {
        val gelb = 0xFFF6BF26.toInt()
        val aufHell = lesbareFarbe(gelb, spalteHell)
        val aufDunkel = lesbareFarbe(gelb, spalteDunkel)
        assertTrue("dunkler als das Original", luminanz(aufHell) < luminanz(gelb))
        assertTrue("auf dunklem Grund reicht Gelb selbst", aufDunkel == gelb || luminanz(aufDunkel) >= luminanz(gelb))
    }

    @Test
    fun `die Kontrastformel stimmt an bekannten Werten`() {
        assertEquals(21.0, kontrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, kontrast(0xFF808080.toInt(), 0xFF808080.toInt()), 0.0001)
    }

    @Test
    fun `die Ueberblendung rechnet in sRGB`() {
        // Halb Weiss ueber Schwarz ist Mittelgrau 128, nicht die Luminanzmitte.
        assertEquals(0xFF808080.toInt(), ueberblendet(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0.5f))
        assertEquals(0xFFFF0000.toInt(), ueberblendet(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 1f))
    }
}
