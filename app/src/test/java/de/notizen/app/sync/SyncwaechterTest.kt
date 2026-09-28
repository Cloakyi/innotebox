package de.notizen.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wann die Zahl der ungesicherten Einträge einen Abgleich anstößt.
 *
 * Nur eine steigende Zahl ist eine Änderung. Eine sinkende ist der Abgleich
 * selbst beim Hochladen; plante sie einen Lauf, bestellte der den laufenden ab,
 * und jede Notiz ginge in einem eigenen Lauf hoch.
 */
class SyncwaechterTest {

    @Test
    fun `der erste Wert ist der Bestand beim Start und kein Anlass`() {
        assertFalse(neuerAnlass(vorher = null, jetzt = 5))
    }

    @Test
    fun `eine steigende Zahl ist ein Anlass`() {
        assertTrue(neuerAnlass(vorher = 0, jetzt = 1))
        assertTrue(neuerAnlass(vorher = 3, jetzt = 4))
    }

    @Test
    fun `eine sinkende Zahl ist der laufende Abgleich und kein Anlass`() {
        assertFalse(neuerAnlass(vorher = 5, jetzt = 4))
        assertFalse(neuerAnlass(vorher = 1, jetzt = 0))
    }

    @Test
    fun `eine gleiche Zahl ist kein Anlass`() {
        assertFalse(neuerAnlass(vorher = 2, jetzt = 2))
    }
}
