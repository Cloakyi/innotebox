package de.notizen.core.data.model

/**
 * Die drei Stationen des Flusses: Eingang -> Workspace -> Archiv.
 *
 * Das ist ein FELD an der Notiz, KEIN Ordner. Es gibt im Kernumfang keine
 * Ordnerhierarchie -- thematisch ordnen Tags.
 *
 * Gespeichert und synchronisiert wird der NAME, nie die Ordinalzahl.
 * Umbenennen oder Umsortieren waere ein Schema-Bruch (siehe SYNC.md 14.1).
 */
enum class Stage {
    /** Startbildschirm. Schnelles Wegwerfen von Gedanken, kein Titel noetig. */
    INBOX,

    /** Der Schreibtisch mit den Post-its. Titel ist Pflicht. */
    WORKSPACE,

    /** Abgearbeitetes. Wird durchsucht, nicht durchblaettert. Titel ist Pflicht. */
    ARCHIVE,
    ;

    /** Naechste Station im Fluss, oder null am Ende. */
    fun next(): Stage? = when (this) {
        INBOX -> WORKSPACE
        WORKSPACE -> ARCHIVE
        ARCHIVE -> null
    }

    /** Vorige Station im Fluss, oder null am Anfang. */
    fun previous(): Stage? = when (this) {
        INBOX -> null
        WORKSPACE -> INBOX
        ARCHIVE -> WORKSPACE
    }

    /** Ab WORKSPACE ist ein Titel Pflicht (Spezifikation Abschnitt 4). */
    fun requiresTitle(): Boolean = this != INBOX
}
