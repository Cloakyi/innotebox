package de.notizen.app.ui.editor

import de.notizen.app.uebersetzung.Uebersetzungsergebnis
import de.notizen.core.data.model.Uebersetzungsweg

/**
 * Was gerade uebersetzt wird (Phase 16): der Zustand des Dialogs im Editor.
 *
 * [bereich] sagt, wohin das Ergebnis gehoert: in eine Auswahl im Text, in den
 * ganzen Text oder in die Eintraege einer Liste. [texte] sind die Stuecke, die
 * uebersetzt werden, in der Reihenfolge, in der sie zurueckkommen.
 */
data class Uebersetzungsanfrage(
    val bereich: Bereich,
    val texte: List<String>,
    val von: String,
    val nach: String,
    /** Der Weg, ueber den uebersetzt wird; entscheidet, wie ein fehlendes Sprachpaket zu holen ist. */
    val weg: Uebersetzungsweg,
    val laeuft: Boolean = false,
    val ergebnis: Uebersetzungsergebnis? = null,
) {
    sealed interface Bereich {
        /** Eine Auswahl im Text, Anfang und Ende als Stellen im Quelltext. */
        data class Auswahl(val anfang: Int, val ende: Int) : Bereich

        /** Der ganze Text der Notiz. */
        data object GanzerText : Bereich

        /** Die Eintraege einer Liste, in der Reihenfolge der Notiz. */
        data class Eintraege(val kennungen: List<String>) : Bereich
    }

    val fertig: List<String>?
        get() = (ergebnis as? Uebersetzungsergebnis.Erfolg)?.texte
}
