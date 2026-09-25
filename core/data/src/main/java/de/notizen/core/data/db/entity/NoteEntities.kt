package de.notizen.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import de.notizen.core.data.model.Herkunft
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage

/**
 * Eine Notiz. Feldbedeutung und Sync-Relevanz stehen in SYNC.md 14.2 --
 * dort zuerst aendern, dann hier.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index("stage"),
        Index("deletedAt"),
        Index("lastOpenedAt"),
        Index("updatedAt"),
        Index("isFavorite"),
        Index("folderId"),
        Index("ordnerArchiviertAt"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,

    val stage: Stage = Stage.INBOX,
    val type: NoteType = NoteType.TEXT,

    /** Leer erlaubt in INBOX, Pflicht ab WORKSPACE. */
    val title: String = "",
    val body: String = "",

    /** DEFAULT = Theme-Flaeche, kein fester Farbwert. Siehe NoteColor. */
    val colorId: NoteColor = NoteColor.DEFAULT,

    val isFavorite: Boolean = false,
    val favoritedAt: Long? = null,

    /** Der Ordner der Notiz, `null` heisst Hauptordner. */
    val folderId: String? = null,

    val sortIndex: Int = 0,

    /**
     * Ein Bild als Flaeche der Notiz statt einer Palettenfarbe. Siehe SYNC.md
     * 6.2 und den Nachtrag zu 6.15.
     *
     * Verweist auf `attachments.id` -- aber OHNE Fremdschluessel. Room wuerde
     * bei einem Fremdschluessel auf `attachments` einen Zirkel bauen
     * (attachments haengt bereits an notes), und ON DELETE SET NULL gaebe es
     * nur in dieser einen Richtung. Das Aufraeumen beim Loeschen eines Anhangs
     * macht deshalb das Repository, nicht die Datenbank.
     *
     * Zeigt der Verweis ins Leere, faellt die Notiz still auf [colorId]
     * zurueck. Nicht loeschen: die Datei kann auf dem anderen Geraet liegen
     * und hier nur noch nicht angekommen sein.
     */
    val backgroundAttachmentId: String? = null,

    val createdAt: Long,

    /**
     * NUR bei echter Inhaltsaenderung hochzaehlen. Basis der
     * Konfliktaufloesung zwischen den Clients.
     */
    val updatedAt: Long,

    /**
     * Wird beim OEFFNEN gesetzt. Ansehen ist nicht Bearbeiten.
     * NICHT sync-relevant -- jeder Client fuehrt seinen eigenen Wert.
     * Steuert die Auto-Archivierung nach Alter.
     */
    val lastOpenedAt: Long,

    val stageChangedAt: Long,

    /** Verweist auf archive_runs.batchId, wenn automatisch archiviert. Lokal. */
    val autoArchivedBatchId: String? = null,

    /** Gesetzt = im Papierkorb. Endgueltige Loeschung laeuft ueber tombstones. */
    val deletedAt: Long? = null,

    /**
     * Ob diese Notiz ueberhaupt in die Cloud darf.
     *
     * REIN LOKAL, und das ist der ganze Sinn: Wer eine Notiz vom Abgleich
     * ausnimmt, will nicht, dass diese Entscheidung selbst irgendwo hingeht.
     * Sie steht deshalb NICHT im Drive-Dokument (SYNC.md 14.2a).
     *
     * Auf `false` gesetzt, raeumt der naechste Abgleich die Notiz und ihre
     * Dateien aus Drive weg -- ohne Grabstein: "bleibt auf diesem Geraet" heisst
     * nicht "ueberall loeschen".
     */
    val syncEnabled: Boolean = true,

    /**
     * Ob diese Notiz im Kalender des Geraets erscheinen darf.
     *
     * REIN LOKAL, aus demselben Grund wie [syncEnabled] und noch einem: Welcher
     * Kalender ueberhaupt gemeint ist, weiss nur dieses Geraet. Auf einem
     * zweiten haengt womoeglich ein ganz anderes Konto.
     *
     * Voreingestellt auf `true`. Der Hauptschalter in den Einstellungen
     * entscheidet, ob ueberhaupt etwas eingetragen wird; dieses Feld nimmt
     * einzelne Notizen davon aus.
     */
    val calendarEnabled: Boolean = true,

    /**
     * Der Termin im Kalender des Geraets, falls es einen gibt.
     *
     * Die Kennung stammt vom Kalender-Anbieter und gilt nur hier. Sie steht
     * deshalb weder im Drive-Dokument noch in der Sicherungsdatei: Auf einem
     * anderen Geraet zeigte sie auf irgendeinen fremden Termin oder ins Leere.
     */
    val calendarEventId: Long? = null,

    /**
     * Ob die letzte Fassung von aussen kam (SYNC.md 8). Wird beim Holen aus
     * Drive gesetzt und beim naechsten eigenen Schreiben wieder auf APP.
     */
    val origin: Herkunft = Herkunft.APP,

    /**
     * Das Archiv des Ordnermodus (SYNC.md 13, Schema 5).
     *
     * `null` heisst: im normalen Ordnerbaum. Gesetzt heisst: im Archivbaum,
     * und zwar seit diesem Zeitpunkt. Getrennt von [stage], weil die
     * beiden Systeme getrennt sind: Eine Notiz kann im Fluss im Eingang
     * liegen und im Ordnersystem archiviert sein. Wer im Ordnermodus
     * archiviert, ruehrt die Stufe nicht an, und umgekehrt.
     */
    val ordnerArchiviertAt: Long? = null,

    /**
     * Der Ordner, aus dem heraus archiviert wurde. Fuer „Zurueck nach …" beim
     * Zurueckholen. `null` = aus dem Hauptordner oder nie archiviert.
     */
    val herkunftOrdnerId: String? = null,

    /**
     * Der geloeschte Ordner, aus dem die Notiz beim Loeschen „nur der Ordner"
     * herausgerueckt ist. Damit zeigt der Ordner im Papierkorb noch, was in
     * ihm lag (ausgegraut). Wird beim naechsten Verschieben von
     * Hand geleert: Wer die Notiz woandershin legt, hat sich entschieden.
     */
    val ehemaligerOrdnerId: String? = null,
)

