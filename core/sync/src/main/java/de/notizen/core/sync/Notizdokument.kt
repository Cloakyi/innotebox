package de.notizen.core.sync

import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Die Schema-Version, die dieser Client schreibt. Muss zu SYNC.md passen.
 *
 * Wer sie hier hochzählt, ohne SYNC.md zu ändern, bricht den Vertrag mit dem
 * Web-Client, und zwar still.
 */
const val SCHEMA_VERSION = SCHEMA_VERSION_5

/**
 * Eine Notiz als Drive-Dokument, genau nach SYNC.md 14.15.
 *
 * Diese Klasse IST der Vertrag. Jedes Feld hier steht dort in einer Tabelle
 * mit „Sync = ja"; jedes Feld, das dort „nein" trägt, fehlt hier absichtlich:
 *
 *  * `lastOpenedAt`, jeder Client führt seinen eigenen Wert. Er steuert das
 *    Auto-Archiv, und wann *dieses* Gerät die Notiz zuletzt geöffnet hat, geht
 *    das andere nichts an.
 *  * `autoArchivedBatchId`, verweist auf einen Lauf, den nur dieses Gerät
 *    kennt.
 *  * `localPath`, ein Gerätepfad ist für die Gegenstelle bedeutungslos.
 *  * `alarmId`, `isFired`, jedes Gerät weckt für sich.
 *
 * `noteId` fehlt in den eingebetteten Listen, weil der Dateiname es schon sagt.
 *
 * `encodeDefaults` ist beim Schreiben eingeschaltet (siehe [Sync]): Ein Feld,
 * das beim Standardwert einfach fehlt, zwingt die Gegenstelle, denselben
 * Standard zu kennen, und das ist eine zweite Stelle, an der die Wahrheit
 * steht.
 */
@Serializable
data class Notizdokument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val id: String,
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
    val updatedAt: Long,
    val stageChangedAt: Long,
    val deletedAt: Long? = null,
    val tagIds: List<String> = emptyList(),
    val items: List<Eintragdokument> = emptyList(),
    val attachments: List<Anhangdokument> = emptyList(),
    val transcripts: List<Transkriptdokument> = emptyList(),
    val reminders: List<Erinnerungsdokument> = emptyList(),

    /**
     * Schema 5 (SYNC.md 13): das Archiv des Ordnermodus und der
     * fruehere Ordner. Mit Standardwerten, damit ein Dokument aus Schema 4
     * sich unveraendert liest. Der Abgleich deutet keines der drei.
     */
    val ordnerArchiviertAt: Long? = null,
    val herkunftOrdnerId: String? = null,
    val ehemaligerOrdnerId: String? = null,
)

@Serializable
data class Eintragdokument(
    val id: String,
    val text: String,
    val isChecked: Boolean,
    val position: Int,
)

@Serializable
data class Anhangdokument(
    val id: String,
    val mimeType: String,
    val sizeBytes: Long,
    val hash: String,
    val remoteId: String? = null,
    val role: Anhangsrolle = Anhangsrolle.INHALT,
)

@Serializable
data class Transkriptdokument(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val speakerLabel: String? = null,
    val soundLabel: String? = null,
    val isFinal: Boolean = true,
)

/**
 * Erinnerung, nur mit dem, was beide Geräte angeht.
 *
 * `alarmId` und `isFired` fehlen mit Absicht: Jedes Gerät stellt seinen eigenen
 * Wecker und hakt ihn für sich ab. Ein synchronisiertes `isFired` hieße, dass
 * das eine Gerät das Klingeln des anderen unterdrückt.
 */
@Serializable
data class Erinnerungsdokument(
    val id: String,
    val triggerAt: Long,
)

/**
 * Ein Tag, wie er in `tags.json` steht (SYNC.md 2 und 6.3).
 *
 * Vervollständigt am 2026-08-23. `iconOrEmoji`, `sortIndex` und `deletedAt`
 * fehlten, obwohl 6.3 sie als synchronisiert führt. Ohne `deletedAt` wäre ein
 * gelöschter Tag beim nächsten Abgleich vom anderen Gerät zurückgekommen. Das
 * ist keine Vertragsänderung, sondern das Nachziehen der Umsetzung: Die Datei
 * wird gerade zum ersten Mal überhaupt geschrieben.
 */
@Serializable
data class Tagdokument(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val iconOrEmoji: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Ein Ordner, wie er in `folders.json` steht (SYNC.md 2 und 6.11).
 *
 * `deletedAt` ist hier kein Beiwerk, sondern der einzige Weg. Fuer Ordner
 * gibt es in Drive keine Grabsteine: Die Grabsteinliste traegt ausschliesslich
 * Notizen. Ein geloeschter Ordner ist deshalb eine Zeile mit Zeitstempel, und
 * ohne sie kaeme er beim naechsten Abgleich vom anderen Geraet zurueck.
 *
 * `parentId` verweist auf einen anderen Ordner derselben Datei. Zeigt der
 * Verweis ins Leere, haengt der Ordner an der obersten Ebene, statt unsichtbar
 * zu werden -- siehe `Ordnerregeln.kinder`.
 */
@Serializable
data class Ordnerdokument(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val iconOrEmoji: String? = null,
    val colorArgb: Int? = null,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,

    /** Schema 5 (SYNC.md 13): welchem Baum der Ordner gehoert, und woher er kam. */
    val bereich: Bereich = Bereich.ORDNER,
    val ehemaligerElternId: String? = null,
)
