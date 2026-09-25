package de.notizen.core.data.search

import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage

/**
 * Was gesucht wird, Text und Filter in einem.
 *
 * Alle Mengen bedeuten leer „egal", nicht „nichts". Ein Filter, bei dem nichts
 * angekreuzt ist, schränkt nicht ein; das ist die einzige Lesart, bei der eine
 * frisch geöffnete Suche alles zeigt.
 *
 * Innerhalb einer Kategorie gilt ODER, zwischen den Kategorien UND: zwei
 * gewählte Farben heißen „eine von beiden", eine Farbe plus ein Tag heißt
 * „beides". Das ist, was Filterchips überall sonst auch tun.
 */
data class Suchanfrage(
    val text: String = "",
    val stufen: Set<Stage> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val farben: Set<NoteColor> = emptySet(),
    val typen: Set<NoteType> = emptySet(),
    val nurFavoriten: Boolean = false,
    val zeitraum: Zeitraum = Zeitraum.EGAL,

    /**
     * Ordner als Filter. Ein gewählter Ordner schließt seine
     * Unterordner ein; das rechnet das Repository über den Baum aus, nicht
     * die Oberfläche. Die Stufen bleiben daneben stehen: zwei getrennte
     * Systeme, beide durchsuchbar.
     */
    val ordnerIds: Set<String> = emptySet(),

    /**
     * Ob der Papierkorb mitgesucht wird, standardmäßig nicht. Der
     * Volltextindex kennt nur lebende Notizen; für den Papierkorb läuft ein
     * zweiter Weg über den Text der Notiz, dann wird zusammengeführt.
     */
    val mitPapierkorb: Boolean = false,
) {
    val hatFilter: Boolean
        get() = stufen.isNotEmpty() || tagIds.isNotEmpty() || farben.isNotEmpty() ||
            typen.isNotEmpty() || nurFavoriten || zeitraum != Zeitraum.EGAL ||
            ordnerIds.isNotEmpty() || mitPapierkorb

    /** Ob überhaupt eingeschränkt wird. Leer heißt: alles zeigen. */
    val istLeer: Boolean
        get() = text.isBlank() && !hatFilter

    val anzahlFilter: Int
        get() = stufen.size + tagIds.size + farben.size + typen.size + ordnerIds.size +
            (if (nurFavoriten) 1 else 0) + (if (zeitraum != Zeitraum.EGAL) 1 else 0) +
            (if (mitPapierkorb) 1 else 0)
}

/**
 * Zeitfilter, gemessen an `updatedAt`.
 *
 * Bewusst nicht an `createdAt`: gesucht wird, woran man zuletzt gearbeitet hat,
 * nicht, wann eine Notiz einmal entstanden ist. Und ausdrücklich nicht an
 * `lastOpenedAt`, bloßes Ansehen darf nicht so aussehen, als hätte man etwas
 * bearbeitet (siehe die `updatedAt`-Disziplin in docs/ENTSCHEIDUNGEN.md).
 */
enum class Zeitraum(val beschriftung: String, val tage: Int?) {
    EGAL("Egal", null),
    HEUTE("Heute", 1),
    WOCHE("Diese Woche", 7),
    MONAT("Dieser Monat", 30),
    JAHR("Dieses Jahr", 365),
    ;

    /** Der früheste Zeitpunkt, der noch zählt. `null` heißt keine Grenze. */
    fun grenze(jetzt: Long): Long? = tage?.let { jetzt - it * 24L * 60 * 60 * 1000 }
}
