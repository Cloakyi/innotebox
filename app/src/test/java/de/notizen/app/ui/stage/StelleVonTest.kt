package de.notizen.app.ui.stage

import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Stelle einer Notiz in der Liste, zu der beim Aufblitzen gescrollt wird.
 *
 * Die Rechnung muss dieselbe Reihenfolge nachbilden, in der das Raster seine
 * Eintraege baut: Kopfzeilen, dann Favoriten (mit Ueberschrift, wenn es beide
 * Gruppen gibt), dann die uebrigen. Ein Fehler hier scrollt an die falsche
 * Karte, und die richtige blitzt ausserhalb des Bildes auf.
 */
class StelleVonTest {

    private fun notiz(id: String, favorit: Boolean = false) = NoteWithRelations(
        note = NoteEntity(id = id, isFavorite = favorit, createdAt = 0, updatedAt = 0, lastOpenedAt = 0, stageChangedAt = 0),
        items = emptyList(),
        tags = emptyList(),
        attachments = emptyList(),
        transcripts = emptyList(),
        reminders = emptyList(),
    )

    private val favoriten = listOf(notiz("f1", true), notiz("f2", true))
    private val uebrige = listOf(notiz("u1"), notiz("u2"), notiz("u3"))

    @Test
    fun `ohne Kopfzeilen und ohne Trennung zaehlt nur die Reihenfolge`() {
        assertEquals(0, stelleVon("u1", 0, false, emptyList(), uebrige))
        assertEquals(2, stelleVon("u3", 0, false, emptyList(), uebrige))
    }

    @Test
    fun `Kopfzeilen schieben alles nach hinten`() {
        assertEquals(3, stelleVon("u1", 3, false, emptyList(), uebrige))
    }

    @Test
    fun `mit beiden Gruppen zaehlen die Ueberschriften mit`() {
        // Kopfzeilen(1), "Favoriten", f1, f2, "Uebrige", u1, u2, u3
        assertEquals(2, stelleVon("f1", 1, true, favoriten, uebrige))
        assertEquals(3, stelleVon("f2", 1, true, favoriten, uebrige))
        assertEquals(5, stelleVon("u1", 1, true, favoriten, uebrige))
        assertEquals(7, stelleVon("u3", 1, true, favoriten, uebrige))
    }

    @Test
    fun `nur Favoriten ergeben keine Ueberschrift`() {
        assertEquals(1, stelleVon("f2", 0, false, favoriten, emptyList()))
    }

    @Test
    fun `eine Notiz, die nicht in der Liste ist, hat keine Stelle`() {
        assertNull(stelleVon("gibt-es-nicht", 1, true, favoriten, uebrige))
    }
}
