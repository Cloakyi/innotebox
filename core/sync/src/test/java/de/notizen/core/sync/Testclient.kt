package de.notizen.core.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.aussen.Weckdienst
import de.notizen.core.data.aussen.endungFuer
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.TagRepository
import de.notizen.core.data.util.Clock
import de.notizen.core.sync.sicherung.Sicherung
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Eine Uhr, die man von Hand stellt. */
class Testuhr(var jetzt: Long) : Clock {
    override fun now(): Long = jetzt
    fun weiter(ms: Long) {
        jetzt += ms
    }
}

/**
 * Ein simulierter Client: eigene Datenbank, eigene Einstellungen, eigene
 * Ablage, gemeinsames Drive (SYNC.md 12).
 */
class Testclient(
    val name: String,
    val drive: Drivezugang,
    val uhr: Testuhr,
    wurzel: File,
) {
    val db: NotizenDatabase = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NotizenDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val einstellungen = Einstellungen(
        PreferenceDataStoreFactory.create { File(wurzel, "$name.preferences_pb") },
    )

    private val ablageordner = File(wurzel, "$name-anhaenge").also { it.mkdirs() }

    val ablage = object : Anhangablage {
        override fun ziel(anhangId: String, mimeType: String): File =
            File(ablageordner, "$anhangId.${endungFuer(mimeType)}")

        override fun ziel(dateiname: String): File = File(ablageordner, dateiname)
    }

    val geweckt = ArrayList<Pair<String, List<Long>>>()

    private val weckdienst = object : Weckdienst {
        override suspend fun uebernehmen(noteId: String, titel: String, termine: List<Long>) {
            geweckt += noteId to termine
        }

        override suspend fun abbestellen(erinnerungen: List<ReminderEntity>) = Unit
    }

    val notes = NoteRepository(
        db = db,
        noteDao = db.noteDao(),
        transcriptDao = db.transcriptDao(),
        attachmentDao = db.attachmentDao(),
        searchDao = db.searchDao(),
        syncDao = db.syncDao(),
        clock = uhr,
    )
    val ordner = FolderRepository(db, db.folderDao(), db.noteDao(), db.syncDao(), uhr)
    val tags = TagRepository(db, db.tagDao(), db.syncDao(), uhr)

    val abgleich = Abgleich(
        drive = drive,
        notes = notes,
        noteDao = db.noteDao(),
        syncDao = db.syncDao(),
        transcriptDao = db.transcriptDao(),
        attachmentDao = db.attachmentDao(),
        reminderDao = db.reminderDao(),
        tagDao = db.tagDao(),
        folderDao = db.folderDao(),
        ablage = ablage,
        weckdienst = weckdienst,
        db = db,
        clock = uhr,
        einstellungen = einstellungen,
    )

    /** Die Sicherung dieses Clients, fuer Snapshots und Wiederherstellen. */
    fun sicherung() = Sicherung(
        notes = notes,
        noteDao = db.noteDao(),
        tagDao = db.tagDao(),
        folderDao = db.folderDao(),
        attachmentDao = db.attachmentDao(),
        transcriptDao = db.transcriptDao(),
        syncDao = db.syncDao(),
        ablage = ablage,
        weckdienst = weckdienst,
        db = db,
        clock = uhr,
    )

    /** Ein Lauf, der geklappt haben muss. */
    suspend fun sync(): Abgleichergebnis.Fertig {
        val ergebnis = abgleich.lauf("token-$name")
        check(ergebnis is Abgleichergebnis.Fertig) { "$name: Abgleich scheiterte mit $ergebnis" }
        return ergebnis
    }

    suspend fun neueNotiz(titel: String, text: String = "Text"): String {
        val id = notes.create()
        notes.updateContent(id, NoteContent(titel, text))
        return id
    }

    suspend fun titel(id: String): String? = notes.get(id)?.note?.title

    suspend fun lebt(id: String): Boolean = notes.get(id)?.note?.deletedAt == null && notes.get(id) != null

    suspend fun imPapierkorb(id: String): Boolean = notes.get(id)?.note?.deletedAt != null

    suspend fun weg(id: String): Boolean = notes.get(id) == null

    suspend fun alleTitel(): List<String> =
        db.noteDao().getPlainByIds(db.noteDao().alleIds()).map { it.title }.sorted()

    fun schliessen() = db.close()
}
