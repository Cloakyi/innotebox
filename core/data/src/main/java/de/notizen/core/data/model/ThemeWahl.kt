package de.notizen.core.data.model

/** Welches Farbschema die App benutzt. */
enum class ThemeWahl {
    /** Folgt der Systemeinstellung. */
    SYSTEM,
    HELL,
    DUNKEL,
    ;

    fun beschriftung(): String = when (this) {
        SYSTEM -> "Wie das System"
        HELL -> "Immer hell"
        DUNKEL -> "Immer dunkel"
    }
}
