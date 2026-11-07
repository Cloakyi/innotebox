package de.notizen.core.data.kalender

import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.model.Wiederholung
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Millisekunden eines Tages. Ganztaegige Termine rechnen in ganzen Tagen (SYNC.md 16.2). */
const val TAG_MS: Long = 86_400_000L

/**
 * Ein einzelnes Vorkommen eines Termins.
 *
 * [beginn] und [ende] sind echte Zeitpunkte; bei ganztaegigen Terminen
 * Mitternacht Ortszeit des ersten Tages und Mitternacht nach dem letzten.
 * [schluessel] ist der Beginn in der Form von `startAt` und der Wert, der in
 * `exceptions` landet, wenn nur dieses Vorkommen geloescht wird.
 */
data class Vorkommen(
    val termin: EventEntity,
    val beginn: Long,
    val ende: Long,
    val ersterTag: LocalDate,
    val letzterTag: LocalDate,
    val schluessel: Long,
)

/**
 * Die Regel einer Serie (SYNC.md 16.2): Einheit, Abstand, Wochentage und Ende.
 *
 * [tage] gilt nur woechentlich; leer heisst der Wochentag des Beginns. [anzahl]
 * gilt nur ohne [bis].
 */
data class Serie(
    val einheit: Wiederholung,
    val alle: Int = 1,
    val tage: Set<DayOfWeek> = emptySet(),
    val bis: LocalDate? = null,
    val anzahl: Int? = null,
) {
    /** Weicht von den einfachen Serien ab, die die Auswahlliste anbietet. */
    val benutzerdefiniert: Boolean
        get() = einheit != Wiederholung.KEINE && (alle > 1 || bis != null || anzahl != null || tage.isNotEmpty())
}

/** Die Serie eines Termins, mit bereinigten Werten. */
val EventEntity.serie: Serie
    get() = Serie(
        einheit = recurrence,
        alle = maxOf(1, repeatInterval),
        tage = if (recurrence == Wiederholung.WOECHENTLICH) {
            repeatDays.filter { it in 1..7 }.map { DayOfWeek.of(it) }.toSet()
        } else {
            emptySet()
        },
        bis = repeatUntil?.let { Terminregeln.datumAus(it) },
        anzahl = if (repeatUntil == null) repeatCount?.takeIf { it > 0 } else null,
    )

/** Eine faellige Erinnerung: welches Vorkommen, wie viele Minuten vorher, wann. */
data class Ausloeser(
    val vorkommen: Vorkommen,
    val minuten: Int,
    val zeit: Long,
)

/**
 * Die Rechnungen des Kalenders, rein und ohne Android.
 *
 * Serien werden nie gespeichert, sondern fuer den sichtbaren Zeitraum
 * ausgerechnet. Gerechnet wird in Ortszeit: Ein woechentlicher Termin um
 * 17 Uhr bleibt auch nach der Zeitumstellung um 17 Uhr.
 */
object Terminregeln {

