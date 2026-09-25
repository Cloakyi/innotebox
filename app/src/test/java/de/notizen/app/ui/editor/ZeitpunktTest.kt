package de.notizen.app.ui.editor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Rechnet nach, dass eine Erinnerung genau zur gewählten Minute weckt.
 *
 * Der Punkt dieser Klasse ist, dass hier **nichts geschätzt** wird. Es gibt
 * keinen festen Versatz, keine „minus zwei Stunden"-Faustregel und keine
 * Annahme über die Zeitzone des Geräts. Geprüft wird über volle, halbe und
 * viertelstündige Zonenversätze, über die Datumsgrenze und über die
 * Sommerzeitumstellung hinweg -- und zwar so, wie es zählt: gewählte Uhrzeit
 * hinein, gewählte Uhrzeit wieder heraus.
 */
class ZeitpunktTest {

    private val urspruenglich: TimeZone = TimeZone.getDefault()

    @After
    fun zuruecksetzen() {
        TimeZone.setDefault(urspruenglich)
    }

    private fun inZone(zone: String, block: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        block()
    }

    /** Mitternacht UTC eines Tages -- genau das, was der DatePicker liefert. */
    private fun waehlertag(jahr: Int, monat: Int, tag: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.GERMANY).apply {
            clear()
            set(jahr, monat - 1, tag, 0, 0, 0)
        }.timeInMillis

    /** Liest einen Zeitstempel als Ortszeit zurück. */
    private fun vorOrt(millis: Long): String =
        Calendar.getInstance().let { k ->
            k.timeInMillis = millis
            "%04d-%02d-%02d %02d:%02d".format(
                k.get(Calendar.YEAR),
                k.get(Calendar.MONTH) + 1,
                k.get(Calendar.DAY_OF_MONTH),
                k.get(Calendar.HOUR_OF_DAY),
                k.get(Calendar.MINUTE),
            )
        }

    // ------------------------------------------------------ gewählt = geweckt

    @Test
    fun `Sommerzeit in Berlin`() = inZone("Europe/Berlin") {
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 9, 30)
        assertEquals("2026-07-15 09:30", vorOrt(zeitpunkt))
    }

    @Test
    fun `Winterzeit in Berlin`() = inZone("Europe/Berlin") {
        // Anderer Zonenversatz als im Sommer. Ein fester Abzug waere hier
        // bereits um eine Stunde daneben.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 1, 15), 9, 30)
        assertEquals("2026-01-15 09:30", vorOrt(zeitpunkt))
    }

    @Test
    fun `am Tag der Zeitumstellung`() = inZone("Europe/Berlin") {
        // In der Nacht auf den 29.03.2026 wird die Uhr um 02:00 vorgestellt.
        // Der Tag hat 23 Stunden -- jede Rechnung mit festen Tageslaengen geht
        // hier schief.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 3, 29), 9, 0)
        assertEquals("2026-03-29 09:00", vorOrt(zeitpunkt))
    }

    @Test
    fun `Zone mit halber Stunde Versatz`() = inZone("Asia/Kolkata") {
        // UTC+5:30. Wer in vollen Stunden rechnet, landet hier 30 Minuten
        // daneben.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 9, 30)
        assertEquals("2026-07-15 09:30", vorOrt(zeitpunkt))
    }

    @Test
    fun `Zone mit dreiviertel Stunde Versatz`() = inZone("Asia/Kathmandu") {
        // UTC+5:45. Es gibt keinen Versatz, den man raten koennte.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 23, 45)
        assertEquals("2026-07-15 23:45", vorOrt(zeitpunkt))
    }

    @Test
    fun `Zone westlich von Greenwich`() = inZone("America/New_York") {
        // Negativer Versatz: der UTC-Tag des Waehlers liegt vor Ort noch im
        // Vortag. Ohne die Umrechnung waere die Erinnerung einen Tag zu frueh.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 8, 0)
        assertEquals("2026-07-15 08:00", vorOrt(zeitpunkt))
    }

    @Test
    fun `kurz nach Mitternacht`() = inZone("Europe/Berlin") {
        // Der empfindlichste Fall: 00:15 Ortszeit liegt in UTC noch im Vortag.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 0, 15)
        assertEquals("2026-07-15 00:15", vorOrt(zeitpunkt))
    }

    @Test
    fun `Sekunden werden auf null gesetzt`() = inZone("Europe/Berlin") {
        // Sonst weckt es je nach Tippzeitpunkt bis zu 59 Sekunden spaeter als
        // angezeigt -- eine Ungenauigkeit, die man auf der Uhr sieht.
        val zeitpunkt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 9, 30)
        val k = Calendar.getInstance().apply { timeInMillis = zeitpunkt }
        assertEquals(0, k.get(Calendar.SECOND))
        assertEquals(0, k.get(Calendar.MILLISECOND))
    }

    // -------------------------------------------------- Weg und Rueckweg

    @Test
    fun `die Vorauswahl trifft denselben Tag`() = inZone("Europe/Berlin") {
        // Der Fehler, den diese Umrechnung verhindert: 00:15 Ortszeit direkt an
        // den Waehler gegeben stuende dort auf dem VORTAG.
        val lokal = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 0, 15)
        assertEquals(waehlertag(2026, 7, 15), Zeitpunkt.alsWaehlertag(lokal))
    }

    @Test
    fun `Vorauswahl und Rueckweg heben sich auf`() = inZone("America/New_York") {
        val lokal = Zeitpunkt.ausWahl(waehlertag(2026, 11, 3), 23, 59)
        val nochmal = Zeitpunkt.ausWahl(Zeitpunkt.alsWaehlertag(lokal), 23, 59)
        assertEquals(lokal, nochmal)
    }

    @Test
    fun `die naechste volle Stunde liegt in der Zukunft und auf der Null`() =
        inZone("Europe/Berlin") {
            val jetzt = Zeitpunkt.ausWahl(waehlertag(2026, 7, 15), 9, 37)
            val vorschlag = Zeitpunkt.naechsteVolleStunde(jetzt)

            assertEquals("2026-07-15 10:00", vorOrt(vorschlag))
        }
}
