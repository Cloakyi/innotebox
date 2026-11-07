package de.notizen.app.kalender

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Deutsche Schreibweisen fuer Daten und Uhrzeiten im Kalender. */
object Kalenderformat {
    private val deutsch: Locale = Locale.GERMANY
    private val uhr = DateTimeFormatter.ofPattern("H:mm", deutsch)
    private val tagKurz = DateTimeFormatter.ofPattern("EEE., d. MMM", deutsch)
    private val tagLang = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", deutsch)
    private val tagMitJahr = DateTimeFormatter.ofPattern("EEE., d. MMM yyyy", deutsch)
    private val datum = DateTimeFormatter.ofPattern("d. MMM yyyy", deutsch)

    fun uhrzeit(millis: Long, zone: ZoneId): String = uhr.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** „Mo., 3. Nov.“, mit Jahr nur, wenn es nicht das laufende ist. */
    fun tag(tag: LocalDate, heute: LocalDate = LocalDate.now()): String =
        if (tag.year == heute.year) tagKurz.format(tag) else tagMitJahr.format(tag)

    fun tagLang(tag: LocalDate): String = tagLang.format(tag)

    /** „30. Nov. 2026“. */
    fun datum(tag: LocalDate): String = datum.format(tag)

    /** „Heute“, „Morgen“, „Gestern“ oder das Datum. */
    fun tagNah(tag: LocalDate, heute: LocalDate = LocalDate.now()): String = when (tag) {
        heute -> "Heute"
        heute.plusDays(1) -> "Morgen"
        heute.minusDays(1) -> "Gestern"
        else -> tag(tag, heute)
    }

    fun monat(monat: YearMonth, heute: LocalDate = LocalDate.now()): String {
        val name = monat.month.getDisplayName(TextStyle.FULL_STANDALONE, deutsch)
        return if (monat.year == heute.year) name else "$name ${monat.year}"
    }

    fun wochentagKurz(tag: LocalDate): String =
        tag.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, deutsch).trimEnd('.')

    /**
     * Die Zeitangabe eines Eintrags, etwa „Heute · 14:00 bis 15:30“ oder
     * „Mo., 3. Nov. bis Mi., 5. Nov. · Ganztägig“.
     */
    fun zeitraum(e: Eintrag, zone: ZoneId, heute: LocalDate = LocalDate.now()): String {
        if (e.ganztaegig) {
            return if (e.letzterTag == e.ersterTag) {
                tagNah(e.ersterTag, heute) + " · Ganztägig"
            } else {
                tag(e.ersterTag, heute) + " bis " + tag(e.letzterTag, heute) + " · Ganztägig"
            }
        }
        if (e.herkunft is Herkunft.Notiz || e.ende <= e.beginn) {
            return tagNah(e.ersterTag, heute) + " · " + uhrzeit(e.beginn, zone)
        }
        return if (e.letzterTag == e.ersterTag) {
            tagNah(e.ersterTag, heute) + " · " + uhrzeit(e.beginn, zone) + " bis " + uhrzeit(e.ende, zone)
        } else {
            tag(e.ersterTag, heute) + ", " + uhrzeit(e.beginn, zone) + " bis " +
                tag(e.letzterTag, heute) + ", " + uhrzeit(e.ende, zone)
        }
    }
}
