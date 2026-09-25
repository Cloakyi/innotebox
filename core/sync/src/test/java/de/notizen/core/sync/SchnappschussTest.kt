package de.notizen.core.sync

import de.notizen.core.data.repository.NoteContent
import de.notizen.core.sync.sicherung.Schnappschuss
import de.notizen.core.sync.sicherung.Sicherungsergebnis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Die Aufbewahrungsregel (SYNC.md 10) als reine Funktion, und die Szenarien
 * 5 und 7 aus SYNC.md 12 ueber den Tagesdurchlauf.
 */
@RunWith(RobolectricTestRunner::class)
class SchnappschussTest {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var drive: DriveImSpeicher
    private lateinit var uhr: Testuhr
    private lateinit var a: Testclient

    @Before
    fun aufbauen() {
        drive = DriveImSpeicher()
        // Ein Dienstag, damit die Wochenregel etwas zu tun hat.
        uhr = Testuhr(1_789_000_000_000L)
        a = Testclient("a", drive, uhr, dateiordner.root)
    }

    @After
    fun abbauen() = a.schliessen()

    private fun tagesdurchlauf(client: Testclient) = Tagesdurchlauf(
        abgleich = client.abgleich,
        schnappschuss = Schnappschuss(drive, client.sicherung(), client.ablage, client.einstellungen, uhr),
        einstellungen = client.einstellungen,
        clock = uhr,
    )

    // ------------------------------------------------------ Aufbewahrung

    @Test
    fun `die letzten sieben Tage bleiben, aeltere Werktage nicht`() {
        val heute = LocalDate.of(2026, 9, 16) // Mittwoch
        val namen = (0..10).map { heute.minusDays(it.toLong()).toString() + ".notesbak" }
        val behalten = Schnappschuss.behalten(namen, heute)
        assertTrue("2026-09-10.notesbak" in behalten) // heute minus 6
        assertTrue("2026-09-09.notesbak" !in behalten) // Mittwoch, heute minus 7
        assertTrue("2026-09-07.notesbak" in behalten) // Montag, Wochenstand
    }

    @Test
    fun `Montage bleiben vier Wochen, Monatserste zwoelf Monate`() {
        val heute = LocalDate.of(2026, 9, 16)
        val namen = listOf(
            "2026-08-24.notesbak", // Montag, 23 Tage her: bleibt
            "2026-08-17.notesbak", // Montag, 30 Tage her: weg
            "2026-08-01.notesbak", // Monatserster: bleibt
            "2025-10-01.notesbak", // elf Monate her: bleibt
            "2025-09-01.notesbak", // zwoelf Monate her: weg
            "2025-08-15.notesbak", // irgendein Tag: weg
        )
        val behalten = Schnappschuss.behalten(namen, heute)
        assertEquals(
            setOf("2026-08-24.notesbak", "2026-08-01.notesbak", "2025-10-01.notesbak"),
            behalten,
        )
    }

    @Test
    fun `fremde Namen und Snapshots von Hand werden nie angefasst`() {
        val heute = LocalDate.of(2026, 9, 16)
        val behalten = Schnappschuss.behalten(
            listOf("notizen.txt", "2026-09-15_1430.notesbak", "2020-01-02_0900.notesbak"),
            heute,
        )
        assertTrue("notizen.txt" in behalten)
        assertTrue("2026-09-15_1430.notesbak" in behalten)
        assertTrue("2020-01-02_0900.notesbak" !in behalten)
    }

    // ------------------------------------------------- Tagesdurchlauf

    @Test
    fun `ein Durchlauf ohne Befund schreibt einen Snapshot in den Backup-Ordner`() = runTest {
        a.neueNotiz("Gesichert")
        val ergebnis = tagesdurchlauf(a).ausfuehren("t")
        assertTrue(ergebnis is Tagesergebnis.Fertig)
        val fertig = ergebnis as Tagesergebnis.Fertig
        assertNotNull(fertig.snapshot)
        assertEquals(listOf(fertig.snapshot), drive.dateinamen("InNoteBox-Backup"))
        assertTrue(tagesdurchlauf(a).heuteSchonGelaufen())
    }

    /** Szenario 5, zweite Haelfte: mit Befund kein Snapshot. */
    @Test
    fun `mit Befund gibt es keinen Snapshot`() = runTest {
        val id = a.neueNotiz("Heil")
        a.sync()
        drive.vonAussenSchreiben("InNoteBox/notes/$id.json", "kaputt")

        val ergebnis = tagesdurchlauf(a).ausfuehren("t") as Tagesergebnis.Fertig
        assertTrue(!ergebnis.bericht.ohneBefund)
        assertNull(ergebnis.snapshot)
        assertEquals(emptyList<String>(), drive.dateinamen("InNoteBox-Backup"))
    }

    /**
     * Szenario 7: Snapshot auf einem leeren Geraet wiederherstellen. Der
     * Bestand ist derselbe, und der Spiegel wird ueberschrieben statt
     * ueberstimmt: Die wiederhergestellte Fassung geht mit rev + 1 hoch.
     */
    @Test
    fun `szenario 7 - ein Snapshot auf leerem Geraet ueberschreibt den Spiegel`() = runTest {
        val id = a.neueNotiz("Erster Stand")
        val ordner = a.ordner.anlegen("Ablage")
        a.sync()
        // Der Snapshot, so wie ihn der Tagesdurchlauf schreibt.
        val puffer = ByteArrayOutputStream()
        assertTrue(a.sicherung().sichern(puffer, "Test") is Sicherungsergebnis.Gesichert)
        val bytes = puffer.toByteArray()

        // Danach aendert sich der Spiegel noch: eine neue Fassung liegt oben.
        uhr.weiter(1_000)
        a.notes.updateContent(id, NoteContent("Zweiter Stand", "x"))
        a.sync()
        assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("Zweiter Stand"))

        // Ein leeres Geraet liest den Snapshot und gleicht ab.
        val c = Testclient("c", drive, uhr, dateiordner.root)
        try {
            val eingelesen = c.sicherung().wiederherstellen(ByteArrayInputStream(bytes))
            assertTrue(eingelesen is Sicherungsergebnis.Eingelesen)
            assertEquals("Erster Stand", c.titel(id))
            assertEquals("Ablage", c.ordner.get(ordner.id)?.name)

            uhr.weiter(1_000)
            c.sync()
            // Der Spiegel traegt jetzt den wiederhergestellten Stand ...
            assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("Erster Stand"))
            assertTrue(drive.text("InNoteBox/notes/$id.json")!!.contains("\"rev\": 3"))
            // ... und C hat ihn behalten statt die Zweitfassung zu holen.
            assertEquals("Erster Stand", c.titel(id))
        } finally {
            c.schliessen()
        }
    }
}
