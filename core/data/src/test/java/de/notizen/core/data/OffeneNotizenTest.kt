package de.notizen.core.data

import de.notizen.core.data.db.entity.SyncStateEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Was das Symbol in der Kopfzeile zählt.
 *
 * **Die Zahl ist eine Zusage, keine Statistik.** Steht dort „alles gesichert",
 * verlässt sich jemand darauf. Zählt sie dagegen dauerhaft etwas mit, das gar
 * nicht hochgehört, steht dort für immer „nicht gesichert" — und dann sieht
 * niemand mehr hin, wenn es einmal stimmt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OffeneNotizenTest : DatenbankTestbasis() {

    private suspend fun offen() = db.syncDao().observeOffeneNotizen().first()

    private suspend fun abgehakt(id: String) = db.syncDao().upsertState(
        SyncStateEntity(
            entityType = EntityType.NOTE,
            entityId = id,
            remoteId = "drive-$id",
            remoteRevision = null,
            localUpdatedAt = clock.now(),
            lastSyncedAt = clock.now(),
            syncStatus = SyncStatus.SYNCED,
        ),
    )

    @Test
    fun `eine frische Notiz steht aus`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Titel", "Text"))

        assertEquals(1, offen())
    }

    @Test
    fun `nach dem Abgleich ist nichts mehr offen`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Titel", "Text"))
        abgehakt(id)

        assertEquals(0, offen())
    }

    @Test
    fun `eine ausgenommene Notiz zaehlt nicht mit`() = runTest {
        // Der Kern: Sie soll gar nicht hoch. Wuerde sie mitzaehlen, stuende in
        // der Kopfzeile fuer immer "nicht gesichert" -- wegen einer Notiz, von
        // der genau das gewollt ist.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Privat", "bleibt hier"))
        notes.setAbgleich(id, an = false)

        assertEquals(0, offen())
    }

    @Test
    fun `wieder eingeschaltet steht sie wieder aus`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Privat", "doch nicht"))
        notes.setAbgleich(id, an = false)
        notes.setAbgleich(id, an = true)

        assertEquals(1, offen())
    }

    @Test
    fun `ein offener Anhang macht seine Notiz offen, nicht eine zweite`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Mit Bild", ""))
        abgehakt(id)

        // Ein Anhang hat drueben keine eigene Adresse -- er liegt in der Datei
        // der Notiz. Zwei offene Zeilen sind trotzdem EINE offene Notiz.
        db.syncDao().markDirty(EntityType.ATTACHMENT, "a-1", clock.now())
        db.attachmentDao().upsertAll(
            listOf(
                de.notizen.core.data.db.entity.AttachmentEntity(
                    id = "a-1",
                    noteId = id,
                    localPath = "/pfad/bild.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1,
                    hash = "h",
                ),
            ),
        )

        assertEquals(1, offen())
    }

    @Test
    fun `eine leere Notiz zaehlt zwar, wird aber nie hochgeladen`() = runTest {
        // Sie steht in der Zaehlung, solange ihre Buchung existiert -- der
        // Abgleich loescht diese Buchung beim ersten Lauf, ohne etwas
        // hochzuladen. Der Test haelt fest, dass das Anlegen allein schon eine
        // Buchung erzeugt; genau deshalb muss der Abgleich sie wieder loeschen,
        // sonst stuende sie fuer immer in der Kopfzeile.
        notes.create()

        assertEquals(1, offen())
    }

    @Test
    fun `eine Buchung ohne Notiz zaehlt nicht`() = runTest {
        // Ein Grabstein oder ein Rest aus einer geloeschten Notiz darf die
        // Anzeige nicht auf Dauer rot halten.
        db.syncDao().markDirty(EntityType.NOTE, "gibt-es-nicht", clock.now())

        assertEquals(0, offen())
    }
}
