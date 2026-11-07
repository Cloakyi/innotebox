package de.notizen.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.test.runTest
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

/** Kalender und Termine in der Datenbank, mit Vormerkung fuer den Abgleich. */
@RunWith(RobolectricTestRunner::class)
class KalenderRepositoryTest : DatenbankTestbasis() {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private lateinit var kalender: KalenderRepository

    @Before
    fun kalenderAufbauen() {
        val datei = dateiordner.newFile("kalender.preferences_pb")
        datei.delete()
        val einstellungen = Einstellungen(PreferenceDataStoreFactory.create { datei }, kiSiegel = { _, _ -> false })
        kalender = KalenderRepository(db, db.kalenderDao(), db.syncDao(), einstellungen, clock)
    }

    private fun neu(id: String = "t-1") = EventEntity(
        id = id, calendarId = KalenderRepository.MEIN_KALENDER, title = "Zahnarzt",
        startAt = START_ZEIT, endAt = START_ZEIT + 3_600_000, timeZone = "Europe/Berlin",
        reminders = listOf(30), createdAt = START_ZEIT, updatedAt = START_ZEIT,
    )

    @Test
    fun `einrichten legt die drei Kalender einmal an`() = runTest {
        kalender.einrichten()
        kalender.einrichten()
        val alle = kalender.kalender()
        assertEquals(listOf("Mein Kalender", "Aufgaben", "Geburtstage"), alle.map { it.name })
        // Feste Kennungen, damit zwei Geraete dieselben haben.
        assertEquals(KalenderRepository.MEIN_KALENDER, alle[0].id)
        assertEquals(SyncStatus.DIRTY, db.syncDao().stateOf(EntityType.EVENT_CALENDAR, alle[0].id)?.syncStatus)
    }

    @Test
    fun `ein unveraenderter Termin wird nicht neu geschrieben`() = runTest {
        kalender.einrichten()
        val erst = kalender.terminSpeichern(neu())
        clock.advanceBy(EIN_TAG)
        val wieder = kalender.terminSpeichern(erst)
        assertEquals(erst.updatedAt, wieder.updatedAt)
        clock.advanceBy(EIN_TAG)
        val geaendert = kalender.terminSpeichern(erst.copy(title = "Zahnarzt Dr. Weiß"))
        assertEquals(START_ZEIT + 2 * EIN_TAG, geaendert.updatedAt)
        assertEquals(SyncStatus.DIRTY, db.syncDao().stateOf(EntityType.EVENT, "t-1")?.syncStatus)
    }

    @Test
    fun `loeschen ist weich und laesst sich zuruecknehmen`() = runTest {
        kalender.terminSpeichern(neu())
        kalender.terminLoeschen("t-1")
        assertNotNull(kalender.termin("t-1")?.deletedAt)
        assertTrue(kalender.termine().isEmpty())
        kalender.terminWiederherstellen("t-1")
        assertNull(kalender.termin("t-1")?.deletedAt)
    }

    @Test
    fun `ein geloeschter Kalender nimmt seine Termine mit und bringt sie zurueck`() = runTest {
        kalender.einrichten()
        kalender.terminSpeichern(neu("a"))
        kalender.terminSpeichern(neu("b"))
        val mit = kalender.kalenderLoeschen(KalenderRepository.MEIN_KALENDER)
        assertEquals(setOf("a", "b"), mit.toSet())
        assertTrue(kalender.termine().isEmpty())
        assertEquals(2, kalender.kalender().size)
        kalender.kalenderWiederherstellen(KalenderRepository.MEIN_KALENDER, mit)
        assertEquals(2, kalender.termine().size)
        assertEquals(3, kalender.kalender().size)
    }

    @Test
    fun `ein Vorkommen loeschen setzt eine Ausnahme und nimmt sie zurueck`() = runTest {
        kalender.terminSpeichern(neu())
        kalender.vorkommenLoeschen("t-1", 42L)
        assertEquals(listOf(42L), kalender.termin("t-1")?.exceptions)
        kalender.vorkommenWiederherstellen("t-1", 42L)
        assertEquals(emptyList<Long>(), kalender.termin("t-1")?.exceptions)
    }

    @Test
    fun `Listen ueberstehen die Datenbank`() = runTest {
        kalender.terminSpeichern(neu().copy(reminders = listOf(-540, 900), exceptions = listOf(-86_400_000L, 5L)))
        val t = kalender.termin("t-1")!!
        assertEquals(listOf(-540, 900), t.reminders)
        assertEquals(listOf(-86_400_000L, 5L), t.exceptions)
    }
}
