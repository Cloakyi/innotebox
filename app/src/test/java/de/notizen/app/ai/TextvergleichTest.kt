package de.notizen.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Vergleich zwischen Rohtranskript und bearbeiteter Fassung.
 *
 * **Warum das mehr ist als Kosmetik:** Ein Sprachmodell, das ein Diktat
 * „aufräumt", schreibt gelegentlich etwas hinein, das nie gesagt wurde. In
 * einer Notiz-App ist das der teuerste denkbare Fehler, weil man dem eigenen
 * Text später glaubt. Die Markierung macht genau diese Stellen sichtbar — also
 * muss sie stimmen.
 */
class TextvergleichTest {

    private fun neuerText(original: String, bearbeitet: String): String =
        Textvergleich.vergleiche(original, bearbeitet)
            .filter { it.neu }
            .joinToString("") { it.text }
            .trim()

    private fun ganzerText(original: String, bearbeitet: String): String =
        Textvergleich.vergleiche(original, bearbeitet).joinToString("") { it.text }

    @Test
    fun `unveraenderter Text ist nirgends markiert`() {
        val text = "Der Termin ist am Montag"
        assertTrue(Textvergleich.vergleiche(text, text).none { it.neu })
    }

    @Test
    fun `ein eingefuegtes Wort wird markiert`() {
        assertEquals(
            "nächsten",
            neuerText("Der Termin ist am Montag", "Der Termin ist am nächsten Montag"),
        )
    }

    @Test
    fun `ein hinzuerfundener Satz faellt auf`() {
        // DER Fall, um den es geht. Erfindet die Aufbereitung etwas dazu, muss
        // man es sehen koennen.
        val neu = neuerText(
            "Wir treffen uns morgen",
            "Wir treffen uns morgen. Anschließend gehen wir essen.",
        )
        assertTrue("Der erfundene Teil muss markiert sein", neu.contains("essen"))
        assertFalse("Das Gesagte nicht", neu.contains("treffen"))
    }

    @Test
    fun `neu gesetzte Satzzeichen leuchten nicht auf`() {
        // Die Aufbereitung setzt fast immer neue Kommas und Punkte. Ein Text,
        // in dem jedes Satzende markiert ist, sagt nichts mehr aus.
        val teile = Textvergleich.vergleiche(
            "also der termin ist am montag",
            "Also, der Termin ist am Montag.",
        )
        assertTrue("Nur Zeichensetzung geaendert", teile.none { it.neu })
    }

    @Test
    fun `Gestrichenes taucht nicht auf`() {
        // Die bearbeitete Fassung soll lesbar bleiben. Wer das Gestrichene
        // sucht, schaltet auf Original um.
        assertEquals(
            "Der Termin ist am Montag",
            ganzerText("Der ähm Termin ist ähm am Montag", "Der Termin ist am Montag").trim(),
        )
    }

    @Test
    fun `der ganze Text kommt vollstaendig wieder heraus`() {
        // Wenn beim Zusammensetzen etwas verlorenginge, faehlte es auf dem
        // Bildschirm -- und niemand kaeme auf die Markierung als Ursache.
        val bearbeitet = "Ein ganz anderer Text mit vielen Wörtern darin"
        assertEquals(bearbeitet, ganzerText("Kurzes Original", bearbeitet))
    }

    @Test
    fun `ohne Original gilt alles als neu`() {
        val teile = Textvergleich.vergleiche("", "Frisch geschrieben")
        assertTrue(teile.all { it.neu })
    }

    @Test
    fun `ohne bearbeitete Fassung gibt es nichts zu zeigen`() {
        assertTrue(Textvergleich.vergleiche("Da war mal was", "").isEmpty())
    }

    @Test
    fun `gleichartige Stuecke werden zusammengezogen`() {
        // Sonst waeren es tausende winziger Abschnitte, und das Zeichnen wuerde
        // spuerbar langsam.
        val teile = Textvergleich.vergleiche(
            "eins zwei drei vier fünf",
            "eins zwei drei vier fünf",
        )
        assertEquals(1, teile.size)
    }

    @Test
    fun `sehr lange Texte brechen nicht ein`() {
        // Ueber der Grenze wird grob verglichen. Das Ergebnis muss trotzdem
        // vollstaendig und in vertretbarer Zeit kommen.
        val original = (1..3000).joinToString(" ") { "wort$it" }
        val bearbeitet = original.replace("wort1500", "geändert")

        val beginn = System.currentTimeMillis()
        val teile = Textvergleich.vergleiche(original, bearbeitet)
        val dauer = System.currentTimeMillis() - beginn

        assertEquals(bearbeitet, teile.joinToString("") { it.text })
        assertTrue("Zu langsam: $dauer ms", dauer < 2_000)
        assertTrue("Die Aenderung muss auffallen", teile.any { it.neu })
    }
}
