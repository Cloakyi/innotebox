package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.ArchiveDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.entity.ArchiveRunEntity
import de.notizen.core.data.db.entity.ArchiveRunItemEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.model.ArchiveTrigger
import de.notizen.core.data.model.Stage
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mechanik der automatischen Archivierung: einen Lauf protokollieren und
 * vollstaendig zuruecknehmen koennen.
 *
 * ABGRENZUNG: Hier steht KEINE Policy. Welche Notizen betroffen sind, welche
 * Schwellen gelten und wann ueberhaupt gelaufen wird, entscheidet die Aufraeumarbeit
 * (WorkManager-Job plus Einstellungen). Dieses Repository bekommt die fertige
 * Auswahl uebergeben.
 *
 * WARUM DAS UEBERHAUPT SO GEBAUT IST: Ein kompletter Lauf soll sich per
 * Snackbar zuruecknehmen lassen. Dafuer reicht
 * `archive_runs` nicht -- dort stehen nur Zaehlwerte. Erst
 * `archive_run_items.previousStage` weiss, ob eine Notiz nach INBOX oder nach
 * WORKSPACE zurueckgehoert. Siehe Abweichung 3 in docs/ENTSCHEIDUNGEN.md.
 */
@Singleton
class ArchiveRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val archiveDao: ArchiveDao,
    private val noteDao: NoteDao,
    private val clock: Clock,
) {

    fun observeRuns(limit: Int = 50): Flow<List<ArchiveRunEntity>> = archiveDao.observeRuns(limit)

    suspend fun run(batchId: String): ArchiveRunEntity? = archiveDao.run(batchId)

    /**
     * Archiviert [notizen] als EIN Batch und merkt sich je Notiz die Stufe,
     * aus der sie kam.
     *
     * Gibt die `batchId` zurueck -- die Undo-Aktion der Benachrichtigung
     * braucht sie. Leere Eingabe erzeugt keinen Lauf und gibt null zurueck.
     */
    suspend fun archive(notizen: List<NoteEntity>, trigger: ArchiveTrigger): String? {
        if (notizen.isEmpty()) return null

        val batchId = UUID.randomUUID().toString()
        val now = clock.now()

        db.withTransaction {
            archiveDao.upsertRun(
                ArchiveRunEntity(
                    batchId = batchId,
                    runAt = now,
                    noteCount = notizen.size,
                    trigger = trigger,
                ),
            )
            // ZUERST die Herkunft festhalten, DANN verschieben -- danach ist
            // die urspruengliche Stufe nicht mehr rekonstruierbar.
            archiveDao.upsertItems(
                notizen.map { ArchiveRunItemEntity(batchId, it.id, it.stage) },
            )
            noteDao.setStage(notizen.map { it.id }, Stage.ARCHIVE, now, batchId)
        }
        return batchId
    }

    /**
     * Nimmt einen kompletten Lauf zurueck. Jede Notiz landet in IHRER
     * urspruenglichen Stufe -- nicht pauschal in INBOX.
     *
     * Ein bereits zurueckgenommener Lauf wird nicht erneut angefasst; die
     * Snackbar koennte sonst doppelt ausgeloest werden.
     */
    suspend fun undo(batchId: String): Boolean {
        val lauf = archiveDao.run(batchId) ?: return false
        if (lauf.undoneAt != null) return false

        val eintraege = archiveDao.itemsOf(batchId)
        if (eintraege.isEmpty()) return false

        val now = clock.now()
        db.withTransaction {
            // Nach Zielstufe gruppieren, damit ein UPDATE je Stufe reicht.
            eintraege.groupBy { it.previousStage }.forEach { (stufe, gruppe) ->
                noteDao.setStage(gruppe.map { it.noteId }, stufe, now, null)
            }
            archiveDao.markUndone(batchId, now)
        }
        return true
    }
}
