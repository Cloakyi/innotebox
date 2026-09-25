package de.notizen.core.data.model

/**
 * Welchem Baum ein Ordner gehoert (SYNC.md 13, `folders.bereich`).
 *
 * Das Archiv des Ordnermodus hat einen eigenen Ordnerbaum. Ein Ordner wechselt
 * den Bereich nie: `Ordnerregeln.darfHinein` laesst ein Verschieben nur
 * innerhalb desselben Bereichs zu, und `parentId` zeigt deshalb immer auf einen
 * Ordner desselben Bereichs. Entschieden am 2026-08-25, ausgestaltet am
 * 2026-09-14, gebaut am 2026-09-19 (Phase 14a).
 */
enum class Bereich {
    ORDNER,
    ARCHIV,
}
