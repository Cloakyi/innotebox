package de.notizen.core.data

import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ordner an der echten Datenbank.
 *
 * Zwei Zusagen stehen hier im Mittelpunkt, und beide kosten beim Bruch Daten:
 * Ein geloeschter Ordner nimmt seinen Inhalt NICHT mit, und ein Ordner kann
 * nicht in seinem eigenen Unterordner landen.
 */
@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest : DatenbankTestbasis() {

    @Test
    fun `ein neuer Ordner steht in der Liste und wartet auf den Abgleich`() = runTest {
        val angelegt = ordner.anlegen("Reisen")

        assertEquals(listOf("Reisen"), ordner.getAll().map { it.name })
        assertNotNull(db.syncDao().stateOf(EntityType.FOLDER, angelegt.id))
    }

    @Test
    fun `Umbenennen zaehlt updatedAt hoch`() = runTest {
        val angelegt = ordner.anlegen("Reisen")
        clock.advanceBy(1_000)
        ordner.umbenennen(angelegt.id, "Urlaub")

        val danach = ordner.get(angelegt.id)
        assertEquals("Urlaub", danach?.name)
        assertTrue((danach?.updatedAt ?: 0) > angelegt.updatedAt)
    }

    // ---------------------------------------------------------- Verschieben

    @Test
    fun `ein Ordner laesst sich zu einem anderen haengen`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte")

        assertTrue(ordner.verschieben(unten.id, oben.id))
        assertEquals(oben.id, ordner.get(unten.id)?.parentId)
    }

    /**
     * Der Fall, der den Baum zerschneidet.
     *
     * Die Ordnerauswahl bietet ein solches Ziel gar nicht erst an, aber der
     * Baum kann sich zwischen dem Aufgehen der Auswahl und dem Antippen
     * geaendert haben. Deshalb steht die Pruefung auch hier.
     */
    @Test
    fun `ein Ordner darf nicht in seinen eigenen Unterordner`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)

        assertFalse(ordner.verschieben(oben.id, unten.id))
        assertNull(ordner.get(oben.id)?.parentId)
    }

    @Test
    fun `ein Ordner darf nicht in sich selbst`() = runTest {
        val einer = ordner.anlegen("Arbeit")

        assertFalse(ordner.verschieben(einer.id, einer.id))
        assertNull(ordner.get(einer.id)?.parentId)
    }

    // -------------------------------------------------------------- Loeschen

    /**
     * Wer einen Ordner wegraeumt, will den Ordner los sein, nicht das, was
     * darin lag. Ein Loeschen, das nebenbei dreissig Notizen mitnimmt, waere
     * die teuerste denkbare Fehlbedienung.
     */
    @Test
    fun `die Notizen eines geloeschten Ordners ruecken eine Ebene hoeher`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)

        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), unten.id)

        ordner.loeschen(unten.id)

        assertEquals(oben.id, notes.get(notiz)?.note?.folderId)
    }

    @Test
    fun `aus dem obersten Ordner ruecken sie in den Hauptordner`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)

        ordner.loeschen(einer.id)

        assertNull(notes.get(notiz)?.note?.folderId)
    }

    @Test
    fun `die Unterordner ruecken mit nach oben`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val mitte = ordner.anlegen("Projekte", oben.id)
        val unten = ordner.anlegen("Kunde", mitte.id)

        ordner.loeschen(mitte.id)

        assertEquals(oben.id, ordner.get(unten.id)?.parentId)
    }

    // -------------------------------------------- Herkunft nach dem Loeschen

    /**
     * Phase 14a fuer 14c: Was beim Loeschen „nur der Ordner" herausrueckt,
     * merkt sich, woher es kam. Der Ordner im Papierkorb zeigt es spaeter
     * ausgegraut als seinen frueheren Inhalt.
     */
    @Test
    fun `herausgerueckte Notizen und Unterordner merken sich den geloeschten Ordner`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val mitte = ordner.anlegen("Projekte", oben.id)
        val unten = ordner.anlegen("Kunde", mitte.id)
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), mitte.id)

        ordner.loeschen(mitte.id)

        assertEquals(mitte.id, notes.get(notiz)?.note?.ehemaligerOrdnerId)
        assertEquals(mitte.id, ordner.get(unten.id)?.ehemaligerElternId)
        assertEquals(listOf(notiz), ordner.observeHerausgerueckteNotizen(mitte.id).first().map { it.note.id })
        assertEquals(listOf(unten.id), ordner.observeHerausgerueckteOrdner(mitte.id).first().map { it.id })
    }

    @Test
    fun `wer die Notiz von Hand verschiebt, hat sich entschieden`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val anderer = ordner.anlegen("Privat")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)
        ordner.loeschen(einer.id)
        assertEquals(einer.id, notes.get(notiz)?.note?.ehemaligerOrdnerId)

        notes.setOrdner(listOf(notiz), anderer.id)

        assertNull(notes.get(notiz)?.note?.ehemaligerOrdnerId)
    }

    @Test
    fun `wer den Unterordner von Hand verschiebt, hat sich entschieden`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val mitte = ordner.anlegen("Projekte", oben.id)
        val unten = ordner.anlegen("Kunde", mitte.id)
        val anderer = ordner.anlegen("Privat")
        ordner.loeschen(mitte.id)
        assertEquals(mitte.id, ordner.get(unten.id)?.ehemaligerElternId)

        assertTrue(ordner.verschieben(unten.id, anderer.id))

        assertNull(ordner.get(unten.id)?.ehemaligerElternId)
    }

    // -------------------------------------------------------------- Bereiche

    /**
     * Das Archiv des Ordnermodus hat einen eigenen Baum (SYNC.md 13). Die
     * Listen sind je Bereich getrennt, ein Unterordner erbt den Bereich, und
     * ueber die Grenze wird nicht verschoben.
     */
    @Test
    fun `Archivordner stehen in einer eigenen Liste und erben den Bereich`() = runTest {
        val normal = ordner.anlegen("Arbeit")
        val archiv = ordner.anlegen("Alt", bereich = Bereich.ARCHIV)
        val darunter = ordner.anlegen("2025", archiv.id)

        assertEquals(listOf(normal.id), ordner.getAll().map { it.id })
        assertEquals(setOf(archiv.id, darunter.id), ordner.getAll(Bereich.ARCHIV).map { it.id }.toSet())
        assertEquals(Bereich.ARCHIV, ordner.get(darunter.id)?.bereich)
        assertEquals(setOf(archiv.id, darunter.id), ordner.observeAll(Bereich.ARCHIV).first().map { it.id }.toSet())
    }

    @Test
    fun `ueber die Bereichsgrenze wird nicht verschoben`() = runTest {
        val normal = ordner.anlegen("Arbeit")
        val archiv = ordner.anlegen("Alt", bereich = Bereich.ARCHIV)

        assertFalse(ordner.verschieben(normal.id, archiv.id))
        assertFalse(ordner.verschieben(archiv.id, normal.id))
        assertNull(ordner.get(normal.id)?.parentId)
        assertNull(ordner.get(archiv.id)?.parentId)
    }

    // ------------------------------------------------ Archiv im Ordnermodus

    /**
     * Phase 14b: Archivieren im Ordnersystem ruehrt die Stufe nicht an, merkt
     * sich die Herkunft und nimmt die Notiz aus dem normalen Baum heraus.
     */
    @Test
    fun `archivieren nimmt die Notiz aus dem Ordner und merkt sich die Herkunft`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)
        clock.advanceBy(1_000)

        notes.imOrdnerArchivieren(listOf(notiz))

        val danach = notes.get(notiz)!!.note
        assertNull(danach.folderId)
        assertEquals(einer.id, danach.herkunftOrdnerId)
        assertEquals(clock.now(), danach.ordnerArchiviertAt)
        assertEquals(Stage.INBOX, danach.stage)
        assertTrue(notes.observeImOrdner(einer.id).first().isEmpty())
        assertEquals(listOf(notiz), notes.observeImOrdner(null, archiviert = true).first().map { it.note.id })
        assertTrue(notes.observeImOrdner(null).first().isEmpty())
        assertEquals(1, notes.observeAnzahlImOrdnerArchiv().first())
    }

    @Test
    fun `zurueckholen legt die Notiz in den gewaehlten Ordner, nicht automatisch in den alten`() = runTest {
        val alt = ordner.anlegen("Arbeit")
        val neu = ordner.anlegen("Privat")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), alt.id)
        notes.imOrdnerArchivieren(listOf(notiz))

        notes.ausOrdnerArchivZurueck(listOf(notiz), neu.id)

        val danach = notes.get(notiz)!!.note
        assertEquals(neu.id, danach.folderId)
        assertNull(danach.ordnerArchiviertAt)
        assertNull(danach.herkunftOrdnerId)
    }

    @Test
    fun `das Undo zum Archivieren bringt jede Notiz dorthin zurueck, woher sie kam`() = runTest {
        val a = ordner.anlegen("A")
        val b = ordner.anlegen("B")
        val inA = notes.create()
        val inB = notes.create()
        val imHaupt = notes.create()
        notes.setOrdner(listOf(inA), a.id)
        notes.setOrdner(listOf(inB), b.id)
        notes.imOrdnerArchivieren(listOf(inA, inB, imHaupt))

        notes.archivierungZuruecknehmen(listOf(inA, inB, imHaupt))

        assertEquals(a.id, notes.get(inA)!!.note.folderId)
        assertEquals(b.id, notes.get(inB)!!.note.folderId)
        assertNull(notes.get(imHaupt)!!.note.folderId)
        assertNull(notes.get(inA)!!.note.ordnerArchiviertAt)
    }

    @Test
    fun `beim Loeschen im Archiv ruecken nur Archivordner nach`() = runTest {
        val archiv = ordner.anlegen("Alt", bereich = Bereich.ARCHIV)
        val darunter = ordner.anlegen("2025", archiv.id)
        val normal = ordner.anlegen("Arbeit")

        ordner.loeschen(archiv.id)

        assertNull(ordner.get(darunter.id)?.parentId)
        assertEquals(Bereich.ARCHIV, ordner.get(darunter.id)?.bereich)
        assertNull(ordner.get(normal.id)?.ehemaligerElternId)
    }

    /**
     * Weich geloescht, nicht entfernt.
     *
     * Fuer Ordner gibt es in Drive keine Grabsteine. Bliebe die Zeile nicht
     * stehen, erfuehre das andere Geraet nie von der Loeschung und schickte den
     * Ordner beim naechsten Abgleich zurueck.
     */
    @Test
    fun `ein geloeschter Ordner behaelt seine Zeile mit deletedAt`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        ordner.loeschen(einer.id)

        assertTrue(ordner.getAll().isEmpty())
        assertNotNull(db.folderDao().getById(einer.id)?.deletedAt)
    }

    // ------------------------------------------- Loeschen samt Inhalt

    /**
     * Alles landet im Papierkorb, nichts verschwindet.
     *
     * Die Notiz behaelt dabei ihre Ordnerkennung. Genau daran haengt, dass sich
     * der Ordner spaeter samt Inhalt zurueckholen laesst.
     */
    @Test
    fun `mit Inhalt geloescht wandert die Notiz in den Papierkorb und behaelt ihren Ordner`() =
        runTest {
            val einer = ordner.anlegen("Arbeit")
            val notiz = notes.create()
            notes.setOrdner(listOf(notiz), einer.id)

            ordner.loeschenMitInhalt(einer.id)

            val danach = notes.get(notiz)?.note
            assertNotNull(danach?.deletedAt)
            assertEquals(einer.id, danach?.folderId)
        }

    /**
     * Ein Loeschen „samt Inhalt", das beim ersten Unterordner haltmacht, ist
     * die Sorte Halbheit, die man erst im Papierkorb bemerkt.
     */
    @Test
    fun `die Notizen der Unterordner gehen mit in den Papierkorb`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)
        val tief = notes.create()
        notes.setOrdner(listOf(tief), unten.id)

        ordner.loeschenMitInhalt(oben.id)

        assertNotNull(notes.get(tief)?.note?.deletedAt)
        assertNotNull(db.folderDao().getById(unten.id)?.deletedAt)
    }

    // ------------------------------------------------- Wiederherstellen

    @Test
    fun `der Ordner kommt mit seinen Notizen zurueck`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)
        ordner.loeschenMitInhalt(einer.id)

        ordner.wiederherstellen(einer.id, mitNotizen = true)

        assertNull(db.folderDao().getById(einer.id)?.deletedAt)
        assertNull(notes.get(notiz)?.note?.deletedAt)
    }

    /** Wer nur die Huelle braucht, bekommt nur die Huelle. */
    @Test
    fun `ohne Notizen kommt nur der Ordner zurueck`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)
        ordner.loeschenMitInhalt(einer.id)

        ordner.wiederherstellen(einer.id, mitNotizen = false)

        assertNull(db.folderDao().getById(einer.id)?.deletedAt)
        assertNotNull(notes.get(notiz)?.note?.deletedAt)
    }

    /**
     * Ohne die Eltern haenge der Ordner an einem Elternteil, das es nicht gibt,
     * und stuende nach `Ordnerregeln.kinder` an der Wurzel statt dort, wo er war.
     */
    @Test
    fun `die geloeschten Eltern kommen mit zurueck`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)
        ordner.loeschenMitInhalt(oben.id)

        ordner.wiederherstellen(unten.id, mitNotizen = false)

        assertNull(db.folderDao().getById(oben.id)?.deletedAt)
        assertNull(db.folderDao().getById(unten.id)?.deletedAt)
    }

    /**
     * Das Undo zu "Ordner und Inhalt". `wiederherstellen` holt nur die
     * Eltern mit; hier muessen die Unterordner mitkommen, sonst staende nach
     * dem Undo ein halber Baum da.
     */
    @Test
    fun `samt Inhalt zurueck holt auch die Unterordner und deren Notizen`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)
        val tief = notes.create()
        notes.setOrdner(listOf(tief), unten.id)
        ordner.loeschenMitInhalt(oben.id)

        ordner.wiederherstellenSamtInhalt(oben.id)

        assertNull(db.folderDao().getById(oben.id)?.deletedAt)
        assertNull(db.folderDao().getById(unten.id)?.deletedAt)
        assertNull(notes.get(tief)?.note?.deletedAt)
    }

    @Test
    fun `endgueltig geloescht ist die Zeile weg`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        ordner.loeschen(einer.id)

        ordner.endgueltigLoeschen(einer.id)

        assertNull(db.folderDao().getById(einer.id))
    }

    /**
     * Die Notizen gehen dabei NICHT mit. Endgueltiges Loeschen von Notizen
     * laeuft ueber den einen Weg, den es dafuer gibt, mit Grabsteinen und dem
     * Aufraeumen der Dateien.
     */
    @Test
    fun `endgueltig geloescht bleibt die Notiz im Papierkorb liegen`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)
        ordner.loeschenMitInhalt(einer.id)

        ordner.endgueltigLoeschen(einer.id)

        assertNotNull(notes.get(notiz))
        assertNotNull(notes.get(notiz)?.note?.deletedAt)
    }

    // ------------------------------------------ Automatisches Leeren

    /**
     * Bis zum 2026-09-14 blieb ein Ordner fuer immer im Papierkorb liegen, weil
     * das automatische Leeren nur Notizen kannte.
     */
    @Test
    fun `ein Ordner ueber der Frist wird beim Leeren entfernt`() = runTest {
        val alt = ordner.anlegen("Alt")
        ordner.loeschen(alt.id)
        clock.advanceBy(31L * 24 * 60 * 60 * 1000)
        val frisch = ordner.anlegen("Frisch")
        ordner.loeschen(frisch.id)

        val entfernt = ordner.papierkorbAufraeumen(tage = 30)

        assertEquals(1, entfernt)
        assertNull(db.folderDao().getById(alt.id))
        assertNotNull(db.folderDao().getById(frisch.id))
    }

    /** Frist null heisst: nie automatisch leeren, genau wie bei den Notizen. */
    @Test
    fun `bei Frist null bleibt alles liegen`() = runTest {
        val alt = ordner.anlegen("Alt")
        ordner.loeschen(alt.id)
        clock.advanceBy(400L * 24 * 60 * 60 * 1000)

        assertEquals(0, ordner.papierkorbAufraeumen(tage = 0))
        assertNotNull(db.folderDao().getById(alt.id))
    }

    // ----------------------------------------------- Ziel beim Loeschen

    @Test
    fun `beim Loeschen laesst sich das Ziel der Notizen waehlen`() = runTest {
        val weg = ordner.anlegen("Arbeit")
        val ziel = ordner.anlegen("Privat")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), weg.id)

        ordner.loeschen(weg.id, zielId = ziel.id)

        assertEquals(ziel.id, notes.get(notiz)?.note?.folderId)
    }

    @Test
    fun `die umgehaengten Notizen gehen in den naechsten Abgleich`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val notiz = notes.create()
        notes.setOrdner(listOf(notiz), einer.id)

        // Der Stand nach dem Anlegen zaehlt nicht: Die Notiz war ohnehin schon
        // vorgemerkt. Erst nach einem Abgleich zeigt sich, ob das Umhaengen
        // selbst eine Vormerkung erzeugt.
        db.syncDao().markSynced(EntityType.NOTE, notiz, clock.now())
        ordner.loeschen(einer.id)

        assertNotNull(db.syncDao().stateOf(EntityType.NOTE, notiz))
        assertEquals(
            de.notizen.core.data.model.SyncStatus.DIRTY,
            db.syncDao().stateOf(EntityType.NOTE, notiz)?.syncStatus,
        )
    }
}
