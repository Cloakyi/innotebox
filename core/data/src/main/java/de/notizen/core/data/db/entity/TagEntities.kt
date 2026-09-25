package de.notizen.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.notizen.core.data.model.Bereich

/**
 * Tag -- das thematische Ordnungssystem, das Ordner ersetzt.
 * Siehe SYNC.md 14.3.
 */
@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true),
        Index("deletedAt"),
    ],
)
data class TagEntity(
    @PrimaryKey val id: String,

    val name: String,

    /**
     * Echter ARGB-Rohwert -- anders als bei NoteColor.
     * Das ist Absicht: eine Tag-Farbe waehlt der Nutzer frei, sie ist keine
     * Position in einer festen Palette.
     *
     * Faerbt NUR den Tag-Chip und den Punkt im Drawer. Hat nichts mit der
     * Farbe der Notizkarte zu tun -- zwei getrennte Systeme.
     */
    val colorArgb: Int,

    val iconOrEmoji: String? = null,
    val sortIndex: Int = 0,

    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Ein Ordner, siehe SYNC.md 14.11.
 *
 * Die Tabelle stand von Anfang an im Schema, ohne dass jemand hineingeschrieben
 * haette -- deshalb kostet der Ordnermodus KEINE Migration. Dasselbe gilt fuer
 * `notes.folderId`.
 *
 * Kein Fremdschluessel auf sich selbst. Ein Ordner verweist ueber
 * [parentId] auf einen anderen, aber die Datenbank erzwingt das nicht: Beim
 * Abgleich kann ein Kind vor seinem Elternteil ankommen, und ein
 * Fremdschluessel liesse dann den ganzen Schreibvorgang scheitern. Ordner ohne
 * auffindbare Eltern haengen stattdessen sichtbar an der Wurzel, siehe
 * `Ordnerregeln.kinder`.
 *
 * Geloescht wird weich. [deletedAt] bleibt stehen, die Zeile nicht. Nur so
 * erfaehrt das andere Geraet ueberhaupt von der Loeschung -- Grabsteine gibt es
 * in Drive ausschliesslich fuer Notizen.
 */
@Entity(
    tableName = "folders",
    indices = [Index("parentId"), Index("deletedAt")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val iconOrEmoji: String? = null,

    /**
     * Echter ARGB-Rohwert wie bei [TagEntity], nicht die Palette der Notizen.
     * `null` heisst: die Farbe des Themes, kein eigener Anstrich.
     */
    val colorArgb: Int? = null,

    /**
     * Die eigene Reihenfolge unter Geschwistern. Andere
     * Sortierungen der Ansicht schreiben hier nie hinein.
     */
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,

    /**
     * Welchem Baum der Ordner gehoert (SYNC.md 13, Schema 5). Das
     * Archiv des Ordnermodus hat einen eigenen Baum; ein Ordner wechselt den
     * Bereich nie, und [parentId] zeigt immer auf einen Ordner desselben
     * Bereichs (`Ordnerregeln.darfHinein`).
     */
    val bereich: Bereich = Bereich.ORDNER,

    /**
     * Der geloeschte Ordner, aus dem dieser beim Loeschen „nur der Ordner"
     * herausgerueckt ist (ausgegraut im Papierkorb). Wird beim
     * naechsten Verschieben von Hand geleert.
     */
    val ehemaligerElternId: String? = null,
)
