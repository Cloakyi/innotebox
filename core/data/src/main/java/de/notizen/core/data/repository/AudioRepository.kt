package de.notizen.core.data.repository

import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.util.Clock
import de.notizen.core.data.util.pruefsummeVon
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aufnahme und Transkript einer Notiz.
 *
 * Das Rohtranskript bleibt immer erhalten, auch nachdem die KI daraus
 * Fließtext gemacht hat (Abschnitt 12): der aufgeräumte Text landet im `body`
 * der Notiz, das Original hier. Wer beides zusammenlegt, verliert genau dann
 * etwas, wenn die Aufbereitung daneben lag, und das merkt man erst später.
 */
@Singleton
class AudioRepository @Inject constructor(
    private val attachmentDao: AttachmentDao,
    private val transcriptDao: TranscriptDao,
    private val syncDao: SyncDao,
    private val notes: NoteRepository,
    private val clock: Clock,
) {

    fun beobachteTranskript(noteId: String): Flow<List<TranscriptEntity>> =
        transcriptDao.observeOf(noteId)

    suspend fun transkript(noteId: String): List<TranscriptEntity> = transcriptDao.of(noteId)

    /**
     * Schreibt weg, was eine Aufnahme hinterlassen hat.
     *
     * Die Datei ist `null`, wenn nichts aufgenommen wurde -- dann gibt es auch
     * keinen Anhang. Eine Audiodatei mit null Sekunden Inhalt einzutragen wäre
     * schlimmer als gar keine: sie sähe aus wie eine Aufnahme, die man abspielen
     * kann.
     *
     * Am Ende geht die Notiz durch `reindex()`, damit das Transkript
     * durchsuchbar wird. Das ist keine Kür -- der FTS-Index wird von Hand
     * gepflegt, und wer diese Zeile vergisst, merkt es nur an einem
     * fehlschlagenden Suchtest.
     */
    suspend fun aufnahmeSichern(
        noteId: String,
        datei: File?,
        abschnitte: List<Triple<String, Long, Long>>,
    ) {
        if (abschnitte.isNotEmpty()) {
            val segmente = abschnitte.map { (text, start, ende) ->
                TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    startMs = start,
                    endMs = ende,
                    text = text,
                    isFinal = true,
                )
            }
            transcriptDao.upsertAll(segmente)
            segmente.forEach {
                syncDao.markDirty(EntityType.TRANSCRIPT, it.id, clock.now())
            }
        }

        if (datei != null && datei.length() > 0) {
            val anhang = AttachmentEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                localPath = datei.absolutePath,
                mimeType = "audio/wav",
                sizeBytes = datei.length(),
                hash = pruefsummeVon(datei),
            )
            attachmentDao.upsertAll(listOf(anhang))
            syncDao.markDirty(EntityType.ATTACHMENT, anhang.id, clock.now())
        }

        notes.reindex(noteId)
    }

    /**
     * Ersetzt das Transkript einer Notiz.
     *
     * Ersetzt und nicht ergaenzt: Ein Transkript wird aus der ganzen Aufnahme
     * erzeugt, also ist ein zweiter Durchgang eine neue Fassung derselben Sache
     * und keine Fortsetzung. Wer hier anhaengt, bekommt beim zweiten Versuch
     * alles doppelt.
     */
    suspend fun transkriptSetzen(
        noteId: String,
        abschnitte: List<Triple<String, Long, Long>>,
    ) {
        transcriptDao.deleteAllOf(noteId)
        aufnahmeSichern(noteId, datei = null, abschnitte = abschnitte)
    }
}
