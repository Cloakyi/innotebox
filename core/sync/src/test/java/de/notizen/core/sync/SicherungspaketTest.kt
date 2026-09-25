package de.notizen.core.sync

import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.sync.sicherung.EINTRAG_NOTIZEN
import de.notizen.core.sync.sicherung.ORDNER_ANHAENGE
import de.notizen.core.sync.sicherung.SICHERUNG_VERSION
import de.notizen.core.sync.sicherung.Sicherungskopf
import de.notizen.core.sync.sicherung.Sicherungsnotiz
import de.notizen.core.sync.sicherung.Sicherungspaket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Die Sicherungsdatei als solche.
 *
 * Geprueft wird hier ohne Datenbank und ohne Geraet: Was hineingegeben wurde,
 * muss unveraendert wieder herauskommen, und ein fremdes Archiv darf nicht
 * ausserhalb des vorgesehenen Ordners schreiben.
 */
class SicherungspaketTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private val paket = Sicherungspaket()

    @Test
    fun `was hineingeht, kommt wieder heraus`() = runTest {
        val bild = ordner.newFile("bild.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        val notiz = Sicherungsnotiz(
            notiz = notizdokument(
                id = "n1",
                anhaenge = listOf(Anhangdokument("a1", "image/jpeg", 5, "hash")),
            ),
            lastOpenedAt = 4711L,
            syncEnabled = false,
        )
        val tag = Tagdokument("t1", "Reise", 0x112233, createdAt = 1, updatedAt = 2)
        val ordnerdokument = Ordnerdokument(
            id = "o1",
            name = "Reisen",
            createdAt = 1,
            updatedAt = 2,
        )

        val roh = ByteArrayOutputStream()
        val bericht = paket.packen(
            ziel = roh,
            kopf = kopf(notizen = 1, tags = 1),
            tags = listOf(tag),
            ordner = listOf(ordnerdokument),
            notizIds = listOf("n1"),
            notizFuer = { notiz },
            dateiFuer = { bild },
        )

        assertEquals(1, bericht.notizen)
        assertEquals(1, bericht.tags)
        assertEquals(1, bericht.ordner)
        assertEquals(1, bericht.dateien)

        val ziel = ordner.newFolder("ausgepackt")
        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { name ->
            File(ziel, name)
        }

        assertEquals(1, inhalt.kopf?.notizen)
        assertEquals(listOf(tag), inhalt.tags)
        assertEquals(listOf(ordnerdokument), inhalt.ordner)
        assertEquals(listOf(notiz), inhalt.notizen)

        // Die geraetelokalen Felder sind der Grund fuer die Huelle um das
        // Notizdokument. Gingen sie verloren, saehe nach dem Wiederherstellen
        // jede Notiz aus, als sei sie nie geoeffnet worden.
        assertEquals(4711L, inhalt.notizen.single().lastOpenedAt)
        assertEquals(false, inhalt.notizen.single().syncEnabled)

        val heraus = inhalt.dateien["a1"]
        assertNotNull(heraus)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), heraus!!.readBytes().toList())
    }

    @Test
    fun `mehrere Notizen ergeben gueltiges JSON`() = runTest {
        val notizen = (1..3).map { Sicherungsnotiz(notizdokument("n$it"), lastOpenedAt = 0) }

        val roh = ByteArrayOutputStream()
        paket.packen(
            ziel = roh,
            kopf = kopf(notizen = 3, tags = 0),
            tags = emptyList(),
            ordner = emptyList(),
            notizIds = notizen.map { it.notiz.id },
            notizFuer = { id -> notizen.first { it.notiz.id == id } },
            dateiFuer = { null },
        )

        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { null }
        assertEquals(listOf("n1", "n2", "n3"), inhalt.notizen.map { it.notiz.id })
    }

    @Test
    fun `ohne Notizen bleibt notes_json trotzdem ein leeres Feld`() = runTest {
        val roh = ByteArrayOutputStream()
        paket.packen(
            ziel = roh,
            kopf = kopf(notizen = 0, tags = 0),
            tags = emptyList(),
            ordner = emptyList(),
            notizIds = emptyList(),
            notizFuer = { null },
            dateiFuer = { null },
        )

        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { null }
        assertTrue(inhalt.notizen.isEmpty())
        assertNotNull(inhalt.kopf)
    }

    /**
     * Eine Anhangsdatei, die es nicht mehr gibt, bricht nichts ab.
     *
     * Die Zeile steht dann in der Notiz und ihre Datei fehlt. Das ist genau der
     * Zustand, in dem die App ohnehin schon war, und eine Sicherung, die daran
     * scheitert, waere die schlechteste aller Antworten darauf.
     */
    @Test
    fun `ein Anhang ohne Datei laesst die Sicherung weiterlaufen`() = runTest {
        val notiz = Sicherungsnotiz(
            notiz = notizdokument(
                id = "n1",
                anhaenge = listOf(Anhangdokument("a1", "image/jpeg", 5, "hash")),
            ),
            lastOpenedAt = 0,
        )

        val roh = ByteArrayOutputStream()
        val bericht = paket.packen(
            ziel = roh,
            kopf = kopf(notizen = 1, tags = 0),
            tags = emptyList(),
            ordner = emptyList(),
            notizIds = listOf("n1"),
            notizFuer = { notiz },
            dateiFuer = { null },
        )

        assertEquals(1, bericht.notizen)
        assertEquals(0, bericht.dateien)

        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { null }
        assertEquals(listOf(notiz), inhalt.notizen)
        assertTrue(inhalt.dateien.isEmpty())
    }

    /**
     * Ein fremdes Archiv darf nicht bestimmen, wohin geschrieben wird.
     *
     * Ohne die Pruefung landete der Eintrag zwei Ordner hoeher. Der Nutzer
     * waehlt die Datei selbst aus, aber er hat sie nicht unbedingt selbst
     * geschrieben.
     */
    @Test
    fun `ein Eintrag, der aus dem Ordner herausfuehrt, wird nicht geschrieben`() {
        val roh = ByteArrayOutputStream()
        ZipOutputStream(roh).use { zip ->
            zip.putNextEntry(ZipEntry(ORDNER_ANHAENGE + "../entwischt.txt"))
            zip.write("nein".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(EINTRAG_NOTIZEN))
            zip.write("[]".toByteArray())
            zip.closeEntry()
        }

        var gefragt: String? = null
        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { name ->
            gefragt = name
            null
        }

        assertNull("Der Name darf gar nicht erst weitergereicht werden", gefragt)
        assertEquals(1, inhalt.abgewiesen)
        assertTrue(inhalt.dateien.isEmpty())
    }

    /** Eine beliebige Datei ist keine Sicherung. */
    @Test
    fun `aus etwas anderem kommt nichts heraus`() {
        val inhalt = paket.entpacken(ByteArrayInputStream("kein ZIP".toByteArray())) { null }
        assertNull(inhalt.kopf)
        assertTrue(inhalt.notizen.isEmpty())
    }

    // ------------------------------------------------------------ Fassung 4

    /**
     * Fassung 4: Die Felder aus Schema 5 gehen mit. Ohne sie laege
     * nach dem Wiederherstellen jede im Ordnermodus archivierte Notiz wieder
     * im normalen Baum, und jeder Archivordner stuende in der Seitenspalte.
     */
    @Test
    fun `das Archiv des Ordnermodus ueberlebt Sichern und Einlesen`() = runTest {
        val notiz = Sicherungsnotiz(
            notiz = notizdokument("n1").copy(
                ordnerArchiviertAt = 77L,
                herkunftOrdnerId = "o-alt",
                ehemaligerOrdnerId = "o-weg",
            ),
            lastOpenedAt = 1,
        )
        val archivordner = Ordnerdokument(
            id = "o1", name = "Alt", createdAt = 1, updatedAt = 2,
            bereich = Bereich.ARCHIV, ehemaligerElternId = "o-weg",
        )

        val roh = ByteArrayOutputStream()
        paket.packen(
            ziel = roh,
            kopf = kopf(notizen = 1, tags = 0),
            tags = emptyList(),
            ordner = listOf(archivordner),
            notizIds = listOf("n1"),
            notizFuer = { notiz },
            dateiFuer = { null },
        )
        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { null }

        assertEquals(SICHERUNG_VERSION, inhalt.kopf?.formatVersion)
        assertEquals(4, SICHERUNG_VERSION)
        assertEquals(77L, inhalt.notizen.single().notiz.ordnerArchiviertAt)
        assertEquals("o-alt", inhalt.notizen.single().notiz.herkunftOrdnerId)
        assertEquals("o-weg", inhalt.notizen.single().notiz.ehemaligerOrdnerId)
        assertEquals(Bereich.ARCHIV, inhalt.ordner.single().bereich)
        assertEquals("o-weg", inhalt.ordner.single().ehemaligerElternId)
    }

    /** Eine Datei aus Fassung 3 kennt die Felder nicht und liest sich trotzdem. */
    @Test
    fun `eine Sicherung aus Fassung 3 liest sich als nicht archiviert`() {
        val roh = ByteArrayOutputStream()
        ZipOutputStream(roh).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                """{"formatVersion":3,"schemaVersion":4,"erzeugtAm":1,"erzeugtVon":"alt",
                   "notizen":1,"tags":0,"ordner":1}""".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("folders.json"))
            zip.write("""[{"id":"o1","name":"Reisen","createdAt":1,"updatedAt":2}]""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(EINTRAG_NOTIZEN))
            zip.write(
                """[{"notiz":{"schemaVersion":4,"id":"n1","stage":"INBOX","type":"TEXT",
                   "title":"Alt","body":"","colorId":"DEFAULT","isFavorite":false,
                   "createdAt":1,"updatedAt":2,"stageChangedAt":1},"lastOpenedAt":3}]""".toByteArray(),
            )
            zip.closeEntry()
        }

        val inhalt = paket.entpacken(ByteArrayInputStream(roh.toByteArray())) { null }

        assertEquals(3, inhalt.kopf?.formatVersion)
        assertNull(inhalt.notizen.single().notiz.ordnerArchiviertAt)
        assertNull(inhalt.notizen.single().notiz.ehemaligerOrdnerId)
        assertEquals(Bereich.ORDNER, inhalt.ordner.single().bereich)
        assertNull(inhalt.ordner.single().ehemaligerElternId)
    }

    // ---------------------------------------------------------------- Hilfen

    private fun kopf(notizen: Int, tags: Int) = Sicherungskopf(
        erzeugtAm = 1_787_486_400_000L,
        erzeugtVon = "Test",
        notizen = notizen,
        tags = tags,
    )

    private fun notizdokument(
        id: String,
        anhaenge: List<Anhangdokument> = emptyList(),
    ) = Notizdokument(
        id = id,
        stage = Stage.INBOX,
        type = NoteType.TEXT,
        title = "Titel $id",
        body = "Text mit Umlauten: Küche, Äpfel, Straße",
        colorId = NoteColor.DEFAULT,
        isFavorite = false,
        createdAt = 1,
        updatedAt = 2,
        stageChangedAt = 1,
        attachments = anhaenge,
    )
}
