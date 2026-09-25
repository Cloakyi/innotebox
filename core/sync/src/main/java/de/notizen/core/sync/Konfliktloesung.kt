package de.notizen.core.sync

import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.model.Stage

/**
 * Die Konfliktkopie (SYNC.md 6.1, Regel 3).
 *
 * Seit Schema 4 entscheidet [Abgleichregeln], WANN es eine Kopie gibt; hier
 * steht nur noch, WIE sie aussieht. Die fruehere Entscheidung nach
 * Zeitstempeln (Schema 3) ist mit dem Zaehler `rev` vom Tisch: Zeit sagt
 * nichts ueber Vorrang, nur ueber Anzeige.
 */
object Konfliktloesung {

    /**
     * Der Titel einer Konfliktkopie.
     *
     * Sie ist als solche erkennbar und traegt das Datum. Zwei Notizen mit
     * demselben Titel nebeneinander waeren genau das stille Durcheinander, das
     * die Kopie verhindern soll.
     */
    fun konflikttitel(original: String, wann: String): String {
        val kern = original.ifBlank { "Ohne Titel" }
        return "$kern (Konflikt $wann)"
    }

    /**
     * Die Konfliktkopie als eigene Notiz.
     *
     * Sie bekommt eine **neue Kennung**, sonst waere sie dieselbe Notiz und der
     * naechste Abgleich loeste denselben Konflikt erneut aus. Und sie landet im
     * Eingang: Etwas, das Aufmerksamkeit braucht, gehoert dorthin, wo man
     * hinsieht.
     */
    fun alsKonfliktkopie(
        fern: Notizdokument,
        neueId: String,
        titel: String,
        jetzt: Long,
    ): NoteEntity = fern.alsNotiz(lastOpenedAt = jetzt).copy(
        id = neueId,
        title = titel,
        stage = Stage.INBOX,
        stageChangedAt = jetzt,
        // Kein Favorit: Die Kopie ist ein Hinweis, keine Auszeichnung.
        isFavorite = false,
        favoritedAt = null,
        deletedAt = null,
    )
}
