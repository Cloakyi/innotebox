package de.notizen.core.data

import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Prueft `setTitle` -- die Datenschicht hinter der Titelpflicht ab WORKSPACE
 * . Ein Titel ist echter Inhalt: er muss `updatedAt` heben, im
 * Volltextindex landen und die Notiz als aenderungsbeduerftig markieren.
 */
@RunWith(RobolectricTestRunner::class)
class TitelpflichtTest : DatenbankTestbasis() {

    private suspend fun trefferIds(ausdruck: String) =
        db.searchDao().search(ausdruck).map { it.note.id }

    @Test
    fun `Titel setzen hebt updatedAt und macht ihn auffindbar`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("", "Zahnarzttermin verschieben"))
        val vorher = notes.get(id)!!.note.updatedAt

        clock.advanceBy(EIN_TAG)
        assertTrue(notes.setTitle(id, "Zahnarzt"))

        val n = notes.get(id)!!
        assertEquals("Zahnarzt", n.note.title)
        assertEquals(START_ZEIT + EIN_TAG, n.note.updatedAt)
        assertTrue(
            "ein neuer Titel muss sofort im Volltextindex stehen",
            trefferIds("Zahnarzt").contains(id),
        )
    }

    @Test
    fun `gleicher Titel schreibt gar nicht`() = runTest {
        val id = notes.create()
        notes.setTitle(id, "Unveraendert")
        val vorher = notes.get(id)!!.note.updatedAt

        clock.advanceBy(EIN_TAG)
        assertFalse(notes.setTitle(id, "Unveraendert"))
        assertEquals(vorher, notes.get(id)!!.note.updatedAt)
    }

    @Test
    fun `alter Titel verschwindet aus dem Index`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Fruehererstand", "Inhalt"))
        assertTrue(trefferIds("Fruehererstand").contains(id))

        notes.setTitle(id, "Spaetererstand")

        assertFalse(trefferIds("Fruehererstand").contains(id))
        assertTrue(trefferIds("Spaetererstand").contains(id))
    }

    @Test
    fun `Titel setzen markiert die Notiz fuer den Sync`() = runTest {
        val id = notes.create()
        db.syncDao().upsertState(
            de.notizen.core.data.db.entity.SyncStateEntity(
                entityType = EntityType.NOTE,
                entityId = id,
                remoteId = "drive-777",
                localUpdatedAt = clock.now(),
                lastSyncedAt = clock.now(),
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        clock.advanceBy(EIN_TAG)
        notes.setTitle(id, "Neu")

        val zustand = db.syncDao().stateOf(EntityType.NOTE, id)!!
        assertEquals(SyncStatus.DIRTY, zustand.syncStatus)
        assertEquals("die Drive-Zuordnung darf dabei nicht verloren gehen", "drive-777", zustand.remoteId)
    }

    /**
     * Nachbau dessen, was StageViewModel.fuehreAus() beim Verschieben und beim
     * Undo tut. Der Punkt: das Undo muss BEIDES zuruecknehmen -- die Stufe und
     * den unterwegs ergaenzten Titel. Sonst gaebe es keinen Weg zurueck zum
     * leeren Titel.
     */
    @Test
    fun `Undo nimmt Stufe und ergaenzten Titel zurueck`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("", "Einkaufen gehen"))

        notes.setTitle(id, "Einkaufen gehen")
        notes.moveTo(listOf(id), Stage.WORKSPACE)
        assertEquals(Stage.WORKSPACE, notes.get(id)!!.note.stage)
        assertEquals("Einkaufen gehen", notes.get(id)!!.note.title)

        notes.moveTo(listOf(id), Stage.INBOX)
        notes.setTitle(id, "")

        val n = notes.get(id)!!
        assertEquals(Stage.INBOX, n.note.stage)
        assertEquals("", n.note.title)
    }

    @Test
    fun `Stufen kennen ihre Titelpflicht`() = runTest {
        assertFalse("im Eingang ist ein Titel freiwillig", Stage.INBOX.requiresTitle())
        assertTrue(Stage.WORKSPACE.requiresTitle())
        assertTrue(Stage.ARCHIVE.requiresTitle())
    }
}
