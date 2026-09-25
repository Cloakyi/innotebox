package de.notizen.core.sync

import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Drive-Dokument gegen SYNC.md 14.15.
 *
 * **Der wichtigste Teil sind die Felder, die NICHT drinstehen.** Ein
 * versehentlich mitgeschriebenes `lastOpenedAt` würde dem anderen Gerät
 * erzählen, wann *dieses* die Notiz geöffnet hat — und damit dessen
 * Auto-Archiv steuern. Solche Fehler fallen nie beim Bauen auf, sondern
 * Monate später als seltsames Verhalten auf dem anderen Gerät.
 */
class AbbildungTest {

    private val T = 1_700_000_000_000L

    private fun vollstaendig() = NoteWithRelations(
        note = NoteEntity(
            id = "n1",
            stage = Stage.WORKSPACE,
            type = NoteType.AUDIO,
            title = "Besprechung",
            body = "**Wichtig**: Rückruf",
            colorId = NoteColor.GREEN,
            isFavorite = true,
            favoritedAt = T,
            sortIndex = 3,
            backgroundAttachmentId = "a1",
            createdAt = T,
            updatedAt = T + 100,
            lastOpenedAt = T + 9999,
            stageChangedAt = T + 50,
            autoArchivedBatchId = "lauf-lokal",
            ordnerArchiviertAt = T + 70,
            herkunftOrdnerId = "o-herkunft",
            ehemaligerOrdnerId = "o-weg",
        ),
        items = listOf(NoteItemEntity("i1", "n1", "Punkt", true, 0)),
        tags = listOf(
            TagEntity(
                id = "t1",
                name = "Arbeit",
                colorArgb = 0xFF00FF00.toInt(),
                createdAt = T,
                updatedAt = T,
            ),
        ),
        attachments = listOf(
            AttachmentEntity("a1", "n1", "/geraet/pfad.jpg", "image/jpeg", 42, "abc", "drive-1"),
            AttachmentEntity(
                "a2", "n1", "/geraet/ton.wav", "audio/wav", 99, "def",
                role = Anhangsrolle.HINTERGRUND,
            ),
        ),
        transcripts = listOf(TranscriptEntity("s1", "n1", 0, 500, "gesprochen", isFinal = true)),
        reminders = listOf(
            ReminderEntity("r1", "n1", T + 5000, alarmId = 7, isFired = false),
            ReminderEntity("r2", "n1", T - 5000, alarmId = 8, isFired = true),
        ),
    )

    // ------------------------------------------------ Was NICHT mitgeht

    @Test
    fun `rein lokale Felder stehen nicht im Dokument`() {
        val json = Sync.encodeToString(vollstaendig().alsDokument())

        assertFalse("lastOpenedAt gehört jedem Gerät selbst", "lastOpenedAt" in json)
        assertFalse("der Archivlauf ist lokal", "autoArchivedBatchId" in json)
        assertFalse("ein Gerätepfad sagt der Gegenstelle nichts", "localPath" in json)
        assertFalse("jedes Gerät weckt für sich", "alarmId" in json)
        assertFalse("jedes Gerät hakt für sich ab", "isFired" in json)
    }

    @Test
    fun `eine abgehakte Erinnerung wandert nicht mit`() {
        // Sie ist Vergangenheit und hat auf dem anderen Gerät nichts mehr zu
        // wecken.
        val doku = vollstaendig().alsDokument()
        assertEquals(listOf("r1"), doku.reminders.map { it.id })
    }

    // ------------------------------------------------ Rundlauf

    @Test
    fun `Schreiben und Lesen ergibt dasselbe Dokument`() {
        val doku = vollstaendig().alsDokument()
        val zurueck = Sync.decodeFromString<Notizdokument>(Sync.encodeToString(doku))
        assertEquals(doku, zurueck)
    }

