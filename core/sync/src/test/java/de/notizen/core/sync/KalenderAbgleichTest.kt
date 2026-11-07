package de.notizen.core.sync

import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import de.notizen.core.data.repository.KalenderRepository
import de.notizen.core.sync.sicherung.Sicherungsergebnis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Kalender und Termine zwischen zwei Geraeten und durch die Sicherung (SYNC.md 16). */
@RunWith(RobolectricTestRunner::class)
class KalenderAbgleichTest {

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

    private fun termin(id: String, titel: String) = EventEntity(
        id = id, calendarId = KalenderRepository.MEIN_KALENDER, art = Terminart.TERMIN, title = titel,
        startAt = 2_000_000L, endAt = 2_000_000L + 3_600_000L, timeZone = "Europe/Berlin",
        recurrence = Wiederholung.WOECHENTLICH, reminders = listOf(30, 0), important = true,
        createdAt = uhr.now(), updatedAt = uhr.now(),
    )

    @Test
    fun `zwei Geraete richten den Kalender ein und haben danach dieselben drei`() = runTest {
        a.kalender.einrichten()
        b.kalender.einrichten()
        a.sync()
        b.sync()
        a.sync()
        assertEquals(3, a.kalender.kalender().size)
        assertEquals(3, b.kalender.kalender().size)
        assertEquals(3, drive.dateinamen("InNoteBox/calendars").size)
    }

    @Test
    fun `ein Termin von A kommt bei B an, mit allen Feldern`() = runTest {
        a.kalender.einrichten()
        a.kalender.terminSpeichern(termin("t-1", "Zahnarzt"))
        a.sync()
        b.sync()
        val t = b.kalender.termin("t-1")!!
        assertEquals("Zahnarzt", t.title)
        assertEquals(Wiederholung.WOECHENTLICH, t.recurrence)
        assertEquals(listOf(0, 30), t.reminders.sorted())
        assertTrue(t.important)
        assertNotNull(drive.text("InNoteBox/events/t-1.json"))
    }

    @Test
    fun `geloescht auf A ist geloescht auf B`() = runTest {
        a.kalender.einrichten()
        a.kalender.terminSpeichern(termin("t-1", "Weg damit"))
        a.sync()
        b.sync()
        tick()
        a.kalender.terminLoeschen("t-1")
        a.sync()
        b.sync()
        assertNotNull(b.kalender.termin("t-1")?.deletedAt)
        assertTrue(b.kalender.termine().isEmpty())
    }

    @Test
    fun `beide aendern denselben Termin, der spaetere gewinnt ohne Kopie`() = runTest {
        a.kalender.einrichten()
        a.kalender.terminSpeichern(termin("t-1", "Treffen"))
        a.sync()
        b.sync()
        tick()
        a.kalender.terminSpeichern(a.kalender.termin("t-1")!!.copy(title = "Treffen von A"))
        tick()
        b.kalender.terminSpeichern(b.kalender.termin("t-1")!!.copy(title = "Treffen von B"))
        a.sync()
        b.sync()
        a.sync()
        assertEquals("Treffen von B", a.kalender.termin("t-1")!!.title)
        assertEquals("Treffen von B", b.kalender.termin("t-1")!!.title)
        assertEquals(1, a.kalender.termine().size)
    }

    @Test
    fun `die Sicherung bringt Kalender und Termine mit`() = runTest {
        a.kalender.einrichten()
        val eigener = a.kalender.kalenderAnlegen("Arbeit", 0xFF0B8043.toInt())
        a.kalender.terminSpeichern(termin("t-1", "Schicht").copy(calendarId = eigener.id))
        val puffer = ByteArrayOutputStream()
        assertTrue(a.sicherung().sichern(puffer, "Test") is Sicherungsergebnis.Gesichert)

        val c = Testclient("c", drive, uhr, dateiordner.root)
        try {
            c.sicherung().wiederherstellen(ByteArrayInputStream(puffer.toByteArray()))
            assertEquals("Schicht", c.kalender.termin("t-1")?.title)
            assertTrue(c.kalender.kalender().any { it.name == "Arbeit" })
        } finally {
            c.schliessen()
        }
    }
}
