package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.util.Clock
import de.notizen.core.data.util.pruefsummeVon
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bildanhaenge einer Notiz.
 *
 * Getrennt von [AudioRepository], obwohl beide in `attachments` schreiben: Ein
 * Bild hat kein Transkript, keine Wellenform und keinen Aufnahmezustand. Die
 * gemeinsame Tabelle macht daraus noch keine gemeinsame Aufgabe.
 *
 * Die Datei gehoert dem Datensatz. Dieses Repository legt die Zeile an und
 * raeumt beim Loeschen die Datei mit weg. Das ist bewusst hier und nicht in der
 * Oberflaeche: Wer die Zeile loescht, ohne die Datei zu loeschen, hinterlaesst
 * Muell, den niemand mehr zuordnen kann -- und das faellt erst auf, wenn der
 * Speicher voll ist.
 */
@Singleton
class BildRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val attachmentDao: AttachmentDao,
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val clock: Clock,
) {

    /**
     * Traegt ein bereits abgelegtes Bild als Anhang ein.
     *
     * Die Datei muss zu diesem Zeitpunkt schon geschrieben sein -- der Hash
     * wird aus ihr berechnet. Das Ablegen selbst macht `Bildspeicher` in der
     * App-Schicht, weil dazu Android-Bildverarbeitung gehoert, die in einem
     * reinen Datenmodul nichts zu suchen hat.
     */
    suspend fun hinzufuegen(
        noteId: String,
        anhangId: String,
        datei: File,
        mimeType: String = "image/jpeg",
        rolle: Anhangsrolle = Anhangsrolle.INHALT,
    ): AttachmentEntity? {
        if (!datei.exists() || datei.length() == 0L) return null
        val anhang = AttachmentEntity(
            id = anhangId,
            noteId = noteId,
            localPath = datei.absolutePath,
            mimeType = mimeType,
            sizeBytes = datei.length(),
            hash = pruefsummeVon(datei),
            role = rolle,
        )
        attachmentDao.upsertAll(listOf(anhang))
        syncDao.markDirty(EntityType.ATTACHMENT, anhang.id, clock.now())
        return anhang
    }

    /**
     * Traegt ein eigens gewaehltes Hintergrundbild ein und macht es zur Flaeche.
     *
     * Es zaehlt NICHT zu den Bildern der Notiz -- genau das ist der Unterschied
     * zu [setzeHintergrund], das ein vorhandenes Bild zur Flaeche erklaert.
     *
     * Ein zuvor eigens gewaehltes Hintergrundbild wird dabei weggeraeumt. Ohne
     * das sammelte jedes Wechseln eine weitere Datei an, die ueber keine
     * Oberflaeche mehr erreichbar waere.
     */
    suspend fun hintergrundAusDatei(
        noteId: String,
        anhangId: String,
        datei: File,
    ): AttachmentEntity? {
        val anhang = hinzufuegen(
            noteId = noteId,
            anhangId = anhangId,
            datei = datei,
            rolle = Anhangsrolle.HINTERGRUND,
        ) ?: return null
        noteDao.setBackground(noteId, anhangId, clock.now())
        aufraeumen(noteId)
        return anhang
    }

    /**
     * Loescht Hintergrundbilder, auf die nichts mehr zeigt.
     *
     * Nur solche mit der Rolle HINTERGRUND: Ein Bild der Notiz bleibt liegen,
     * auch wenn es gerade nicht die Flaeche ist -- es steht ja weiter im
     * Raster.
     */
    private suspend fun aufraeumen(noteId: String) {
        attachmentDao.verwaisteHintergruende(noteId).forEach { entfernen(it.id) }
    }

    /**
     * Loescht einen Anhang samt Datei.
     *
     * Die Reihenfolge ist nicht beliebig: erst den Hintergrundverweis loesen,
     * dann die Zeile, dann die Datei. Scheitert das Loeschen der Datei, bleibt
     * eine verwaiste Datei zurueck -- laestig, aber harmlos. Andersherum bliebe
     * eine Zeile stehen, die auf nichts mehr zeigt, und die Oberflaeche zeigte
     * ein Bild an, das es nicht gibt.
     */
    suspend fun entfernen(anhangId: String) {
        val anhang = attachmentDao.byId(anhangId) ?: return
        val now = clock.now()
        db.withTransaction {
            noteDao.clearBackground(anhangId, now)
            syncDao.upsertTombstone(TombstoneEntity(EntityType.ATTACHMENT, anhangId, now))
            syncDao.dropState(EntityType.ATTACHMENT, anhangId)
            attachmentDao.delete(listOf(anhangId))
        }
        runCatching { File(anhang.localPath).delete() }
    }

    /**
     * Waehlt das Hintergrundbild, oder loescht die Wahl mit `null`.
     *
     * Geprueft wird, dass der Anhang wirklich zu DIESER Notiz gehoert. Ohne die
     * Pruefung koennte ein Zeiger auf ein fremdes Bild entstehen, und beim
     * Loeschen jener Notiz stuende hier ein toter Verweis -- der Fremdschluessel
     * faengt das nicht ab, den gibt es in diese Richtung nicht.
     */
    suspend fun setzeHintergrund(noteId: String, anhangId: String?) {
        if (anhangId != null) {
            val anhang = attachmentDao.byId(anhangId) ?: return
            if (anhang.noteId != noteId) return
        }
        noteDao.setBackground(noteId, anhangId, clock.now())
        // Waehlt man ein Bild der Notiz zur Flaeche, wird ein vorher eigens
        // gewaehltes Hintergrundbild dadurch unerreichbar -- also weg damit.
        aufraeumen(noteId)
    }
}
