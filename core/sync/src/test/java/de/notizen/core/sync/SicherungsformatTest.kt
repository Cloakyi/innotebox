package de.notizen.core.sync

import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.sync.sicherung.ORDNER_ANHAENGE
import de.notizen.core.sync.sicherung.Sicherungsnotiz
import de.notizen.core.sync.sicherung.neuAuflegen
import de.notizen.core.sync.sicherung.Uebernahme
import de.notizen.core.sync.sicherung.anhangIdAus
import de.notizen.core.sync.sicherung.dateiname
import de.notizen.core.sync.sicherung.entscheiden
import de.notizen.core.sync.sicherung.sichererAnhangname
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

/**
 * Die Regeln der Sicherungsdatei, ohne Datei und ohne Datenbank.
 *
 * Zwei davon sind die Stellen, an denen es teuer wird, wenn sie falsch sind:
 * Wer beim Einlesen gewinnt, und was ein Eintragsname aus einem fremden Archiv
 * anrichten darf.
 */
class SicherungsformatTest {

    // ----------------------------------------------------- wer gewinnt

    @Test
    fun `was es hier nicht gibt, kommt neu dazu`() {
        assertEquals(Uebernahme.NEU, entscheiden(lokalUpdatedAt = null, ausDatei = 100))
    }

    @Test
    fun `der neuere Stand aus der Datei ersetzt den hiesigen`() {
        assertEquals(Uebernahme.ERSETZEN, entscheiden(lokalUpdatedAt = 100, ausDatei = 200))
    }

    @Test
    fun `ein aelterer Stand aus der Datei ueberschreibt nichts`() {
        assertEquals(Uebernahme.BEHALTEN, entscheiden(lokalUpdatedAt = 200, ausDatei = 100))
    }

    /**
     * Gleichstand ist kein Grund zu schreiben.
     *
     * Der naheliegende Fehler wäre `ausDatei >= lokalUpdatedAt`. Damit würde
     * beim Einlesen derselben Sicherung jede einzelne Notiz neu geschrieben,
     * neu indiziert und für den Abgleich vorgemerkt — Arbeit ohne jede Wirkung,
     * und in Google Drive landete anschließend der gesamte Bestand noch einmal.
     */
    @Test
    fun `bei gleichem Stand bleibt alles, wie es ist`() {
        assertEquals(Uebernahme.BEHALTEN, entscheiden(lokalUpdatedAt = 100, ausDatei = 100))
    }

    // ------------------------------------------- Namen aus fremden Archiven

    @Test
    fun `ein gewoehnlicher Anhangsname geht durch`() {
        assertEquals("abc.jpg", sichererAnhangname(ORDNER_ANHAENGE + "abc.jpg"))
    }

    /**
     * Der Grund, warum diese Funktion existiert.
     *
     * Ein Eintrag in einem ZIP-Archiv darf alles heißen. Wer den Namen
     * ungeprüft an einen Ordnerpfad hängt, schreibt dorthin, wohin das Archiv
     * zeigt, und nicht dorthin, wohin er wollte.
     */
    @Test
    fun `ein Name, der aus dem Ordner herausfuehrt, wird abgewiesen`() {
        assertNull(sichererAnhangname(ORDNER_ANHAENGE + "../../shared_prefs/etwas.xml"))
        assertNull(sichererAnhangname(ORDNER_ANHAENGE + "unterordner/bild.jpg"))
        assertNull(sichererAnhangname(ORDNER_ANHAENGE + ".."))
        assertNull(sichererAnhangname("../etwas.jpg"))
    }

    @Test
    fun `was nicht im Anhangordner liegt, ist kein Anhang`() {
        assertNull(sichererAnhangname("notes.json"))
        assertNull(sichererAnhangname(ORDNER_ANHAENGE))
    }

    @Test
    fun `die Kennung steht vor der Endung`() {
        assertEquals("abc-123", anhangIdAus("abc-123.jpg"))
        assertEquals("ohneEndung", anhangIdAus("ohneEndung"))
    }

    // ------------------------------------------- eine neue Kennung bekommen

    private fun doku(
        id: String = "alt",
        anhaenge: List<Anhangdokument> = emptyList(),
        flaeche: String? = null,
        geloescht: Long? = null,
    ) = Notizdokument(
        id = id,
        stage = Stage.ARCHIVE,
        type = NoteType.TEXT,
        title = "Titel",
        body = "Text",
        colorId = NoteColor.DEFAULT,
        isFavorite = true,
        favoritedAt = 5,
        createdAt = 1,
        updatedAt = 2,
        stageChangedAt = 3,
        deletedAt = geloescht,
        backgroundAttachmentId = flaeche,
        tagIds = listOf("t1"),
        attachments = anhaenge,
        items = listOf(Eintragdokument("i1", "Milch", false, 0)),
        transcripts = listOf(Transkriptdokument("s1", 0, 100, "gesprochen")),
        reminders = listOf(Erinnerungsdokument("r1", 999)),
    )

