package de.notizen.core.data

import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.ordner.darfHinein
import de.notizen.core.data.ordner.gesamtzahlen
import de.notizen.core.data.ordner.imBereich
import de.notizen.core.data.ordner.kinder
import de.notizen.core.data.ordner.mitKindern
import de.notizen.core.data.ordner.moeglicheZiele
import de.notizen.core.data.ordner.nachkommen
import de.notizen.core.data.ordner.pfad
import de.notizen.core.data.ordner.Rueckkehr
import de.notizen.core.data.ordner.rueckkehr
import de.notizen.core.data.ordner.sichtbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Rechenregeln des Ordnerbaums.
 *
 * Zwei Dinge stehen hier im Mittelpunkt, und beide fallen im Alltag nicht auf,
 * bis sie auffallen: ein Ordner, der in sich selbst wandert, und ein Ordner,
 * dessen Eltern es nicht mehr gibt. Der erste zerschneidet den Baum, der
 * zweite laesst einen ganzen Ast unsichtbar werden.
 */
class OrdnerregelnTest {

    private fun ordner(
        id: String,
        name: String = id,
        eltern: String? = null,
        sortIndex: Int = 0,
    ) = FolderEntity(
        id = id,
        parentId = eltern,
        name = name,
        sortIndex = sortIndex,
        createdAt = 0,
        updatedAt = 0,
    )

    /**
     *   arbeit
     *     projekte
     *       kunde
     *   privat
     */
    private val baumbestand = listOf(
        ordner("arbeit", "Arbeit"),
        ordner("projekte", "Projekte", eltern = "arbeit"),
        ordner("kunde", "Kunde", eltern = "projekte"),
        ordner("privat", "Privat"),
    )

    // ------------------------------------------------------------- Ebenen

    @Test
    fun `die oberste Ebene sind die Ordner ohne Eltern`() {
        assertEquals(
            listOf("arbeit", "privat"),
            kinder(baumbestand, null).map { it.id },
        )
    }

    @Test
    fun `ein Ordner kennt seine unmittelbaren Kinder`() {
        assertEquals(listOf("projekte"), kinder(baumbestand, "arbeit").map { it.id })
    }

    /**
     * Ein Ordner, dessen Elternteil fehlt, haengt an der Wurzel.
     *
     * Sonst waere er unsichtbar, und mit ihm alles, was darin liegt. Der Fall
     * ist nicht ausgedacht: Beim Abgleich kann ein Kind vor seinem Elternteil
     * ankommen, und ein Elternteil kann auf dem anderen Geraet geloescht
     * worden sein.
     */
    @Test
    fun `ein Ordner ohne auffindbare Eltern haengt an der Wurzel`() {
        val waise = listOf(ordner("kind", "Kind", eltern = "gibtesnicht"))

        assertEquals(listOf("kind"), kinder(waise, null).map { it.id })
    }

    @Test
    fun `unter Geschwistern zaehlt erst die Sortierung, dann der Name`() {
        val bestand = listOf(
            ordner("b", "Beta", sortIndex = 1),
            ordner("c", "Alpha", sortIndex = 2),
            ordner("a", "Zeta", sortIndex = 1),
        )

        // Sortierung 1 vor 2; innerhalb der 1 entscheidet der Name.
        assertEquals(listOf("b", "a", "c"), kinder(bestand, null).map { it.id })
    }

    // --------------------------------------------------------- Nachkommen

    @Test
    fun `Nachkommen reichen beliebig tief`() {
        assertEquals(setOf("projekte", "kunde"), nachkommen(baumbestand, "arbeit"))
    }

    @Test
    fun `ein Blatt hat keine Nachkommen`() {
        assertTrue(nachkommen(baumbestand, "kunde").isEmpty())
    }

    /** Zwei Ordner, die aufeinander zeigen, duerfen die Suche nicht aufhaengen. */
    @Test
    fun `ein Kreis im Baum bringt die Suche nicht zum Haengen`() {
        val kreis = listOf(
            ordner("a", eltern = "b"),
            ordner("b", eltern = "a"),
        )

        assertEquals(setOf("a", "b"), nachkommen(kreis, "a"))
    }

    // --------------------------------------------------------------- Pfad

    @Test
    fun `der Pfad geht von der Wurzel bis zum Ordner`() {
        assertEquals(
            listOf("arbeit", "projekte", "kunde"),
            pfad(baumbestand, "kunde").map { it.id },
        )
    }

    @Test
    fun `der Hauptordner hat keinen Pfad`() {
        assertTrue(pfad(baumbestand, null).isEmpty())
    }

