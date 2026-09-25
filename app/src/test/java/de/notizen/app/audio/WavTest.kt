package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * Der WAV-Kopf der Rohaufnahme.
 *
 * Prüfbar ohne Gerät, und das ist gut so: Der Kopf entscheidet darüber, ob die
 * Aufnahme sich außerhalb dieser App überhaupt öffnen lässt. Ein Fehler darin
 * fällt sonst erst auf, wenn jemand eine Datei exportiert -- also spät.
 */
class WavTest {

    @get:Rule
    val ordner = TemporaryFolder()

    /** Schreibt eine Datei so, wie der Mitschnitt es tut: Platz, Daten, Kopf. */
    private fun aufnahme(audioBytes: Int): File {
        val datei = ordner.newFile("test.wav")
        FileOutputStream(datei).use { strom ->
            platzFuerKopf(strom)
            strom.write(ByteArray(audioBytes) { 0x11 })
        }
        kopfNachtragen(datei, audioBytes.toLong())
        return datei
    }

    private fun ByteArray.text(von: Int, laenge: Int) = String(this, von, laenge)

    private fun ByteArray.zahl32(von: Int): Int =
        (this[von].toInt() and 0xFF) or
            ((this[von + 1].toInt() and 0xFF) shl 8) or
            ((this[von + 2].toInt() and 0xFF) shl 16) or
            ((this[von + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.zahl16(von: Int): Int =
        (this[von].toInt() and 0xFF) or ((this[von + 1].toInt() and 0xFF) shl 8)

    @Test
    fun `die Datei traegt die Kennungen eines WAV`() {
        val bytes = aufnahme(1000).readBytes()

        assertEquals("RIFF", bytes.text(0, 4))
        assertEquals("WAVE", bytes.text(8, 4))
        assertEquals("fmt ", bytes.text(12, 4))
        assertEquals("data", bytes.text(36, 4))
    }

    @Test
    fun `die Laengen im Kopf stimmen mit der Datei ueberein`() {
        // Der haeufigste WAV-Fehler: eine Datei, die abgespielt wird, aber nach
        // Sekunden abbricht, weil im Kopf die falsche Laenge steht.
        val audioBytes = 32_000
        val datei = aufnahme(audioBytes)
        val bytes = datei.readBytes()

        assertEquals("data-Laenge", audioBytes, bytes.zahl32(40))
        assertEquals("RIFF-Laenge", datei.length().toInt() - 8, bytes.zahl32(4))
    }

    @Test
    fun `das Format ist das, in dem aufgenommen wird`() {
        // Steht im Kopf etwas anderes, als der Mitschnitt schreibt, klingt die
        // Aufnahme zu schnell oder zu langsam -- und niemand kaeme darauf, dass
        // die Daten in Ordnung sind.
        val bytes = aufnahme(1000).readBytes()

        assertEquals("PCM unkomprimiert", 1, bytes.zahl16(20))
        assertEquals("Mono", 1, bytes.zahl16(22))
        assertEquals("Abtastrate", ABTASTRATE, bytes.zahl32(24))
        assertEquals("16 Bit", 16, bytes.zahl16(34))
        assertEquals("Byte-Rate", ABTASTRATE * 2, bytes.zahl32(28))
        assertEquals("Blockausrichtung", 2, bytes.zahl16(32))
    }

    @Test
    fun `eine leere Aufnahme hinterlaesst keine Datei`() {
        // Eine 44 Byte grosse Audiodatei, die sich oeffnen laesst und nichts
        // enthaelt, ist irrefuehrender als gar keine.
        val datei = ordner.newFile("leer.wav")
        FileOutputStream(datei).use { platzFuerKopf(it) }

        kopfNachtragen(datei, 0)

        assertFalse("Sie muss weg sein", datei.exists())
    }

    @Test
    fun `eine Sekunde Audio ergibt die erwartete Dateigroesse`() {
        // Kontrollrechnung fuer die Daueranzeige: 16 kHz mal 2 Byte.
        val eineSekunde = ABTASTRATE * 2
        val datei = aufnahme(eineSekunde)

        assertEquals(eineSekunde + 44L, datei.length())
        assertTrue(datei.length() > 0)
    }
}
