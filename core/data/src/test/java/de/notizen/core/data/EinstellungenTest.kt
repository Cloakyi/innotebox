package de.notizen.core.data

import de.notizen.core.data.model.Darstellung
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.ThemeWahl
import de.notizen.core.data.model.WischZiel
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.NoteSort
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.model.Sperrverzoegerung
import de.notizen.core.data.model.Uebersetzungsweg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Prueft die dauerhaften Einstellungen.
 *
 * Der Punkt ist die TRENNUNG NACH STUFE: der Eingang ist ein Ablagestapel, das
 * Archiv eine Nachschlagesammlung -- eine gemeinsame Einstellung waere die
 * falsche Vereinfachung. Vorher lebten diese Werte im ViewModel und gingen bei
 * jedem Stufenwechsel verloren.
 */
@RunWith(RobolectricTestRunner::class)
class EinstellungenTest {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var einstellungen: Einstellungen

    /**
     * Jeder Test bekommt eine eigene, leere Datei. Wuerde hier der
     * Context-Delegate benutzt, teilten sich alle Tests EINEN prozessweiten
     * Speicher -- der erste Test faerbte dann den naechsten ein.
     */
    @Before
    fun aufbauen() {
        val datei = dateiordner.newFile("test.preferences_pb")
        datei.delete()
        // Ein Siegel für die Tests: gültig ist, was mit "echt" gesiegelt ist.
        // Das echte Siegel (Android Keystore) prüft ZustimmungssiegelTest in :app.
        einstellungen = Einstellungen(
            PreferenceDataStoreFactory.create { datei },
            kiSiegel = { _, siegel -> siegel == "echt" },
        )
    }

    @Test
    fun `Standard ist Raster und Erstellungsdatum`() = runTest {
        assertEquals(Darstellung.RASTER, einstellungen.darstellung(Stage.INBOX).first())
        assertEquals(NoteSort.CREATED, einstellungen.sortierung(Stage.INBOX).first())
    }

    @Test
    fun `Darstellung bleibt erhalten`() = runTest {
        einstellungen.setDarstellung(Stage.INBOX, Darstellung.LISTE)
        assertEquals(Darstellung.LISTE, einstellungen.darstellung(Stage.INBOX).first())
    }

    @Test
    fun `jede Stufe hat ihre eigene Darstellung`() = runTest {
        einstellungen.setDarstellung(Stage.INBOX, Darstellung.LISTE)

        assertEquals(
            "der Eingang behaelt seine Wahl",
            Darstellung.LISTE,
            einstellungen.darstellung(Stage.INBOX).first(),
        )
        assertEquals(
            "der Workspace darf davon nichts mitbekommen",
            Darstellung.RASTER,
            einstellungen.darstellung(Stage.WORKSPACE).first(),
        )
        assertEquals(
            Darstellung.RASTER,
            einstellungen.darstellung(Stage.ARCHIVE).first(),
        )
    }

    @Test
    fun `jede Stufe hat ihre eigene Sortierung`() = runTest {
        einstellungen.setSortierung(Stage.ARCHIVE, NoteSort.LAST_OPENED)

        assertEquals(NoteSort.LAST_OPENED, einstellungen.sortierung(Stage.ARCHIVE).first())
        assertEquals(NoteSort.CREATED, einstellungen.sortierung(Stage.INBOX).first())
    }

    @Test
    fun `Theme steht standardmaessig auf System mit Dynamic Color`() = runTest {
        assertEquals(ThemeWahl.SYSTEM, einstellungen.themeWahl().first())
        assertEquals(
            "Dynamic Color ist der Standard",
            true,
            einstellungen.dynamicColor().first(),
        )
    }

    @Test
    fun `Theme-Wahl bleibt erhalten`() = runTest {
        einstellungen.setThemeWahl(ThemeWahl.DUNKEL)
        einstellungen.setDynamicColor(false)

        assertEquals(ThemeWahl.DUNKEL, einstellungen.themeWahl().first())
        assertEquals(false, einstellungen.dynamicColor().first())
    }

    @Test
    fun `Wischgesten folgen ab Werk dem Fluss`() = runTest {
        assertEquals(WischZiel.NAECHSTE_STUFE, einstellungen.wischRechts().first())
        assertEquals(WischZiel.VORHERIGE_STUFE, einstellungen.wischLinks().first())
    }

