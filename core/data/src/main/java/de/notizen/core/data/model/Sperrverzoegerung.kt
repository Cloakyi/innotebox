package de.notizen.core.data.model

/**
 * Wie lange die App im Hintergrund sein darf, bevor sie sich sperrt (Phase 19).
 *
 * „Sofort" heißt: beim nächsten Öffnen aus dem Hintergrund. Eine Drehung des
 * Geräts ist kein Hintergrund.
 */
enum class Sperrverzoegerung(val minuten: Int, val beschriftung: String) {
    SOFORT(0, "Sofort"),
    EINE_MINUTE(1, "Nach einer Minute"),
    FUENF_MINUTEN(5, "Nach fünf Minuten"),
    DREISSIG_MINUTEN(30, "Nach dreißig Minuten"),
    ;

    companion object {
        val STANDARD = EINE_MINUTE

        /** Unbekannte gespeicherte Werte fallen still auf den Standard zurück. */
        fun ausName(name: String?): Sperrverzoegerung =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
