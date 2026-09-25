package de.notizen.core.data.model

/** Was einen Auto-Archiv-Lauf ausgeloest hat (Spezifikation Abschnitt 8). */
enum class ArchiveTrigger {
    /** Notiz seit X Tagen nicht mehr GEOEFFNET (lastOpenedAt, nicht updatedAt). */
    AGE,

    /** Stufe hat N Notizen ueberschritten, aelteste weichen. */
    COUNT,

    /**
     * Beide Ausloeser im selben Lauf.
     *
     * Ergaenzt am 2026-08-23 (Phase 8d). Unkritisch, weil `archive_runs` rein
     * lokal ist (SYNC.md 14.9) -- kein anderer Client liest diesen Wert je, und
     * bestehende Zeilen tragen weiter AGE oder COUNT.
     *
     * Zwei getrennte Laeufe waeren die Alternative gewesen: zwei
     * Benachrichtigungen und zwei Undo-Knoepfe fuer dasselbe naechtliche
     * Aufraeumen. Wer den einen drueckt, wundert sich ueber den anderen.
     */
    BOTH,
}

/** Abgleichzustand einer einzelnen Entitaet. Rein lokal, geht nie nach Drive. */
enum class SyncStatus {
    /** Lokal und entfernt identisch. */
    SYNCED,

    /** Lokal geaendert, noch nicht hochgeladen. */
    DIRTY,

    /** Beide Seiten geaendert. Braucht eine Entscheidung, siehe SYNC.md 4. */
    CONFLICT,
}

/**
 * Welche Art von Entitaet ein Sync-Zustand oder ein Tombstone betrifft.
 * Der Name wandert in `tombstones` mit nach Drive -- Umbenennen ist ein
 * Schema-Bruch.
 */
enum class EntityType {
    NOTE,
    TAG,
    NOTE_ITEM,
    ATTACHMENT,
    TRANSCRIPT,
    REMINDER,
    FOLDER,

    /**
     * Ein Termin im Kalender des Geraets, der noch weg muss.
     *
     * **Kein Vertrag mit dem anderen Geraet, sondern ein Merkzettel fuer
     * dieses** -- genau wie bei ATTACHMENT. Er entsteht, wenn eine Notiz
     * endgueltig geloescht wird, ohne vorher im Papierkorb gelegen zu haben:
     * Dann ist ihre Zeile weg, und mit ihr der einzige Hinweis darauf, welcher
     * Termin zu ihr gehoerte. Der Grabstein faellt weg, sobald der Termin
     * geloescht ist.
     *
     * Geht NIE nach Drive. Der Abgleich schickt ausschliesslich Grabsteine vom
     * Typ NOTE.
     */
    CALENDAR,
}