    /** Zaehlt hoch, damit im Test steht, was herauskommt. */
    private fun zaehler(): () -> String {
        var n = 0
        return { "neu${++n}" }
    }

    @Test
    fun `alles bekommt eine neue Kennung, nicht nur die Notiz`() {
        val eintrag = Sicherungsnotiz(
            doku(anhaenge = listOf(Anhangdokument("a1", "image/jpeg", 5, "h"))),
            lastOpenedAt = 111,
        )

        val neu = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler()).eintrag.notiz

        val kennungen = listOf(neu.id) +
            neu.attachments.map { it.id } +
            neu.items.map { it.id } +
            neu.transcripts.map { it.id } +
            neu.reminders.map { it.id }

        assertEquals(
            "Keine alte Kennung darf ueberleben",
            emptyList<String>(),
            kennungen.filterNot { it.startsWith("neu") },
        )
        assertEquals("Und keine doppelt", kennungen.size, kennungen.toSet().size)
    }

    /**
     * Der Verweis auf das Hintergrundbild muss mitwandern.
     *
     * Ohne das zeigte er auf den Anhang der Notiz, die geloescht wurde, und die
     * gibt es nicht mehr. Dieselbe Falle wie beim Kopieren einer Notiz.
     */
    @Test
    fun `der Verweis auf die Flaeche wandert mit`() {
        val eintrag = Sicherungsnotiz(
            doku(
                anhaenge = listOf(Anhangdokument("a1", "image/jpeg", 5, "h")),
                flaeche = "a1",
            ),
            lastOpenedAt = 111,
        )

        val auflage = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler())

        assertEquals(
            auflage.eintrag.notiz.attachments.single().id,
            auflage.eintrag.notiz.backgroundAttachmentId,
        )
        assertEquals(
            mapOf("a1" to auflage.eintrag.notiz.attachments.single().id),
            auflage.anhangKennungen,
        )
    }

    @Test
    fun `zeigt die Flaeche ins Leere, bleibt sie leer`() {
        val eintrag = Sicherungsnotiz(doku(flaeche = "weg"), lastOpenedAt = 111)

        val neu = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler()).eintrag.notiz

        assertNull(neu.backgroundAttachmentId)
    }

    @Test
    fun `sie landet im Eingang`() {
        val eintrag = Sicherungsnotiz(doku(), lastOpenedAt = 111)

        val auflage = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler())

        assertEquals(Stage.INBOX, auflage.eintrag.notiz.stage)
        assertEquals(5_000L, auflage.eintrag.notiz.stageChangedAt)
        // Sonst schoebe der naechste Aufraeumlauf sie sofort wieder weg.
        assertEquals(5_000L, auflage.eintrag.lastOpenedAt)
    }

    @Test
    fun `der Inhalt bleibt unangetastet`() {
        val eintrag = Sicherungsnotiz(doku(), lastOpenedAt = 111)

        val neu = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler()).eintrag.notiz

        assertEquals("Titel", neu.title)
        assertEquals("Text", neu.body)
        assertEquals(listOf("t1"), neu.tagIds)
        assertEquals("Milch", neu.items.single().text)
        assertEquals("gesprochen", neu.transcripts.single().text)
        // Der Zeitpunkt der letzten Aenderung ist eine Aussage ueber den
        // Inhalt, und der Inhalt ist derselbe.
        assertEquals(2L, neu.updatedAt)
    }

    /** Lag sie im Papierkorb, liegt sie danach wieder dort. */
    @Test
    fun `der Papierkorb bleibt der Papierkorb`() {
        val eintrag = Sicherungsnotiz(doku(geloescht = 777), lastOpenedAt = 111)

        val neu = neuAuflegen(eintrag, jetzt = 5_000, kennung = zaehler()).eintrag.notiz

        assertEquals(777L, neu.deletedAt)
    }

    // ------------------------------------------------------------ der Name

    @Test
    fun `der Dateiname traegt das Datum`() {
        val vorher = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            // 2026-08-23, 12:00 UTC
            assertEquals("innotebox-2026-08-23.notesbak", dateiname(1_787_486_400_000L))
        } finally {
            TimeZone.setDefault(vorher)
        }
    }
}
