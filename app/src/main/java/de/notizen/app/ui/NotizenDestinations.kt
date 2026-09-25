package de.notizen.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.ui.graphics.vector.ImageVector
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage

/**
 * Routen der App. Bewusst wenige -- der Drawer bleibt kurz
 * (Spezifikation Abschnitt 14).
 */
object Routes {
    /**
     * Die Stufe. `hervorheben` (seit 14c) nennt eine Notiz, zu der die Liste
     * scrollt und die kurz aufblitzt; leer bei gewoehnlicher Navigation.
     */
    const val STAGE = "stage/{stage}?hervorheben={hervorheben}"
    const val TRASH = "trash"
    const val TAGS = "tags"
    /** `hervorheben` (seit Phase 15) springt zu einem Abschnitt und hebt ihn kurz hervor. */
    const val SETTINGS = "settings?hervorheben={hervorheben}"
    const val SYNC = "sync"
    const val BACKUP = "backup"
    const val PROTOKOLL = "protokoll"
    const val LIZENZEN = "lizenzen"
    const val KALENDER = "kalender"
    const val EDITOR = "editor/{noteId}"

    /**
     * Ein Ordner. Ohne Parameter der Hauptordner.
     *
     * **Die Stufe fehlt hier mit Absicht.** `StageViewModel` unterscheidet die
     * beiden Ansichten genau daran: Wo kein `stage` steht, steht ein Ordner.
     * Deshalb darf diese Route dieses Argument nie bekommen.
     */
    const val ORDNER = "ordner?ordner={ordner}&hervorheben={hervorheben}"

    /**
     * Das Archiv des Ordnermodus, mit eigenem Ordnerbaum (Phase 14b). Ohne
     * Parameter die oberste Ebene des Archivs.
     *
     * Dieselbe Bauart wie [ORDNER], also ebenfalls ohne Stufe. Woran die
     * ViewModels das Archiv erkennen, ist das feste Argument `bereich`, das
     * diese Route im Graphen mitbekommt; so wie der Papierkorb an
     * `papierkorb = true`.
     */
    const val ARCHIV = "archiv?ordner={ordner}&hervorheben={hervorheben}"

    /**
     * Die Suche. Stufe und Tag sind nur die VOREINSTELLUNG der Filter, keine
     * Einschränkung: gesucht wird immer über alles, man fängt nur woanders an.
     */
    const val SUCHE = "suche?stufe={stufe}&tag={tag}&ordner={ordner}"

    fun stage(stage: Stage, hervorheben: String? = null) =
        "stage/${stage.name}" + (hervorheben?.let { "?hervorheben=$it" } ?: "")

    /** [ordnerId] ist der Ordner, aus dem heraus gesucht wird; er steht vorausgewaehlt (14d). */
    fun suche(stufe: Stage? = null, tagId: String? = null, ordnerId: String? = null): String {
        val teile = buildList {
            stufe?.let { add("stufe=${it.name}") }
            tagId?.let { add("tag=$it") }
            ordnerId?.let { add("ordner=$it") }
        }
        return if (teile.isEmpty()) "suche" else "suche?" + teile.joinToString("&")
    }

    fun editor(noteId: String) = "editor/$noteId"

    fun settings(hervorheben: String? = null) =
        if (hervorheben == null) "settings" else "settings?hervorheben=$hervorheben"

    fun ordner(id: String? = null, hervorheben: String? = null) =
        mitParametern("ordner", id, hervorheben)

    fun archiv(id: String? = null, hervorheben: String? = null) =
        mitParametern("archiv", id, hervorheben)

    private fun mitParametern(basis: String, ordnerId: String?, hervorheben: String?): String {
        val teile = buildList {
            ordnerId?.let { add("ordner=$it") }
            hervorheben?.let { add("hervorheben=$it") }
        }
        return if (teile.isEmpty()) basis else basis + "?" + teile.joinToString("&")
    }
}

/**
 * Feste, eindeutige Icons pro Notiztyp -- zentral, damit die Karte, der FAB
 * und spaeter der Filter dieselben verwenden (Spezifikation Abschnitt 13).
 */
object NoteTypeIcons {
    fun of(type: NoteType): ImageVector = when (type) {
        NoteType.TEXT -> Icons.AutoMirrored.Outlined.Notes
        NoteType.LIST -> Icons.Outlined.Checklist
        NoteType.AUDIO -> Icons.Outlined.Mic
        NoteType.IMAGE -> Icons.Outlined.Image
        NoteType.DRAWING -> Icons.Outlined.Draw
    }

    fun label(type: NoteType): String = when (type) {
        NoteType.TEXT -> "Textnotiz"
        NoteType.LIST -> "Liste"
        NoteType.AUDIO -> "Audio"
        NoteType.IMAGE -> "Bild"
        NoteType.DRAWING -> "Zeichnung"
    }
}

/** Icon und Beschriftung einer Stufe im Drawer. */
object StageUi {
    fun icon(stage: Stage): ImageVector = when (stage) {
        Stage.INBOX -> Icons.Outlined.Inbox
        Stage.WORKSPACE -> Icons.Outlined.Workspaces
        Stage.ARCHIVE -> Icons.Outlined.Archive
    }

    fun label(stage: Stage): String = when (stage) {
        Stage.INBOX -> "Eingang"
        Stage.WORKSPACE -> "Workspace"
        Stage.ARCHIVE -> "Archiv"
    }
}

/**
 * Eine Aktion, die sich zuruecknehmen laesst.
 *
 * Es gibt keinen Speichern-Knopf und keine Loeschabfrage. Ohne verlaesslichen
 * Rueckweg waere jede Fehlbedienung endgueltig -- deshalb ist die
 * Undo-Snackbar nach Loeschen, Archivieren und Stufenwechsel Pflicht
 * (Spezifikation Abschnitt 9), nicht Kuer.
 */
data class UndoRequest(
    val message: String,
    val undo: suspend () -> Unit,
)
