package de.notizen.core.data.model

/**
 * Art der Notiz. Gespeichert wird der NAME (siehe SYNC.md 14.1).
 */
enum class NoteType {
    TEXT,
    LIST,
    AUDIO,

    /**
     * IM UMFANG NICHT ENTHALTEN. Existiert nur, damit Zeichnungs-Notizen in
     * spaeter ohne Schema-Migration nachruestbar sind. Der FAB bietet diesen
     * Typ nicht an, und nichts erzeugt ihn.
     */
    DRAWING,

    IMAGE,
}
