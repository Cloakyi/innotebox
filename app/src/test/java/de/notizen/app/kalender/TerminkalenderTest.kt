package de.notizen.app.kalender

import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.db.relation.Notiztermin
import de.notizen.core.data.kalender.Serie
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Was der Kalender zeigt und wie er es anordnet. */
class TerminkalenderTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val tag: LocalDate = LocalDate.of(2026, 11, 3)

    private fun um(stunde: Int, minute: Int = 0) = Terminregeln.zeitpunkt(tag, LocalTime.of(stunde, minute), berlin)

    private val kalender = listOf(
        EventCalendarEntity("k1", "Mein Kalender", 0xFF4285F4.toInt(), 0, 1, 1),
        EventCalendarEntity("k2", "Arbeit", 0xFF0B8043.toInt(), 1, 1, 1),
    )

    private fun termin(id: String, k: String, von: Long, bis: Long) = EventEntity(
        id = id, calendarId = k, title = id, startAt = von, endAt = bis, timeZone = "Europe/Berlin",
        createdAt = 0, updatedAt = 0,
    )

    private fun eintrag(id: String, von: Long, bis: Long) =
        eintraege(listOf(termin(id, "k1", von, bis)), kalender, emptySet(), emptyList(), tag.minusDays(1), tag.plusDays(2), berlin).single()

    private fun kaskade(vararg e: Eintrag) = kaskade(e.toList(), um(0), um(23, 59), 0).associateBy { it.eintrag.titel }

    private fun assertNahe(erwartet: Float, ist: Float) = assertEquals(erwartet, ist, 0.001f)

    // ------------------------------------------------------------- Ebenen

    @Test
    fun `ausgeblendete Kalender und ihre Termine fallen heraus`() {
        val termine = listOf(termin("a", "k1", um(9), um(10)), termin("b", "k2", um(11), um(12)))
        val notiz = Notiztermin("n", "Notiz", NoteColor.DEFAULT, um(8), false)
        val alle = eintraege(termine, kalender, emptySet(), listOf(notiz), tag, tag.plusDays(1), berlin)
        assertEquals(listOf("Notiz", "a", "b"), alle.map { it.titel })
        val ohne = eintraege(termine, kalender, setOf("k2", EBENE_NOTIZEN), listOf(notiz), tag, tag.plusDays(1), berlin)
        assertEquals(listOf("a"), ohne.map { it.titel })
    }

    @Test
    fun `ein Termin ohne Kalender erscheint nicht`() {
        val alle = eintraege(listOf(termin("x", "weg", um(9), um(10))), kalender, emptySet(), emptyList(), tag, tag.plusDays(1), berlin)
        assertTrue(alle.isEmpty())
    }

    // ------------------------------------------------- Ueber Mitternacht

    @Test
    fun `ein Termin ueber Mitternacht unter einem Tag steht im Raster, geteilt`() {
        val e = eintrag("Schlafen", um(23), Terminregeln.zeitpunkt(tag.plusDays(1), LocalTime.of(1, 0), berlin))
        assertFalse(e.oben)
        assertEquals("(1/2)", teil(e, tag))
        assertEquals("(2/2)", teil(e, tag.plusDays(1)))
        assertEquals(listOf(e), amTag(listOf(e), tag.plusDays(1)))
    }

    @Test
    fun `ab einem ganzen Tag steht ein Termin oben`() {
        val e = eintrag("Reise", um(8), Terminregeln.zeitpunkt(tag.plusDays(1), LocalTime.of(8, 0), berlin))
        assertTrue(e.oben)
        assertNull(teil(e, tag))
        assertNull(teil(eintrag("kurz", um(9), um(10)), tag))
    }

    // ------------------------------------------------------------ Kaskade

    @Test
    fun `was kurz nacheinander beginnt, steht in einer Reihe und greift unter den Nachbarn`() {
        val p = kaskade(eintrag("a", um(9), um(11)), eintrag("b", um(9, 15), um(10)))
        assertNahe(0f, p["a"]!!.links)
        assertNahe(0.85f, p["a"]!!.breite)
        assertNahe(0.5f, p["b"]!!.links)
        assertNahe(0.5f, p["b"]!!.breite)
        assertTrue(p["b"]!!.ebene > p["a"]!!.ebene)
    }

    @Test
    fun `was deutlich spaeter beginnt, liegt eingerueckt darueber`() {
        val p = kaskade(eintrag("lang", um(9), um(12)), eintrag("kurz", um(10), um(11)))
        assertNahe(0f, p["lang"]!!.links)
        assertNahe(1f, p["lang"]!!.breite)
        assertNahe(EINRUECKEN, p["kurz"]!!.links)
        assertNahe(1f - EINRUECKEN, p["kurz"]!!.breite)
        assertTrue(p["kurz"]!!.ebene > p["lang"]!!.ebene)
    }

    @Test
    fun `wird es zu schmal, geht der Termin in die Reihe statt tiefer`() {
        val p = kaskade(
            eintrag("a", um(6), um(18)),
            eintrag("b", um(7), um(17)),
            eintrag("c", um(8), um(16)),
            eintrag("d", um(9), um(15)),
            eintrag("e", um(10), um(14)),
            eintrag("f", um(11), um(13)),
        )
        assertNahe(0.48f, p["e"]!!.links)
        // f haette 0,60 und damit weniger als die Haelfte; es teilt sich die Reihe mit e.
        assertNahe(0.74f, p["f"]!!.links)
        assertNahe(0.26f, p["f"]!!.breite)
        assertNahe(0.26f * UNTERGREIFEN, p["e"]!!.breite)
    }

    @Test
    fun `was sich nur beruehrt, ueberschneidet sich nicht`() {
        val p = kaskade(eintrag("a", um(9), um(10)), eintrag("b", um(10), um(11)))
        assertNahe(0f, p["b"]!!.links)
        assertNahe(1f, p["b"]!!.breite)
    }

    @Test
    fun `der laengere von zwei gleichzeitigen liegt hinten`() {
        val p = kaskade(eintrag("kurz", um(9), um(10)), eintrag("lang", um(9), um(12)))
        assertNahe(0f, p["lang"]!!.links)
        assertTrue(p["kurz"]!!.ebene > p["lang"]!!.ebene)
    }

    // ------------------------------------------------------------ Aufgaben

    @Test
    fun `die Aufgabenliste ordnet nach Zeitpunkt, ohne Datum und erledigt zuletzt`() {
        val k = listOf(EventCalendarEntity("auf", "Aufgaben", 0xFF039BE5.toInt(), 0, 1, 1))
        fun aufgabe(id: String, t: LocalDate, ohne: Boolean = false, fertig: Boolean = false) = EventEntity(
            id = id, calendarId = "auf", art = Terminart.AUFGABE, title = id, allDay = true,
            startAt = Terminregeln.alsTagesmillis(t), endAt = Terminregeln.alsTagesmillis(t), timeZone = "Europe/Berlin",
            undated = ohne, done = fertig, createdAt = 0, updatedAt = 0,
        )
        val gruppen = aufgabengruppen(
            listOf(
                aufgabe("spaeter", tag.plusDays(5)),
                aufgabe("heute", tag),
                aufgabe("alt", tag.minusDays(3)),
                aufgabe("irgendwann", tag, ohne = true),
                aufgabe("fertig", tag.minusDays(1), fertig = true),
            ),
            k, emptySet(), tag, berlin,
        )
        assertEquals(
            listOf("Überfällig", "Heute", Kalenderformat.tagNah(tag.plusDays(5), tag), "Ohne Datum", "Erledigt"),
            gruppen.map { it.titel },
        )
        assertEquals(listOf("alt", "heute", "spaeter", "irgendwann", "fertig"), gruppen.flatMap { g -> g.aufgaben.map { it.termin.title } })
    }

    // ------------------------------------------------------------- Raster

    @Test
    fun `tippen ins Raster rundet auf halbe Stunden ab`() {
        assertEquals(9 * 60, tippminute(9.4f * 52, 52f))
        assertEquals(9 * 60 + 30, tippminute(9.6f * 52, 52f))
        assertEquals(23 * 60 + 30, tippminute(30 * 52f, 52f))
    }

    // --------------------------------------------------------------- Texte

    @Test
    fun `Erinnerungen in Worten`() {
        assertEquals("30 Minuten vorher", erinnerungstext(30, false))
        assertEquals("1 Stunde vorher", erinnerungstext(60, false))
        assertEquals("1 Tag vorher", erinnerungstext(1440, false))
        assertEquals("Zum Zeitpunkt des Termins", erinnerungstext(0, false))
        assertEquals("Am selben Tag um 9:00", erinnerungstext(-540, true))
        assertEquals("Am Tag vorher um 9:00", erinnerungstext(900, true))
        assertEquals("2 Tage vorher um 9:00", erinnerungstext(2340, true))
        assertEquals("Eine Woche vorher um 9:00", erinnerungstext(9540, true))
    }

    @Test
    fun `die Uebersicht zeigt nur Tage mit Eintraegen und immer heute`() {
        val e = eintrag("a", um(9), um(10))
        val zeilen = uebersichtszeilen(listOf(e), tag.minusDays(10), tag.plusDays(40), tag.minusDays(2))
        assertEquals(
            listOf("m", "t" + tag.minusDays(2), "t" + tag),
            zeilen.map { if (it is Zeile.Monat) "m" else "t" + (it as Zeile.Tag).tag },
        )
    }

    // ------------------------------------------------------------ Symbole

    @Test
    fun `Symbole kommen aus den Woertern, das laengste gewinnt`() {
        assertEquals("doctor", Terminsymbole.ausWoertern("Zahnarzt Dr. Weiß", "", Terminart.TERMIN))
        assertEquals("car", Terminsymbole.ausWoertern("Fahrstunde", "", Terminart.TERMIN))
        assertEquals("birthday", Terminsymbole.ausWoertern("Oma", "", Terminart.GEBURTSTAG))
        assertEquals("task", Terminsymbole.ausWoertern("Irgendwas", "", Terminart.AUFGABE))
        assertEquals("event", Terminsymbole.ausWoertern("Sabadell", "", Terminart.TERMIN))
        assertEquals("swim", Terminsymbole.ausWoertern("Mit Lena", "Schwimmbad am See", Terminart.TERMIN))
    }

    @Test
    fun `die Antwort der KI zaehlt nur, wenn sie in der Liste steht`() {
        assertEquals("doctor", Terminsymbole.ausAntwort("Doctor."))
        assertEquals("gym", Terminsymbole.ausAntwort("Antwort: gym"))
        assertNull(Terminsymbole.ausAntwort("unicorn"))
        assertNull(Terminsymbole.ausAntwort(null))
    }

    @Test
    fun `jeder Schluessel ist eindeutig`() {
        val schluessel = Terminsymbole.alle.map { it.schluessel }
        assertEquals(schluessel.size, schluessel.toSet().size)
    }

    // -------------------------------------------------------- Wiederholung

    @Test
    fun `eine Serie in Worten`() {
        assertEquals("Jede Woche", wiederholungstext(Serie(Wiederholung.WOECHENTLICH)))
        assertEquals(
            "Alle 2 Wochen am Mo, Mi",
            wiederholungstext(Serie(Wiederholung.WOECHENTLICH, 2, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))),
        )
        assertEquals("Jede Woche am Freitag", wiederholungstext(Serie(Wiederholung.WOECHENTLICH, tage = setOf(DayOfWeek.FRIDAY))))
        assertEquals("Jeden Tag · 5 Mal", wiederholungstext(Serie(Wiederholung.TAEGLICH, anzahl = 5)))
        assertEquals(
            "Alle 3 Monate bis 30. Nov. 2026",
            wiederholungstext(Serie(Wiederholung.MONATLICH, 3, bis = LocalDate.of(2026, 11, 30))),
        )
    }
}
