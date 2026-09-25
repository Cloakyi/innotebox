package de.notizen.core.sync

import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Herkunft
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import kotlinx.serialization.Serializable

/**
 * Die Schema-Version, die dieser Client schreibt. Muss zu SYNC.md passen.
 *
 * 4 seit dem 2026-09-16 (Phase 20): eine Huelle fuer alle Entitaeten,
 * Zustaende statt Grabsteinlisten, Zaehler statt Zeitstempel.
 * 5 seit dem 2026-09-19 (Phase 14a, SYNC.md 13): die Felder fuer das Archiv
 * im Ordnermodus und den Papierkorb, alle additiv. Die Huelle selbst ist
 * unveraendert; ein Leser auf Schema 4 uebergeht die neuen Felder.
 */
const val SCHEMA_VERSION_4 = 4
const val SCHEMA_VERSION_5 = 5

/** Die drei Zustaende einer Entitaet in Drive (SYNC.md 3.2). */
@Serializable
enum class Zustand {
    ACTIVE,
    TRASHED,
    DELETED,
}

/** Wer zuletzt geschrieben hat, wenn es kein Geraet war. */
const val ASSISTENT = "assistant"

/**
 * Die einheitliche Huelle fuer Notiz, Ordner und Tag (SYNC.md 3.1).
 *
 * **Diese Klasse IST der Vertrag.** `payload` ist bei `DELETED` `null` und
 * wird als leeres Objekt geschrieben; sonst vollstaendig. `rev` steigt bei
 * jedem Schreiben um eins ueber das Maximum aus lokalem und fernem Stand.
 */
@Serializable
data class Huelle<T>(
    val schemaVersion: Int = SCHEMA_VERSION_5,
    val id: String,
    val type: String,
    val rev: Long,
    val lastEditor: String,
    val origin: Herkunft = Herkunft.APP,
    val state: Zustand,
    val stateChangedAt: Long,
    val updatedAt: Long,
    val purgeAfter: Long? = null,
    val payload: T? = null,
) {
    val entityType: EntityType?
        get() = runCatching { EntityType.valueOf(type) }.getOrNull()
}

/** Der Notizinhalt: das Notizdokument aus SYNC.md 14.15 ohne die Huellenfelder. */
@Serializable
data class Notizinhalt(
    val stage: Stage,
    val type: NoteType,
    val title: String,
    val body: String,
    val colorId: NoteColor,
    val isFavorite: Boolean,
    val favoritedAt: Long? = null,
    val folderId: String? = null,
    val sortIndex: Int = 0,
    val backgroundAttachmentId: String? = null,
    val createdAt: Long,
    val stageChangedAt: Long,
    val tagIds: List<String> = emptyList(),
    val items: List<Eintragdokument> = emptyList(),
    val attachments: List<Anhangdokument> = emptyList(),
    val transcripts: List<Transkriptdokument> = emptyList(),
    val reminders: List<Erinnerungsdokument> = emptyList(),
    // Schema 5 (SYNC.md 13). Mit Standardwerten, damit ein Dokument aus
    // Schema 4 sich unveraendert liest.
    val ordnerArchiviertAt: Long? = null,
    val herkunftOrdnerId: String? = null,
    val ehemaligerOrdnerId: String? = null,
)

@Serializable
data class Ordnerinhalt(
    val parentId: String? = null,
    val name: String,
    val iconOrEmoji: String? = null,
    val colorArgb: Int? = null,
    val sortIndex: Int = 0,
    val createdAt: Long,
    // Schema 5 (SYNC.md 13).
    val bereich: Bereich = Bereich.ORDNER,
    val ehemaligerElternId: String? = null,
)

@Serializable
data class Taginhalt(
    val name: String,
    val colorArgb: Int,
    val iconOrEmoji: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long,
)

/** Ein Geraet in `index.json` (SYNC.md 3.4). */
@Serializable
data class Geraet(
    val deviceId: String,
    val label: String,
    val lastSeenAt: Long,
)

/** `index.json` nach Schema 4. */
@Serializable
data class Index4(
    val schemaVersion: Int = SCHEMA_VERSION_5,
    val purgeWatermark: Long = 0,
    val devices: List<Geraet> = emptyList(),
)

// ------------------------------------------------------------ Umwandlungen
//
// Das Notizdokument aus Schema 3 bleibt die Form, mit der die App intern
// arbeitet (Abbildung.kt, Sicherung). Die Huelle ist nur die Verpackung fuer
// Drive. Hin und zurueck ist ein reines Umkopieren.

