package de.notizen.core.data.model

/**
 * Wozu ein Anhang da ist. Gespeichert wird der NAME (siehe SYNC.md 14.1 und 6.6).
 *
 * **Nicht zu verwechseln mit `notes.backgroundAttachmentId`.** Die Rolle sagt,
 * ob ein Anhang im Bildraster der Notiz **auftaucht**; der Verweis sagt, welcher
 * Anhang gerade die **Flaeche** ist. Ein Bild der Notiz darf beides sein.
 */
enum class Anhangsrolle {
    /** Gehoert zum Inhalt der Notiz und steht im Bildraster. */
    INHALT,

    /**
     * Ausschliesslich Flaeche. Erscheint NICHT im Raster.
     *
     * Damit laesst sich ein Hintergrundbild waehlen, ohne dass es zu den
     * Bildern der Notiz zaehlt -- der Wunsch, aus dem dieses Feld entstanden
     * ist. Die Alternative waere gewesen, das Hintergrundbild aus dem Raster zu
     * entfernen; fuer jemanden, der ein vorhandenes Bild zur Flaeche macht,
     * saehe das aus wie Loeschen.
     */
    HINTERGRUND,
}
