package de.notizen.core.data

import de.notizen.core.data.model.EntityType
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Was eine Aufnahme in der Datenbank hinterlässt.
 *
 * Der wichtigste Punkt steht im letzten Test: **das Transkript muss durchsuchbar
 * werden.** Der FTS-Index wird von Hand gepflegt, und wer eine neue
 * Schreiboperation ergänzt und `reindex()` vergisst, merkt es nur hier.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioRepositoryTest : DatenbankTestbasis() {

    @get:Rule
    val dateiordner = TemporaryFolder()

    private fun wav(bytes: Int = 2000) = dateiordner.newFile("aufnahme.wav").apply {
        writeBytes(ByteArray(bytes) { 0x10 })
    }

    @Test
    fun `Transkriptsegmente landen in der Datenbank`() = runTest {
        val id = notes.create()
        audio.aufnahmeSichern(
            noteId = id,
            datei = null,
            abschnitte = listOf(
                Triple("Erster Satz", 0L, 3_000L),
                Triple("Zweiter Satz", 3_000L, 7_000L),
            ),
        )

        val segmente = audio.transkript(id)
        assertEquals(2, segmente.size)
        assertEquals("Erster Satz", segmente[0].text)
        assertEquals(3_000L, segmente[1].startMs)
    }

    @Test
    fun `die Aufnahme wird als Anhang eingetragen`() = runTest {
        val id = notes.create()
        val datei = wav()

        audio.aufnahmeSichern(id, datei, emptyList())

        val anhang = notes.get(id)!!.attachments.single()
        assertEquals("audio/wav", anhang.mimeType)
        assertEquals(datei.length(), anhang.sizeBytes)
        assertEquals(datei.absolutePath, anhang.localPath)
        assertTrue("Hash gehoert gesetzt", anhang.hash.isNotBlank())
    }

    @Test
    fun `ohne Datei gibt es keinen Anhang`() = runTest {
        // Eine Audiodatei mit null Sekunden Inhalt einzutragen waere schlimmer
        // als gar keine: sie saehe aus wie eine Aufnahme, die man abspielen
        // kann.
        val id = notes.create()
        audio.aufnahmeSichern(id, null, listOf(Triple("Text", 0L, 1_000L)))

        assertTrue(notes.get(id)!!.attachments.isEmpty())
    }

    @Test
    fun `eine leere Datei wird nicht eingetragen`() = runTest {
        val id = notes.create()
        val leer = dateiordner.newFile("leer.wav")

        audio.aufnahmeSichern(id, leer, emptyList())

        assertTrue(notes.get(id)!!.attachments.isEmpty())
    }

    @Test
    fun `Anhang und Transkript werden zum Abgleich vorgemerkt`() = runTest {
        val id = notes.create()
        audio.aufnahmeSichern(id, wav(), listOf(Triple("Gesprochenes", 0L, 2_000L)))

        val segment = audio.transkript(id).single()
        val anhang = notes.get(id)!!.attachments.single()

        assertNotNull(
            "Ohne Marke landet das Transkript nie beim zweiten Client",
            db.syncDao().stateOf(EntityType.TRANSCRIPT, segment.id),
        )
        assertNotNull(
            db.syncDao().stateOf(EntityType.ATTACHMENT, anhang.id),
        )
    }

    @Test
    fun `derselbe Inhalt ergibt denselben Hash`() = runTest {
        // Grundlage dafuer, dass Phase 9 unveraenderte Anhaenge nicht erneut
        // hochlaedt.
        val a = notes.create()
        val b = notes.create()
        audio.aufnahmeSichern(a, wav(1234), emptyList())
        audio.aufnahmeSichern(b, dateiordner.newFile("zweite.wav").apply {
            writeBytes(ByteArray(1234) { 0x10 })
        }, emptyList())

        assertEquals(
            notes.get(a)!!.attachments.single().hash,
            notes.get(b)!!.attachments.single().hash,
        )
    }

    @Test
    fun `das Transkript ist danach durchsuchbar`() = runTest {
        // DER Test dieser Klasse. SQLite haelt den FTS-Index nicht selbst
        // aktuell -- ohne reindex() waere ein Diktat unauffindbar, und zwar
        // ohne jede Fehlermeldung.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Besprechung", ""))
        audio.aufnahmeSichern(
            noteId = id,
            datei = null,
            abschnitte = listOf(Triple("Wir reden ueber den Bebauungsplan", 0L, 4_000L)),
        )

        val gefunden = db.searchDao().search("Bebauungsplan").map { it.note.id }
        assertTrue("Das Diktat muss auffindbar sein", gefunden.contains(id))
    }

    @Test
    fun `das Rohtranskript ueberlebt eine Aenderung des Fliesstextes`() = runTest {
        // Abschnitt 12: der aufgeraeumte Text landet im body, das Original
        // bleibt stehen. Wer beides zusammenlegt, verliert genau dann etwas,
        // wenn die Aufbereitung daneben lag.
        val id = notes.create()
        audio.aufnahmeSichern(id, null, listOf(Triple("aehm also der Termin ist Montag", 0L, 3_000L)))

        notes.updateContent(id, NoteContent("Termin", "Der Termin ist am Montag."))

        assertEquals(
            "aehm also der Termin ist Montag",
            audio.transkript(id).single().text,
        )
        assertFalse(notes.get(id)!!.note.body.contains("aehm"))
    }
}
