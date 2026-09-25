package de.notizen.core.data.model

/**
 * Was eine Wischgeste auf einer Notizkarte auslöst.
 *
 * Getrennt je Richtung einstellbar. Voreinstellung ist der Fluss selbst --
 * nach rechts vorwaerts, nach links zurueck.
 */
enum class WischZiel {
    /** Eingang -> Workspace -> Archiv. Am Ende des Flusses wirkungslos. */
    NAECHSTE_STUFE,

    /** Archiv -> Workspace -> Eingang. Am Anfang wirkungslos. */
    VORHERIGE_STUFE,

    /** In den Papierkorb. Umkehrbar ueber die Snackbar. */
    PAPIERKORB,

    /** Geste in dieser Richtung abgeschaltet. */
    NICHTS,

    /**
     * Nur im Papierkorb: die Notiz kommt zurueck. Rastet ein wie das
     * Loeschen, erst ein Tippen holt zurueck (Nutzer, 2026-09-15: auch das
     * Zurueckholen soll die zweite Handlung verlangen). Dazu die Snackbar.
     */
    WIEDERHERSTELLEN,

    /**
     * Nur im Papierkorb: endgueltig loeschen. Rastet ein wie der Papierkorb,
     * erst ein Tippen auf die Flaeche loescht. Kein Undo, dafuer die zweite
     * Handlung.
     */
    ENDGUELTIG,
    ;

    fun beschriftung(): String = when (this) {
        NAECHSTE_STUFE -> "Naechste Stufe"
        VORHERIGE_STUFE -> "Vorherige Stufe"
        PAPIERKORB -> "Papierkorb"
        NICHTS -> "Nichts"
        WIEDERHERSTELLEN -> "Zurueckholen"
        ENDGUELTIG -> "Endgueltig loeschen"
    }

    /**
     * Ob die Geste einrastet statt abzureissen.
     *
     * Einrasten heisst: Die Karte bleibt an der Schwelle stehen, und erst ein
     * Tippen auf die freigelegte Flaeche fuehrt aus. Das gilt fuer alles, was
     * nicht ueber die Snackbar zurueckzunehmen ist oder zumindest schwer wiegt.
     */
    fun rastetEin(): Boolean =
        this == PAPIERKORB || this == ENDGUELTIG || this == WIEDERHERSTELLEN

    /**
     * Ob die Geste in dieser Stufe ueberhaupt etwas bewirkt.
     *
     * Eine Geste, die ins Leere laeuft, wird gar nicht erst freigeschaltet --
     * eine Karte, die sich wegziehen laesst und dann zurueckschnappt, sieht aus
     * wie ein Fehler.
     */
    fun wirktIn(stage: Stage): Boolean = when (this) {
        NAECHSTE_STUFE -> stage.next() != null
        VORHERIGE_STUFE -> stage.previous() != null
        PAPIERKORB -> true
        NICHTS -> false
        WIEDERHERSTELLEN -> true
        ENDGUELTIG -> true
    }

    companion object {
        /**
         * Was in den Einstellungen zur Wahl steht.
         *
         * Die beiden Papierkorb-Ziele fehlen: Sie gelten nur dort, und dort
         * sind sie fest. Wer sie fuer den Eingang waehlen koennte, haette eine
         * Geste, die eine lebende Notiz "zurueckholt".
         */
        val waehlbar: List<WischZiel> = listOf(NAECHSTE_STUFE, VORHERIGE_STUFE, PAPIERKORB, NICHTS)

        /** Was im Papierkorb zur Wahl steht. Dort gibt es keine Stufen. */
        val imPapierkorbWaehlbar: List<WischZiel> = listOf(ENDGUELTIG, WIEDERHERSTELLEN, NICHTS)
    }
}
