package de.notizen.app.kalender

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Die Vorgaben je Kalender, wie sie gespeichert und gelesen werden. */
class KalendervorgabeTest {

    @Test
    fun hinUndZurueck() {
        listOf(
            Kalendervorgabe(),
            Kalendervorgabe(erinnerung = 10, dauer = 90, ganztaegig = true),
            Kalendervorgabe(erinnerung = Kalendervorgabe.KEINE_ERINNERUNG),
            Kalendervorgabe(erinnerung = 0, dauer = 15),
        ).forEach { v -> assertEquals(v, Kalendervorgabe.lesen(v.kodiert())) }
    }

    @Test
    fun leerOderKaputtIstAllgemein() {
        assertEquals(Kalendervorgabe(), Kalendervorgabe.lesen(null))
        assertEquals(Kalendervorgabe(), Kalendervorgabe.lesen(""))
        assertEquals(Kalendervorgabe(), Kalendervorgabe.lesen("unsinn;d=0;e=x"))
    }

    @Test
    fun erinnerungen() {
        assertEquals(listOf(30), Kalendervorgabe().erinnerungen(false, 30))
        assertEquals(listOf(5), Kalendervorgabe(erinnerung = 5).erinnerungen(false, 30))
        assertEquals(emptyList<Int>(), Kalendervorgabe(erinnerung = Kalendervorgabe.KEINE_ERINNERUNG).erinnerungen(false, 30))
        assertEquals(listOf(-540), Kalendervorgabe(erinnerung = 5).erinnerungen(true, 30))
    }

    @Test
    fun kalenderwoche() {
        // Der 1. Januar 2027 ist ein Freitag und gehoert noch zur Woche 53 von 2026.
        assertEquals(53, kalenderwochennummer(LocalDate.of(2027, 1, 1)))
        assertEquals(1, kalenderwochennummer(LocalDate.of(2027, 1, 4)))
        assertEquals(45, kalenderwochennummer(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun dauertexte() {
        assertEquals("15 Minuten", dauertext(15))
        assertEquals("1 Stunde", dauertext(60))
        assertEquals("1,5 Stunden", dauertext(90))
        assertEquals("2 Stunden", dauertext(120))
    }
}
