package de.notizen.core.sync

import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import kotlinx.serialization.json.Json

/**
 * Der JSON-Umgang für den Drive-Ordner.
 *
 * `prettyPrint` ist Absicht und keine Spielerei: Der Ordner liegt offen in
 * Drive, und SYNC.md 2 sagt zu, dass der Inhalt lesbar ist. Wer im Notfall
 * selbst an seine Notizen muss, soll sie lesen können, ohne ein Werkzeug zu
 * suchen.
 *
 * `ignoreUnknownKeys` fängt den Fall ab, dass der Web-Client bereits ein Feld
 * schreibt, das dieser Client noch nicht kennt, ein neueres Schema darf ein
 * älteres nicht zum Absturz bringen.
 *
 * `encodeDefaults` schreibt auch Standardwerte aus. Ein Feld, das beim
 * Standardwert einfach fehlt, zwingt die Gegenstelle, denselben Standard zu
 * kennen; damit stünde dieselbe Wahrheit an zwei Stellen.
 */
val Sync: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Aus der Datenbank ins Drive-Dokument.
 *
 * Was hier NICHT mitgeht, steht in der Klassendokumentation von
 * [Notizdokument], und dort steht auch, warum.
 */
fun NoteWithRelations.alsDokument(): Notizdokument = Notizdokument(
    id = note.id,
    stage = note.stage,
    type = note.type,
    title = note.title,
    body = note.body,
    colorId = note.colorId,
    isFavorite = note.isFavorite,
    favoritedAt = note.favoritedAt,
    folderId = note.folderId,
    sortIndex = note.sortIndex,
    backgroundAttachmentId = note.backgroundAttachmentId,
    createdAt = note.createdAt,
    updatedAt = note.updatedAt,
    stageChangedAt = note.stageChangedAt,
    deletedAt = note.deletedAt,
    tagIds = tags.map { it.id },
    items = orderedItems.map {
        Eintragdokument(it.id, it.text, it.isChecked, it.position)
    },
    attachments = attachments.map {
        Anhangdokument(it.id, it.mimeType, it.sizeBytes, it.hash, it.remoteId, it.role)
    },
    transcripts = orderedTranscripts.map {
        Transkriptdokument(
            id = it.id,
            startMs = it.startMs,
            endMs = it.endMs,
            text = it.text,
            speakerLabel = it.speakerLabel,
            soundLabel = it.soundLabel,
            isFinal = it.isFinal,
        )
    },
    // Nur die noch nicht ausgelösten. Eine abgehakte Erinnerung ist Vergangenheit
    // und hat auf dem anderen Gerät nichts mehr zu wecken.
    reminders = reminders.filterNot { it.isFired }.map { Erinnerungsdokument(it.id, it.triggerAt) },
    ordnerArchiviertAt = note.ordnerArchiviertAt,
    herkunftOrdnerId = note.herkunftOrdnerId,
    ehemaligerOrdnerId = note.ehemaligerOrdnerId,
)

/**
 * Vom Drive-Dokument in eine Notiz-Zeile.
 *
 * [lastOpenedAt] kommt von außen, weil es nicht im Dokument steht: Jedes
 * Gerät führt seinen eigenen Wert. Beim ersten Eintreffen einer fremden Notiz
 * ist `createdAt` der einzige ehrliche Startwert, sie wurde auf diesem Gerät
 * noch nie geöffnet, und `jetzt` einzusetzen hieße zu behaupten, sie sei eben
 * angesehen worden. Damit wäre sie vor dem Auto-Archiv geschützt, ohne dass
 * jemand sie je gesehen hat.
 */
