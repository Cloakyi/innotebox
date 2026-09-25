package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Verdichten des Pegelverlaufs auf die Balken der Tonspur.
 *
 * Eine Minute Aufnahme sind 3000 Messwerte, eine Karte zeigt rund 50 Balken.
 * Wie zusammengefasst wird, entscheidet darüber, ob man der Spur ansieht, wo
 * gesprochen wurde, oder ob alles gleich aussieht.
 */
class WellenformTest {

    @Test
    fun `es kommen genau so viele Balken heraus wie verlangt`() {
        assertEquals(56, verdichten(FloatArray(3000) { 0.2f }, 56).size)
    }

    @Test
    fun `eine Pause bleibt als Pause sichtbar`() {
        // Der Punkt der ganzen Uebung: Man soll der Spur ansehen, wo etwas los
        // war. Verschwaende die Pause, waere die Wellenform nur Dekoration.
        val pegel = FloatArray(300) { if (it in 100..199) 0.0005f else 0.3f }
        val balken = verdichten(pegel, 30)

        assertTrue("Mitte muss leiser sein", balken[15] < balken[5])
    }

    @Test
    fun `ein kurzer lauter Moment geht nicht unter`() {
        // Spitzenwert statt Mittelwert: Ueber eine Sekunde gemittelt
        // verschwaende ein kurzer Ausruf, und die Spur waere ein flacher Strich.
        val pegel = FloatArray(300) { 0.001f }
        pegel[150] = 0.8f
        val balken = verdichten(pegel, 30)

        assertTrue("Der laute Moment muss sichtbar bleiben", balken[15] > 0.5f)
    }

    @Test
    fun `die Skalierung ist dieselbe wie bei der Live-Anzeige`() {
        // Sonst saehe dieselbe Aufnahme waehrend des Sprechens anders aus als
        // hinterher -- und man suchte den Fehler beim Mikrofon.
        val pegel = FloatArray(10) { 0.1f }
        assertEquals(ausschlagAusAnteil(0.1f), verdichten(pegel, 1).single(), 0.001f)
    }

    @Test
    fun `Stille ergibt eine flache Spur`() {
        verdichten(FloatArray(300) { 0f }, 30).forEach { assertEquals(0f, it, 0.001f) }
    }

    @Test
    fun `keine Aufnahme ergibt keine Balken`() {
        assertTrue(verdichten(FloatArray(0), 30).isEmpty())
        assertTrue(verdichten(FloatArray(300) { 0.3f }, 0).isEmpty())
    }

    @Test
    fun `mehr Balken als Messwerte stuerzt nicht ab`() {
        // Kommt bei sehr kurzen Aufnahmen vor -- eine halbe Sekunde sind nur
        // 25 Messwerte.
        val balken = verdichten(FloatArray(5) { 0.3f }, 56)
        assertEquals(56, balken.size)
    }
}
