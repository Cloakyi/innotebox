package de.notizen.app.uebersetzung

/**
 * Was ein Übersetzungsversuch ergeben hat (Phase 16).
 *
 * Die Fälle sind absichtlich grob: Der Dialog im Editor muss nur wissen, ob er
 * den Text zeigen, zum Nachladen anbieten oder einen Satz sagen soll.
 */
sealed interface Uebersetzungsergebnis {

    /** Ein übersetzter Text je Eingabetext, in derselben Reihenfolge. */
    data class Erfolg(val texte: List<String>) : Uebersetzungsergebnis

    /**
     * Das Sprachpaar ist auf diesem Weg nicht da. [ladbar] sagt, ob es sich
     * holen lässt: über die Systemeinstellung (System) oder über den Knopf
     * im Dialog (ML Kit). Die KI kennt den Fall nicht.
     */
    data class SprachpaarFehlt(val ladbar: Boolean) : Uebersetzungsergebnis

    /** Der Weg geht auf diesem Gerät gerade gar nicht, mit dem Grund als Satz. */
    data class NichtVerfuegbar(val grund: String) : Uebersetzungsergebnis

    /** Der Weg ist da, der Versuch ist trotzdem gescheitert. */
    data class Fehler(val meldung: String) : Uebersetzungsergebnis
}

/**
 * Ein Weg, Text zu übersetzen. Drei Umsetzungen, eine je [de.notizen.core.data.model.Uebersetzungsweg].
 *
 * **Mehrere Texte auf einmal**, weil eine Listennotiz aus Einträgen besteht,
 * die einzeln übersetzt gehören; wer sie mit Zeilenumbrüchen zusammenklebt,
 * bekommt vom Übersetzer gern eine andere Zahl Zeilen zurück.
 */
interface Uebersetzer {

    /**
     * Übersetzt [texte] von [von] nach [nach] (Sprachcodes nach BCP 47).
     * [laden] erlaubt, ein fehlendes Sprachpaket vorher zu holen, wo der Weg
     * das selbst kann (ML Kit); sonst kommt [Uebersetzungsergebnis.SprachpaarFehlt].
     */
    suspend fun uebersetzen(
        texte: List<String>,
        von: String,
        nach: String,
        laden: Boolean = false,
    ): Uebersetzungsergebnis
}
