package de.notizen.core.data

import de.notizen.core.data.search.Suchausdruck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Der Übersetzer von Tippeingabe nach FTS4-MATCH.
 *
 * Läuft ohne Datenbank -- geprüft wird die Zeichenkette, nicht das Ergebnis.
 * Dass die erzeugte Zeichenkette auch tut, was sie soll, prüft
 * `VolltextsucheTest` am echten SQLite.
 */
class SuchausdruckTest {

    @Test
    fun `ein Wort wird zur Praefixsuche`() {
        assertEquals("\"Tomaten*\"", Suchausdruck.bauen("Tomaten"))
    }

    @Test
    fun `mehrere Woerter werden mit Leerzeichen verbunden`() {
        // Leerzeichen und NICHT das Wort AND: in der Standard-Abfragesyntax von
        // FTS3/4 ist das Leerzeichen bereits das Und, waehrend "AND" dort ein
        // ganz normaler Suchbegriff waere.
        assertEquals("\"Tomaten*\" \"Basilikum*\"", Suchausdruck.bauen("Tomaten Basilikum"))
    }

    @Test
    fun `Bindestriche trennen Woerter statt auszuschliessen`() {
        // Der teuerste Einzelfall: FTS4 liest ein fuehrendes Minus als NOT.
        // Wer "Meier-Schmidt" durchreicht, sucht nach Meier OHNE Schmidt --
        // also nach dem Gegenteil dessen, was der Nutzer wollte.
        assertEquals("\"Meier*\" \"Schmidt*\"", Suchausdruck.bauen("Meier-Schmidt"))
    }

    @Test
    fun `Anfuehrungszeichen brechen die Abfrage nicht`() {
        // Ein einzelnes Anfuehrungszeichen mitten im Tippen wuerde als
        // unbeendete Phrase einen SQL-Fehler ausloesen.
        assertEquals("\"Zitat*\"", Suchausdruck.bauen("\"Zitat"))
    }

    @Test
    fun `Sternchen aus der Eingabe verdoppeln sich nicht`() {
        assertEquals("\"foo*\"", Suchausdruck.bauen("foo*"))
    }

    @Test
    fun `Operatorwoerter bleiben Suchbegriffe`() {
        // In Anfuehrungszeichen gesetzt, sonst waeren OR und NEAR Operatoren
        // und die Abfrage etwas voellig anderes.
        assertEquals("\"OR*\" \"NEAR*\"", Suchausdruck.bauen("OR NEAR"))
    }

    @Test
    fun `Umlaute bleiben erhalten`() {
        // Die Faltung macht der unicode61-Tokenizer in SQLite. Hier darf nichts
        // verlorengehen, sonst kommt die falsche Anfrage dort an.
        assertEquals("\"Küche*\"", Suchausdruck.bauen("Küche"))
    }

    @Test
    fun `leere Eingabe ergibt keine Abfrage`() {
        // null heisst "gar nicht gesucht", nicht "nichts gefunden". Daran
        // haengt, ob die Oberflaeche alles zeigt oder einen leeren Bereich.
        assertNull(Suchausdruck.bauen(""))
        assertNull(Suchausdruck.bauen("   "))
    }

    @Test
    fun `Eingabe nur aus Satzzeichen ergibt keine Abfrage`() {
        assertNull(Suchausdruck.bauen("--- ??? \"\""))
    }

    @Test
    fun `die hervorzuhebenden Woerter sind dieselben wie die gesuchten`() {
        // Sonst leuchtet in der Trefferliste eine andere Stelle als die, die
        // den Treffer verursacht hat.
        assertEquals(listOf("Meier", "Schmidt"), Suchausdruck.woerter("Meier-Schmidt"))
    }
}
