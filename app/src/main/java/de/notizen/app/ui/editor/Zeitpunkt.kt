package de.notizen.app.ui.editor

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Die Umrechnung zwischen dem Datumswähler und der lokalen Zeit.
 *
 * **Hier wird nichts geschätzt und nichts pauschal verschoben.** Kein
 * „minus zwei Stunden", kein fester Versatz. Der Material-`DatePicker` sagt in
 * seiner eigenen Dokumentation zu, dass `selectedDateMillis` den
 * *„start of the day in UTC milliseconds"* liefert — also Mitternacht UTC des
 * angetippten Tages. Genau dieser zugesagte Wert wird verwendet: Jahr, Monat
 * und Tag werden **in UTC ausgelesen**, weil sie so hineingeschrieben wurden,
 * und **in der lokalen Zeitzone wieder gesetzt**, weil der Nutzer sie so
 * gemeint hat.
 *
 * Dass das über Zeitzonen mit halben Stunden Versatz und über die
 * Sommerzeitumstellung hinweg stimmt, ist nicht behauptet, sondern in
 * `ZeitpunktTest` nachgerechnet — dort wird die Zeitzone der JVM umgestellt und
 * das Ergebnis zurückgelesen.
 */
internal object Zeitpunkt {

    /**
     * Baut aus dem UTC-Tag des Wählers und der lokalen Uhrzeit den Zeitstempel,
     * zu dem geweckt wird.
     */
    fun ausWahl(tagUtc: Long, stunde: Int, minute: Int): Long {
        val inUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.GERMANY).apply {
            timeInMillis = tagUtc
        }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, inUtc.get(Calendar.YEAR))
            set(Calendar.MONTH, inUtc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, inUtc.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, stunde)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Die Gegenrichtung: aus einem lokalen Zeitstempel den Tag machen, den der
     * Wähler vorausgewählt zeigen soll.
     *
     * Ohne diese Umrechnung stünde der Wähler zeitweise auf dem **falschen
     * Tag**. Ein lokaler Zeitpunkt am frühen Morgen liegt in UTC noch im
     * Vortag; direkt übergeben zeigte der Kalender dann gestern statt heute.
     * Genau der Sorte Ungenauigkeit, die man erst bemerkt, wenn man nachts
     * etwas einträgt.
     */
    fun alsWaehlertag(lokal: Long): Long {
        val vorOrt = Calendar.getInstance().apply { timeInMillis = lokal }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.GERMANY).apply {
            clear()
            set(
                vorOrt.get(Calendar.YEAR),
                vorOrt.get(Calendar.MONTH),
                vorOrt.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }.timeInMillis
    }

    /** Vorschlag, wenn die Notiz noch keine Erinnerung trägt. */
    fun naechsteVolleStunde(jetzt: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = jetzt
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
