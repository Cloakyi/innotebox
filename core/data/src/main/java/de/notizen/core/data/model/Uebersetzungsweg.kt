package de.notizen.core.data.model

/**
 * Über welchen Weg die App übersetzt (Phase 16, entschieden am 2026-09-14).
 *
 * Drei Wege, weil kein einzelner überall da ist: Die Übersetzung des Systems
 * fehlt auf manchen Geräten ganz (Xiaomi), die KI auf dem Gerät hängt an
 * AICore, und ML Kit lädt seine Sprachpakete über das Netz, darf also nur nach
 * dem Netz-Opt-in. Der Nutzer wählt in den Einstellungen; was das Gerät nicht
 * kann, steht dort ausgegraut.
 */
enum class Uebersetzungsweg(val beschriftung: String) {
    SYSTEM("Die Übersetzung des Systems"),
    GERAETE_KI("Die KI auf dem Gerät"),
    MLKIT("Sprachpakete von Google (ML Kit)"),
    ;

    companion object {
        val STANDARD = SYSTEM

        /** Unbekannte gespeicherte Werte fallen still auf den Standard zurück. */
        fun ausName(name: String?): Uebersetzungsweg =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
