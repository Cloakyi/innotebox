package de.notizen.core.data

import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Bildanhänge.
 *
 * Der Schwerpunkt liegt nicht auf dem Anlegen, das ist eine Zeile, sondern
 * auf dem Aufräumen: Es gibt bewusst keinen Fremdschlüssel von `notes` auf
 * `attachments`, die Datenbank fängt hier also nichts ab. Jede Zusage, die das
 * Repository stattdessen gibt, steht deshalb unten als eigener Test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BildRepositoryTest : DatenbankTestbasis() {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private fun bilddatei(inhalt: String = "bilddaten"): File =
        dateiordner.newFile("${UUID.randomUUID()}.jpg").apply { writeText(inhalt) }

    private suspend fun bildnotiz(): String = notes.create(NoteType.IMAGE)

    // ------------------------------------------------------------- Anlegen

    @Test
    fun `ein Bild wird als Anhang eingetragen`() = runTest {
        val id = bildnotiz()
        val datei = bilddatei()

        val anhang = bilder.hinzufuegen(id, "anhang-1", datei)

        assertNotNull(anhang)
        val geladen = notes.get(id)!!.attachments
        assertEquals(1, geladen.size)
        assertEquals("image/jpeg", geladen.single().mimeType)
        assertEquals(datei.length(), geladen.single().sizeBytes)
    }

    @Test
    fun `eine leere Datei wird nicht eingetragen`() = runTest {
        // Eine Anhangszeile ohne Inhalt sähe aus wie ein Bild, das man ansehen
        // kann. Genau dieselbe Regel gilt schon für Aufnahmen.
        val id = bildnotiz()
        val leer = dateiordner.newFile("leer.jpg")

        assertNull(bilder.hinzufuegen(id, "anhang-1", leer))
        assertTrue(notes.get(id)!!.attachments.isEmpty())
    }

    @Test
    fun `derselbe Inhalt ergibt denselben Hash`() = runTest {
        val id = bildnotiz()
        val a = bilder.hinzufuegen(id, "a", bilddatei("gleich"))!!
        val b = bilder.hinzufuegen(id, "b", bilddatei("gleich"))!!
        val c = bilder.hinzufuegen(id, "c", bilddatei("anders"))!!

        assertEquals(a.hash, b.hash)
        assertFalse(a.hash == c.hash)
    }

    // -------------------------------------------------------- Hintergrund

    @Test
    fun `ein Bild laesst sich als Hintergrund setzen und wieder aufheben`() = runTest {
        val id = bildnotiz()
        bilder.hinzufuegen(id, "anhang-1", bilddatei())

        bilder.setzeHintergrund(id, "anhang-1")
        assertEquals("anhang-1", notes.get(id)!!.note.backgroundAttachmentId)

        bilder.setzeHintergrund(id, null)
        assertNull(notes.get(id)!!.note.backgroundAttachmentId)
    }

    @Test
    fun `ein fremder Anhang wird nicht zum Hintergrund`() = runTest {
        // Ohne diese Prüfung entstünde ein Verweis über Notizgrenzen hinweg.
        // Wird die andere Notiz gelöscht, stünde hier ein toter Zeiger, und
        // kein Fremdschlüssel schlägt an, den gibt es in diese Richtung nicht.
        val meine = bildnotiz()
        val fremde = bildnotiz()
        bilder.hinzufuegen(fremde, "fremd", bilddatei())

        bilder.setzeHintergrund(meine, "fremd")

        assertNull(notes.get(meine)!!.note.backgroundAttachmentId)
    }

    @Test
    fun `ein Hintergrund hebt updatedAt an`() = runTest {
        val id = bildnotiz()
        bilder.hinzufuegen(id, "anhang-1", bilddatei())
        val vorher = notes.get(id)!!.note.updatedAt

        clock.advanceBy(EIN_TAG)
        bilder.setzeHintergrund(id, "anhang-1")

        // Die Notiz sieht danach anders aus. Bliebe updatedAt stehen, bekäme
        // der andere Client die Änderung nie zu sehen.
        assertTrue(notes.get(id)!!.note.updatedAt > vorher)
    }

    // ----------------------------------------------------------- Entfernen

    @Test
    fun `beim Loeschen verschwindet auch die Datei`() = runTest {
        val id = bildnotiz()
        val datei = bilddatei()
        bilder.hinzufuegen(id, "anhang-1", datei)

        bilder.entfernen("anhang-1")

        assertTrue(notes.get(id)!!.attachments.isEmpty())
        assertFalse("Die Datei liegt noch da", datei.exists())
    }

    @Test
    fun `das Loeschen des Hintergrundbildes loest den Verweis`() = runTest {
        // Der eigentliche Punkt dieses Repositories. Ohne das Lösen zeigte
        // `backgroundAttachmentId` auf einen Anhang, den es nicht mehr gibt.
        val id = bildnotiz()
        bilder.hinzufuegen(id, "anhang-1", bilddatei())
        bilder.setzeHintergrund(id, "anhang-1")

        bilder.entfernen("anhang-1")

        assertNull(notes.get(id)!!.note.backgroundAttachmentId)
    }

    @Test
    fun `das Loeschen eines ANDEREN Bildes laesst den Hintergrund stehen`() = runTest {
        val id = bildnotiz()
        bilder.hinzufuegen(id, "hintergrund", bilddatei("a"))
        bilder.hinzufuegen(id, "beiwerk", bilddatei("b"))
        bilder.setzeHintergrund(id, "hintergrund")

        bilder.entfernen("beiwerk")

        assertEquals("hintergrund", notes.get(id)!!.note.backgroundAttachmentId)
    }

    @Test
    fun `ein geloeschter Anhang hinterlaesst einen Grabstein`() = runTest {
        val id = bildnotiz()
        bilder.hinzufuegen(id, "anhang-1", bilddatei())

        bilder.entfernen("anhang-1")

        val grabsteine = db.syncDao().tombstonesOf(EntityType.ATTACHMENT)
        assertEquals(listOf("anhang-1"), grabsteine.map { it.entityId })
    }

    // ------------------------------------- Hintergrund ohne Notizbild

    @Test
    fun `ein eigens gewaehltes Hintergrundbild zaehlt nicht zu den Bildern der Notiz`() = runTest {
        // Der Wunsch, aus dem die Rolle entstanden ist: eine Fläche wählen,
        // ohne dass sie im Bildraster auftaucht.
        val id = bildnotiz()
        bilder.hinzufuegen(id, "inhalt", bilddatei("a"))
        bilder.hintergrundAusDatei(id, "flaeche", bilddatei("b"))

        val n = notes.get(id)!!
        assertEquals("flaeche", n.note.backgroundAttachmentId)

        val imRaster = n.attachments.filter { it.role == Anhangsrolle.INHALT }
        assertEquals(listOf("inhalt"), imRaster.map { it.id })
    }

    @Test
    fun `ein Bild der Notiz bleibt im Raster, auch wenn es die Flaeche ist`() = runTest {
        // Die andere Richtung, und sie ist Absicht: Wer ein vorhandenes Bild
        // zur Fläche macht, soll es nicht aus seiner Notiz verschwinden sehen.
        val id = bildnotiz()
        bilder.hinzufuegen(id, "inhalt", bilddatei())
        bilder.setzeHintergrund(id, "inhalt")

        val n = notes.get(id)!!
        assertEquals("inhalt", n.note.backgroundAttachmentId)
        assertEquals(Anhangsrolle.INHALT, n.attachments.single().role)
    }

    @Test
    fun `das Wechseln des Hintergrundbildes laesst keine Datei zurueck`() = runTest {
        // Ohne das Aufräumen sammelte jedes Wechseln eine Datei an, die über
        // keine Oberfläche mehr erreichbar wäre.
        val id = bildnotiz()
        val alt = bilddatei("alt")
        bilder.hintergrundAusDatei(id, "alt", alt)

        val neu = bilddatei("neu")
        bilder.hintergrundAusDatei(id, "neu", neu)

        assertEquals("neu", notes.get(id)!!.note.backgroundAttachmentId)
        assertEquals(listOf("neu"), notes.get(id)!!.attachments.map { it.id })
        assertFalse("Die alte Hintergrunddatei liegt noch da", alt.exists())
        assertTrue(neu.exists())
    }

    @Test
    fun `ein Notizbild zur Flaeche zu machen raeumt das alte Hintergrundbild weg`() = runTest {
        val id = bildnotiz()
        val flaeche = bilddatei("flaeche")
        bilder.hintergrundAusDatei(id, "flaeche", flaeche)
        bilder.hinzufuegen(id, "inhalt", bilddatei("inhalt"))

        bilder.setzeHintergrund(id, "inhalt")

        assertEquals("inhalt", notes.get(id)!!.note.backgroundAttachmentId)
        assertFalse(flaeche.exists())
    }

    @Test
    fun `das Aufheben der Flaeche raeumt ein eigenes Hintergrundbild weg`() = runTest {
        val id = bildnotiz()
        val flaeche = bilddatei()
        bilder.hintergrundAusDatei(id, "flaeche", flaeche)

        bilder.setzeHintergrund(id, null)

        assertNull(notes.get(id)!!.note.backgroundAttachmentId)
        assertTrue(notes.get(id)!!.attachments.isEmpty())
        assertFalse(flaeche.exists())
    }

    @Test
    fun `ein Notizbild bleibt liegen, wenn es nicht mehr die Flaeche ist`() = runTest {
        // Aufgeräumt werden ausdrücklich NUR Anhänge mit der Rolle
        // HINTERGRUND. Ein Bild der Notiz steht ja weiter im Raster.
        val id = bildnotiz()
        val datei = bilddatei()
        bilder.hinzufuegen(id, "inhalt", datei)
        bilder.setzeHintergrund(id, "inhalt")

        bilder.setzeHintergrund(id, null)

        assertEquals(listOf("inhalt"), notes.get(id)!!.attachments.map { it.id })
        assertTrue(datei.exists())
    }

    @Test
    fun `eine Notiz mit nur einem Hintergrundbild gilt nicht als leer`() = runTest {
        val id = bildnotiz()
        bilder.hintergrundAusDatei(id, "flaeche", bilddatei())

        assertFalse(notes.discardIfEmpty(id))
    }

    // ------------------------------------------- Endgueltiges Loeschen

    @Test
    fun `endgueltiges Loeschen der Notiz raeumt die Bilddateien mit weg`() = runTest {
        // CASCADE räumt die TABELLE, nicht die PLATTE. Ohne das Aufräumen im
        // Repository blieben die Fotos endgültig gelöschter Notizen liegen.
        val id = bildnotiz()
        notes.updateContent(id, NoteContent("Urlaub", ""))
        val eins = bilddatei("a")
        val zwei = bilddatei("b")
        bilder.hinzufuegen(id, "a", eins)
        bilder.hinzufuegen(id, "b", zwei)

        notes.purge(listOf(id))

        assertFalse(eins.exists())
        assertFalse(zwei.exists())
    }

    @Test
    fun `Dateien anderer Notizen bleiben beim Loeschen unberuehrt`() = runTest {
        val weg = bildnotiz()
        val bleibt = bildnotiz()
        val dateiWeg = bilddatei("a")
        val dateiBleibt = bilddatei("b")
        bilder.hinzufuegen(weg, "a", dateiWeg)
        bilder.hinzufuegen(bleibt, "b", dateiBleibt)

        notes.purge(listOf(weg))

        assertFalse(dateiWeg.exists())
        assertTrue(dateiBleibt.exists())
    }

    // ------------------------------------------------------------- Leerlauf

    @Test
    fun `eine Notiz mit nur einem Bild gilt nicht als leer`() = runTest {
        // Sonst würde eine frisch fotografierte Notiz beim Verlassen gelöscht,
        // weil sie weder Titel noch Text hat.
        val id = bildnotiz()
        bilder.hinzufuegen(id, "anhang-1", bilddatei())

        assertFalse(notes.discardIfEmpty(id))
        assertNotNull(notes.get(id))
    }
}
