package de.notizen.core.data.model

/**
 * Woher die letzte Fassung einer Notiz kam (SYNC.md 3.1, `origin`).
 *
 * `EXTERNAL` heisst: Ein Assistent oder ein anderes Werkzeug hat die Datei in
 * Drive geschrieben, nicht eine Fassung dieser App. Die Karte zeigt das mit
 * einem kleinen Hinweis, damit nachvollziehbar bleibt, was von aussen kam.
 * Sobald die App die Notiz selbst schreibt, ist es wieder `APP`.
 */
enum class Herkunft {
    APP,
    EXTERNAL,
}
