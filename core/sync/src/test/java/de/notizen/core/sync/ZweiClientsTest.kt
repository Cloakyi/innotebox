package de.notizen.core.sync

import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Herkunft
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.test.runTest
import org.junit.After
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

/**
 * Die Pruefszenarien aus SYNC.md 12, mit zwei simulierten Clients A und B an
 * einem Drive im Speicher. Das System gilt erst als fertig, wenn diese
 * laufen.
 */
@RunWith(RobolectricTestRunner::class)
class ZweiClientsTest {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var drive: DriveImSpeicher
    private lateinit var uhr: Testuhr
    private lateinit var a: Testclient
    private lateinit var b: Testclient

    @Before
    fun aufbauen() {
        drive = DriveImSpeicher()
        uhr = Testuhr(1_000_000L)
        a = Testclient("a", drive, uhr, dateiordner.root)
        b = Testclient("b", drive, uhr, dateiordner.root)
    }

    @After
    fun abbauen() {
        a.schliessen()
        b.schliessen()
    }

    private fun tick() = uhr.weiter(1_000)

    // ----------------------------------------------------------- Grundweg

    @Test
    fun `eine Notiz von A kommt bei B an, als eine Datei je Notiz`() = runTest {
        val id = a.neueNotiz("Einkauf")
        a.sync()
        b.sync()

        assertEquals("Einkauf", b.titel(id))
        assertNotNull(drive.text("InNoteBox/notes/$id.json"))
        assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("\"rev\": 1"))
    }

    @Test
    fun `ein zweiter Lauf ohne Aenderung schreibt nichts`() = runTest {
        a.neueNotiz("Ruhe")
        a.sync()
        b.sync()
        val vorher = drive.schreibvorgaenge
        // index.json wird je Lauf geschrieben, das ist der einzige erlaubte Schreibvorgang.
        a.sync()
        assertEquals(vorher + 1, drive.schreibvorgaenge)
    }

    @Test
    fun `Ordner und Tags gehen als eigene Dateien mit`() = runTest {
        val ordner = a.ordner.anlegen("Reisen")
        val tag = a.tags.create("wichtig", 0xFF0000)
        a.sync()
        b.sync()

        assertEquals("Reisen", b.ordner.get(ordner.id)?.name)
        assertEquals("wichtig", b.tags.getAll().single().name)
        assertNotNull(drive.text("InNoteBox/folders/${ordner.id}.json"))
        assertNotNull(drive.text("InNoteBox/tags/${tag.id}.json"))
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 1

    /**
     * Notiz auf A loeschen, B war offline. Nach dem Abgleich ist sie auf B
     * weg und kommt auch spaeter nicht zurueck.
     */
    @Test
    fun `szenario 1 - endgueltig geloescht auf A verschwindet auf B und bleibt weg`() = runTest {
        val id = a.neueNotiz("Weg damit")
        a.sync(); b.sync()
        assertEquals("Weg damit", b.titel(id))

        tick()
        a.notes.trash(listOf(id))
        a.notes.purge(listOf(id))
        a.sync()

        // Die Datei ist noch da, als Grabstein. Regel 2: Loeschen ist ein Zustand.
        assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("DELETED"))

        b.sync()
        assertTrue(b.weg(id))

        // Und spaeter, nach weiteren Laeufen beider Seiten: bleibt weg.
        tick(); a.sync(); b.sync(); a.sync()
        assertTrue(b.weg(id))
        assertTrue(a.weg(id))
    }

    /** Die Geisterdatei aus SYNC.md 6.2, auf einem Client. */
    @Test
    fun `szenario 1b - der Grabstein gewinnt gegen die eigene alte Drive-Fassung`() = runTest {
        val id = a.neueNotiz("Geist")
        a.sync()
        tick()
        a.notes.trash(listOf(id))
        a.notes.purge(listOf(id))
        a.sync()
        a.sync()
        assertTrue(a.weg(id))
        assertNotNull(a.db.syncDao().tombstone(EntityType.NOTE, id))
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 2

    /**
     * Notiz auf A bearbeiten, gleichzeitig auf B in den Papierkorb. Sie landet
     * im Papierkorb, die Bearbeitung geht nicht verloren.
     */
    @Test
    fun `szenario 2 - Papierkorb gewinnt, Bearbeitung bleibt`() = runTest {
        val id = a.neueNotiz("Original")
        a.sync(); b.sync()

        tick()
        b.notes.trash(listOf(id))
        tick()
        a.notes.updateContent(id, NoteContent("Bearbeitet", "neuer Text"))

        b.sync()
        a.sync()
        b.sync()

        assertTrue(a.imPapierkorb(id))
        assertTrue(b.imPapierkorb(id))
        assertEquals("Bearbeitet", a.titel(id))
        assertEquals("Bearbeitet", b.titel(id))
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 3

    /**
     * Papierkorb auf A leeren, B meldet sich 30 Tage spaeter. Nichts kommt
     * zurueck, obwohl B die Notiz noch lebend kannte.
     */
    @Test
    fun `szenario 3 - B nach dreissig Tagen bringt nichts zurueck`() = runTest {
        val id = a.neueNotiz("Alt")
        a.sync(); b.sync()

        tick()
        a.notes.trash(listOf(id))
        a.notes.purge(listOf(id))
        a.sync()

        uhr.weiter(31L * 24 * 60 * 60 * 1000)
        b.sync()
        assertTrue(b.weg(id))
        a.sync()
        assertTrue(a.weg(id))
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 4

    /**
     * App waehrend des Uploads beendet: Die Vormerkung steht noch, und beim
     * naechsten Lauf geht sie raus. Simuliert ueber einen Lauf, der vor dem
     * Schreiben stirbt (das Drive verweigert einmal).
     */
    @Test
    fun `szenario 4 - eine unterbrochene Vormerkung geht beim naechsten Lauf raus`() = runTest {
        val id = a.neueNotiz("Unterwegs")
        val sperre = SperrendesDrive(drive)
        val aa = Testclient("aa", sperre, uhr, dateiordner.root)
        try {
            val id2 = aa.neueNotiz("Unterwegs")
            sperre.sperren = true
            val ergebnis = aa.abgleich.lauf("t")
            assertTrue(ergebnis is Abgleichergebnis.KeinNetz)
            assertTrue(aa.db.syncDao().withStatus(SyncStatus.DIRTY).isNotEmpty())

            sperre.sperren = false
            aa.sync()
            assertNotNull(drive.text("InNoteBox/notes/$id2.json"))
            assertTrue(aa.db.syncDao().withStatus(SyncStatus.DIRTY).isEmpty())
        } finally {
            aa.schliessen()
        }
        assertNotNull(id)
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 5

    /**
     * Eine Notizdatei in Drive von Hand kaputt: Der Pruefbericht meldet es,
     * und `ohneBefund` ist falsch, also gibt es keinen Snapshot.
     */
    @Test
    fun `szenario 5 - eine kaputte Datei steht im Pruefbericht`() = runTest {
        val id = a.neueNotiz("Heil")
        a.sync()
        drive.vonAussenSchreiben("InNoteBox/notes/$id.json", "{ das ist kein json")

        val ergebnis = b.sync()
        assertEquals(listOf("$id.json"), ergebnis.bericht.unlesbar)
        assertFalse(ergebnis.bericht.ohneBefund)
        // Und nichts wurde daraus abgeleitet: B hat die Notiz nicht, A behaelt sie.
        assertTrue(b.weg(id))
        assertEquals("Heil", a.titel(id))
    }

    /** Regel 1: eine fehlende Datei ist keine Information, sie wird geheilt. */
    @Test
    fun `eine drueben verschwundene Datei wird gemeldet und neu hochgeladen`() = runTest {
        val id = a.neueNotiz("Verschwunden")
        a.sync()
        val kennung = drive.eintraege.values.first { it.name == "$id.json" }.id
        drive.loeschen("t", kennung)

        val ergebnis = a.sync()
        assertEquals(1, ergebnis.bericht.fehlendeDateien)
        assertNotNull(drive.text("InNoteBox/notes/$id.json"))
        assertEquals("Verschwunden", a.titel(id))
    }

    // ------------------------------------------------ SYNC.md 12, Szenario 6

    /**
     * Ein Assistent legt ueber Drive eine Notiz an: neue Datei, rev 1,
     * origin EXTERNAL. Sie erscheint in der App mit Hinweis auf die Herkunft.
     */
    @Test
    fun `szenario 6 - eine Notiz vom Assistenten kommt mit Herkunft EXTERNAL an`() = runTest {
        a.sync()
        val id = "11111111-2222-3333-4444-555555555555"
        drive.vonAussenSchreiben(
            "InNoteBox/notes/$id.json",
            """
            {
              "schemaVersion": 4, "id": "$id", "type": "NOTE", "rev": 1,
              "lastEditor": "assistant", "origin": "EXTERNAL", "state": "ACTIVE",
              "stateChangedAt": 5, "updatedAt": 5, "purgeAfter": null,
              "payload": {
                "stage": "INBOX", "type": "TEXT", "title": "Vom Assistenten", "body": "Hallo",
                "colorId": "DEFAULT", "isFavorite": false, "createdAt": 5, "stageChangedAt": 5
              }
            }
            """.trimIndent(),
        )

        a.sync()
        val notiz = a.notes.get(id)
        assertEquals("Vom Assistenten", notiz?.note?.title)
        assertEquals(Herkunft.EXTERNAL, notiz?.note?.origin)

        // Sobald die App selbst schreibt, ist es wieder APP, und rev steigt.
        tick()
        a.notes.updateContent(id, NoteContent("Vom Assistenten", "Hallo zurueck"))
        a.sync()
        assertEquals(Herkunft.APP, a.notes.get(id)?.note?.origin)
        assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("\"rev\": 2"))
    }

    /** Ein Assistent legt in den Papierkorb, indem er state auf TRASHED setzt. */
    @Test
    fun `ein Assistent kann in den Papierkorb legen`() = runTest {
        val id = a.neueNotiz("Bitte weg")
        a.sync()
        val alt = drive.text("InNoteBox/notes/$id.json")!!
        val neu = alt.replace("\"rev\": 1", "\"rev\": 2").replace("\"state\": \"ACTIVE\"", "\"state\": \"TRASHED\"")
        drive.vonAussenSchreiben("InNoteBox/notes/$id.json", neu)

        a.sync()
        assertTrue(a.imPapierkorb(id))
    }

    /** Die Datei bekam eine neue Drive-Kennung, der Name zaehlt. */
    @Test
    fun `eine von aussen neu angelegte Datei wird ueber den Namen gefunden`() = runTest {
        val id = a.neueNotiz("Umgeschrieben")
        a.sync()
        val alt = drive.text("InNoteBox/notes/$id.json")!!
        drive.vonAussenSchreiben(
            "InNoteBox/notes/$id.json",
            alt.replace("\"rev\": 1", "\"rev\": 2").replace("Umgeschrieben", "Neu von aussen"),
        )
        a.sync()
        assertEquals("Neu von aussen", a.titel(id))

        // Und A kann danach weiter schreiben, ohne dass eine zweite Datei entsteht.
        tick()
        a.notes.updateContent(id, NoteContent("Und zurueck", "x"))
        a.sync()
        assertEquals(1, drive.dateinamen("InNoteBox/notes").count { it == "$id.json" })
    }

    // ------------------------------------------------------- Konfliktkopie

    @Test
    fun `beide bearbeiten dieselbe Notiz - lokal bleibt, fern wird zur Kopie`() = runTest {
        val id = a.neueNotiz("Basis")
        a.sync(); b.sync()

        tick()
        a.notes.updateContent(id, NoteContent("Fassung A", "a"))
        tick()
        b.notes.updateContent(id, NoteContent("Fassung B", "b"))

        a.sync()
        val ergebnis = b.sync()
        assertEquals(1, ergebnis.konflikte)
        assertEquals("Fassung B", b.titel(id))
        assertTrue(b.alleTitel().any { it.startsWith("Fassung A (Konflikt") })

        a.sync()
        assertEquals("Fassung B", a.titel(id))
        assertTrue(a.alleTitel().any { it.startsWith("Fassung A (Konflikt") })
    }

    // ------------------------------------------------------------- Ordner

    @Test
    fun `ein endgueltig geloeschter Ordner kommt nicht zurueck`() = runTest {
        val ordner = a.ordner.anlegen("Kurzlebig")
        a.sync(); b.sync()
        assertNotNull(b.ordner.get(ordner.id))

        tick()
        a.ordner.loeschen(ordner.id)
        a.ordner.endgueltigLoeschen(ordner.id)
        a.sync()
        b.sync()
        assertNull(b.db.folderDao().getById(ordner.id))
        a.sync(); b.sync()
        assertNull(a.db.folderDao().getById(ordner.id))
    }

    // -------------------------------------------------------------- Purge

    @Test
    fun `der Purge entfernt einen reifen Grabstein erst, wenn alle Geraete ihn gesehen haben`() = runTest {
        val id = a.neueNotiz("Purge mich")
        a.sync(); b.sync()
        tick()
        a.notes.trash(listOf(id)); a.notes.purge(listOf(id))
        a.sync()

        uhr.weiter(31L * 24 * 60 * 60 * 1000)
        // B hat den Grabstein noch nicht gesehen: nichts wird entfernt.
        assertEquals(0, a.abgleich.purge("t"))
        assertNotNull(drive.text("InNoteBox/notes/$id.json"))

        b.sync()
        a.sync()
        assertEquals(1, a.abgleich.purge("t"))
        assertNull(drive.text("InNoteBox/notes/$id.json"))
        assertTrue(drive.text("InNoteBox/index.json")!!.contains("purgeWatermark"))
    }
}

/** Ein Drive, das sich auf Wunsch wie „kein Netz" verhaelt. */
class SperrendesDrive(private val echt: Drivezugang) : Drivezugang by echt {
    var sperren = false

    private fun pruefen() {
        if (sperren) throw Drivefehler.KeinNetz("gesperrt")
    }

    override suspend fun ordner(token: String, name: String, elternId: String): String {
        pruefen()
        return echt.ordner(token, name, elternId)
    }

    override suspend fun anlegen(token: String, ordnerId: String, name: String, inhalt: String): Drivedatei {
        pruefen()
        return echt.anlegen(token, ordnerId, name, inhalt)
    }
}
