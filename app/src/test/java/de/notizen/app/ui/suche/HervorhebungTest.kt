package de.notizen.app.ui.suche

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import de.notizen.core.data.repository.TREFFER_AUF
import de.notizen.core.data.repository.TREFFER_ZU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Umwandlung des markierten Ausschnitts in formatierten Text.
 *
 * Der wichtigste Punkt ist der unauffälligste: **kein Steuerzeichen darf im
 * sichtbaren Text landen.** Es würde als leeres Kästchen erscheinen, und man
 * käme nie darauf, woher es kommt.
 */
class HervorhebungTest {

    private val stil = SpanStyle(color = Color.Red)

    private fun umgewandelt(roh: String) = hervorgehoben(roh, stil)

    @Test
    fun `die Markierungen verschwinden aus dem Text`() {
        val text = umgewandelt("Wir sprachen über den ${TREFFER_AUF}Bebauungsplan$TREFFER_ZU heute")

        assertEquals("Wir sprachen über den Bebauungsplan heute", text.text)
        assertFalse(text.text.contains(TREFFER_AUF))
        assertFalse(text.text.contains(TREFFER_ZU))
    }

    @Test
    fun `die Fundstelle bekommt genau ihren Bereich`() {
        val text = umgewandelt("aa ${TREFFER_AUF}bb$TREFFER_ZU cc")
        val bereich = text.spanStyles.single()

        assertEquals(3, bereich.start)
        assertEquals(5, bereich.end)
        assertEquals(stil, bereich.item)
    }

    @Test
    fun `mehrere Fundstellen werden einzeln hervorgehoben`() {
        val text = umgewandelt("${TREFFER_AUF}eins$TREFFER_ZU und ${TREFFER_AUF}zwei$TREFFER_ZU")
        assertEquals(2, text.spanStyles.size)
    }

    @Test
    fun `abgeschnittene Markierung hebt bis zum Ende hervor`() {
        // snippet() kuerzt auf eine feste Zahl Token. Faellt dabei das
        // schliessende Zeichen weg, darf weder Text verlorengehen noch ein
        // Steuerzeichen durchrutschen.
        val text = umgewandelt("Anfang ${TREFFER_AUF}Rest ohne Ende")

        assertEquals("Anfang Rest ohne Ende", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(text.text.length, text.spanStyles.single().end)
    }

    @Test
    fun `eine einzelne schliessende Markierung wird ignoriert`() {
        val text = umgewandelt("Text ${TREFFER_ZU}mit Schluss ohne Anfang")

        assertEquals("Text mit Schluss ohne Anfang", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }

    @Test
    fun `Text ohne Fundstelle bleibt unveraendert`() {
        val text = umgewandelt("Ganz gewöhnlicher Text")

        assertEquals("Ganz gewöhnlicher Text", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }
}