    @Test
    fun `ein unbekannter Ordner hat keinen Pfad`() {
        assertTrue(pfad(baumbestand, "gibtesnicht").isEmpty())
    }

    @Test
    fun `ein Kreis bricht den Pfad ab, statt ewig zu laufen`() {
        val kreis = listOf(
            ordner("a", eltern = "b"),
            ordner("b", eltern = "a"),
        )

        assertEquals(2, pfad(kreis, "a").size)
    }

    // --------------------------------------------------------------- Baum

    /**
     * Tiefensuche, nicht Ebene fuer Ebene.
     *
     * Ein Ordner steht unmittelbar unter seinem Elternteil. Bei einer
     * Breitensuche stuenden erst alle obersten Ordner da und die Unterordner
     * ganz am Ende, was in einer eingerueckten Liste niemand mehr zuordnet.
     */
    @Test
    fun `der Baum steht in Tiefensuche mit Einrueckung da`() {
        assertEquals(
            listOf("arbeit" to 0, "projekte" to 1, "kunde" to 2, "privat" to 0),
            baum(baumbestand).map { it.ordner.id to it.tiefe },
        )
    }

    @Test
    fun `ein Kreis sprengt den Baum nicht`() {
        val kreis = listOf(
            ordner("a", eltern = "b"),
            ordner("b", eltern = "a"),
            ordner("frei", "Frei"),
        )

        // Der freie Ordner muss auf jeden Fall dastehen; die beiden im Kreis
        // haengen an keiner Wurzel und tauchen deshalb gar nicht auf.
        assertTrue(baum(kreis).any { it.ordner.id == "frei" })
    }

    // ------------------------------------------------- Aufklappen im Baum

    @Test
    fun `zugeklappt zeigt nur die oberste Ebene`() {
        assertEquals(
            listOf("arbeit", "privat"),
            sichtbar(baum(baumbestand), emptySet()).map { it.ordner.id },
        )
    }

    @Test
    fun `ein aufgeklappter Ordner zeigt seine Kinder`() {
        assertEquals(
            listOf("arbeit", "projekte", "privat"),
            sichtbar(baum(baumbestand), setOf("arbeit")).map { it.ordner.id },
        )
    }

    /**
     * Ein Enkel bleibt verborgen, solange sein Elternteil zu ist.
     *
     * Der Fall entsteht beim Zuklappen mitten im Baum: Die Kennung des Enkels
     * steht dann noch in der Menge der aufgeklappten Ordner, sichtbar ist er
     * trotzdem nicht.
     */
    @Test
    fun `ein Enkel bleibt verborgen, wenn sein Elternteil zu ist`() {
        assertEquals(
            listOf("arbeit", "privat"),
            sichtbar(baum(baumbestand), setOf("projekte", "kunde")).map { it.ordner.id },
        )
    }

    @Test
    fun `alles aufgeklappt zeigt den ganzen Baum`() {
        assertEquals(
            listOf("arbeit", "projekte", "kunde", "privat"),
            sichtbar(baum(baumbestand), setOf("arbeit", "projekte")).map { it.ordner.id },
        )
    }

    /** Nur wer Kinder hat, bekommt ein Zeichen zum Aufklappen. */
    @Test
    fun `nur Ordner mit Kindern zaehlen`() {
        assertEquals(setOf("arbeit", "projekte"), mitKindern(baumbestand))
    }

    @Test
    fun `ein Ordner mit verschwundenen Eltern macht sie nicht zu Eltern`() {
        val waise = listOf(ordner("kind", eltern = "gibtesnicht"))

        assertTrue(mitKindern(waise).isEmpty())
    }

    // ---------------------------------------------------------- Verschieben

    @Test
    fun `ein Ordner darf nicht in sich selbst`() {
        assertFalse(darfHinein(baumbestand, "arbeit", "arbeit"))
    }

    @Test
    fun `ein Ordner darf nicht in seinen eigenen Unterordner`() {
        assertFalse(darfHinein(baumbestand, "arbeit", "kunde"))
    }

    @Test
    fun `ein Ordner darf zu einem Geschwister`() {
        assertTrue(darfHinein(baumbestand, "arbeit", "privat"))
    }

    @Test
    fun `nach ganz oben darf immer`() {
        assertTrue(darfHinein(baumbestand, "kunde", null))
    }

