package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Pegelverlauf, aus dem die mitlaufende Welle gezeichnet wird.
 *
 * Der wichtigste Punkt ist die Begrenzung: Eine Stunde Aufnahme wären
 * siebzigtausend Werte, von denen nur die letzten paar Dutzend je gezeichnet
 * werden. Ohne Deckel wüchse die Liste mit jeder Sekunde weiter — und der
 * Zustand wird zwanzigmal pro Sekunde kopiert.
 *
 * Seit Phase 18 kommt dazu: Die Werte entstehen in Fenstern fester Länge,
 * nicht je Block, und der Zustand zählt sie durch, damit die Welle über die
 * Zeit läuft statt über die Schübe.
 */
class VerlaufTest {

    private val sitzung = AufnahmeSitzung()

    private fun bloecke(anzahl: Int, wert: Float = 0.4f, werteJeBlock: Int = 1) {
        repeat(anzahl) {
            sitzung.fortschritt(
                dauerMs = (it + 1) * 50L,
                pegel = wert,
                hatTon = true,
                feinpegel = FloatArray(werteJeBlock) { wert },
            )
        }
    }

    /** Ein Block mit so vielen Abtastungen, alle mit demselben Betrag. */
    private fun block(abtastungen: Int, betrag: Int): ByteArray {
        val bytes = ByteArray(abtastungen * 2)
        for (i in 0 until abtastungen) {
            bytes[2 * i] = (betrag and 0xFF).toByte()
            bytes[2 * i + 1] = (betrag shr 8).toByte()
        }
        return bytes
    }

    @Test
    fun `ein Wert je fuenfzig Millisekunden, egal wie gross die Bloecke sind`() {
        // 50 ms bei 16 kHz sind 800 Abtastungen. Ein Block von 1000 Abtastungen
        // (62,5 ms, der Block des Pixel) bringt mal einen, mal zwei Werte; ueber
        // acht Bloecke (500 ms) sind es genau zehn.
        val fenster = Pegelfenster()
        var werte = 0
        repeat(8) {
            val b = block(1000, 8_000)
            werte += fenster.verarbeiten(b, b.size).size
        }
        assertEquals(10, werte)

        // Dasselbe mit Bloecken von 200 ms (3200 Abtastungen): vier je Block.
        val grob = Pegelfenster()
        val b = block(3200, 8_000)
        assertEquals(4, grob.verarbeiten(b, b.size).size)
    }

    @Test
    fun `ein Fenster reicht ueber die Blockgrenze und traegt die Spitze mit`() {
        // Der laute Teil liegt im ersten Block, das Fenster wird erst im zweiten
        // voll. Der Wert des Fensters muss trotzdem die Spitze aus dem ersten
        // Block sein.
        val fenster = Pegelfenster()
        val laut = block(400, 20_000)
        val leise = block(400, 200)
        assertEquals(0, fenster.verarbeiten(laut, laut.size).size)
        val werte = fenster.verarbeiten(leise, leise.size)
        assertEquals(1, werte.size)
        assertEquals(alsAusschlag(20_000), werte[0], 0.001f)
        // Die Blockspitze dagegen gehoert zum jeweiligen Block.
        assertEquals(200, fenster.blockspitze)
    }

    @Test
    fun `der Zustand zaehlt jeden Wert mit, auch die vergessenen`() {
        sitzung.beginnen("n1")
        bloecke(500, werteJeBlock = 2)

        assertEquals(1000L, sitzung.aufnahme.value.verlaufGesamt)
        assertTrue(sitzung.aufnahme.value.verlauf.size < 1000)
    }

    @Test
    fun `der Verlauf waechst nicht ins Unendliche`() {
        sitzung.beginnen("n1")
        bloecke(2_000)

        assertTrue(
            "Sonst waechst der Zustand mit jeder Sekunde weiter, " +
                "war ${sitzung.aufnahme.value.verlauf.size}",
            sitzung.aufnahme.value.verlauf.size <= 400,
        )
    }

    @Test
    fun `es bleibt das Juengste stehen`() {
        // Die Welle laeuft von rechts nach links: Was aus dem Bild wandert, ist
        // das Aelteste. Behielte man das Aelteste, staende die Welle still.
        sitzung.beginnen("n1")
        bloecke(500, wert = 0.1f)
        bloecke(1, wert = 0.9f)

        assertEquals(0.9f, sitzung.aufnahme.value.verlauf.last(), 0.001f)
    }

    @Test
    fun `eine neue Aufnahme faengt mit leerem Verlauf an`() {
        // Sonst haenge die alte Welle vorn an der neuen -- und man saehe
        // Sekunden, die zu einer anderen Aufnahme gehoeren.
        sitzung.beginnen("n1")
        bloecke(10)
        sitzung.beenden()

        sitzung.beginnen("n2")

        assertTrue(sitzung.aufnahme.value.verlauf.isEmpty())
        assertEquals(0L, sitzung.aufnahme.value.verlaufGesamt)
    }
}
