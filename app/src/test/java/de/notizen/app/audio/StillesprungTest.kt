package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Das Überspringen der stillen Stellen beim Abspielen.
 *
 * Die Abschnitte sind dieselben, aus denen auch das Transkript entsteht — was
 * man hört, ist also genau das, was im Text steht.
 */
class StillesprungTest {

    private val abschnitte = listOf(
        Sprechabschnitt(2_000, 8_000),
        Sprechabschnitt(15_000, 20_000),
    )
    private val dauer = 30_000L

    @Test
    fun `mitten im Sprechen wird nicht gesprungen`() {
        assertNull(Stillesprung.naechsteStelle(abschnitte, 5_000, dauer))
    }

    @Test
    fun `am Anfang wird zur ersten Stelle gesprungen`() {
        // Die typische Aufnahme faengt mit dem Suchen des Knopfes an.
        assertEquals(2_000L, Stillesprung.naechsteStelle(abschnitte, 0, dauer))
    }

    @Test
    fun `in der Pause geht es zur naechsten Stelle`() {
        assertEquals(15_000L, Stillesprung.naechsteStelle(abschnitte, 10_000, dauer))
    }

    @Test
    fun `hinter der letzten Stelle geht es ans Ende`() {
        // Der Nachlauf am Schluss -- sonst hoert man ihn sich an, obwohl die
        // Aufnahme inhaltlich vorbei ist.
        assertEquals(dauer, Stillesprung.naechsteStelle(abschnitte, 25_000, dauer))
    }

    @Test
    fun `genau am Anfang eines Abschnitts wird nicht gesprungen`() {
        // Sonst spraenge es beim Erreichen jeder Stelle sofort weiter -- und
        // man hoerte nie den Anfang eines Satzes.
        assertNull(Stillesprung.naechsteStelle(abschnitte, 2_000, dauer))
    }

    @Test
    fun `ohne Abschnitte wird gar nicht gesprungen`() {
        // Gibt es keine Zerlegung, ist Ueberspringen nur Raten.
        assertNull(Stillesprung.naechsteStelle(emptyList(), 5_000, dauer))
    }

    @Test
    fun `die Ersparnis ist alles Ungesprochene`() {
        // 30 Sekunden gesamt, 11 davon gesprochen.
        assertEquals(19_000L, Stillesprung.ersparnisMs(abschnitte, dauer))
    }

    @Test
    fun `ein durchgehendes Diktat spart nichts`() {
        // Und dann soll der Schalter das auch sagen, statt etwas zu versprechen.
        val durchgehend = listOf(Sprechabschnitt(0, 30_000))
        assertEquals(0L, Stillesprung.ersparnisMs(durchgehend, dauer))
    }
}