    /** Datum eines ganztaegigen `startAt`/`endAt` (Mitternacht UTC). */
    fun datumAus(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, TAG_MS))

    /** Die Form, in der ein Datum in `startAt`/`endAt` steht. */
    fun alsTagesmillis(tag: LocalDate): Long = tag.toEpochDay() * TAG_MS

    /** Alle Vorkommen, die den Zeitraum [von] bis [bis] (ausschliesslich) beruehren. */
    fun vorkommen(termin: EventEntity, von: LocalDate, bis: LocalDate, zone: ZoneId): List<Vorkommen> {
        if (!bis.isAfter(von) || termin.undated) return emptyList()
        val ergebnis = ArrayList<Vorkommen>()
        val ausnahmen = termin.exceptions.toHashSet()

        if (termin.allDay) {
            val erster = datumAus(termin.startAt)
            val tage = maxOf(0L, Math.floorDiv(termin.endAt - termin.startAt, TAG_MS))
            val schritte = schritte(termin.serie, erster, von.minusDays(tage))
            for (tag in schritte) {
                if (!tag.isBefore(bis)) break
                val letzter = tag.plusDays(tage)
                if (letzter.isBefore(von)) continue
                val schluessel = alsTagesmillis(tag)
                if (schluessel in ausnahmen) continue
                ergebnis += Vorkommen(
                    termin = termin,
                    beginn = tag.atStartOfDay(zone).toInstant().toEpochMilli(),
                    ende = letzter.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    ersterTag = tag,
                    letzterTag = letzter,
                    schluessel = schluessel,
                )
            }
            return ergebnis
        }

        val basis = LocalDateTime.ofInstant(Instant.ofEpochMilli(termin.startAt), zone)
        val dauer = maxOf(0L, termin.endAt - termin.startAt)
        val dauerTage = dauer / TAG_MS + 1
        val schritte = schritte(termin.serie, basis.toLocalDate(), von.minusDays(dauerTage))
        for (tag in schritte) {
            if (!tag.isBefore(bis)) break
            val beginn = LocalDateTime.of(tag, basis.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
            val ende = beginn + dauer
            val ersterTag = tag
            // Ein Termin, der um Mitternacht endet, gehoert nicht mehr zum Folgetag.
            val letzterTag = Instant.ofEpochMilli(maxOf(beginn, ende - 1)).atZone(zone).toLocalDate()
            if (letzterTag.isBefore(von)) continue
            if (beginn in ausnahmen) continue
            ergebnis += Vorkommen(termin, beginn, ende, ersterTag, letzterTag, beginn)
        }
        return ergebnis
    }

    /** Die Starttage einer einfachen Serie, siehe [schritte]. */
    fun schritte(regel: Wiederholung, erster: LocalDate, ab: LocalDate): Sequence<LocalDate> =
        schritte(Serie(regel), erster, ab)

    /**
     * Die Starttage einer Serie ab [erster], beginnend nahe [ab].
     *
     * Springt bei langen Serien direkt in die Naehe des Zeitraums, statt von
     * einem Geburtstag 1990 an jedes Jahr einzeln abzulaufen. Damit das Ende
     * nach Anzahl trotzdem stimmt, traegt jeder Tag seine Nummer in der Serie.
     */
    fun schritte(serie: Serie, erster: LocalDate, ab: LocalDate): Sequence<LocalDate> {
        if (serie.einheit == Wiederholung.KEINE) return sequenceOf(erster)
        var folge = nummeriert(serie, erster, if (ab.isAfter(erster)) ab else erster)
        serie.bis?.let { letzter -> folge = folge.takeWhile { !it.second.isAfter(letzter) } }
        if (serie.bis == null) serie.anzahl?.let { n -> folge = folge.takeWhile { it.first < n } }
        return folge.map { it.second }
    }

    /** Die Tage einer Serie mit ihrer Nummer (ab 0), beginnend nahe [ab]. */
    private fun nummeriert(serie: Serie, erster: LocalDate, ab: LocalDate): Sequence<Pair<Long, LocalDate>> {
        val alle = maxOf(1, serie.alle).toLong()
        return when (serie.einheit) {
            Wiederholung.KEINE -> sequenceOf(0L to erster)
            Wiederholung.TAEGLICH -> {
                val n0 = ChronoUnit.DAYS.between(erster, ab) / alle
                generateSequence(n0) { it + 1 }.map { it to erster.plusDays(it * alle) }
            }
            Wiederholung.WERKTAGS -> wochen(erster, ab, 1, WERKTAGE)
            Wiederholung.WOECHENTLICH -> {
                val tage = serie.tage.ifEmpty { setOf(erster.dayOfWeek) }
                if (tage == setOf(erster.dayOfWeek)) {
                    val n0 = ChronoUnit.WEEKS.between(erster, ab) / alle
                    generateSequence(n0) { it + 1 }.map { it to erster.plusWeeks(it * alle) }
                } else {
                    wochen(erster, ab, alle, tage)
                }
            }
            Wiederholung.MONATLICH -> {
                val n0 = maxOf(0L, ChronoUnit.MONTHS.between(erster, ab) / alle - 1)
                generateSequence(n0) { it + 1 }.map { it to erster.plusMonths(it * alle) }
            }
            Wiederholung.JAEHRLICH -> {
                val n0 = maxOf(0L, ChronoUnit.YEARS.between(erster, ab) / alle - 1)
                generateSequence(n0) { it + 1 }.map { it to erster.plusYears(it * alle) }
            }
        }
    }

    private val WERKTAGE = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    /**
     * Woechentlich an mehreren Tagen: jede [alle]-te Woche ab dem Montag der
     * ersten, in der ersten nur die Tage ab [erster].
     */
    private fun wochen(erster: LocalDate, ab: LocalDate, alle: Long, tage: Set<DayOfWeek>): Sequence<Pair<Long, LocalDate>> {
        val reihe = tage.sorted()
        val montag = erster.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val inErster = reihe.count { !montag.plusDays(it.value - 1L).isBefore(erster) }.toLong()
        val w0 = ChronoUnit.WEEKS.between(montag, ab.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) / alle
        return generateSequence(w0) { it + 1 }.flatMap { w ->
            val beginn = montag.plusWeeks(w * alle)
            val nummer = if (w == 0L) 0L else inErster + (w - 1) * reihe.size
            val tageDerWoche = reihe.map { beginn.plusDays(it.value - 1L) }.filter { w > 0 || !it.isBefore(erster) }
            tageDerWoche.mapIndexed { i, tag -> (nummer + i) to tag }.asSequence()
        }
    }

    /**
     * Wann eine Erinnerung [minuten] vor einem Vorkommen faellig ist.
     * Bei ganztaegigen Terminen ab Mitternacht Ortszeit des ersten Tages.
     */
    fun ausloesezeit(v: Vorkommen, minuten: Int): Long = v.beginn - minuten * 60_000L

    /**
     * Alle Erinnerungen, die nach [nach] und hoechstens bis [bis] faellig sind.
     *
     * Erledigte Aufgaben erinnern nicht mehr.
     */
    fun ausloeser(termine: List<EventEntity>, nach: Long, bis: Long, zone: ZoneId): List<Ausloeser> {
        val ergebnis = ArrayList<Ausloeser>()
        for (t in termine) {
            if (t.reminders.isEmpty() || t.done || t.deletedAt != null) continue
            val frueheste = t.reminders.min()
            val spaeteste = t.reminders.max()
            val von = Instant.ofEpochMilli(nach + frueheste * 60_000L).atZone(zone).toLocalDate().minusDays(1)
            val bisTag = Instant.ofEpochMilli(bis + spaeteste * 60_000L).atZone(zone).toLocalDate().plusDays(2)
            for (v in vorkommen(t, von, bisTag, zone)) {
                for (m in t.reminders.distinct()) {
                    val zeit = ausloesezeit(v, m)
                    if (zeit in (nach + 1)..bis) ergebnis += Ausloeser(v, m, zeit)
                }
            }
        }
        return ergebnis.sortedBy { it.zeit }
    }

    /** Die naechste Erinnerung nach [nach], gesucht ueber gut ein Jahr. */
    fun naechster(termine: List<EventEntity>, nach: Long, zone: ZoneId, nurWichtige: Boolean? = null): Ausloeser? {
        val auswahl = when (nurWichtige) {
            null -> termine
            true -> termine.filter { it.important }
            false -> termine.filter { !it.important }
        }
        return ausloeser(auswahl, nach, nach + 400L * TAG_MS, zone).firstOrNull()
    }

    /** Beginn eines neuen ganztaegigen Termins an [tag]. */
    fun ganztaegig(tag: LocalDate, tage: Long = 1): Pair<Long, Long> =
        alsTagesmillis(tag) to alsTagesmillis(tag.plusDays(tage - 1))

    /** Der Zeitpunkt zu Datum und Uhrzeit in Ortszeit. */
    fun zeitpunkt(tag: LocalDate, uhrzeit: LocalTime, zone: ZoneId): Long =
        LocalDateTime.of(tag, uhrzeit).atZone(zone).toInstant().toEpochMilli()

    /** Datum eines Zeitpunkts in Ortszeit. */
    fun tagVon(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
