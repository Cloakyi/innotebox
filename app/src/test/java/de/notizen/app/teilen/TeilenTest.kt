package de.notizen.app.teilen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Welcher Text aus Geteiltem wird.
 *
 * Ein Browser schickt den Titel der Seite als Betreff und die Adresse als Text;
 * andere Apps schicken nur eins von beiden oder den Betreff noch einmal im Text.
 */
class TeilenTest {

    @Test
    fun `Betreff und Text stehen untereinander`() {
        assertEquals(
            "Rezept für Brot\n\nhttps://example.org/brot",
            geteilterText(betreff = "Rezept für Brot", text = "https://example.org/brot"),
        )
    }

    @Test
    fun `steht der Betreff schon im Text, kommt er nicht doppelt`() {
        assertEquals(
            "Rezept für Brot https://example.org/brot",
            geteilterText(betreff = "Rezept für Brot", text = "Rezept für Brot https://example.org/brot"),
        )
    }

    @Test
    fun `nur Text oder nur Betreff`() {
        assertEquals("Nur Text", geteilterText(betreff = null, text = "Nur Text"))
        assertEquals("Nur Betreff", geteilterText(betreff = "Nur Betreff", text = "  "))
    }

    @Test
    fun `Leerraum am Rand faellt weg, nichts bleibt leer`() {
        assertEquals("Mitte", geteilterText(betreff = "  ", text = "\n Mitte \n"))
        assertEquals("", geteilterText(betreff = null, text = null))
    }
}
