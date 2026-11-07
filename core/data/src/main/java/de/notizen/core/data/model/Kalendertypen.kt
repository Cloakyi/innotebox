package de.notizen.core.data.model

/** Was ein Eintrag im Kalender ist (SYNC.md 16.2). Name geht nach Drive. */
enum class Terminart {
    TERMIN,
    AUFGABE,
    GEBURTSTAG,
}

/**
 * Wie sich ein Termin wiederholt (SYNC.md 16.2). Name geht nach Drive.
 *
 * Gerechnet wird in Ortszeit ab dem ersten Vorkommen, siehe `Terminregeln`.
 */
enum class Wiederholung {
    KEINE,
    TAEGLICH,
    WERKTAGS,
    WOECHENTLICH,
    MONATLICH,
    JAEHRLICH,
}
