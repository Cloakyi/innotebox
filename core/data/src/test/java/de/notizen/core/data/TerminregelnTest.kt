package de.notizen.core.data

import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.kalender.TAG_MS
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.model.Wiederholung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Die Rechnungen des Kalenders: Serien, ganze Tage, Ausnahmen, Erinnerungen.
 * Rein, ohne Datenbank.
 */
class TerminregelnTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val tokio = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")

    private fun termin(
        start: Long,
        ende: Long = start + 60 * 60_000L,
        ganz: Boolean = false,
        regel: Wiederholung = Wiederholung.KEINE,
        ausnahmen: List<Long> = emptyList(),
        erinnerungen: List<Int> = emptyList(),
        wichtig: Boolean = false,
        erledigt: Boolean = false,
    ) = EventEntity(
        id = "t", calendarId = "k", title = "T", allDay = ganz, startAt = start, endAt = ende,
        timeZone = "Europe/Berlin", recurrence = regel, exceptions = ausnahmen, reminders = erinnerungen,
        important = wichtig, done = erledigt, createdAt = 0, updatedAt = 0,
    )

    private fun um(tag: LocalDate, stunde: Int, minute: Int = 0, zone: ZoneId = berlin) =
        Terminregeln.zeitpunkt(tag, LocalTime.of(stunde, minute), zone)

    @Test
    fun `ein einzelner Termin erscheint genau an seinem Tag`() {
        val tag = LocalDate.of(2026, 11, 3)
        val t = termin(um(tag, 14))
        val v = Terminregeln.vorkommen(t, tag.minusDays(3), tag.plusDays(3), berlin)
        assertEquals(1, v.size)
        assertEquals(tag, v[0].ersterTag)
        assertEquals(tag, v[0].letzterTag)
        assertTrue(Terminregeln.vorkommen(t, tag.plusDays(1), tag.plusDays(5), berlin).isEmpty())
    }

    @Test
    fun `ein Termin bis Mitternacht gehoert nicht zum Folgetag`() {
        val tag = LocalDate.of(2026, 11, 3)
        val t = termin(um(tag, 22), um(tag.plusDays(1), 0))
        val v = Terminregeln.vorkommen(t, tag, tag.plusDays(2), berlin).single()
        assertEquals(tag, v.letzterTag)
    }

    @Test
    fun `ein ganztaegiger Termin bleibt in jeder Zeitzone derselbe Tag`() {
        val tag = LocalDate.of(2026, 7, 14)
        val (s, e) = Terminregeln.ganztaegig(tag)
        val t = termin(s, e, ganz = true)
        for (zone in listOf(berlin, tokio, newYork)) {
            val v = Terminregeln.vorkommen(t, tag.minusDays(2), tag.plusDays(2), zone).single()
            assertEquals(tag, v.ersterTag)
            assertEquals(tag, v.letzterTag)
            assertEquals(tag.atStartOfDay(zone).toInstant().toEpochMilli(), v.beginn)
        }
    }

    @Test
    fun `ein mehrtaegiger ganzer Termin reicht bis zum letzten Tag einschliesslich`() {
        val tag = LocalDate.of(2026, 12, 24)
        val (s, e) = Terminregeln.ganztaegig(tag, 3)
        val v = Terminregeln.vorkommen(termin(s, e, ganz = true), tag.plusDays(2), tag.plusDays(3), berlin).single()
        assertEquals(tag, v.ersterTag)
        assertEquals(tag.plusDays(2), v.letzterTag)
    }

    @Test
    fun `woechentlich bleibt nach der Zeitumstellung bei derselben Uhrzeit`() {
        // 2026-10-25 endet die Sommerzeit in Berlin.
        val erster = LocalDate.of(2026, 10, 15)
        val t = termin(um(erster, 17), regel = Wiederholung.WOECHENTLICH)
        val v = Terminregeln.vorkommen(t, erster, erster.plusWeeks(3), berlin)
        assertEquals(3, v.size)
        v.forEach { assertEquals(LocalTime.of(17, 0), java.time.Instant.ofEpochMilli(it.beginn).atZone(berlin).toLocalTime()) }
    }

    @Test
    fun `werktags laesst das Wochenende aus`() {
        val montag = LocalDate.of(2026, 11, 2)
        val t = termin(um(montag, 8), regel = Wiederholung.WERKTAGS)
        val v = Terminregeln.vorkommen(t, montag, montag.plusDays(14), berlin)
        assertEquals(10, v.size)
    }

    @Test
    fun `ein Geburtstag von 1990 erscheint im richtigen Jahr ohne alle Jahre abzulaufen`() {
        val geburt = LocalDate.of(1990, 3, 9)
        val (s, e) = Terminregeln.ganztaegig(geburt)
        val t = termin(s, e, ganz = true, regel = Wiederholung.JAEHRLICH)
        val v = Terminregeln.vorkommen(t, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), berlin)
        assertEquals(listOf(LocalDate.of(2026, 3, 9)), v.map { it.ersterTag })
    }

    @Test
    fun `monatlich am 31 faellt in kurzen Monaten auf den letzten Tag`() {
        val erster = LocalDate.of(2026, 1, 31)
        val t = termin(um(erster, 9), regel = Wiederholung.MONATLICH)
        val v = Terminregeln.vorkommen(t, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), berlin)
        assertEquals(listOf(LocalDate.of(2026, 2, 28)), v.map { it.ersterTag })
    }

    @Test
    fun `eine Ausnahme nimmt genau ein Vorkommen heraus`() {
        val erster = LocalDate.of(2026, 11, 2)
        val t = termin(um(erster, 8), regel = Wiederholung.TAEGLICH)
        val alle = Terminregeln.vorkommen(t, erster, erster.plusDays(5), berlin)
        val ohne = Terminregeln.vorkommen(t.copy(exceptions = listOf(alle[2].schluessel)), erster, erster.plusDays(5), berlin)
        assertEquals(5, alle.size)
        assertEquals(alle - alle[2], ohne.map { it.copy(termin = t) })
    }

    @Test
    fun `ganztaegige Ausnahmen sind der Tag in UTC`() {
        val erster = LocalDate.of(2026, 11, 2)
        val (s, e) = Terminregeln.ganztaegig(erster)
        val t = termin(s, e, ganz = true, regel = Wiederholung.TAEGLICH, ausnahmen = listOf(erster.plusDays(1).toEpochDay() * TAG_MS))
        val v = Terminregeln.vorkommen(t, erster, erster.plusDays(3), tokio)
        assertEquals(listOf(erster, erster.plusDays(2)), v.map { it.ersterTag })
    }

    @Test
    fun `eine Erinnerung liegt die Minuten vor dem Beginn`() {
        val tag = LocalDate.of(2026, 11, 3)
        val t = termin(um(tag, 14), erinnerungen = listOf(30, 10))
        val a = Terminregeln.ausloeser(listOf(t), um(tag, 0), um(tag, 23), berlin)
        assertEquals(listOf(um(tag, 13, 30), um(tag, 13, 50)), a.map { it.zeit })
    }

    @Test
    fun `ganztaegig um 9 Uhr am selben Tag und am Vortag`() {
        val tag = LocalDate.of(2026, 11, 3)
        val (s, e) = Terminregeln.ganztaegig(tag)
        val t = termin(s, e, ganz = true, erinnerungen = listOf(-540, 900))
        val a = Terminregeln.ausloeser(listOf(t), um(tag.minusDays(3), 0), um(tag.plusDays(1), 0), berlin)
        assertEquals(listOf(um(tag.minusDays(1), 9), um(tag, 9)), a.map { it.zeit })
    }

    @Test
    fun `erledigte Aufgaben und Termine ohne Erinnerung klingeln nicht`() {
        val tag = LocalDate.of(2026, 11, 3)
        val erledigt = termin(um(tag, 14), erinnerungen = listOf(0), erledigt = true)
        val stumm = termin(um(tag, 15))
        assertTrue(Terminregeln.ausloeser(listOf(erledigt, stumm), um(tag, 0), um(tag, 23), berlin).isEmpty())
    }

    @Test
    fun `der naechste trennt wichtige und gewoehnliche`() {
        val tag = LocalDate.of(2026, 11, 3)
        val normal = termin(um(tag, 10), erinnerungen = listOf(0)).copy(id = "n")
        val wichtig = termin(um(tag, 12), erinnerungen = listOf(0), wichtig = true).copy(id = "w")
        val ab = um(tag, 0)
        assertEquals("n", Terminregeln.naechster(listOf(normal, wichtig), ab, berlin, nurWichtige = false)!!.vorkommen.termin.id)
        assertEquals("w", Terminregeln.naechster(listOf(normal, wichtig), ab, berlin, nurWichtige = true)!!.vorkommen.termin.id)
        assertNull(Terminregeln.naechster(listOf(normal, wichtig), um(tag, 13), berlin))
    }

    @Test
    fun `eine taegliche Serie findet ihre naechste Erinnerung auch nach Jahren`() {
        val erster = LocalDate.of(2020, 1, 1)
        val t = termin(um(erster, 7), regel = Wiederholung.TAEGLICH, erinnerungen = listOf(0))
        val heute = LocalDate.of(2026, 11, 3)
        val n = Terminregeln.naechster(listOf(t), um(heute, 8), berlin)!!
        assertEquals(um(heute.plusDays(1), 7), n.zeit)
    }

    @Test
    fun `eine Aufgabe ohne Datum steht nirgends und klingelt nie`() {
        val tag = LocalDate.of(2026, 11, 3)
        val t = termin(um(tag, 10), erinnerungen = listOf(0)).copy(undated = true)
        assertTrue(Terminregeln.vorkommen(t, tag.minusDays(1), tag.plusDays(1), berlin).isEmpty())
        assertNull(Terminregeln.naechster(listOf(t), um(tag, 0), berlin))
    }

    // ------------------------------------------------- Benutzerdefinierte Serien

    private fun tage(t: EventEntity, von: LocalDate, bis: LocalDate) =
        Terminregeln.vorkommen(t, von, bis, berlin).map { it.ersterTag.dayOfMonth }

    @Test
    fun `alle zwei Wochen laesst jede zweite Woche aus`() {
        val montag = LocalDate.of(2026, 11, 2)
        val t = termin(um(montag, 18), regel = Wiederholung.WOECHENTLICH).copy(repeatInterval = 2)
        assertEquals(listOf(2, 16, 30), tage(t, montag, LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `woechentlich an mehreren Tagen, in der ersten Woche erst ab dem Beginn`() {
        val mittwoch = LocalDate.of(2026, 11, 4)
        val t = termin(um(mittwoch, 18), regel = Wiederholung.WOECHENTLICH).copy(repeatDays = listOf(1, 3, 5))
        assertEquals(listOf(4, 6, 9, 11, 13), tage(t, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `alle zwei Wochen an zwei Tagen`() {
        val mittwoch = LocalDate.of(2026, 11, 4)
        val t = termin(um(mittwoch, 18), regel = Wiederholung.WOECHENTLICH)
            .copy(repeatInterval = 2, repeatDays = listOf(1, 3))
        assertEquals(listOf(4, 16, 18, 30), tage(t, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `endet nach drei Terminen, auch beim Sprung in die Zukunft`() {
        val mittwoch = LocalDate.of(2026, 11, 4)
        val t = termin(um(mittwoch, 18), regel = Wiederholung.WOECHENTLICH)
            .copy(repeatDays = listOf(1, 3), repeatCount = 3)
        assertEquals(listOf(4, 9, 11), tage(t, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)))
        assertEquals(listOf(11), tage(t, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 1)))
        assertTrue(tage(t, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1)).isEmpty())
    }

    @Test
    fun `eine Ausnahme zaehlt beim Ende nach Anzahl mit`() {
        val tag = LocalDate.of(2026, 11, 2)
        val t = termin(um(tag, 8), regel = Wiederholung.TAEGLICH, ausnahmen = listOf(um(tag.plusDays(1), 8)))
            .copy(repeatCount = 3)
        assertEquals(listOf(2, 4), tage(t, tag, tag.plusDays(10)))
    }

    @Test
    fun `endet am Datum einschliesslich`() {
        val tag = LocalDate.of(2026, 11, 2)
        val t = termin(um(tag, 8), regel = Wiederholung.TAEGLICH)
            .copy(repeatUntil = Terminregeln.alsTagesmillis(LocalDate.of(2026, 11, 5)))
        assertEquals(listOf(2, 3, 4, 5), tage(t, tag, tag.plusDays(10)))
    }

    @Test
    fun `alle drei Monate und ein Jahrestag mit Anzahl`() {
        val t = termin(um(LocalDate.of(2026, 1, 15), 9), regel = Wiederholung.MONATLICH).copy(repeatInterval = 3)
        val monate = Terminregeln.vorkommen(t, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), berlin)
            .map { it.ersterTag.monthValue }
        assertEquals(listOf(1, 4, 7, 10), monate)

        val geburtstag = termin(Terminregeln.alsTagesmillis(LocalDate.of(1990, 5, 3)), ganz = true, regel = Wiederholung.JAEHRLICH)
        val jahr = LocalDate.of(2026, 1, 1) to LocalDate.of(2027, 1, 1)
        assertTrue(Terminregeln.vorkommen(geburtstag.copy(repeatCount = 5), jahr.first, jahr.second, berlin).isEmpty())
        assertEquals(1, Terminregeln.vorkommen(geburtstag.copy(repeatCount = 50), jahr.first, jahr.second, berlin).size)
    }
}
