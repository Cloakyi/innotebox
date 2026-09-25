package de.notizen.core.data

import de.notizen.core.data.model.ArchiveTrigger
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

/**
 * Prueft die Rueckabwicklung der automatischen Archivierung.
 *
 * Das ist der Test zu Abweichung 3 aus docs/ENTSCHEIDUNGEN.md: Die urspruengliche
 * Spezifikation haette Batch-Undo nicht bauen koennen, weil nirgends stand,
 * aus welcher Stufe eine Notiz kam. Der entscheidende Fall ist deshalb der
 * GEMISCHTE Lauf -- Notizen aus INBOX und aus WORKSPACE in einem Batch.
 * Ein Undo, das pauschal nach INBOX zurueckschiebt, faellt hier durch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArchiveRepositoryTest : DatenbankTestbasis() {

    private suspend fun notizIn(stufe: Stage, titel: String): String {
        val id = notes.create()
        notes.updateContent(id, NoteContent(titel, "Inhalt"))
        if (stufe != Stage.INBOX) notes.moveTo(listOf(id), stufe)
        return id
    }

    @Test
    fun `Undo bringt jede Notiz in ihre urspruengliche Stufe zurueck`() = runTest {
        val ausEingang = notizIn(Stage.INBOX, "Gedanke")
        val ausWorkspace = notizIn(Stage.WORKSPACE, "Projekt")

        val batchId = archive.archive(
            db.noteDao().getPlainByIds(listOf(ausEingang, ausWorkspace)),
            ArchiveTrigger.AGE,
        )
        assertNotNull(batchId)

        // Nach dem Lauf liegen beide im Archiv.
        assertEquals(Stage.ARCHIVE, notes.get(ausEingang)!!.note.stage)
        assertEquals(Stage.ARCHIVE, notes.get(ausWorkspace)!!.note.stage)

        clock.advanceBy(60_000)
        assertTrue(archive.undo(batchId!!))

        assertEquals(
            "aus dem Eingang gekommen, in den Eingang zurueck",
            Stage.INBOX,
            notes.get(ausEingang)!!.note.stage,
        )
        assertEquals(
            "aus dem Workspace gekommen, in den Workspace zurueck",
            Stage.WORKSPACE,
            notes.get(ausWorkspace)!!.note.stage,
        )
    }

    @Test
    fun `Undo loescht die Batch-Zuordnung an der Notiz`() = runTest {
        val id = notizIn(Stage.INBOX, "Gedanke")
        val batchId = archive.archive(db.noteDao().getPlainByIds(listOf(id)), ArchiveTrigger.COUNT)!!

        assertEquals(batchId, notes.get(id)!!.note.autoArchivedBatchId)

        archive.undo(batchId)
        assertNull(notes.get(id)!!.note.autoArchivedBatchId)
    }

    @Test
    fun `ein Lauf laesst sich nicht zweimal zuruecknehmen`() = runTest {
        val id = notizIn(Stage.WORKSPACE, "Projekt")
        val batchId = archive.archive(db.noteDao().getPlainByIds(listOf(id)), ArchiveTrigger.AGE)!!

        assertTrue(archive.undo(batchId))

        // Die Notiz danach von Hand weiterschieben -- ein zweites Undo duerfte
        // das nicht ueberschreiben.
        notes.moveTo(listOf(id), Stage.ARCHIVE)
        assertFalse("zweites Undo muss wirkungslos bleiben", archive.undo(batchId))
        assertEquals(Stage.ARCHIVE, notes.get(id)!!.note.stage)
    }

    @Test
    fun `leerer Lauf erzeugt keinen Batch`() = runTest {
        assertNull(archive.archive(emptyList(), ArchiveTrigger.AGE))
    }

    @Test
    fun `Lauf wird protokolliert`() = runTest {
        val a = notizIn(Stage.INBOX, "eins")
        val b = notizIn(Stage.INBOX, "zwei")

        val batchId = archive.archive(
            db.noteDao().getPlainByIds(listOf(a, b)),
            ArchiveTrigger.COUNT,
        )!!

        val lauf = archive.run(batchId)!!
        assertEquals(2, lauf.noteCount)
        assertEquals(ArchiveTrigger.COUNT, lauf.trigger)
        assertNull(lauf.undoneAt)

        archive.undo(batchId)
        assertNotNull(archive.run(batchId)!!.undoneAt)
    }

    @Test
    fun `Favoriten und faellige Erinnerungen sind von der Auswahl ausgenommen`() = runTest {
        val normal = notizIn(Stage.INBOX, "normal")
        val favorit = notizIn(Stage.INBOX, "favorit")
        val mitErinnerung = notizIn(Stage.INBOX, "erinnert")

        notes.setFavorite(listOf(favorit), true)
        db.reminderDao().upsert(
            de.notizen.core.data.db.entity.ReminderEntity(
                id = "r1",
                noteId = mitErinnerung,
                triggerAt = clock.now() + EIN_TAG,
                alarmId = 1,
                isFired = false,
            ),
        )

        clock.advanceBy(30 * EIN_TAG)
        val kandidaten = db.noteDao().archiveCandidatesByAge(Stage.INBOX, clock.now())

        val ids = kandidaten.map { it.id }
        assertTrue(ids.contains(normal))
        assertFalse("Favoriten werden nie automatisch archiviert", ids.contains(favorit))
        assertFalse("offene Erinnerung schuetzt ebenfalls", ids.contains(mitErinnerung))
    }
}
