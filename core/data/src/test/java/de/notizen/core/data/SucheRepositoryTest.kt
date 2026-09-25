package de.notizen.core.data

import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.repository.TREFFER_AUF
import de.notizen.core.data.repository.TREFFER_ZU
import de.notizen.core.data.search.Suchanfrage
import de.notizen.core.data.search.Zeitraum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suche mit Filtern.
 *
 * Den Volltext findet SQLite, gefiltert wird in Kotlin -- die Begründung steht
 * im `SucheRepository`. Geprüft wird beides zusammen, weil nur das Zusammenspiel
 * das Ergebnis ausmacht, das auf dem Bildschirm landet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SucheRepositoryTest : DatenbankTestbasis() {

    private suspend fun treffer(anfrage: Suchanfrage) =
        suche.suche(anfrage).first().map { it.notiz.note.id }

    private suspend fun notiz(
        titel: String,
        text: String = "",
        stufe: Stage = Stage.INBOX,
        farbe: NoteColor = NoteColor.DEFAULT,
        typ: NoteType = NoteType.TEXT,
        favorit: Boolean = false,
    ): String {
        val id = notes.create(typ)
        notes.updateContent(id, NoteContent(titel, text))
        if (stufe != Stage.INBOX) notes.moveTo(listOf(id), stufe)
        if (farbe != NoteColor.DEFAULT) notes.setColor(listOf(id), farbe)
        if (favorit) notes.setFavorite(listOf(id), true)
        return id
    }

    // ------------------------------------------------------------ Volltext

    @Test
    fun `ohne Suchtext und ohne Filter kommt alles`() = runTest {
        val a = notiz("Eins")
        val b = notiz("Zwei", stufe = Stage.WORKSPACE)

        val alle = treffer(Suchanfrage())
        assertTrue(alle.containsAll(listOf(a, b)))
    }

    @Test
    fun `die Suche geht ueber alle Stufen`() = runTest {
        // Der Punkt der ganzen App: gesucht wird, nicht navigiert. Eine Suche,
        // die nur die gerade offene Stufe durchsieht, waere Navigation mit
        // Textfeld.
        val archiviert = notiz("Steuerbescheid", stufe = Stage.ARCHIVE)
        assertTrue(treffer(Suchanfrage(text = "Steuerbescheid")).contains(archiviert))
    }

    @Test
    fun `Papierkorb bleibt aussen vor`() = runTest {
        val id = notiz("Verworfenes")
        notes.trash(listOf(id))
        assertFalse(treffer(Suchanfrage(text = "Verworfenes")).contains(id))
    }

    // -------------------------------------------------------------- Filter

    @Test
    fun `Stufenfilter schraenkt ein`() = runTest {
        val eingang = notiz("Bericht")
        val archiv = notiz("Bericht", stufe = Stage.ARCHIVE)

        val nurArchiv = treffer(Suchanfrage(text = "Bericht", stufen = setOf(Stage.ARCHIVE)))
        assertEquals(listOf(archiv), nurArchiv)
        assertFalse(nurArchiv.contains(eingang))
    }

    @Test
    fun `mehrere Werte einer Kategorie bedeuten oder`() = runTest {
        val rot = notiz("Ampel", farbe = NoteColor.RED)
        val gruen = notiz("Ampel", farbe = NoteColor.GREEN)
        val blau = notiz("Ampel", farbe = NoteColor.BLUE)

        val gewaehlt = treffer(
            Suchanfrage(text = "Ampel", farben = setOf(NoteColor.RED, NoteColor.GREEN)),
        )
        assertTrue(gewaehlt.containsAll(listOf(rot, gruen)))
        assertFalse(gewaehlt.contains(blau))
    }

    @Test
    fun `verschiedene Kategorien bedeuten und`() = runTest {
        val beides = notiz("Termin", stufe = Stage.WORKSPACE, favorit = true)
        val nurStufe = notiz("Termin", stufe = Stage.WORKSPACE)
        val nurFavorit = notiz("Termin", favorit = true)

        val gewaehlt = treffer(
            Suchanfrage(
                text = "Termin",
                stufen = setOf(Stage.WORKSPACE),
                nurFavoriten = true,
            ),
        )
        assertEquals(listOf(beides), gewaehlt)
        assertFalse(gewaehlt.contains(nurStufe))
        assertFalse(gewaehlt.contains(nurFavorit))
    }

    @Test
    fun `Tagfilter greift ueber die Zuordnung`() = runTest {
        val mit = notiz("Rechnung")
        val ohne = notiz("Rechnung")
        val tag = tags.create("Steuer", 0xFF4285F4.toInt())
        notes.setTags(mit, listOf(tag.id))

        val gewaehlt = treffer(Suchanfrage(text = "Rechnung", tagIds = setOf(tag.id)))
        assertEquals(listOf(mit), gewaehlt)
        assertFalse(gewaehlt.contains(ohne))
    }

    @Test
    fun `Typfilter unterscheidet Notizarten`() = runTest {
        val liste = notiz("Reise", typ = NoteType.LIST)
        val text = notiz("Reise", typ = NoteType.TEXT)

        val gewaehlt = treffer(Suchanfrage(text = "Reise", typen = setOf(NoteType.LIST)))
        assertEquals(listOf(liste), gewaehlt)
        assertFalse(gewaehlt.contains(text))
    }

    @Test
    fun `Zeitraum misst an updatedAt und nicht am Oeffnen`() = runTest {
        val alt = notiz("Altes")
        clock.advanceBy(10 * EIN_TAG)
        val neu = notiz("Neues")

        // Die alte Notiz wird nur ANGESEHEN. Ansehen ist nicht Bearbeiten --
        // sie darf dadurch nicht in den Zeitraum rutschen.
        notes.open(alt)

        val letzteWoche = treffer(Suchanfrage(zeitraum = Zeitraum.WOCHE))
        assertTrue(letzteWoche.contains(neu))
        assertFalse("Oeffnen darf nicht wie Bearbeiten zaehlen", letzteWoche.contains(alt))
    }

    @Test
    fun `Filter allein ohne Suchtext funktioniert`() = runTest {
        val favorit = notiz("Wichtig", favorit = true)
        val gewoehnlich = notiz("Beilaeufig")

        val gewaehlt = treffer(Suchanfrage(nurFavoriten = true))
        assertEquals(listOf(favorit), gewaehlt)
        assertFalse(gewaehlt.contains(gewoehnlich))
    }

    // ---------------------------------------------------------- Ausschnitt

    @Test
    fun `Treffer bringen einen markierten Ausschnitt mit`() = runTest {
        notiz("Protokoll", "Wir haben lange ueber den Bebauungsplan gesprochen")

        val ausschnitt = suche.suche(Suchanfrage(text = "Bebauungsplan"))
            .first().single().ausschnitt

        assertTrue(
            "Die Fundstelle muss eingefasst sein, sonst kann die UI nichts hervorheben",
            ausschnitt!!.contains("$TREFFER_AUF"),
        )
        assertTrue(ausschnitt.contains("$TREFFER_ZU"))
    }

    @Test
    fun `ohne Suchtext gibt es keinen erfundenen Ausschnitt`() = runTest {
        notiz("Wichtig", "Irgendein Text", favorit = true)

        val treffer = suche.suche(Suchanfrage(nurFavoriten = true)).first().single()
        assertNull(
            "Ohne Suchbegriff gibt es keine Fundstelle -- also auch keinen Ausschnitt",
            treffer.ausschnitt,
        )
    }

    // -------------------------------------------------------- Ordner (14d)

    @Test
    fun `ein gewaehlter Ordner schliesst seine Unterordner ein`() = runTest {
        val oben = ordner.anlegen("Arbeit")
        val unten = ordner.anlegen("Projekte", oben.id)
        val andere = ordner.anlegen("Privat")
        val inOben = notiz("Oben")
        val inUnten = notiz("Unten")
        val inAndere = notiz("Andere")
        val ohne = notiz("Hauptordner")
        notes.setOrdner(listOf(inOben), oben.id)
        notes.setOrdner(listOf(inUnten), unten.id)
        notes.setOrdner(listOf(inAndere), andere.id)

        val gefunden = treffer(Suchanfrage(ordnerIds = setOf(oben.id)))

        assertTrue(gefunden.contains(inOben))
        assertTrue("der Unterordner gehoert dazu", gefunden.contains(inUnten))
        assertFalse(gefunden.contains(inAndere))
        assertFalse(gefunden.contains(ohne))
    }

    @Test
    fun `Ordner und Stufe filtern zusammen`() = runTest {
        val einer = ordner.anlegen("Arbeit")
        val eingang = notiz("Im Eingang")
        val workspace = notiz("Im Workspace", stufe = Stage.WORKSPACE)
        notes.setOrdner(listOf(eingang, workspace), einer.id)

        val gefunden = treffer(Suchanfrage(ordnerIds = setOf(einer.id), stufen = setOf(Stage.WORKSPACE)))

        assertEquals(listOf(workspace), gefunden)
    }

    // ---------------------------------------------------- Papierkorb (14d)

    @Test
    fun `der Papierkorb bleibt draussen, solange der Filter aus ist`() = runTest {
        val weg = notiz("Weggeworfen", "Bebauungsplan")
        notes.trash(listOf(weg))

        assertFalse(treffer(Suchanfrage(text = "Bebauungsplan")).contains(weg))
        assertFalse(treffer(Suchanfrage()).contains(weg))
    }

    @Test
    fun `mit dem Filter kommt der Papierkorb dazu, ueber seinen Text gesucht`() = runTest {
        val lebend = notiz("Lebend", "Bebauungsplan")
        val weg = notiz("Weggeworfen", "Bebauungsplan im Papierkorb")
        val anderes = notiz("Anderes", "Nichts davon")
        notes.trash(listOf(weg, anderes))

        val gefunden = suche.suche(Suchanfrage(text = "bebauungs", mitPapierkorb = true)).first()

        assertEquals(listOf(lebend, weg), gefunden.map { it.notiz.note.id })
        assertTrue(gefunden.last().imPapierkorb)
        assertFalse(gefunden.first().imPapierkorb)
        assertNull("kein Ausschnitt fuer den Papierkorb, er steht nicht im Index", gefunden.last().ausschnitt)
    }

    @Test
    fun `im Papierkorb muessen alle Woerter vorkommen, auch in Listeneintraegen`() = runTest {
        val id = notes.create(NoteType.LIST)
        val eintrag = de.notizen.core.data.db.entity.NoteItemEntity(
            java.util.UUID.randomUUID().toString(), id, "Zahnbürste", false, 0,
        )
        notes.updateContent(id, NoteContent("Packliste", "Für die Reise", listOf(eintrag)))
        notes.trash(listOf(id))

        assertTrue(treffer(Suchanfrage(text = "Reise zahnbürste", mitPapierkorb = true)).contains(id))
        assertFalse(treffer(Suchanfrage(text = "Reise Handtuch", mitPapierkorb = true)).contains(id))
    }
}
