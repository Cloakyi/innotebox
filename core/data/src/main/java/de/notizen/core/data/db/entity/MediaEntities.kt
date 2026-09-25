package de.notizen.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.notizen.core.data.model.Anhangsrolle

/** Bild, Zeichnung oder Audiodatei einer Notiz. Siehe SYNC.md 14.6. */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("hash")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,

    /** Geraetepfad. NICHT sync-relevant -- fuer den anderen Client bedeutungslos. */
    val localPath: String,

    val mimeType: String,
    val sizeBytes: Long,

    /** Inhaltshash. Erspart erneutes Hochladen unveraenderter Dateien. */
    val hash: String,

    /** Drive-Datei-ID, sobald hochgeladen. */
    val remoteId: String? = null,

    /**
     * Ob der Anhang zum Inhalt gehoert oder nur Flaeche ist. Siehe
     * [Anhangsrolle] und SYNC.md 14.6.
     */
    val role: Anhangsrolle = Anhangsrolle.INHALT,
)

/**
 * Ein Segment eines Audio-Transkripts. Siehe SYNC.md 14.7.
 *
 * Das Rohtranskript bleibt IMMER erhalten, auch nachdem die KI daraus
 * Fliesstext gemacht hat -- der aufgeraeumte Text landet im `body` der Notiz,
 * das Original hier.
 */
@Entity(
    tableName = "transcripts",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val noteId: String,

    /** Versatz innerhalb der Aufnahme, nicht Uhrzeit. */
    val startMs: Long,
    val endMs: Long,

    val text: String,

    /**
     * BLEIBT LEER. Die ML-Kit-Speech-API kann keine Sprechererkennung
     * (Diarization). Platzhalter fuer eine spaetere, noch nicht evaluierte
     * Loesung -- kein Client darf das Feld befuellen, solange das nicht
     * entschieden ist.
     */
    val speakerLabel: String? = null,

    /** BLEIBT LEER. Keine Geraeusch-Klassifikation in der API. Wie oben. */
    val soundLabel: String? = null,

    /** false = Zwischenergebnis der Live-Erkennung. */
    val isFinal: Boolean = false,
)
