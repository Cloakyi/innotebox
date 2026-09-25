package de.notizen.core.data

import androidx.room.Room
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.repository.ArchiveRepository
import de.notizen.core.data.repository.AudioRepository
import de.notizen.core.data.repository.BildRepository
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.ReminderRepository
import de.notizen.core.data.repository.SucheRepository
import de.notizen.core.data.repository.TagRepository
import de.notizen.core.data.util.FixedClock
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment

/**
 * Gemeinsame Grundlage der Datenschicht-Tests.
 *
 * Die Uhr steht still ([FixedClock]) und wird von Hand weitergestellt. Das ist
 * hier keine Bequemlichkeit: die halbe Datenschicht dreht sich darum, WANN ein
 * Zeitstempel steigt und wann eben nicht. Mit der echten Systemzeit waeren
 * genau diese Aussagen nicht pruefbar.
 */
abstract class DatenbankTestbasis {

    protected lateinit var db: NotizenDatabase
    protected lateinit var clock: FixedClock
    protected lateinit var notes: NoteRepository
    protected lateinit var tags: TagRepository
    protected lateinit var archive: ArchiveRepository
    protected lateinit var suche: SucheRepository
    protected lateinit var erinnerungen: ReminderRepository
    protected lateinit var audio: AudioRepository
    protected lateinit var bilder: BildRepository
    protected lateinit var ordner: FolderRepository

    @Before
    fun aufbauen() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NotizenDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        clock = FixedClock(START_ZEIT)

        notes = NoteRepository(
            db = db,
            noteDao = db.noteDao(),
            transcriptDao = db.transcriptDao(),
            attachmentDao = db.attachmentDao(),
            searchDao = db.searchDao(),
            syncDao = db.syncDao(),
            clock = clock,
        )
        tags = TagRepository(db, db.tagDao(), db.syncDao(), clock)
        suche = SucheRepository(db.searchDao(), db.noteDao(), db.folderDao(), clock)
        erinnerungen = ReminderRepository(db.reminderDao(), db.syncDao(), clock)
        audio = AudioRepository(
            db.attachmentDao(),
            db.transcriptDao(),
            db.syncDao(),
            notes,
            clock,
        )
        archive = ArchiveRepository(db, db.archiveDao(), db.noteDao(), clock)
        ordner = FolderRepository(db, db.folderDao(), db.noteDao(), db.syncDao(), clock)
        bilder = BildRepository(
            db = db,
            attachmentDao = db.attachmentDao(),
            noteDao = db.noteDao(),
            syncDao = db.syncDao(),
            clock = clock,
        )
    }

    @After
    fun abbauen() {
        db.close()
    }

    companion object {
        /** Beliebig, aber fest: 2026-01-01T00:00:00Z. */
        const val START_ZEIT = 1_767_225_600_000L

        const val EIN_TAG = 24L * 60 * 60 * 1000
    }
}
