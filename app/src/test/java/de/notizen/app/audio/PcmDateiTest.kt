package de.notizen.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Das strömende Lesen der Aufnahme.
 *
 * `leseVoll` und `ueberspringe` stehen für `readNBytes` und `skipNBytes`, die es
 * auf älteren Android-Fassungen nicht gibt. Geprüft wird deshalb der unbequeme
 * Fall: ein Strom, der je Aufruf nur ein Byte hergibt und nie etwas überspringt.
 */
class PcmDateiTest {

    @get:Rule
    val ordner = TemporaryFolder()

    /** Gibt je Aufruf höchstens ein Byte her, und `skip` überspringt nichts. */
    private class ZaeherStrom(daten: ByteArray) : InputStream() {
        private val innen = ByteArrayInputStream(daten)
        override fun read(): Int = innen.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            if (len == 0) 0 else innen.read(b, off, 1)
        override fun skip(n: Long): Long = 0
    }

    @Test
    fun `leseVoll fuellt den Puffer auch aus einem zaehen Strom`() {
        val puffer = ByteArray(5)
        val gelesen = ZaeherStrom(byteArrayOf(1, 2, 3, 4, 5, 6, 7)).leseVoll(puffer)

        assertEquals(5, gelesen)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), puffer)
    }

    @Test
    fun `leseVoll meldet am Ende des Stroms weniger`() {
        assertEquals(3, ZaeherStrom(byteArrayOf(1, 2, 3)).leseVoll(ByteArray(5)))
    }

    @Test
    fun `ueberspringe kommt auch ohne skip ans Ziel`() {
        val strom = ZaeherStrom(byteArrayOf(1, 2, 3, 4, 5))
        strom.ueberspringe(3)

        assertEquals(4, strom.read())
    }

    @Test(expected = EOFException::class)
    fun `ueberspringe ueber das Ende hinaus wirft`() {
        ZaeherStrom(byteArrayOf(1, 2)).ueberspringe(3)
    }

    @Test
    fun `pcmAbschnitt schneidet genau den Abschnitt heraus`() {
        // 16 kHz und 16 Bit sind 32 Bytes je Millisekunde. Jede Millisekunde
        // bekommt ihren eigenen Wert, damit ein Versatz auffällt.
        val datei = ordner.newFile("abschnitt.wav")
        val audio = ByteArray(10 * 32) { (it / 32).toByte() }
        FileOutputStream(datei).use { strom ->
            platzFuerKopf(strom)
            strom.write(audio)
        }

        val ziel = ByteArrayOutputStream()
        pcmAbschnitt(datei, Sprechabschnitt(startMs = 2, endeMs = 5), ziel)

        assertArrayEquals(audio.copyOfRange(2 * 32, 5 * 32), ziel.toByteArray())
    }

    @Test
    fun `pcmPegel liefert je vollem Rahmen einen Wert`() {
        // Ein Rahmen sind 20 ms, also 640 Bytes. Der halbe Rahmen am Ende zählt nicht.
        val datei = ordner.newFile("pegel.wav")
        FileOutputStream(datei).use { strom ->
            platzFuerKopf(strom)
            strom.write(ByteArray(640 * 2 + 320))
        }

        assertEquals(2, pcmPegel(datei).size)
    }
}
