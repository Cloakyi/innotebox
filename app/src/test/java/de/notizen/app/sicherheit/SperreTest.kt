package de.notizen.app.sicherheit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.notizen.core.data.model.Sperrverzoegerung
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Wann die App sich sperrt (Phase 19).
 *
 * Die Regeln sind klein, aber jede davon ist am Geraet aergerlich, wenn sie
 * falsch ist: eine Sperre bei jeder Drehung, eine Sperre, die nach dem Start
 * fehlt, eine Sperre, die bei „Sofort" nie kommt.
 */
class SperreTest {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var einstellungen: Einstellungen
    private var jetzt = 1_000_000L
    private val uhr = Clock { jetzt }
    private lateinit var sperre: Sperre

    @Before
    fun aufbauen() {
        val datei = dateiordner.newFile("test.preferences_pb")
        datei.delete()
        einstellungen = Einstellungen(PreferenceDataStoreFactory.create { datei })
        sperre = Sperre(einstellungen, uhr)
    }

    @Test
    fun `ohne Sperre passiert nichts`() = runBlocking {
        sperre.beimStart()
        assertFalse(sperre.gesperrt.value)
        sperre.beimStopp(konfigurationswechsel = false)
        jetzt += 60 * 60_000L
        sperre.beimStart()
        assertFalse(sperre.gesperrt.value)
    }

    @Test
    fun `mit Sperre ist der erste Start gesperrt`() = runBlocking {
        einstellungen.setSperreAn(true)
        sperre.beimStart()
        assertTrue(sperre.gesperrt.value)
        sperre.entsperrt()
        assertFalse(sperre.gesperrt.value)
    }

    @Test
    fun `eine Drehung sperrt nicht`() = runBlocking {
        einstellungen.setSperreAn(true)
        einstellungen.setSperrverzoegerung(Sperrverzoegerung.SOFORT)
        sperre.beimStart()
        sperre.entsperrt()

        sperre.beimStopp(konfigurationswechsel = true)
        jetzt += 5_000
        sperre.beimStart()

        assertFalse(sperre.gesperrt.value)
    }

    @Test
    fun `Sofort sperrt beim naechsten Oeffnen aus dem Hintergrund`() = runBlocking {
        einstellungen.setSperreAn(true)
        einstellungen.setSperrverzoegerung(Sperrverzoegerung.SOFORT)
        sperre.beimStart()
        sperre.entsperrt()

        sperre.beimStopp(konfigurationswechsel = false)
        jetzt += 1
        sperre.beimStart()

        assertTrue(sperre.gesperrt.value)
    }

    @Test
    fun `eine Minute Verzoegerung laesst kurze Wechsel durch`() = runBlocking {
        einstellungen.setSperreAn(true)
        einstellungen.setSperrverzoegerung(Sperrverzoegerung.EINE_MINUTE)
        sperre.beimStart()
        sperre.entsperrt()

        sperre.beimStopp(konfigurationswechsel = false)
        jetzt += 30_000
        sperre.beimStart()
        assertFalse("Nach dreissig Sekunden noch offen", sperre.gesperrt.value)

        sperre.beimStopp(konfigurationswechsel = false)
        jetzt += 61_000
        sperre.beimStart()
        assertTrue("Nach einer Minute gesperrt", sperre.gesperrt.value)
    }

    @Test
    fun `das Einschalten sperrt nicht sofort, der Nutzer hat sich gerade ausgewiesen`() = runBlocking {
        sperre.beimStart()
        einstellungen.setSperreAn(true)
        sperre.eingeschaltet()
        assertFalse(sperre.gesperrt.value)

        // Aber ab jetzt gilt sie.
        sperre.beimStopp(konfigurationswechsel = false)
        jetzt += 2 * 60_000L
        sperre.beimStart()
        assertTrue(sperre.gesperrt.value)
    }
}