    /**
     * Das Archiv des Ordnermodus ist ein eigener Baum (SYNC.md 13). Ein
     * Archivordner im normalen Baum laege dort, wo seine Notizen nicht zu
     * sehen sind, und umgekehrt.
     */
    @Test
    fun `ein Ordner wechselt nie den Bereich`() {
        val beide = baumbestand + listOf(
            ordner("alt", "Alt").copy(bereich = Bereich.ARCHIV),
            ordner("alt-2026", "2026", eltern = "alt").copy(bereich = Bereich.ARCHIV),
        )

        assertFalse("Archiv in den normalen Baum", darfHinein(beide, "alt", "privat"))
        assertFalse("normaler Ordner ins Archiv", darfHinein(beide, "privat", "alt"))
        assertTrue("innerhalb des Archivs", darfHinein(beide, "alt-2026", "alt"))
        assertTrue("ans obere Ende des eigenen Baums", darfHinein(beide, "alt-2026", null))
        assertEquals(
            listOf("alt"),
            moeglicheZiele(beide, "alt-2026").map { it.ordner.id },
        )
    }

    @Test
    fun `ein Ziel, das es nicht gibt, gilt als fremder Bereich`() {
        assertFalse(darfHinein(baumbestand, "privat", "gibt-es-nicht"))
    }

    // ------------------------------------------------- Zurueck aus dem Archiv

    @Test
    fun `aus dem Hauptordner archiviert heisst zurueck in den Hauptordner`() {
        assertEquals(Rueckkehr.Hauptordner, rueckkehr(baumbestand, null))
    }

    @Test
    fun `ein lebender Herkunftsordner wird mit seinem Pfad vorgeschlagen`() {
        val r = rueckkehr(baumbestand, "kunde")
        assertTrue(r is Rueckkehr.Vorhanden)
        r as Rueckkehr.Vorhanden
        assertEquals("kunde", r.ordner.id)
        assertEquals(listOf("arbeit", "projekte", "kunde"), r.pfad.map { it.id })
    }

    @Test
    fun `ein geloeschter Herkunftsordner nennt den Pfad und den naechsten lebenden`() {
        val mitGeloeschtem = baumbestand.map {
            if (it.id == "projekte") it.copy(deletedAt = 5) else it
        }
        val r = rueckkehr(mitGeloeschtem, "projekte")
        assertTrue(r is Rueckkehr.Fehlt)
        r as Rueckkehr.Fehlt
        assertEquals(listOf("arbeit", "projekte"), r.pfad.map { it.id })
        assertTrue(r.wiederherstellbar)
        assertEquals("arbeit", r.naechster?.id)
    }

    @Test
    fun `sind alle Vorfahren geloescht, ist der naechste der Hauptordner`() {
        val alleWeg = baumbestand.map {
            if (it.id in setOf("arbeit", "projekte", "kunde")) it.copy(deletedAt = 5) else it
        }
        val r = rueckkehr(alleWeg, "kunde") as Rueckkehr.Fehlt
        assertEquals(null, r.naechster)
        assertEquals(listOf("arbeit", "projekte", "kunde"), r.pfad.map { it.id })
    }

    @Test
    fun `ein endgueltig entfernter Herkunftsordner laesst sich nicht neu anlegen`() {
        val r = rueckkehr(baumbestand, "gibt-es-nicht-mehr") as Rueckkehr.Fehlt
        assertFalse(r.wiederherstellbar)
        assertTrue(r.pfad.isEmpty())
        assertEquals(null, r.naechster)
    }

    @Test
    fun `imBereich trennt die beiden Baeume`() {
        val beide = baumbestand + ordner("alt", "Alt").copy(bereich = Bereich.ARCHIV)
        assertEquals(listOf("alt"), imBereich(beide, Bereich.ARCHIV).map { it.id })
        assertEquals(baumbestand.map { it.id }, imBereich(beide, Bereich.ORDNER).map { it.id })
    }

    @Test
    fun `unmoegliche Ziele stehen gar nicht erst zur Wahl`() {
        assertEquals(
            listOf("privat"),
            moeglicheZiele(baumbestand, "arbeit").map { it.ordner.id },
        )
    }

    // ------------------------------------------------------------ Zaehlung

    /**
     * Ein Ordner, der selbst nichts enthaelt, aber volle Unterordner hat, ist
     * nicht leer -- und darf nicht mit einer Null dastehen.
     */
    @Test
    fun `die Zahl eines Ordners zaehlt seine Unterordner mit`() {
        val direkt = mapOf("kunde" to 3, "privat" to 1)
        val gesamt = gesamtzahlen(baumbestand, direkt)

        assertEquals(3, gesamt["arbeit"])
        assertEquals(3, gesamt["projekte"])
        assertEquals(3, gesamt["kunde"])
        assertEquals(1, gesamt["privat"])
    }

    @Test
    fun `ein Ordner ohne alles steht bei null`() {
        assertEquals(0, gesamtzahlen(baumbestand, emptyMap())["arbeit"])
    }
}
