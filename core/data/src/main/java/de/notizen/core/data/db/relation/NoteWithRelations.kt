package de.notizen.core.data.db.relation

import de.notizen.core.data.model.NoteColor
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TranscriptEntity

/**
 * Eine Notiz mit allem, was zu ihr gehoert.
 *
 * Entspricht genau dem, was in SYNC.md 14.15 als ein Drive-Dokument
 * `notes/<uuid>.json` abgelegt wird -- Einträge, Tags, Anhaenge und
 * Transkripte eingebettet, nicht als eigene Dateien. Wer hier etwas
 * hinzufuegt, aendert damit auch das Sync-Format.
 */
data class NoteWithRelations(
    @Embedded val note: NoteEntity,

    @Relation(parentColumn = "id", entityColumn = "noteId")
    val items: List<NoteItemEntity> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity> = emptyList(),

    @Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<AttachmentEntity> = emptyList(),

    @Relation(parentColumn = "id", entityColumn = "noteId")
    val transcripts: List<TranscriptEntity> = emptyList(),

    @Relation(parentColumn = "id", entityColumn = "noteId")
    val reminders: List<ReminderEntity> = emptyList(),
) {
    /** Einträge in Anzeigereihenfolge. */
    val orderedItems: List<NoteItemEntity> get() = items.sortedBy { it.position }

    /** Transkriptsegmente in zeitlicher Reihenfolge. */
    val orderedTranscripts: List<TranscriptEntity> get() = transcripts.sortedBy { it.startMs }

    /**
     * Die naechste noch nicht ausgeloeste Erinnerung.
     *
     * Eine Notiz traegt hoechstens eine; `firstOrNull` statt `single`, weil ein
     * Sync von einem anderen Geraet theoretisch eine zweite mitbringen kann und
     * ein Absturz beim Zeichnen die schlechteste Antwort darauf waere.
     */
    /**
     * Ob an dieser Notiz nichts dran ist.
     *
     * Eine Definition, zwei Verwender. Der Editor verwirft eine leere Notiz
     * beim Verlassen; der Abgleich lädt sie gar nicht erst hoch. Stünde die
     * Regel zweimal da, liefen die beiden irgendwann auseinander, und dann
     * läge in Drive eine leere Datei, die auf dem Gerät längst weg ist.
     *
     * Eine gesetzte Erinnerung oder ein Tag macht eine Notiz nicht voll:
     * Beides hängt an ihr, ist aber nicht ihr Inhalt.
     */
    val istLeer: Boolean
        get() = note.title.isBlank() &&
            note.body.isBlank() &&
            items.all { it.text.isBlank() } &&
            attachments.isEmpty() &&
            transcripts.isEmpty()

    val offeneErinnerung: ReminderEntity?
        get() = reminders.filterNot { it.isFired }.minByOrNull { it.triggerAt }
}

/**
 * Eine Notiz, so weit der Kalender sie angeht.
 *
 * [triggerAt] ist der naechste noch nicht ausgeloeste Termin oder `null`, wenn
 * es keinen gibt. Eine Notiz traegt hoechstens eine Erinnerung; das `MIN` ist
 * die Vorsicht fuer den Fall, dass doch einmal zwei Zeilen entstehen.
 */
data class Kalenderzeile(
    val noteId: String,
    val title: String,
    val calendarEnabled: Boolean,
    val calendarEventId: Long?,
    val deletedAt: Long?,
    val triggerAt: Long?,
)

/** Notiz und der Termin, der im Kalender des Geraets zu ihr steht. */
data class Termineintrag(
    val id: String,
    val calendarEventId: Long,
)

/** Eine Notiz, wie sie im Kalender der App erscheint. */
data class Notiztermin(
    val noteId: String,
    val title: String,
    val colorId: NoteColor,
    val triggerAt: Long,
    val isFired: Boolean,
)
