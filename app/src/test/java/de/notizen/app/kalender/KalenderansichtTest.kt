package de.notizen.app.kalender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tag, Woche und Monat.
 *
 * Die Rechnungen sehen harmlos aus und sind es nicht: Eine um einen Tag falsche
 * Grenze lässt einen Termin am Rand verschwinden, und das merkt man erst, wenn
 * man ihn sucht.
 */
class KalenderansichtTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** Ein Mittwoch. */
    private val mittwoch: LocalDate = LocalDate.of(2026, 8, 19)

    // ------------------------------------------------------------ Wochenrand

    @Test
    fun `die Woche faengt am Montag an`() {
        assertEquals(LocalDate.of(2026, 8, 17), wochenstart(mittwoch))
    }

    @Test
    fun `ein Montag ist sein eigener Wochenanfang`() {
        val montag = LocalDate.of(2026, 8, 17)
        assertEquals(montag, wochenstart(montag))
    }

    @Test
    fun `ein Sonntag gehoert noch zur Woche davor`() {
        val sonntag = LocalDate.of(2026, 8, 23)
        assertEquals(LocalDate.of(2026, 8, 17), wochenstart(sonntag))
    }

    // ----------------------------------------------------------- Monatsraster

    /**
     * Das Raster fängt beim Montag vor dem Ersten an.
     *
     * Der 1. August 2026 ist ein Samstag, das Raster beginnt also am 27. Juli.
     * Ohne das begänne der Monat mitten in einer Zeile.
     */
    @Test
    fun `das Raster faengt beim Montag vor dem Ersten an`() {
        assertEquals(
            LocalDate.of(2026, 7, 27),
            rasterstart(java.time.YearMonth.of(2026, 8)),
        )
    }

    @Test
    fun `faengt der Monat an einem Montag an, faengt das Raster dort an`() {
        // Der 1. Juni 2026 ist ein Montag.
        assertEquals(
            LocalDate.of(2026, 6, 1),
            rasterstart(java.time.YearMonth.of(2026, 6)),
        )
    }

    // ---------------------------------------------------------------- Bereich

    private fun tage(ansicht: Kalenderansicht, anker: LocalDate): Long {
        val (von, bis) = bereich(ansicht, anker, berlin)
        return (bis - von) / (24L * 60 * 60 * 1000)
    }

    @Test
    fun `der Tag umfasst einen Tag`() {
        assertEquals(1L, tage(Kalenderansicht.TAG, mittwoch))
    }

    @Test
    fun `die Woche umfasst sieben Tage`() {
        assertEquals(7L, tage(Kalenderansicht.WOCHE, mittwoch))
    }

    /**
     * Der Monat umfasst SECHS WOCHEN, nicht einen Monat.
     *
     * Das Raster zeigt am Anfang und Ende Tage der Nachbarmonate. Ohne sie im
     * Zeitraum blieben die Punkte dort leer, obwohl an diesen Tagen etwas steht.
     */
    @Test
    fun `der Monat umfasst das ganze Raster`() {
        assertEquals(42L, tage(Kalenderansicht.MONAT, mittwoch))
    }

    @Test
    fun `der Tagesbereich faengt um Mitternacht an`() {
        val (von, _) = bereich(Kalenderansicht.TAG, mittwoch, berlin)
        val zurueck = java.time.Instant.ofEpochMilli(von).atZone(berlin)

        assertEquals(mittwoch, zurueck.toLocalDate())
        assertEquals(0, zurueck.hour)
        assertEquals(0, zurueck.minute)
    }

    /**
     * Ein Termin um 0:30 Uhr liegt in UTC noch im Vortag.
     *
     * Wer die Grenzen in UTC zieht, verliert ihn am Rand. Dieselbe Falle wie
     * beim Datumswähler der Erinnerungen.
     */
    @Test
    fun `ein Termin kurz nach Mitternacht faellt in seinen Tag`() {
        val (von, bis) = bereich(Kalenderansicht.TAG, mittwoch, berlin)
        val halbEins = mittwoch.atTime(0, 30).atZone(berlin).toInstant().toEpochMilli()

        assertTrue(halbEins in von until bis)
    }

    // ------------------------------------------------------------- Blaettern

    @Test
    fun `im Tag blaettert man Tage`() {
        assertEquals(
            LocalDate.of(2026, 8, 20),
            verschoben(Kalenderansicht.TAG, mittwoch, 1),
        )
    }

    @Test
    fun `in der Woche blaettert man Wochen`() {
        assertEquals(
            LocalDate.of(2026, 8, 26),
            verschoben(Kalenderansicht.WOCHE, mittwoch, 1),
        )
    }

    @Test
    fun `im Monat blaettert man Monate`() {
        assertEquals(
            LocalDate.of(2026, 9, 19),
            verschoben(Kalenderansicht.MONAT, mittwoch, 1),
        )
    }

    @Test
    fun `zurueck geht genauso`() {
        assertEquals(
            LocalDate.of(2026, 8, 12),
            verschoben(Kalenderansicht.WOCHE, mittwoch, -1),
        )
    }

    // ---------------------------------------------------------------- Heute

    @Test
    fun `im Monat reicht derselbe Monat`() {
        assertTrue(zeigtHeute(Kalenderansicht.MONAT, LocalDate.of(2026, 8, 1), mittwoch))
    }

    @Test
    fun `im Tag muss es derselbe Tag sein`() {
        assertFalse(zeigtHeute(Kalenderansicht.TAG, LocalDate.of(2026, 8, 1), mittwoch))
        assertTrue(zeigtHeute(Kalenderansicht.TAG, mittwoch, mittwoch))
    }

    @Test
    fun `in der Woche reicht dieselbe Woche`() {
        assertTrue(zeigtHeute(Kalenderansicht.WOCHE, LocalDate.of(2026, 8, 17), mittwoch))
        assertFalse(zeigtHeute(Kalenderansicht.WOCHE, LocalDate.of(2026, 8, 24), mittwoch))
    }

    // ------------------------------------------------------------- Kopftext

    @Test
    fun `der Monat steht mit Jahr da`() {
        assertEquals("August 2026", kopftext(Kalenderansicht.MONAT, mittwoch))
    }

    @Test
    fun `der Tag steht ausgeschrieben da`() {
        assertEquals("Mittwoch, 19. August 2026", kopftext(Kalenderansicht.TAG, mittwoch))
    }

    /** „24. bis 30. August" liest sich besser als „24. August bis 30. August". */
    @Test
    fun `bei einer Woche in einem Monat steht der Monat nur einmal`() {
        assertEquals("17. bis 23. August 2026", kopftext(Kalenderansicht.WOCHE, mittwoch))
    }

    @Test
    fun `eine Woche ueber zwei Monate nennt beide`() {
        // Der 31. August 2026 ist ein Montag.
        val woche = LocalDate.of(2026, 9, 2)

        assertEquals(
            "31. August bis 6. September 2026",
            kopftext(Kalenderansicht.WOCHE, woche),
        )
    }

    @Test
    fun `eine Woche ueber zwei Jahre nennt beide Jahre`() {
        // Der 28. Dezember 2026 ist ein Montag.
        val silvesterwoche = LocalDate.of(2026, 12, 30)

        assertEquals(
            "28. Dezember 2026 bis 3. Januar 2027",
            kopftext(Kalenderansicht.WOCHE, silvesterwoche),
        )
    }
}
