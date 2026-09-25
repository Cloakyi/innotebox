package de.notizen.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Gummiband-Widerstand der Wischgeste.
 *
 * Getestet wird nicht, ob es sich gut anfuehlt -- das entscheidet nur das
 * Geraet. Getestet werden die drei Eigenschaften, auf die sich die Geste
 * verlaesst: die Karte laeuft dem Finger nie voraus, sie bleibt in derselben
 * Richtung, und die Schwelle ist ueberhaupt erreichbar. Faellt eine davon weg,
 * ist die Geste entweder nicht ausloesbar oder springt.
 */
class WischWiderstandTest {

    private val schwelle = 250f

    @Test
    fun `die Karte laeuft dem Finger nie voraus`() {
        var weg = 1f
        while (weg < 4000f) {
            assertTrue(
                "Bei $weg px Fingerweg darf die Karte hoechstens ebenso weit gehen",
                gummi(weg, schwelle) <= weg + 0.001f,
            )
            weg *= 1.3f
        }
    }

    @Test
    fun `am Anfang folgt sie fast eins zu eins`() {
        // Ohne das fuehlt sich die Karte beim Antippen tot an.
        val kurz = 4f
        assertEquals(kurz.toDouble(), gummi(kurz, schwelle).toDouble(), 0.2)
    }

    @Test
    fun `die Schwelle liegt beim Anderthalbfachen des Fingerwegs`() {
        // Der Punkt, an dem das Band reisst: der Finger hat 1,5 mal so weit
        // gezogen, wie die Karte gewandert ist. Genau das macht die Geste
        // absichtlich und nicht versehentlich.
        assertEquals(schwelle.toDouble(), gummi(1.5f * schwelle, schwelle).toDouble(), 0.01)
    }

    @Test
    fun `kurz vor der Schwelle loest sie noch nicht aus`() {
        assertTrue(gummi(1.49f * schwelle, schwelle) < schwelle)
    }

    @Test
    fun `weiterziehen bewegt immer noch etwas`() {
        // Monoton steigend: eine Karte, die ab einem Punkt stehen bleibt,
        // sieht aus wie eine haengende App.
        var vorher = 0f
        var weg = 10f
        while (weg < 3000f) {
            val jetzt = gummi(weg, schwelle)
            assertTrue("bei $weg px", jetzt > vorher)
            vorher = jetzt
            weg += 10f
        }
    }

    @Test
    fun `langsam ueber die Schwelle reisst trotzdem`() {
        // Der entscheidende Fall: wer kriechend zieht, hat am Ende fast kein
        // Tempo -- aber die Spannung ist da und muss sich entladen. Ohne die
        // Untergrenze wuerde die Karte davonschleichen statt wegzuschnellen.
        val tempo = abfluggeschwindigkeit(gemessen = 40f, mindest = 5000f, nachRechts = true)
        assertEquals(5000.0, tempo.toDouble(), 0.001)
    }

    @Test
    fun `ein schneller Wisch behaelt sein eigenes Tempo`() {
        val tempo = abfluggeschwindigkeit(gemessen = 9000f, mindest = 5000f, nachRechts = true)
        assertEquals(9000.0, tempo.toDouble(), 0.001)
    }

    @Test
    fun `nach links fliegt sie nach links`() {
        // Der Betrag kommt aus der Messung, das Vorzeichen aus der Richtung.
        // Die gemessene Geschwindigkeit ist beim Ziehen nach links negativ --
        // wer beides multipliziert statt zu ersetzen, schickt die Karte in die
        // falsche Richtung.
        val tempo = abfluggeschwindigkeit(gemessen = -9000f, mindest = 5000f, nachRechts = false)
        assertEquals(-9000.0, tempo.toDouble(), 0.001)
    }

    @Test
    fun `ohne gemessene Kartenbreite passiert nichts`() {
        // Vor dem ersten Layout ist die Schwelle 0. Dann darf die Karte sich
        // nicht bewegen, statt auf einen erratenen Wert zu springen.
        assertEquals(0f, gummi(500f, 0f), 0f)
    }
}
