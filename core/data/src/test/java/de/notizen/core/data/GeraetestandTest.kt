package de.notizen.core.data

import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.model.Geraetestand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Geraetestand (Phase 15): woran die App entscheidet, ob sie beim Start
 * etwas sagt. Die drei Ausgaenge haengen an drei Fragen, und die muessen
 * unabhaengig von Zeit und Version sein, sonst meldete jeder Neustart nach
 * einem Update „etwas hat sich veraendert".
 */
class GeraetestandTest {

    private fun stand(
        sprache: Faehigkeit = Faehigkeit.VERFUEGBAR,
        textki: Faehigkeit = Faehigkeit.VERFUEGBAR,
        uebersetzung: Faehigkeit = Faehigkeit.VERFUEGBAR,
        am: Long = 1,
        version: Int = 7,
    ) = Geraetestand(sprache, textki, uebersetzung, am, version)

    @Test
    fun `alles da heisst kein Dialog`() {
        val s = stand()
        assertTrue(s.allesVerfuegbar)
        assertFalse(s.etwasLadbar)
        assertFalse(s.etwasFehlt)
    }

    @Test
    fun `ladbar und fehlend werden getrennt erkannt`() {
        val ladbar = stand(textki = Faehigkeit.LADBAR)
        assertTrue(ladbar.etwasLadbar)
        assertFalse(ladbar.etwasFehlt)
        assertFalse(ladbar.allesVerfuegbar)

        val fehlt = stand(uebersetzung = Faehigkeit.NICHT)
        assertTrue(fehlt.etwasFehlt)
        assertFalse(fehlt.etwasLadbar)
    }

    @Test
    fun `Zeit und App-Version zaehlen nicht als Veraenderung`() {
        val alt = stand(am = 1, version = 6)
        val neu = stand(am = 999, version = 7)
        assertFalse(neu.weichtAbVon(alt))
    }

    @Test
    fun `eine andere Faehigkeit ist eine Veraenderung`() {
        val alt = stand(sprache = Faehigkeit.LADBAR)
        val neu = stand(sprache = Faehigkeit.VERFUEGBAR)
        assertTrue(neu.weichtAbVon(alt))
        assertTrue(alt.weichtAbVon(neu))
    }
}
