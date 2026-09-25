package de.notizen.core.data.model

/**
 * Wonach Ordner unter Geschwistern geordnet werden (Phase 14e).
 *
 * Eine Einstellung fuer die ganze App, nicht je Ordner: Ordner sind eine
 * Sammlung wie die naechste, und eine Reihenfolge, die man dreissigmal
 * einstellt, stellt niemand ein. `EIGENE` ist die Reihenfolge aus
 * `folders.sortIndex`, die der Nutzer per Ziehen festlegt; die anderen beiden
 * schreiben dort nie hinein, damit sie beim Zurueckschalten wieder da ist.
 */
enum class Ordnersortierung(val beschriftung: String) {
    EIGENE("Eigene Reihenfolge"),
    NAME("Name"),
    DATUM("Zuletzt angelegt"),
}
