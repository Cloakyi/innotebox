package de.notizen.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Fallback-Titel ist der Vorschlag, der IMMER sofort dasteht -- ohne KI,
 * ohne Warten, ohne Fehlerfall. Genau deshalb muss er verlaesslich sein: er
 * traegt die Titelpflicht auch dann, wenn Gemini Nano nicht antwortet.
 *
 * Reiner JUnit-Test, kein Robolectric noetig -- die Funktion hat keinerlei
 * Android-Abhaengigkeiten.
 */
class FallbackTitelTest {

    @Test
    fun `nimmt die erste nicht leere Zeile`() {
        assertEquals("Zahnarzt anrufen", fallbackTitel("Zahnarzt anrufen\nwegen Termin"))
    }

    @Test
    fun `ueberspringt fuehrende Leerzeilen`() {
        assertEquals("Der Inhalt", fallbackTitel("\n\n   \nDer Inhalt\nnoch mehr"))
    }

    @Test
    fun `faellt auf Checklisteneintraege zurueck wenn der Body leer ist`() {
        assertEquals("Milch", fallbackTitel("", listOf("", "Milch", "Brot")))
    }

    @Test
    fun `ohne jeden Inhalt bleibt der Titel leer`() {
        assertEquals(
            "leer heisst leer -- der Aufrufer soll die Notiz verwerfen koennen",
            "",
            fallbackTitel("   \n\n", emptyList()),
        )
    }

    @Test
    fun `kuerzt lange Zeilen an der Wortgrenze`() {
        val lang = "Dies ist eine ausgesprochen lange erste Zeile die deutlich " +
            "ueber die erlaubte Laenge hinausgeht"
        val titel = fallbackTitel(lang)

        assertTrue("muss gekuerzt werden", titel.endsWith("..."))
        assertTrue("bleibt in der Naehe der Grenze", titel.length <= 53)
        assertTrue(
            "darf kein Wort mitten durchschneiden",
            lang.startsWith(titel.removeSuffix("...")),
        )
        assertFalse("kein Leerzeichen vor den Punkten", titel.contains(" ..."))
    }

    @Test
    fun `kurze Zeilen bleiben unveraendert`() {
        assertEquals("Kurz", fallbackTitel("Kurz"))
    }

    @Test
    fun `schneidet umgebende Leerzeichen weg`() {
        assertEquals("Mit Rand", fallbackTitel("   Mit Rand   \nzweite Zeile"))
    }
}
