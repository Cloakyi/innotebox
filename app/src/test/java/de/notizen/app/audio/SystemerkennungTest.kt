package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Wie die Antwort des Erkennungsdienstes eingeordnet wird.
 *
 * Die Dienste schreiben Sprachen verschieden: `de-DE`, `de_DE`, `de`, mal groß,
 * mal klein. Eine Zeichenkette zu vergleichen hieße, auf manchen Geräten ein
 * vorhandenes Sprachpaket für fehlend zu halten.
 */
class SystemerkennungTest {

    private val deutsch = Locale.Builder().setLanguage("de").setRegion("DE").build()

    private fun paket(
        installiert: List<String> = emptyList(),
        ausstehend: List<String> = emptyList(),
        moeglich: List<String> = emptyList(),
    ) = sprachpaketAus(installiert, ausstehend, moeglich, deutsch)

    @Test
    fun `installiert schlaegt alles andere`() {
        assertEquals(Sprachpaket.INSTALLIERT, paket(installiert = listOf("de-DE"), moeglich = listOf("de-DE")))
    }

    @Test
    fun `Schreibweisen der Dienste werden erkannt`() {
        listOf("de-DE", "de_DE", "DE-de", "de").forEach {
            assertEquals(it, Sprachpaket.INSTALLIERT, paket(installiert = listOf(it)))
        }
    }

    @Test
    fun `ein anderes Land derselben Sprache zaehlt nicht`() {
        assertEquals(Sprachpaket.NICHT, paket(installiert = listOf("de-AT")))
    }

    @Test
    fun `ausstehend heisst es laedt schon`() {
        assertEquals(Sprachpaket.LAEDT, paket(ausstehend = listOf("de-DE"), moeglich = listOf("de-DE")))
    }

    @Test
    fun `nur moeglich heisst ladbar`() {
        assertEquals(Sprachpaket.LADBAR, paket(moeglich = listOf("en-US", "de-DE")))
    }

    @Test
    fun `nichts davon heisst nicht da`() {
        assertEquals(Sprachpaket.NICHT, paket(installiert = listOf("en-US")))
    }
}
