package de.notizen.core.data.model

/**
 * Farbe einer Notiz-Bubble. Unabhaengig von Tags, unabhaengig von der Stufe.
 * Feste Auswahl, keine freie Farbwahl.
 *
 * HIER STEHEN BEWUSST KEINE FARBWERTE.
 *
 * Gespeichert und synchronisiert wird ausschliesslich der Enum-NAME. Die
 * konkreten Tonwerte sind Client-Sache: Android leitet helle und
 * dunkle Container-Toene ab, der Web-Client darf eigene verwenden. Ein
 * gespeicherter Rohfarbwert saehe in einem der beiden Theme-Modi immer falsch
 * aus -- und dieses Modul hat ohnehin keine UI-Abhaengigkeiten.
 *
 * Die Referenzwerte (Google-Kalender-Palette) und die daraus abgeleiteten
 * Tonwerte leben in :app.
 */
enum class NoteColor {
    /** Keine eigene Farbe: die Karte uebernimmt die Theme-Flaeche (surface). */
    DEFAULT,

    RED,
    PINK,
    ORANGE,
    YELLOW,
    GREEN,
    DARK_GREEN,
    TEAL,
    BLUE,
    DARK_BLUE,
    PURPLE,
    DARK_PURPLE,
    BROWN,
    DARK_BROWN,
    GREY,
    BLACK,
}
