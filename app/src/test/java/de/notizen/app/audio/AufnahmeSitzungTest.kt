package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Buchführung während Aufnahme und Transkription.
 *
 * Ohne Mikrofon und ohne Modell prüfbar. Getestet wird die Fassade, über die
 * Dienst und Oberfläche miteinander reden -- geht die kaputt, sieht man am
 * Gerät eine Aufnahme, die nicht läuft, oder einen Fortschritt, der steht.
 */
class AufnahmeSitzungTest {

    private val sitzung = AufnahmeSitzung()

    @Test
    fun `waehrend der Aufnahme gibt es keinen Text`() {
        // Der Kern des Entwurfs: aufgenommen wird erst, erkannt spaeter. Wer
        // hier einen Live-Text erwartet, hat den Umbau nicht mitbekommen.
        sitzung.beginnen("n1")
        sitzung.fortschritt(dauerMs = 3_000, pegel = 0.4f, hatTon = true)

        assertTrue(sitzung.aufnahme.value.laeuft)
        assertEquals(3_000, sitzung.aufnahme.value.dauerMs)
        assertTrue(sitzung.transkript.value.teile.isEmpty())
    }

    @Test
    fun `Stille wird als solche gemeldet`() {
        // Ohne diese Angabe saehe eine Aufnahme mit stummem Mikrofon genauso
        // aus wie eine gelungene -- man merkte es erst, wenn hinterher kein
        // Transkript herauskommt, und suchte an der falschen Stelle.
        sitzung.beginnen("n1")
        sitzung.fortschritt(dauerMs = 5_000, pegel = 0f, hatTon = false)

        assertEquals(false, sitzung.aufnahme.value.hatTon)
    }

    @Test
    fun `beenden laesst die Dauer stehen`() {
        // Sie wird danach noch angezeigt: "zuletzt 2:14".
        sitzung.beginnen("n1")
        sitzung.fortschritt(dauerMs = 134_000, pegel = 0.3f, hatTon = true)
        sitzung.beenden()

        assertEquals(false, sitzung.aufnahme.value.laeuft)
        assertEquals(134_000, sitzung.aufnahme.value.dauerMs)
        assertEquals("Der Ausschlag geht auf null", 0f, sitzung.aufnahme.value.pegel)
    }

    @Test
    fun `der Fortschritt ist ein echter Anteil`() {
        // Die Zahl der Abschnitte steht nach dem Zerlegen fest -- deshalb darf
        // ein echter Balken gezeigt werden und kein unbestimmter Kringel.
        sitzung.transkriptBeginnen(Erkennungsmodus.ADVANCED)
        sitzung.zerlegt(4)
        sitzung.transkriptFortschritt(1, 4)

        assertEquals(0.25f, sitzung.transkript.value.anteil)
    }

    @Test
    fun `ohne Abschnitte gibt es keinen Anteil statt einer Division durch null`() {
        sitzung.transkriptBeginnen(Erkennungsmodus.BASIC)
        assertEquals(0f, sitzung.transkript.value.anteil)
    }

    @Test
    fun `die Teile werden nach ihrer Lage in der Aufnahme sortiert`() {
        // Sie tragen ihre Zeitstempel aus dem Schnitt. Kaemen sie in falscher
        // Reihenfolge an, staende der Text durcheinander -- und man wuesste
        // nicht, warum.
        sitzung.transkriptBeginnen(Erkennungsmodus.ADVANCED)
        sitzung.teil(Transkriptteil("zweiter", 5_000, 8_000))
        sitzung.teil(Transkriptteil("erster", 0, 3_000))

        assertEquals("erster zweiter", sitzung.transkript.value.text)
    }

    @Test
    fun `ein neues Transkript beginnt bei null`() {
        // Sonst haenge der zweite Durchgang an den Ergebnissen des ersten, und
        // der Text stuende doppelt da.
        sitzung.transkriptBeginnen(Erkennungsmodus.ADVANCED)
        sitzung.teil(Transkriptteil("alt", 0, 1_000))
        sitzung.transkriptFertig()

        sitzung.transkriptBeginnen(Erkennungsmodus.ADVANCED)

        assertTrue(sitzung.transkript.value.teile.isEmpty())
        assertTrue(sitzung.transkript.value.laeuft)
    }

    @Test
    fun `Aufnahme und Transkript stoeren einander nicht`() {
        // Zwei getrennte Zustaende, weil es zwei getrennte Vorgaenge sind.
        sitzung.beginnen("n1")
        sitzung.transkriptBeginnen(Erkennungsmodus.BASIC)
        sitzung.transkriptFertig()

        assertTrue("Die Aufnahme laeuft weiter", sitzung.aufnahme.value.laeuft)
    }
}
