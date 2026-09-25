package de.notizen.core.data

import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.search.Suchausdruck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Prueft den Volltextindex.
 *
 * Der Index ist eine eigenstaendige FTS4-Tabelle, die das Repository von Hand
 * pflegt -- SQLite haelt sie NICHT selbst aktuell (siehe SYNC.md 14.14). Wenn
 * eine Schreiboperation den Index vergisst, faellt das nur hier auf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolltextsucheTest : DatenbankTestbasis() {

    private suspend fun trefferIds(ausdruck: String) =
        db.searchDao().search(ausdruck).map { it.note.id }

    @Test
    fun `findet ueber Titel und Body`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Einkaufsliste", "Tomaten und Basilikum besorgen"))

        assertTrue(trefferIds("Einkaufsliste").contains(id))
        assertTrue(trefferIds("Basilikum").contains(id))
    }

    @Test
    fun `findet ueber Checklisteneintraege`() = runTest {
        val id = notes.create()
        val eintrag = NoteItemEntity(UUID.randomUUID().toString(), id, "Zahnbuerste", false, 0)
        notes.updateContent(id, NoteContent("Packliste", "", listOf(eintrag)))

        assertTrue(
            "Checklisteneintraege gehoeren in den Index",
            trefferIds("Zahnbuerste").contains(id),
        )
    }

    @Test
    fun `findet ueber Transkripttext`() = runTest {
        val id = notes.create()
        db.transcriptDao().upsertAll(
            listOf(
                TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = id,
                    startMs = 0,
                    endMs = 2000,
                    text = "Erinnerung an das Gespraech mit Herrn Kellermann",
                    isFinal = true,
                ),
            ),
        )
        notes.reindex(id)

        assertTrue(
            "Transkripte von Audio-Notizen sind durchsuchbar",
            trefferIds("Kellermann").contains(id),
        )
    }

    @Test
    fun `beide Woerter muessen vorkommen`() = runTest {
        // Prueft die Annahme, auf der `Suchausdruck` steht: in der
        // Standard-Abfragesyntax von FTS3/4 verbindet das LEERZEICHEN zwei
        // Begriffe mit UND. Das steht so in der Dokumentation -- hier steht es
        // am echten SQLite. Waere es ein ODER, faende jede Suche mit zwei
        // Woertern viel zu viel.
        val beide = notes.create()
        notes.updateContent(beide, NoteContent("Einkauf", "Tomaten und Basilikum"))
        val nurEins = notes.create()
        notes.updateContent(nurEins, NoteContent("Garten", "Basilikum umtopfen"))

        val ausdruck = Suchausdruck.bauen("Tomaten Basilikum")!!
        val gefunden = trefferIds(ausdruck)

        assertTrue(gefunden.contains(beide))
        assertFalse("Leerzeichen muss UND bedeuten, nicht ODER", gefunden.contains(nurEins))
    }

    @Test
    fun `ein Bindestrich schliesst nichts aus`() = runTest {
        // Der Beweis fuer den teuersten Einzelfall: roh durchgereicht wuerde
        // FTS4 hier nach "Meier" OHNE "Schmidt" suchen und die Notiz gerade
        // NICHT finden.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Meier-Schmidt", "Rueckruf vereinbart"))

        val ausdruck = Suchausdruck.bauen("Meier-Schmidt")!!
        assertTrue(trefferIds(ausdruck).contains(id))
    }

    /**
     * Am echten SQLite geprueft: Ein
     * unterstrichenes Wort steht im Text als `__wort__`, und der Tokenizer
     * `unicode61` muss den Unterstrich als Trenner lesen, sonst hiesse das
     * Wort im Index `__wort__` und waere ueber „wort" nicht zu finden.
     */
    @Test
    fun `ein unterstrichenes Wort ist auffindbar`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Vertrag", "Bitte den __Kuendigungstermin__ beachten"))

        assertTrue(trefferIds(Suchausdruck.bauen("Kuendigungstermin")!!).contains(id))
        assertTrue("auch beim Tippen", trefferIds(Suchausdruck.bauen("Kuendig")!!).contains(id))
        assertTrue("der Unterstrich selbst ist kein Suchbegriff", Suchausdruck.woerter("__x__") == listOf("x"))
    }

    @Test
    fun `Praefixsuche filtert waehrend des Tippens`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Besprechung", "Protokoll"))

        assertTrue(trefferIds("Bespr*").contains(id))
        assertFalse("ohne Stern kein Teiltreffer", trefferIds("Bespr").contains(id))
    }

    /**
     * Derselbe Fall ueber den Ausdruck, den die App wirklich schickt. Bis zum
     * 2026-09-19 stand der Stern hinter den Anfuehrungszeichen (`"Bespr"*`),
     * und FTS4 las das nicht als Praefix: Der Test oben mit dem von Hand
     * geschriebenen Ausdruck war gruen, die Suche in der App fand beim Tippen
     * trotzdem nur ganze Woerter. Deshalb prueft dieser Test den Weg, den
     * der Nutzer geht, und nicht den, den man sich denkt.
     */
    @Test
    fun `die Praefixsuche geht auch durch Suchausdruck`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Besprechung", "Protokoll"))

        assertTrue(trefferIds(Suchausdruck.bauen("Bespr")!!).contains(id))
        assertTrue(trefferIds(Suchausdruck.bauen("bespr proto")!!).contains(id))
        assertFalse(trefferIds(Suchausdruck.bauen("Besprechungen")!!).contains(id))
    }

    @Test
    fun `Umlaute werden unabhaengig von Gross- und Kleinschreibung gefunden`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Küche", "Kühlschrank abtauen"))

        assertTrue("gleiche Schreibweise", trefferIds("Küche").contains(id))
        assertTrue("kleingeschrieben", trefferIds("küche").contains(id))
        assertTrue(
            "unicode61 entfernt Diakritika -- ohne diesen Tokenizer schluege das fehl",
            trefferIds("kuche").contains(id),
        )
    }

    @Test
    fun `Aenderung aktualisiert den Index`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Alterstand", "veraltet"))
        assertTrue(trefferIds("Alterstand").contains(id))

        notes.updateContent(id, NoteContent("Neuerstand", "aktuell"))

        assertFalse("alter Text darf nicht mehr treffen", trefferIds("Alterstand").contains(id))
        assertTrue(trefferIds("Neuerstand").contains(id))
    }

    @Test
    fun `Papierkorb verschwindet aus der Suche und kommt zurueck`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Geheimnis", "Inhalt"))

        notes.trash(listOf(id))
        assertFalse(trefferIds("Geheimnis").contains(id))

        notes.restore(listOf(id))
        assertTrue("Wiederherstellen muss den Index erneuern", trefferIds("Geheimnis").contains(id))
    }

    @Test
    fun `endgueltig geloeschte Notiz hinterlaesst keinen Indexeintrag`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Fluechtig", "Inhalt"))

        notes.purge(listOf(id))

        assertTrue(trefferIds("Fluechtig").isEmpty())
    }

    @Test
    fun `Ausschnitt zeigt Kontext um die Fundstelle`() = runTest {
        val id = notes.create()
        notes.updateContent(
            id,
            NoteContent("Reise", "Wir fahren im September nach Lissabon und bleiben zwei Wochen"),
        )

        val ausschnitte = db.searchDao().searchSnippets("Lissabon", vorne = "[", hinten = "]")

        assertEquals(1, ausschnitte.size)
        assertTrue(
            "die Fundstelle muss markiert sein",
            ausschnitte.first().snippet.contains("[Lissabon]"),
        )
    }

    @Test
    fun `markDirty erhaelt die Drive-Zuordnung`() = runTest {
        val id = notes.create()

        // So, wie der Abgleich es nach einem erfolgreichen Upload eintraegt.
        db.syncDao().upsertState(
            de.notizen.core.data.db.entity.SyncStateEntity(
                entityType = EntityType.NOTE,
                entityId = id,
                remoteId = "drive-datei-123",
                remoteRevision = "rev-7",
                localUpdatedAt = clock.now(),
                lastSyncedAt = clock.now(),
                syncStatus = de.notizen.core.data.model.SyncStatus.SYNCED,
            ),
        )

        clock.advanceBy(EIN_TAG)
        notes.updateContent(id, NoteContent("Neuer Titel", "Neuer Text"))

        val zustand = db.syncDao().stateOf(EntityType.NOTE, id)!!
        assertEquals(de.notizen.core.data.model.SyncStatus.DIRTY, zustand.syncStatus)
        assertEquals(
            "sonst laedt der Abgleich die Notiz als neue Datei erneut hoch",
            "drive-datei-123",
            zustand.remoteId,
        )
        assertEquals("rev-7", zustand.remoteRevision)
    }
}
