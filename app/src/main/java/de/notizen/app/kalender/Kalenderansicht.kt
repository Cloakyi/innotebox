package de.notizen.app.kalender

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Wie weit der Kalender auf einmal zeigt. */
enum class Kalenderansicht { TAG, WOCHE, MONAT }

/**
 * Der Montag der Woche, in der [tag] liegt.
 *
 * Montag zuerst, wie im deutschsprachigen Raum üblich. `DayOfWeek.value` zählt
 * bereits von Montag an, also ist die Rechnung dieselbe für jeden Wochentag.
 */
fun wochenstart(tag: LocalDate): LocalDate = tag.minusDays((tag.dayOfWeek.value - 1).toLong())

/**
 * Der erste Tag im Monatsraster.
 *
 * Das Raster fängt beim Montag vor dem Monatsersten an, nicht beim Ersten
 * selbst. Sonst begänne der Monat mitten in einer Zeile.
 */
fun rasterstart(monat: YearMonth): LocalDate = wochenstart(monat.atDay(1))

/** Wie viele Tage das Monatsraster umfasst. Immer sechs volle Wochen. */
const val RASTERTAGE = 42

/**
 * Der Zeitraum, den eine Ansicht braucht, in UTC-Millis.
 *
 * **Über die Zeitzone des Geräts gerechnet, nicht über UTC.** Ein Termin um
 * 0:30 Uhr liegt in UTC noch im Vortag; wer die Grenzen in UTC zieht, verliert
 * ihn am Rand. Dieselbe Falle wie beim Datumswähler der Erinnerungen.
 *
 * **Beim Monat sind es sechs Wochen, nicht ein Monat.** Das Raster zeigt am
 * Anfang und Ende Tage der Nachbarmonate. Ohne sie im Zeitraum blieben die
 * Punkte dort leer, obwohl an diesen Tagen etwas steht.
 */
fun bereich(ansicht: Kalenderansicht, anker: LocalDate, zone: ZoneId): Pair<Long, Long> {
    val (von, tage) = when (ansicht) {
        Kalenderansicht.TAG -> anker to 1
        Kalenderansicht.WOCHE -> wochenstart(anker) to 7
        Kalenderansicht.MONAT -> rasterstart(YearMonth.from(anker)) to RASTERTAGE
    }

    val anfang = von.atStartOfDay(zone).toInstant().toEpochMilli()
    val ende = von.plusDays(tage.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    return anfang to ende
}

/**
 * Wohin ein Schritt vor oder zurück führt.
 *
 * Die Schrittweite folgt der Ansicht: Wer den Monat sieht, blättert Monate; wer
 * den Tag sieht, blättert Tage. Ein Pfeil, der in jeder Ansicht dasselbe täte,
 * wäre in zwei von dreien der falsche.
 */
fun verschoben(ansicht: Kalenderansicht, anker: LocalDate, schritte: Long): LocalDate =
    when (ansicht) {
        Kalenderansicht.TAG -> anker.plusDays(schritte)
        Kalenderansicht.WOCHE -> anker.plusWeeks(schritte)
        Kalenderansicht.MONAT -> anker.plusMonths(schritte)
    }

/**
 * Ob der Sprung zu „heute" überhaupt etwas ändern würde.
 *
 * Ein Knopf, der zum heutigen Tag führt, während man ihn schon ansieht, tut
 * nichts. Was „schon ansehen" heißt, hängt von der Ansicht ab: Im Monat reicht
 * derselbe Monat, im Tag muss es derselbe Tag sein.
 */
fun zeigtHeute(ansicht: Kalenderansicht, anker: LocalDate, heute: LocalDate): Boolean =
    when (ansicht) {
        Kalenderansicht.TAG -> anker == heute
        Kalenderansicht.WOCHE -> wochenstart(anker) == wochenstart(heute)
        Kalenderansicht.MONAT -> YearMonth.from(anker) == YearMonth.from(heute)
    }

/**
 * Was oben über dem Kalender steht.
 *
 * Beim Monat der Monat, beim Tag das ausgeschriebene Datum, bei der Woche ihre
 * beiden Ränder. Der Monatsname steht dort nur einmal, wenn beide Ränder im
 * selben Monat liegen: „24. bis 30. August" liest sich besser als „24. August
 * bis 30. August". Das Jahr steht dabei, wenn die Woche zwei Jahre berührt.
 */
fun kopftext(ansicht: Kalenderansicht, anker: LocalDate): String = when (ansicht) {
    Kalenderansicht.MONAT -> "${monatsname(anker)} ${anker.year}"

    Kalenderansicht.TAG ->
        "${wochentagsname(anker)}, ${anker.dayOfMonth}. ${monatsname(anker)} ${anker.year}"

    Kalenderansicht.WOCHE -> {
        val von = wochenstart(anker)
        val bis = von.plusDays(6)
        when {
            von.year != bis.year ->
                "${von.dayOfMonth}. ${monatsname(von)} ${von.year} bis " +
                    "${bis.dayOfMonth}. ${monatsname(bis)} ${bis.year}"

            von.month != bis.month ->
                "${von.dayOfMonth}. ${monatsname(von)} bis " +
                    "${bis.dayOfMonth}. ${monatsname(bis)} ${bis.year}"

            else -> "${von.dayOfMonth}. bis ${bis.dayOfMonth}. ${monatsname(bis)} ${bis.year}"
        }
    }
}

/** Die Beschriftung der drei Knöpfe. */
fun ansichtsname(ansicht: Kalenderansicht): String = when (ansicht) {
    Kalenderansicht.TAG -> "Tag"
    Kalenderansicht.WOCHE -> "Woche"
    Kalenderansicht.MONAT -> "Monat"
}

private fun monatsname(tag: LocalDate): String =
    tag.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMANY)

private fun wochentagsname(tag: LocalDate): String =
    tag.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMANY)