    @Test
    fun `der Inhalt einer Notiz ueberlebt den Rundlauf`() {
        val original = vollstaendig()
        val doku = Sync.decodeFromString<Notizdokument>(
            Sync.encodeToString(original.alsDokument()),
        )
        val notiz = doku.alsNotiz()

        assertEquals("Besprechung", notiz.title)
        assertEquals("**Wichtig**: Rückruf", notiz.body)
        assertEquals(NoteColor.GREEN, notiz.colorId)
        assertEquals(Stage.WORKSPACE, notiz.stage)
        assertEquals(NoteType.AUDIO, notiz.type)
        assertEquals("a1", notiz.backgroundAttachmentId)
        assertEquals(3, notiz.sortIndex)
        assertEquals(T + 100, notiz.updatedAt)
        assertEquals(T + 50, notiz.stageChangedAt)
        // Schema 5: das Archiv des Ordnermodus geht mit, sonst laege die Notiz
        // auf dem anderen Geraet wieder im normalen Baum.
        assertEquals(T + 70, notiz.ordnerArchiviertAt)
        assertEquals("o-herkunft", notiz.herkunftOrdnerId)
        assertEquals("o-weg", notiz.ehemaligerOrdnerId)
    }

    @Test
    fun `ein Dokument aus Schema 4 ohne die Archivfelder liest sich als nicht archiviert`() {
        val json = """
            {
              "schemaVersion": 4, "id": "n1", "stage": "INBOX", "type": "TEXT",
              "title": "Alt", "body": "", "colorId": "DEFAULT",
              "isFavorite": false, "createdAt": 1, "updatedAt": 2,
              "stageChangedAt": 1
            }
        """.trimIndent()

        val notiz = Sync.decodeFromString<Notizdokument>(json).alsNotiz()
        assertNull(notiz.ordnerArchiviertAt)
        assertNull(notiz.herkunftOrdnerId)
        assertNull(notiz.ehemaligerOrdnerId)
    }

    @Test
    fun `ein Ordner aus Schema 4 gehoert zum normalen Baum`() {
        val json = """{ "id": "o1", "name": "Alt", "createdAt": 1, "updatedAt": 1 }"""
        val ordner = Sync.decodeFromString<Ordnerdokument>(json).alsOrdner()
        assertEquals(Bereich.ORDNER, ordner.bereich)
        assertNull(ordner.ehemaligerElternId)
    }

    @Test
    fun `Bereich und Herkunft eines Ordners ueberleben den Rundlauf`() {
        val ordner = FolderEntity(
            id = "o1", name = "Archiviert", createdAt = T, updatedAt = T,
            bereich = Bereich.ARCHIV, ehemaligerElternId = "o-weg",
        )
        val zurueck = Sync.decodeFromString<Ordnerdokument>(
            Sync.encodeToString(ordner.alsDokument()),
        ).alsOrdner()
        assertEquals(Bereich.ARCHIV, zurueck.bereich)
        assertEquals("o-weg", zurueck.ehemaligerElternId)

        // Und durch die Huelle, die nach Drive geht.
        val inhalt = ordner.alsDokument().alsInhalt()
        val huelle = Huelle(
            id = "o1", type = "FOLDER", rev = 1, lastEditor = "x",
            state = Zustand.ACTIVE, stateChangedAt = T, updatedAt = T, payload = inhalt,
        )
        val ausHuelle = Sync.decodeFromString<Huelle<Ordnerinhalt>>(Sync.encodeToString(huelle))
            .alsDokument()!!
        assertEquals(Bereich.ARCHIV, ausHuelle.bereich)
        assertEquals("o-weg", ausHuelle.ehemaligerElternId)
    }