fun Notizdokument.alsInhalt(): Notizinhalt = Notizinhalt(
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
    stageChangedAt = stageChangedAt,
    tagIds = tagIds,
    items = items,
    attachments = attachments,
    transcripts = transcripts,
    reminders = reminders,
    ordnerArchiviertAt = ordnerArchiviertAt,
    herkunftOrdnerId = herkunftOrdnerId,
    ehemaligerOrdnerId = ehemaligerOrdnerId,
)

/**
 * Aus einer Huelle das Notizdokument, mit dem der Rest des Moduls arbeitet.
 *
 * `deletedAt` ergibt sich aus dem Zustand: `TRASHED` traegt `stateChangedAt`
 * als Loeschzeit. Bei `DELETED` gibt es keinen Inhalt und damit kein Dokument.
 */
fun Huelle<Notizinhalt>.alsDokument(): Notizdokument? {
    val p = payload ?: return null
    return Notizdokument(
        schemaVersion = schemaVersion,
        id = id,
        stage = p.stage,
        type = p.type,
        title = p.title,
        body = p.body,
        colorId = p.colorId,
        isFavorite = p.isFavorite,
        favoritedAt = p.favoritedAt,
        folderId = p.folderId,
        sortIndex = p.sortIndex,
        backgroundAttachmentId = p.backgroundAttachmentId,
        createdAt = p.createdAt,
        updatedAt = updatedAt,
        stageChangedAt = p.stageChangedAt,
        deletedAt = if (state == Zustand.TRASHED) stateChangedAt else null,
        tagIds = p.tagIds,
        items = p.items,
        attachments = p.attachments,
        transcripts = p.transcripts,
        reminders = p.reminders,
        ordnerArchiviertAt = p.ordnerArchiviertAt,
        herkunftOrdnerId = p.herkunftOrdnerId,
        ehemaligerOrdnerId = p.ehemaligerOrdnerId,
    )
}

fun Ordnerdokument.alsInhalt(): Ordnerinhalt = Ordnerinhalt(
    parentId = parentId,
    name = name,
    iconOrEmoji = iconOrEmoji,
    colorArgb = colorArgb,
    sortIndex = sortIndex,
    createdAt = createdAt,
    bereich = bereich,
    ehemaligerElternId = ehemaligerElternId,
)

fun Huelle<Ordnerinhalt>.alsDokument(): Ordnerdokument? {
    val p = payload ?: return null
    return Ordnerdokument(
        id = id,
        parentId = p.parentId,
        name = p.name,
        iconOrEmoji = p.iconOrEmoji,
        colorArgb = p.colorArgb,
        sortIndex = p.sortIndex,
        createdAt = p.createdAt,
        updatedAt = updatedAt,
        deletedAt = if (state == Zustand.TRASHED) stateChangedAt else null,
        bereich = p.bereich,
        ehemaligerElternId = p.ehemaligerElternId,
    )
}

fun Tagdokument.alsInhalt(): Taginhalt = Taginhalt(
    name = name,
    colorArgb = colorArgb,
    iconOrEmoji = iconOrEmoji,
    sortIndex = sortIndex,
    createdAt = createdAt,
)

fun Huelle<Taginhalt>.alsDokument(): Tagdokument? {
    val p = payload ?: return null
    return Tagdokument(
        id = id,
        name = p.name,
        colorArgb = p.colorArgb,
        iconOrEmoji = p.iconOrEmoji,
        sortIndex = p.sortIndex,
        createdAt = p.createdAt,
        updatedAt = updatedAt,
        deletedAt = if (state == Zustand.TRASHED) stateChangedAt else null,
    )
}

/** Der Zustand, den ein `deletedAt` bedeutet. */
fun zustandVon(deletedAt: Long?): Zustand =
    if (deletedAt == null) Zustand.ACTIVE else Zustand.TRASHED

/** Wie lange ein Grabstein liegen bleibt, bevor der Purge ihn entfernen darf. */
const val PURGE_FRIST_MS: Long = 30L * 24 * 60 * 60 * 1000

/**
 * Eine Huelle mit `state: DELETED` und leerem Inhalt, der Grabstein in Drive.
 */
fun <T> grabsteinhuelle(
    typ: EntityType,
    id: String,
    rev: Long,
    deletedAt: Long,
    editor: String,
): Huelle<T> = Huelle(
    id = id,
    type = typ.name,
    rev = rev,
    lastEditor = editor,
    origin = Herkunft.APP,
    state = Zustand.DELETED,
    stateChangedAt = deletedAt,
    updatedAt = deletedAt,
    purgeAfter = deletedAt + PURGE_FRIST_MS,
    payload = null,
)
