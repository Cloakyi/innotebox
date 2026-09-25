package de.notizen.core.data

import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.search.Suchanfrage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Eine Notiz kopieren.
 *
 * Der interessante Teil ist nicht, dass kopiert wird, sondern was nicht:
 * Favorit und Erinnerung sind Aussagen über *diese* Notiz, nicht über ihren
 * Inhalt. Eine mitkopierte Erinnerung würde zweimal klingeln, und das merkt
 * man erst nachts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplizierenTest : DatenbankTestbasis() {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private fun datei(inhalt: String) =
        dateiordner.newFile("${UUID.randomUUID()}.jpg").apply { writeText(inhalt) }

    /** Legt Kopien neben das Original, benannt nach der neuen Anhangskennung. */
    private fun ziel() = { _: de.notizen.core.data.db.entity.AttachmentEntity,
        neueAnhangId: String,
        _: String,
        ->
        File(dateiordner.root, "$neueAnhangId.jpg")
    }

    // ------------------------------------------------------------- Inhalt

    @Test
    fun `Titel, Text und Farbe gehen mit`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Einkauf", "Milch und Brot"))
        notes.setColor(listOf(id), NoteColor.GREEN)

        val kopie = notes.duplizieren(id, ziel())!!
        val n = notes.get(kopie)!!.note

        assertEquals("Einkauf", n.title)
        assertEquals("Milch und Brot", n.body)
        assertEquals(NoteColor.GREEN, n.colorId)
        assertNotEquals(id, kopie)
    }

    @Test
    fun `Checklisteneintraege gehen mit und bekommen eigene Kennungen`() = runTest {
        val id = notes.create(NoteType.LIST)
        notes.updateContent(
            id,
            NoteContent(
                "Packliste",
                "",
                items = listOf(
                    NoteItemEntity(UUID.randomUUID().toString(), id, "Zelt", true, 0),
                    NoteItemEntity(UUID.randomUUID().toString(), id, "Schlafsack", false, 1),
                ),
            ),
        )

        val kopie = notes.duplizieren(id, ziel())!!
        val eintraege = notes.get(kopie)!!.orderedItems

        assertEquals(listOf("Zelt", "Schlafsack"), eintraege.map { it.text })
        assertEquals(listOf(true, false), eintraege.map { it.isChecked })

        // Eigene Kennungen: Geteilte wären beim Sync zwei Notizen, die
        // denselben Eintrag beanspruchen.
        val original = notes.get(id)!!.orderedItems.map { it.id }.toSet()
        assertTrue(eintraege.none { it.id in original })
    }

    @Test
    fun `Tags gehen mit`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Reise", ""))
        val tag = tags.create("Urlaub", 0xFF00FF00.toInt())
        notes.setTags(id, listOf(tag.id))

        val kopie = notes.duplizieren(id, ziel())!!

        assertEquals(listOf("Urlaub"), notes.get(kopie)!!.tags.map { it.name })
    }

    @Test
    fun `die Kopie ist sofort auffindbar`() = runTest {
        // Ohne `reindex` stünde die Kopie in keiner Suche, und der FTS-Index
        // wird von Hand gepflegt.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Rhabarberkuchen", "mit Streuseln"))

        val kopie = notes.duplizieren(id, ziel())!!
        val treffer = suche.suche(Suchanfrage(text = "Rhabarberkuchen"))
            .first()
            .map { it.notiz.note.id }

        assertTrue("Die Kopie fehlt in der Suche", kopie in treffer)
    }

    // ------------------------------------------------------- Was NICHT mitgeht

    @Test
    fun `der Favoritenstern geht NICHT mit`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Wichtig", ""))
        notes.setFavorite(listOf(id), true)

        val kopie = notes.duplizieren(id, ziel())!!
        val n = notes.get(kopie)!!.note

        assertFalse(n.isFavorite)
        assertNull(n.favoritedAt)
    }

    @Test
    fun `die Erinnerung geht NICHT mit`() = runTest {
        // Sonst klingelt es zweimal, und man sucht die zweite Notiz nachts.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Anruf", ""))
        erinnerungen.setzen(id, START_ZEIT + EIN_TAG)

        val kopie = notes.duplizieren(id, ziel())!!

        assertTrue(notes.get(kopie)!!.reminders.isEmpty())
        // Das Original behält seine.
        assertEquals(1, notes.get(id)!!.reminders.size)
    }

    @Test
    fun `die Kopie landet im Eingang, egal wo das Original stand`() = runTest {
        val id = notes.create()
        notes.updateContent(id, NoteContent("Archiviert", ""))
        notes.moveTo(listOf(id), Stage.ARCHIVE)

        val kopie = notes.duplizieren(id, ziel())!!

        assertEquals(Stage.INBOX, notes.get(kopie)!!.note.stage)
        assertEquals(Stage.ARCHIVE, notes.get(id)!!.note.stage)
    }

    // ------------------------------------------------------------- Anhänge

    @Test
    fun `Anhaenge werden als eigene Dateien kopiert`() = runTest {
        val id = notes.create(NoteType.IMAGE)
        val quelldatei = datei("bilddaten")
        bilder.hinzufuegen(id, "a", quelldatei)

        val kopie = notes.duplizieren(id, ziel())!!
        val anhang = notes.get(kopie)!!.attachments.single()

        assertNotEquals("a", anhang.id)
        assertNotEquals(quelldatei.absolutePath, anhang.localPath)
        assertTrue("Die kopierte Datei fehlt", File(anhang.localPath).exists())
        assertEquals("bilddaten", File(anhang.localPath).readText())
        // Das Original bleibt unangetastet, eine Kopie darf nichts verschieben.
        assertTrue(quelldatei.exists())
    }

    @Test
    fun `der Verweis auf das Hintergrundbild wandert mit`() = runTest {
        // Er zeigt auf eine Anhangskennung, und die ist in der Kopie neu. Ohne
        // Mitwandern zeigte er auf den Anhang der ORIGINALNOTIZ.
        val id = notes.create(NoteType.IMAGE)
        bilder.hinzufuegen(id, "a", datei("x"))
        bilder.setzeHintergrund(id, "a")

        val kopie = notes.duplizieren(id, ziel())!!
        val n = notes.get(kopie)!!

        assertNotNull(n.note.backgroundAttachmentId)
        assertEquals(n.attachments.single().id, n.note.backgroundAttachmentId)
        assertNotEquals("a", n.note.backgroundAttachmentId)
    }

    @Test
    fun `ein Anhang ohne Datei wird uebersprungen`() = runTest {
        // Eine Anhangszeile ohne Datei sähe aus wie ein Bild, das man ansehen
        // kann.
        val id = notes.create(NoteType.IMAGE)
        val quelldatei = datei("weg")
        bilder.hinzufuegen(id, "a", quelldatei)
        quelldatei.delete()

        val kopie = notes.duplizieren(id, ziel())!!

        assertTrue(notes.get(kopie)!!.attachments.isEmpty())
    }

    @Test
    fun `die Kopie gilt als noch nicht hochgeladen`() = runTest {
        val id = notes.create(NoteType.IMAGE)
        bilder.hinzufuegen(id, "a", datei("x"))

        val kopie = notes.duplizieren(id, ziel())!!

        // Sonst verspräche der Sync eine Drive-Datei, die es für diesen Anhang
        // nicht gibt.
        assertNull(notes.get(kopie)!!.attachments.single().remoteId)
    }

    @Test
    fun `eine Notiz, die es nicht gibt, ergibt null`() = runTest {
        assertNull(notes.duplizieren("gibt-es-nicht", ziel()))
    }
}
