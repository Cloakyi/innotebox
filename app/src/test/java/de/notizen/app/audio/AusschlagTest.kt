package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Pegelanzeige.
 *
 * **Der Fehler, den diese Tests festhalten:** Vorher wurde der Abtastwert
 * linear auf 0..1 abgebildet. Normale Sprache liegt bei etwa −20 dBFS, linear
 * also bei 0,1 — der Ausschlag stand damit bei einem Zehntel, obwohl die
 * Aufnahme in Ordnung war. Am Gerät sah das aus, als käme kaum Ton an.
 *
 * Pegelanzeigen sind aus genau diesem Grund logarithmisch: Der Bereich, in dem
 * Sprache stattfindet, ist linear ein schmaler Streifen ganz unten.
 */
class AusschlagTest {

    /** Ein Abtastwert für einen Pegel in dBFS. */
    private fun beiDb(db: Float): Int =
        (32_768f * Math.pow(10.0, (db / 20f).toDouble()).toFloat()).toInt()

    @Test
    fun `Stille bleibt bei null`() {
        assertEquals(0f, alsAusschlag(0), 0.001f)
    }

    @Test
    fun `Vollausschlag ist eins`() {
        assertEquals(1f, alsAusschlag(32_767), 0.01f)
    }

    @Test
    fun `normale Sprache schlaegt deutlich aus`() {
        // DAS ist der Punkt. Linear waeren es 0,1 gewesen -- ein Zehntel, das
        // wie ein defektes Mikrofon aussieht.
        val ausschlag = alsAusschlag(beiDb(-20f))

        assertTrue(
            "Bei -20 dBFS muss die Anzeige deutlich reagieren, war $ausschlag",
            ausschlag > 0.5f,
        )
    }

    @Test
    fun `leise Sprache ist noch sichtbar`() {
        val ausschlag = alsAusschlag(beiDb(-35f))
        assertTrue("Bei -35 dBFS noch erkennbar, war $ausschlag", ausschlag > 0.25f)
    }

    @Test
    fun `Raumstille bleibt unten`() {
        // Sonst zappelte die Anzeige, obwohl niemand spricht.
        assertTrue(alsAusschlag(beiDb(-60f)) < 0.05f)
    }

    @Test
    fun `lauter ist immer auch mehr Ausschlag`() {
        var vorher = -1f
        (-55..0 step 5).forEach { db ->
            val jetzt = alsAusschlag(beiDb(db.toFloat()))
            assertTrue("bei $db dBFS", jetzt >= vorher)
            vorher = jetzt
        }
    }
}