fun Notizdokument.alsNotiz(lastOpenedAt: Long = createdAt): NoteEntity = NoteEntity(
    id = id,
    stage = stage,
    type = type,
    title = title,
    body = body,
    colorId = colorId,
    isFavorite = isFavorite,
    favoritedAt = favoritedAt,
    folderId = folderId,
    sortIndex = sortIndex,
    backgroundAttachmentId = backgroundAttachmentId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    stageChangedAt = stageChangedAt,
    autoArchivedBatchId = null,
    deletedAt = deletedAt,
    ordnerArchiviertAt = ordnerArchiviertAt,
    herkunftOrdnerId = herkunftOrdnerId,
    ehemaligerOrdnerId = ehemaligerOrdnerId,
)

fun Notizdokument.alsEintraege(): List<NoteItemEntity> =
    items.map { NoteItemEntity(it.id, id, it.text, it.isChecked, it.position) }

/**
 * Anhänge als Zeilen.
 *
 * [pfadFuer] muss sagen, wo die Datei auf diesem Gerät liegt oder liegen
 * wird, der Pfad steht nicht im Dokument. Solange die Datei noch nicht
 * heruntergeladen ist, zeigt er ins Leere; die Oberfläche behandelt das schon
 * (ein fehlendes Bild fällt still auf die Notizfarbe zurück).
 */
fun Notizdokument.alsAnhaenge(pfadFuer: (Anhangdokument) -> String): List<AttachmentEntity> =
    attachments.map {
        AttachmentEntity(
            id = it.id,
            noteId = id,
            localPath = pfadFuer(it),
            mimeType = it.mimeType,
            sizeBytes = it.sizeBytes,
            hash = it.hash,
            remoteId = it.remoteId,
            role = it.role,
        )
    }

fun Notizdokument.alsTranskripte(): List<TranscriptEntity> =
    transcripts.map {
        TranscriptEntity(
            id = it.id,
            noteId = id,
            startMs = it.startMs,
            endMs = it.endMs,
            text = it.text,
            speakerLabel = it.speakerLabel,
            soundLabel = it.soundLabel,
            isFinal = it.isFinal,
        )
    }

/**
 * Erinnerungen als Zeilen.
 *
 * [alarmId] vergibt der Aufrufer aus der lokalen Zählung, sie ist gerätelokal
 * (SYNC.md 14.8). Zwei Erinnerungen mit derselben Kennung bestellen sich
 * gegenseitig ab, und das fände man im Nachhinein nie.
 */
fun Notizdokument.alsErinnerungen(alarmId: (Int) -> Int): List<ReminderEntity> =
    reminders.mapIndexed { i, r ->
        ReminderEntity(
            id = r.id,
            noteId = id,
            triggerAt = r.triggerAt,
            alarmId = alarmId(i),
            isFired = false,
        )
    }

/**
 * Ein Tag ins Drive-Format.
 *
 * Hier geht alles mit, anders als bei der Notiz: Ein Tag hat keine
 * gerätelokalen Felder. Er ist auf beiden Geräten dasselbe Ding.
 */
fun TagEntity.alsDokument(): Tagdokument = Tagdokument(
    id = id,
    name = name,
    colorArgb = colorArgb,
    iconOrEmoji = iconOrEmoji,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * Ein Ordner ins Drive-Format und zurueck.
 *
 * Wie beim Tag geht alles mit: Ein Ordner hat kein geraetelokales Feld, er ist
 * auf beiden Geraeten dasselbe Ding.
 */
fun FolderEntity.alsDokument(): Ordnerdokument = Ordnerdokument(
    id = id,
    parentId = parentId,
    name = name,
    iconOrEmoji = iconOrEmoji,
    colorArgb = colorArgb,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    bereich = bereich,
    ehemaligerElternId = ehemaligerElternId,
)

fun Ordnerdokument.alsOrdner(): FolderEntity = FolderEntity(
    id = id,
    parentId = parentId,
    name = name,
    iconOrEmoji = iconOrEmoji,
    colorArgb = colorArgb,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    bereich = bereich,
    ehemaligerElternId = ehemaligerElternId,
)

fun Tagdokument.alsTag(): TagEntity = TagEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    iconOrEmoji = iconOrEmoji,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