    @Test
    fun `Wischgesten sind je Richtung getrennt einstellbar`() = runTest {
        einstellungen.setWischLinks(WischZiel.PAPIERKORB)

        assertEquals(WischZiel.PAPIERKORB, einstellungen.wischLinks().first())
        assertEquals(
            "die andere Richtung darf davon nichts mitbekommen",
            WischZiel.NAECHSTE_STUFE,
            einstellungen.wischRechts().first(),
        )
    }

    @Test
    fun `eine Geste ins Leere wird gar nicht erst angeboten`() {
        // Am Anfang des Flusses gibt es kein Zurueck, am Ende kein Vorwaerts.
        assertEquals(false, WischZiel.VORHERIGE_STUFE.wirktIn(Stage.INBOX))
        assertEquals(true, WischZiel.NAECHSTE_STUFE.wirktIn(Stage.INBOX))
        assertEquals(false, WischZiel.NAECHSTE_STUFE.wirktIn(Stage.ARCHIVE))
        assertEquals(true, WischZiel.VORHERIGE_STUFE.wirktIn(Stage.ARCHIVE))

        // Der Papierkorb geht ueberall, "nichts" nirgends.
        Stage.entries.forEach {
            assertEquals(true, WischZiel.PAPIERKORB.wirktIn(it))
            assertEquals(false, WischZiel.NICHTS.wirktIn(it))
        }
    }

    @Test
    fun `Umschalten wechselt hin und zurueck`() {
        assertEquals(Darstellung.LISTE, Darstellung.RASTER.umgeschaltet())
        assertEquals(Darstellung.RASTER, Darstellung.LISTE.umgeschaltet())
    }

    // ------------------------------------------------------ Sprache und KI

    @Test
    fun `voreingestellt wird auf Deutsch erkannt`() = runTest {
        // Die App ist eine deutschsprachige. Alles andere waere eine Annahme
        // ueber den Nutzer, die niemand getroffen hat.
        assertEquals(Transkriptsprache.DEUTSCH, einstellungen.transkriptsprache().first())
    }

    @Test
    fun `die Sprache bleibt gespeichert`() = runTest {
        einstellungen.setTranskriptsprache(Transkriptsprache.ENGLISCH_GB)
        assertEquals(Transkriptsprache.ENGLISCH_GB, einstellungen.transkriptsprache().first())
    }

    @Test
    fun `jede Sprache traegt einen brauchbaren Locale`() = runTest {
        // Die Erkennung bekommt diesen Locale direkt. Ein leeres Land oder eine
        // leere Sprache wuerde dort erst auffallen, wenn nichts erkannt wird.
        Transkriptsprache.entries.forEach { sprache ->
            val locale = sprache.alsLocale()
            assertTrue(sprache.name, locale.language.length == 2)
            assertTrue(sprache.name, locale.country.length == 2)
        }
    }

    @Test
    fun `ein unbekannter gespeicherter Wert faellt auf Deutsch zurueck`() = runTest {
        // Etwa nach einem Rueckschritt auf eine aeltere Fassung. Ein Absturz
        // beim Start waere die schlechteste Antwort darauf.
        assertEquals(Transkriptsprache.DEUTSCH, Transkriptsprache.ausName("KLINGONISCH"))
        assertEquals(Transkriptsprache.DEUTSCH, Transkriptsprache.ausName(null))
    }

    @Test
    fun `ohne Zustimmung ist die KI aus und die Frage offen`() = runTest {
        // Seit Alpha 9: ML Kit meldet Kennzahlen an Google, also erst fragen.
        assertFalse(einstellungen.kiAktiv().first())
        assertTrue(einstellungen.kiFrageOffen().first())
    }

    @Test
    fun `mit gueltig versiegelter Zustimmung ist die KI an und die Frage erledigt`() = runTest {
        einstellungen.kiEinschalten("aufzeichnung", "echt")
        assertTrue(einstellungen.kiAktiv().first())
        assertFalse(einstellungen.kiFrageOffen().first())
        assertEquals("aufzeichnung", einstellungen.kiZustimmung().first())
    }