/** Checklisteneintrag. Gehoert zur Notiz, faellt mit ihr. Siehe SYNC.md 14.5. */
@Entity(
    tableName = "note_items",
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
data class NoteItemEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String = "",
    val isChecked: Boolean = false,
    val position: Int,
)

/**
 * n:m zwischen Notiz und Tag. `position` bestimmt die Reihenfolge der Chips;
 * der erste Tag ist der fuehrende.
 */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
    val position: Int = 0,
)

/**
 * Volltextindex. REIN LOKAL, wird nie synchronisiert.
 *
 * Eigenstaendige Tabelle, KEINE External-Content-Tabelle: der Index speist sich
 * aus drei Quelltabellen (notes, note_items, transcripts), External Content
 * unterstuetzt aber nur genau eine. Das Repository pflegt ihn bei jeder
 * Schreiboperation mit.
 *
 * FTS4, nicht FTS5 -- Room bietet nur @Fts3/@Fts4. Fuer die
 * Trefferhervorhebung steht snippet() zur Verfuegung; highlight() ist
 * FTS5-only und existiert fuer uns nicht.
 *
 * TOKENIZER: unicode61 statt des Standards `simple`. Der Standard kennt nur
 * ASCII-Kleinschreibung -- "Kueche" und "kueche" waeren damit verschiedene
 * Woerter, sobald ein Umlaut im Spiel ist. unicode61 faltet korrekt und
 * entfernt Diakritika, sodass "Kuche" auch "Kueche" findet. Fuer eine
 * deutschsprachige App ist das keine Feinheit, sondern Voraussetzung.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(
    // autoGenerate, damit SQLite die rowid vergibt. Ohne das wuerde beim
    // zweiten Eintrag die feste 0 kollidieren.
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val noteId: String,
    val title: String,
    val body: String,
    /** Alle Checklisteneintraege der Notiz, zeilenweise zusammengefasst. */
    val itemsText: String,
    /** Alle Transkriptsegmente der Notiz, zeilenweise zusammengefasst. */
    val transcriptText: String,
)