    @Test
    fun `die Archivfelder einer Notiz gehen durch die Huelle`() {
        val doku = vollstaendig().alsDokument()
        val huelle = Huelle(
            id = doku.id, type = "NOTE", rev = 1, lastEditor = "x",
            state = Zustand.ACTIVE, stateChangedAt = T, updatedAt = doku.updatedAt,
            payload = doku.alsInhalt(),
        )
        val zurueck = Sync.decodeFromString<Huelle<Notizinhalt>>(Sync.encodeToString(huelle))
            .alsDokument()!!
        assertEquals(T + 70, zurueck.ordnerArchiviertAt)
        assertEquals("o-herkunft", zurueck.herkunftOrdnerId)
        assertEquals("o-weg", zurueck.ehemaligerOrdnerId)
    }

    @Test
    fun `Eintraege, Anhaenge und Transkripte ueberleben den Rundlauf`() {
        val doku = vollstaendig().alsDokument()

        assertEquals(listOf("Punkt"), doku.alsEintraege().map { it.text })
        assertEquals(listOf("t1"), doku.tagIds)

        val anhaenge = doku.alsAnhaenge { "/neu/${it.id}" }
        assertEquals(listOf("a1", "a2"), anhaenge.map { it.id })
        assertEquals("/neu/a1", anhaenge.first().localPath)
        // Die Rolle muss mit, sonst taucht ein Hintergrundbild drüben im
        // Bildraster auf.
        assertEquals(Anhangsrolle.HINTERGRUND, anhaenge.last().role)

        assertEquals(listOf("gesprochen"), doku.alsTranskripte().map { it.text })
    }

    @Test
    fun `eine ankommende Notiz gilt als nie geoeffnet`() {
        // `jetzt` einzusetzen hieße zu behaupten, sie sei eben angesehen
        // worden — damit wäre sie vor dem Auto-Archiv geschützt, ohne dass
        // jemand sie je gesehen hat.
        val doku = vollstaendig().alsDokument()
        assertEquals(doku.createdAt, doku.alsNotiz().lastOpenedAt)
    }

    @Test
    fun `eine ankommende Notiz gehoert keinem Archivlauf`() {
        assertNull(vollstaendig().alsDokument().alsNotiz().autoArchivedBatchId)
    }

    @Test
    fun `ankommende Erinnerungen bekommen lokale Alarmkennungen`() {
        val doku = vollstaendig().alsDokument()
        val erinnerungen = doku.alsErinnerungen { i -> 100 + i }

        assertEquals(listOf(100), erinnerungen.map { it.alarmId })
        assertFalse(erinnerungen.single().isFired)
    }

    // ------------------------------------------------ Fremde Schemata

    @Test
    fun `ein unbekanntes Feld bringt den Client nicht um`() {
        // Der Web-Client darf vor uns ein Feld ergänzen. Ein neueres Schema
        // darf ein älteres nicht zum Absturz bringen.
        val json = """
            {
              "schemaVersion": 99, "id": "n1", "stage": "INBOX", "type": "TEXT",
              "title": "Von morgen", "body": "", "colorId": "DEFAULT",
              "isFavorite": false, "createdAt": 1, "updatedAt": 2,
              "stageChangedAt": 1,
              "dieseSpalteGibtEsNochNicht": "egal"
            }
        """.trimIndent()

        val doku = Sync.decodeFromString<Notizdokument>(json)
        assertEquals("Von morgen", doku.title)
        assertEquals(99, doku.schemaVersion)
    }

    @Test
    fun `das Dokument traegt die Schema-Version aus SYNC punkt md`() {
        assertEquals(5, SCHEMA_VERSION)
        assertEquals(SCHEMA_VERSION, vollstaendig().alsDokument().schemaVersion)
    }

    @Test
    fun `der Ordnerinhalt bleibt lesbar`() {
        // SYNC.md 2 sagt zu, dass der Ordner offen und lesbar in Drive liegt.
        // Wer im Notfall selbst an seine Notizen muss, soll das ohne Werkzeug
        // können.
        val json = Sync.encodeToString(vollstaendig().alsDokument())
        assertTrue("Nicht umgebrochen — das liest niemand", json.lines().size > 5)
        assertTrue("Besprechung" in json)
    }
}
