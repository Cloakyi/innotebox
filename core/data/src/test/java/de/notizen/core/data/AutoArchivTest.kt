package de.notizen.core.data

import de.notizen.core.data.model.ArchiveTrigger
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.AutoArchiv
import de.notizen.core.data.repository.NoteContent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Politik der automatischen Archivierung.
 *
 * Hier steht die Frage, wer wegkommt — die Mechanik des Verschiebens und
 * Zurücknehmens prüft `ArchiveRepositoryTest`. Die Trennung ist der Grund,
 * warum beides ohne Gerät prüfbar ist.
 *
 * **Der wichtigste Test ist der, dass NICHTS passiert:** Die Automatik ist
 * standardmäßig aus, und eine Notizen-App, die beim ersten Start ungefragt
 * aufräumt, hat einen Fehler, den niemand mehr rückgängig macht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArchivTest : DatenbankTestbasis() {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var prefs: Einstellungen
    private lateinit var auto: AutoArchiv

    @Before
    fun autoAufbauen() {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { dateiordner.newFile("prefs.preferences_pb").also { it.delete() } },
        )
        prefs = Einstellungen(store)
        auto = AutoArchiv(db.noteDao(), prefs, archive, clock)
    }

    /** Legt eine Notiz an, die vor [tageAlt] Tagen zuletzt geöffnet wurde. */
    private suspend fun notiz(
        titel: String,
        stufe: Stage = Stage.INBOX,
        tageAlt: Int = 0,
        favorit: Boolean = false,
    ): String {
        val id = notes.create()
        notes.updateContent(id, NoteContent(titel, ""))
        if (stufe != Stage.INBOX) notes.moveTo(listOf(id), stufe)
        if (favorit) notes.setFavorite(listOf(id), true)
        if (tageAlt > 0) {
            // `lastOpenedAt` direkt setzen: `open()` nimmt immer die aktuelle
            // Uhr, und die steht in Tests still.
            db.noteDao().markOpened(id, clock.now() - tageAlt * EIN_TAG)
        }
        return id
    }

    private suspend fun anschalten(
        alterEingang: Int = 0,
        mengeEingang: Int = 0,
        alterWorkspace: Int = 0,
    ) {
        prefs.setAutoArchivAn(true)
        prefs.setAltersgrenze(Stage.INBOX, alterEingang)
        prefs.setMengengrenze(Stage.INBOX, mengeEingang)
        prefs.setAltersgrenze(Stage.WORKSPACE, alterWorkspace)
        prefs.setMengengrenze(Stage.WORKSPACE, 0)
    }

    // ------------------------------------------------------------- Aus heißt aus

    @Test
    fun `ausgeschaltet passiert nichts`() = runTest {
        notiz("uralt", tageAlt = 999)

        assertFalse(auto.eingeschaltet())
        assertTrue(auto.planen().leer)
    }

    @Test
    fun `eingeschaltet, aber ohne Grenzen passiert auch nichts`() = runTest {
        // Beide Grenzen auf 0 heißt aus. Ohne diese Regel räumte der
        // Hauptschalter allein schon alles weg.
        notiz("uralt", tageAlt = 999)
        anschalten()

        assertTrue(auto.planen().leer)
    }

    // ---------------------------------------------------------------- Alter

    @Test
    fun `zu lange nicht geoeffnet wandert weg`() = runTest {
        notiz("vergessen", tageAlt = 30)
        notiz("frisch", tageAlt = 1)
        anschalten(alterEingang = 14)

        val plan = auto.planen()

        assertEquals(listOf("vergessen"), plan.nachAlter.map { it.title })
        assertTrue(plan.nachMenge.isEmpty())
    }

    @Test
    fun `genau auf der Grenze bleibt`() = runTest {
        notiz("grenzfall", tageAlt = 14)
        anschalten(alterEingang = 14)

        assertTrue("Genau 14 Tage ist noch nicht MEHR als 14", auto.planen().leer)
    }

    @Test
    fun `jede Stufe hat ihre eigene Frist`() = runTest {
        notiz("eingang", stufe = Stage.INBOX, tageAlt = 20)
        notiz("workspace", stufe = Stage.WORKSPACE, tageAlt = 20)
        anschalten(alterEingang = 14, alterWorkspace = 60)

        assertEquals(listOf("eingang"), auto.planen().alle.map { it.title })
    }

    // ---------------------------------------------------------------- Menge

    @Test
    fun `ueber der Mengengrenze weichen die aeltesten`() = runTest {
        notiz("a", tageAlt = 5)
        notiz("b", tageAlt = 4)
        notiz("c", tageAlt = 3)
        anschalten(mengeEingang = 2)

        val plan = auto.planen()

        assertEquals(listOf("a"), plan.nachMenge.map { it.title })
    }

    @Test
    fun `unter der Mengengrenze weicht niemand`() = runTest {
        notiz("a")
        notiz("b")
        anschalten(mengeEingang = 5)

        assertTrue(auto.planen().leer)
    }

    @Test
    fun `Alter und Menge zaehlen dieselbe Notiz nicht doppelt`() = runTest {
        // Der Fehler, der ohne Abzug entstünde: Die alte Notiz steht in beiden
        // Listen, und der Lauf räumt eine Notiz mehr weg, als die Grenze
        // verlangt.
        notiz("uralt", tageAlt = 40)
        notiz("b", tageAlt = 3)
        notiz("c", tageAlt = 2)
        anschalten(alterEingang = 14, mengeEingang = 2)

        val plan = auto.planen()

        assertEquals(1, plan.nachAlter.size)
        assertTrue(
            "Doppelt eingeplant: " + plan.alle.map { it.title },
            plan.alle.map { it.id }.toSet().size == plan.alle.size,
        )
        // Drei Notizen, Grenze 2, eine davon ist ohnehin zu alt: Es muss genau
        // die eine weichen.
        assertEquals(listOf("uralt"), plan.alle.map { it.title })
    }

    // ------------------------------------------------------------- Ausnahmen

    @Test
    fun `Favoriten bleiben, egal wie alt`() = runTest {
        notiz("liebling", tageAlt = 999, favorit = true)
        anschalten(alterEingang = 14, mengeEingang = 1)

        assertTrue(auto.planen().leer)
    }

    @Test
    fun `eine offene Erinnerung schuetzt die Notiz`() = runTest {
        // Eine Erinnerung ist die ausdrückliche Ansage, die Notiz noch zu
        // brauchen. Sie wegzuräumen wäre das Gegenteil dessen, was man gesagt
        // bekommen hat.
        val id = notiz("mit Wecker", tageAlt = 999)
        erinnerungen.setzen(id, clock.now() + EIN_TAG)
        anschalten(alterEingang = 14)

        assertTrue(auto.planen().leer)
    }

    @Test
    fun `eine ausgeloeste Erinnerung schuetzt nicht mehr`() = runTest {
        val id = notiz("abgelaufen", tageAlt = 999)
        erinnerungen.setzen(id, clock.now() - EIN_TAG)
        erinnerungen.zuNotiz(id).forEach { erinnerungen.abhaken(it.id) }
        anschalten(alterEingang = 14)

        assertEquals(listOf("abgelaufen"), auto.planen().alle.map { it.title })
    }

    @Test
    fun `das Archiv ist nie Quelle`() = runTest {
        notiz("schon da", stufe = Stage.ARCHIVE, tageAlt = 999)
        anschalten(alterEingang = 1, alterWorkspace = 1)

        assertTrue(auto.planen().leer)
    }

    @Test
    fun `der Papierkorb wird nicht mit archiviert`() = runTest {
        val id = notiz("weggeworfen", tageAlt = 999)
        notes.trash(listOf(id))
        anschalten(alterEingang = 14)

        assertTrue(auto.planen().leer)
    }

    // ------------------------------------------------------------- Ausführen

    @Test
    fun `ein Lauf verschiebt und laesst sich zurueckholen`() = runTest {
        val ausEingang = notiz("eingang", stufe = Stage.INBOX, tageAlt = 30)
        val ausWorkspace = notiz("workspace", stufe = Stage.WORKSPACE, tageAlt = 90)
        anschalten(alterEingang = 14, alterWorkspace = 60)

        val batch = auto.ausfuehren(auto.planen())!!

        assertEquals(Stage.ARCHIVE, notes.get(ausEingang)!!.note.stage)
        assertEquals(Stage.ARCHIVE, notes.get(ausWorkspace)!!.note.stage)

        archive.undo(batch)

        // Jede Notiz landet in IHRER Stufe, nicht pauschal im Eingang.
        assertEquals(Stage.INBOX, notes.get(ausEingang)!!.note.stage)
        assertEquals(Stage.WORKSPACE, notes.get(ausWorkspace)!!.note.stage)
    }

    @Test
    fun `beide Ausloeser ergeben EINEN Lauf`() = runTest {
        // Zwei Läufe wären zwei Benachrichtigungen und zwei Undo-Knöpfe für
        // dasselbe nächtliche Aufräumen.
        notiz("alt", tageAlt = 40)
        notiz("b", tageAlt = 3)
        notiz("c", tageAlt = 2)
        notiz("d", tageAlt = 1)
        anschalten(alterEingang = 14, mengeEingang = 2)

        val plan = auto.planen()
        assertTrue(plan.nachAlter.isNotEmpty() && plan.nachMenge.isNotEmpty())

        val batch = auto.ausfuehren(plan)!!
        assertEquals(ArchiveTrigger.BOTH, archive.run(batch)!!.trigger)
        assertEquals(plan.alle.size, archive.run(batch)!!.noteCount)
    }

    @Test
    fun `ein leerer Plan erzeugt keinen Lauf`() = runTest {
        // Sonst stünde im Protokoll jede Nacht eine Zeile mit null Notizen.
        assertNull(auto.ausfuehren(auto.planen()))
    }

    // ------------------------------------------------------------ Papierkorb

    private suspend fun weggeworfen(titel: String, vorTagen: Int): String {
        val id = notiz(titel)
        notes.trash(listOf(id))
        // `deletedAt` zurückdatieren — die Uhr steht still, also von Hand.
        db.noteDao().softDelete(listOf(id), clock.now() - vorTagen * EIN_TAG)
        return id
    }

    @Test
    fun `ohne Frist wird der Papierkorb nie geleert`() = runTest {
        // Der Standardwert ist 0, und das ist keine Bequemlichkeit: Hier wird
        // endgültig gelöscht.
        weggeworfen("uralt", vorTagen = 999)

        assertEquals(0, notes.papierkorbAufraeumen(0))
        assertEquals(1, notes.observeTrash().first().size)
    }

    @Test
    fun `was laenger als die Frist liegt, verschwindet endgueltig`() = runTest {
        val alt = weggeworfen("alt", vorTagen = 40)
        val neu = weggeworfen("neu", vorTagen = 3)

        assertEquals(1, notes.papierkorbAufraeumen(30))

        assertNull(notes.get(alt))
        assertNotNull(notes.get(neu))
    }

    @Test
    fun `gemessen wird am Wegwerfen, nicht am Bearbeiten`() = runTest {
        // Eine uralte Notiz, gestern weggeworfen, ist gestern weggeworfen
        // worden. Andersherum verschwände sie sofort.
        val id = notiz("von 2019", tageAlt = 2000)
        notes.trash(listOf(id))

        assertEquals(0, notes.papierkorbAufraeumen(30))
        assertNotNull(notes.get(id))
    }

    @Test
    fun `Notizen ausserhalb des Papierkorbs bleiben unberuehrt`() = runTest {
        val drin = weggeworfen("weg", vorTagen = 99)
        val draussen = notiz("bleibt", tageAlt = 999)

        notes.papierkorbAufraeumen(30)

        assertNull(notes.get(drin))
        assertNotNull(notes.get(draussen))
    }

    @Test
    fun `das Leeren hinterlaesst Grabsteine`() = runTest {
        // Ohne sie käme die Notiz beim nächsten Abgleich vom anderen Gerät
        // zurück — als wäre nie etwas gelöscht worden.
        val id = weggeworfen("weg", vorTagen = 99)

        notes.papierkorbAufraeumen(30)

        val grabsteine = db.syncDao().tombstonesOf(EntityType.NOTE).map { it.entityId }
        assertTrue(id in grabsteine)
    }
}