    @Test
    fun `ein Siegel, das nicht passt, zaehlt nicht und die App fragt neu`() = runTest {
        // Verändert, kopiert oder erfunden: Die KI bleibt aus, die Frage kommt wieder.
        einstellungen.kiEinschalten("aufzeichnung", "gefaelscht")
        assertFalse(einstellungen.kiAktiv().first())
        assertTrue(einstellungen.kiFrageOffen().first())
    }

    @Test
    fun `Ausschalten loescht die Zustimmung restlos und fragt nicht wieder`() = runTest {
        einstellungen.kiEinschalten("aufzeichnung", "echt")
        einstellungen.kiAusschalten()
        assertFalse(einstellungen.kiAktiv().first())
        assertFalse(einstellungen.kiFrageOffen().first())
        assertNull(einstellungen.kiZustimmung().first())

        // Wer wieder einschaltet, stimmt neu zu; ohne das bleibt sie aus.
        einstellungen.kiEinschalten("neue aufzeichnung", "echt")
        assertTrue(einstellungen.kiAktiv().first())
    }

    @Test
    fun `Sprache und KI-Schalter sind voneinander unabhaengig`() = runTest {
        // Ausdruecklich getrennt: Die Spracherkennung ist keine
        // Textgenerierung. Wer die KI-Aufbereitung abschaltet, will keine
        // erfundenen Titel -- ein Transkript will er trotzdem.
        einstellungen.kiAusschalten()
        einstellungen.setTranskriptsprache(Transkriptsprache.FRANZOESISCH)

        assertFalse(einstellungen.kiAktiv().first())
        assertEquals(Transkriptsprache.FRANZOESISCH, einstellungen.transkriptsprache().first())
    }

    @Test
    fun `das Netz ist voreingestellt verboten und merkt sich die Zustimmung`() = runTest {
        // Ohne Opt-in geht nichts ins Netz, auch kein Sprachpaket.
        assertFalse(einstellungen.netzErlaubt().first())
        assertEquals(0L, einstellungen.netzZugestimmtAm().first())

        einstellungen.setNetzErlaubt(true, wann = 1_700_000_000_000L)
        assertTrue(einstellungen.netzErlaubt().first())
        assertEquals(1_700_000_000_000L, einstellungen.netzZugestimmtAm().first())

        // Abschalten loescht den Zeitpunkt nicht: Er belegt, dass einmal
        // zugestimmt wurde.
        einstellungen.setNetzErlaubt(false, wann = 0L)
        assertFalse(einstellungen.netzErlaubt().first())
        assertEquals(1_700_000_000_000L, einstellungen.netzZugestimmtAm().first())
    }

    @Test
    fun `die Sperre ist voreingestellt aus und merkt sich Verzoegerung und Aufnahmeschutz`() = runTest {
        // Niemand wird ausgesperrt, der es nicht eingeschaltet hat.
        assertFalse(einstellungen.sperreAn().first())
        assertFalse(einstellungen.aufnahmeschutzAn().first())
        assertEquals(Sperrverzoegerung.EINE_MINUTE, einstellungen.sperrverzoegerung().first())

        einstellungen.setSperreAn(true)
        einstellungen.setSperrverzoegerung(Sperrverzoegerung.SOFORT)
        einstellungen.setAufnahmeschutzAn(true)

        assertTrue(einstellungen.sperreAn().first())
        assertEquals(Sperrverzoegerung.SOFORT, einstellungen.sperrverzoegerung().first())
        assertTrue(einstellungen.aufnahmeschutzAn().first())
    }

    @Test
    fun `der Uebersetzungsweg und die Sprachen bleiben gespeichert`() = runTest {
        assertEquals(Uebersetzungsweg.SYSTEM, einstellungen.uebersetzungsweg().first())
        assertEquals("de" to "en", einstellungen.uebersetzungSprachen().first())

        einstellungen.setUebersetzungsweg(Uebersetzungsweg.MLKIT)
        einstellungen.setUebersetzungSprachen("en", "fr")

        assertEquals(Uebersetzungsweg.MLKIT, einstellungen.uebersetzungsweg().first())
        assertEquals("en" to "fr", einstellungen.uebersetzungSprachen().first())
    }
}
