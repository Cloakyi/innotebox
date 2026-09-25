package de.notizen.core.data

import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.Stage
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Prueft die Regeln, an denen der Multi-Client-Sync haengt.
 *
 * Der Kern ist immer derselbe: `updatedAt` darf sich NUR bewegen, wenn sich
 * wirklich Inhalt geaendert hat. Jede andere Operation -- oeffnen,
 * favorisieren, einfaerben, verschieben -- hat ihre eigenen Zeitstempel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteRepositoryTest : DatenbankTestbasis() {

    @Test
    fun `neue Notiz landet im Eingang`() = runTest {
        val id = notes.create()
        val n = notes.get(id)!!

        assertEquals(Stage.INBOX, n.note.stage)
        assertEquals(START_ZEIT, n.note.createdAt)
        assertEquals(START_ZEIT, n.note.updatedAt)
        assertEquals(START_ZEIT, n.note.lastOpenedAt)
    }

    @Test
    fun `oeffnen setzt lastOpenedAt und laesst updatedAt in Ruhe`() = runTest {
        val id = notes.create()
        clock.advanceBy(EIN_TAG)

        notes.open(id)

        val n = notes.get(id)!!
        assertEquals("lastOpenedAt muss steigen", START_ZEIT + EIN_TAG, n.note.lastOpenedAt)
        assertEquals("updatedAt darf sich NICHT bewegen", START_ZEIT, n.note.updatedAt)
    }

    @Test
    fun `echte Inhaltsaenderung hebt updatedAt`() = runTest {
        val id = notes.create()
        clock.advanceBy(EIN_TAG)

        val geaendert = notes.updateContent(id, NoteContent("Titel", "Text"))

        assertTrue(geaendert)
        assertEquals(START_ZEIT + EIN_TAG, notes.get(id)!!.note.updatedAt)
    }

    @Test
    fun `unveraenderter Inhalt schreibt gar nicht`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Titel", "Text"))
        val nachErsterAenderung = notes.get(id)!!.note.updatedAt

        // Der Autosave feuert alle ~800 ms, auch wenn nur der Cursor wandert.
        clock.advanceBy(EIN_TAG)
        val geaendert = notes.updateContent(id, NoteContent("Titel", "Text"))

        assertFalse("gleicher Inhalt darf nicht als Aenderung gelten", geaendert)
        assertEquals(nachErsterAenderung, notes.get(id)!!.note.updatedAt)
    }

    @Test
    fun `abgehakter Eintrag zaehlt als Inhaltsaenderung`() = runTest {
        val id = notes.create()
        val eintrag = NoteItemEntity(UUID.randomUUID().toString(), id, "Milch", false, 0)
        notes.updateContent(id, NoteContent("Einkauf", "", listOf(eintrag)))
        val vorher = notes.get(id)!!.note.updatedAt

        clock.advanceBy(EIN_TAG)
        val geaendert = notes.updateContent(
            id,
            NoteContent("Einkauf", "", listOf(eintrag.copy(isChecked = true))),
        )

        assertTrue(geaendert)
        assertTrue(notes.get(id)!!.note.updatedAt > vorher)
    }

    @Test
    fun `favorisieren ist keine Inhaltsaenderung`() = runTest {
        val id = notes.create()
        clock.advanceBy(EIN_TAG)

        notes.setFavorite(listOf(id), true)

        val n = notes.get(id)!!
        assertTrue(n.note.isFavorite)
        assertEquals(START_ZEIT + EIN_TAG, n.note.favoritedAt)
        assertEquals(
            "ein Stern darf beim anderen Client keinen Textstand ueberholen",
            START_ZEIT,
            n.note.updatedAt,
        )
    }

    @Test
    fun `Einfaerben ist eine Inhaltsaenderung`() = runTest {
        val id = notes.create()
        clock.advanceBy(EIN_TAG)

        notes.setColor(listOf(id), NoteColor.TEAL)

        val n = notes.get(id)!!
        assertEquals(NoteColor.TEAL, n.note.colorId)
        assertEquals(START_ZEIT + EIN_TAG, n.note.updatedAt)
    }

    @Test
    fun `leere Notiz wird beim Verlassen verworfen`() = runTest {
        val id = notes.create()

        val verworfen = notes.discardIfEmpty(id)

        assertTrue(verworfen)
        assertNull(notes.get(id))
        assertNotNull(
            "auch eine verworfene Notiz braucht einen Tombstone",
            db.syncDao().tombstone(EntityType.NOTE, id),
        )
    }

    @Test
    fun `Notiz mit Inhalt wird nicht verworfen`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("", "nur ein Gedanke"))

        assertFalse(notes.discardIfEmpty(id))
        assertNotNull(notes.get(id))
    }

    @Test
    fun `Papierkorb ist umkehrbar`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Titel", "Text"))

        notes.trash(listOf(id))
        assertNotNull("im Papierkorb existiert die Zeile weiter", notes.get(id)!!.note.deletedAt)

        notes.restore(listOf(id))
        assertNull(notes.get(id)!!.note.deletedAt)
    }

    @Test
    fun `endgueltiges Loeschen hinterlaesst Tombstone und raeumt Kinder ab`() = runTest {
        val id = notes.create()
        val eintrag = NoteItemEntity(UUID.randomUUID().toString(), id, "Milch", false, 0)
        notes.updateContent(id, NoteContent("Einkauf", "", listOf(eintrag)))

        notes.purge(listOf(id))

        assertNull(notes.get(id))
        assertTrue(
            "ON DELETE CASCADE muss die Einträge mitnehmen",
            db.noteDao().itemsOf(id).isEmpty(),
        )
        val stein = db.syncDao().tombstone(EntityType.NOTE, id)
        assertNotNull("ohne Tombstone laesst der andere Client die Notiz wiederauferstehen", stein)
        assertEquals(EntityType.NOTE, stein!!.entityType)
    }

    @Test
    fun `Stufenwechsel setzt stageChangedAt`() = runTest {
        val id = notes.create()
        clock.advanceBy(EIN_TAG)

        notes.moveTo(listOf(id), Stage.WORKSPACE)

        val n = notes.get(id)!!
        assertEquals(Stage.WORKSPACE, n.note.stage)
        assertEquals(START_ZEIT + EIN_TAG, n.note.stageChangedAt)
    }

    @Test
    fun `Tag-Zuordnung behaelt die Reihenfolge`() = runTest {
        val id = notes.create()
        val a = tags.create("Arbeit", 0xFF00897B.toInt())
        val b = tags.create("Privat", 0xFFF4511E.toInt())

        notes.setTags(id, listOf(b.id, a.id))

        val refs = db.noteDao().tagRefsOf(id)
        assertEquals(listOf(b.id, a.id), refs.sortedBy { it.position }.map { it.tagId })
    }
}
