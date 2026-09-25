package de.notizen.core.sync

import de.notizen.core.sync.Abgleichregeln.Aufloesung
import de.notizen.core.sync.Abgleichregeln.Fern
import de.notizen.core.sync.Abgleichregeln.Handlung
import de.notizen.core.sync.Abgleichregeln.Lokal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Tabelle aus SYNC.md 6, Zeile fuer Zeile, plus die drei Konfliktregeln
 * und der Geisterdatei-Fall aus 6.2.
 */
class AbgleichregelnTest {

    private fun lokal(
        baseRev: Long,
        geaendert: Boolean,
        zustand: Zustand = Zustand.ACTIVE,
        updatedAt: Long = 100,
        rev: Long = baseRev,
    ) = Lokal(baseRev, geaendert, zustand, updatedAt, rev)

    private fun fern(rev: Long, zustand: Zustand = Zustand.ACTIVE, updatedAt: Long = 100) =
        Fern(rev, zustand, updatedAt)

    // ------------------------------------------------------------ die Tabelle

    @Test
    fun `unbekannt hier und vorhanden drueben heisst herunterladen`() {
        assertEquals(Handlung.Herunterladen, Abgleichregeln.entscheiden(null, fern(3)))
    }

    @Test
    fun `ein DELETED das hier niemand kennt ist kein Auftrag`() {
        assertEquals(Handlung.Nichts, Abgleichregeln.entscheiden(null, fern(3, Zustand.DELETED)))
    }

    @Test
    fun `gleicher Stand auf beiden Seiten heisst nichts tun`() {
        assertEquals(Handlung.Nichts, Abgleichregeln.entscheiden(lokal(5, false), fern(5)))
    }

    @Test
    fun `lokal geaendert und drueben unveraendert heisst hochladen mit rev plus eins`() {
        assertEquals(Handlung.Hochladen(6), Abgleichregeln.entscheiden(lokal(5, true), fern(5)))
    }

    @Test
    fun `nie oben gewesen heisst hochladen mit rev eins`() {
        assertEquals(Handlung.Hochladen(1), Abgleichregeln.entscheiden(lokal(0, true), null))
    }

    @Test
    fun `drueben neuer und lokal unveraendert heisst herunterladen`() {
        assertEquals(Handlung.Herunterladen, Abgleichregeln.entscheiden(lokal(5, false), fern(7)))
    }

    @Test
    fun `beide geaendert ist ein Konflikt mit rev ueber beiden`() {
        val handlung = Abgleichregeln.entscheiden(lokal(5, true, updatedAt = 10), fern(7, updatedAt = 20))
        assertTrue(handlung is Handlung.Konflikt)
        assertEquals(8, (handlung as Handlung.Konflikt).rev)
    }

    /** Regel 1: Abwesenheit ist keine Information. Kein Loeschen, ein Befund. */
    @Test
    fun `oben gewesen und drueben weg ist ein Befund und wird geheilt`() {
        assertEquals(Handlung.FehltDrueben(6), Abgleichregeln.entscheiden(lokal(5, false), null))
    }

    // ------------------------------------------------------ die Konfliktregeln

    @Test
    fun `DELETED von drueben gewinnt gegen eine lokale Bearbeitung`() {
        val handlung = Abgleichregeln.entscheiden(lokal(5, true), fern(7, Zustand.DELETED))
        assertEquals(Aufloesung.GeloeschtGewinnt, (handlung as Handlung.Konflikt).aufloesung)
    }

    @Test
    fun `der eigene Grabstein gewinnt gegen eine fremde neuere Fassung`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(5, false, Zustand.DELETED, rev = 6),
            fern(9),
        )
        assertEquals(Handlung.Konflikt(Aufloesung.GeloeschtGewinnt, 10), handlung)
    }

    @Test
    fun `der eigene Grabstein geht hoch wenn drueben noch die alte Fassung liegt`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(5, false, Zustand.DELETED, rev = 6),
            fern(5),
        )
        assertEquals(Handlung.Hochladen(6), handlung)
    }

    @Test
    fun `steht drueben schon DELETED ist der Grabstein erledigt`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(5, false, Zustand.DELETED, rev = 6),
            fern(6, Zustand.DELETED),
        )
        assertEquals(Handlung.Nichts, handlung)
    }

    /** SYNC.md 6.2, die Geisterdatei: Grabstein gegen Drive-Fassung. */
    @Test
    fun `die Geisterdatei kommt nicht zurueck`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(17, false, Zustand.DELETED, rev = 18),
            fern(17, Zustand.ACTIVE),
        )
        assertEquals(Handlung.Hochladen(18), handlung)
    }

    @Test
    fun `TRASHED gewinnt gegen eine Bearbeitung und der neuere Inhalt bleibt`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(5, true, Zustand.TRASHED, updatedAt = 10),
            fern(7, Zustand.ACTIVE, updatedAt = 20),
        )
        assertEquals(
            Aufloesung.PapierkorbGewinnt(inhaltVonDrueben = true),
            (handlung as Handlung.Konflikt).aufloesung,
        )
    }

    @Test
    fun `beide bearbeitet gibt eine Kopie`() {
        val handlung = Abgleichregeln.entscheiden(
            lokal(5, true, updatedAt = 30),
            fern(7, updatedAt = 20),
        )
        assertEquals(Aufloesung.Kopie(fernGewinnt = false), (handlung as Handlung.Konflikt).aufloesung)
    }

    // -------------------------------------------------------------- Purge

    @Test
    fun `ein Grabstein verfaellt erst unter dem Wasserzeichen`() {
        assertTrue(Abgleichregeln.grabsteinVerfallen(deletedAt = 10, wasserzeichen = 20))
        assertTrue(!Abgleichregeln.grabsteinVerfallen(deletedAt = 30, wasserzeichen = 20))
        assertTrue(!Abgleichregeln.grabsteinVerfallen(deletedAt = 10, wasserzeichen = 0))
    }
}
