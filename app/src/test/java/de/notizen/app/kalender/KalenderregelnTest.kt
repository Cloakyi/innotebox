package de.notizen.app.kalender

import de.notizen.core.data.db.relation.Kalenderzeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wer einen Termin im Kalender bekommt und wer keinen.
 *
 * **Beide Richtungen sind teuer.** Ein Termin zu viel steht in einem Kalender,
 * den womöglich andere sehen; einer zu wenig heißt, dass eine Erinnerung
 * nirgends auftaucht. Und beides fällt erst Wochen später auf, wenn es klingelt
 * oder eben nicht.
 */
class KalenderregelnTest {

    private fun zeile(
        id: String = "n1",
        titel: String = "Zahnarzt",
        an: Boolean = true,
        termin: Long? = null,
        geloescht: Long? = null,
        erinnerung: Long? = 5_000L,
    ) = Kalenderzeile(
        noteId = id,
        title = titel,
        calendarEnabled = an,
        calendarEventId = termin,
        deletedAt = geloescht,
        triggerAt = erinnerung,
    )

    // ------------------------------------------------------- wer hineingehoert

    @Test
    fun `eine Notiz mit Erinnerung gehoert hinein`() {
        assertTrue(sollImKalender(zeile(), hauptschalterAn = true))
    }

    @Test
    fun `bei ausgeschaltetem Hauptschalter gehoert nichts hinein`() {
        assertEquals(false, sollImKalender(zeile(), hauptschalterAn = false))
    }

    @Test
    fun `eine ausgenommene Notiz gehoert nicht hinein`() {
        assertEquals(false, sollImKalender(zeile(an = false), hauptschalterAn = true))
    }

    @Test
    fun `was im Papierkorb liegt, gehoert nicht hinein`() {
        assertEquals(false, sollImKalender(zeile(geloescht = 99L), hauptschalterAn = true))
    }

    @Test
    fun `ohne Erinnerung gibt es nichts einzutragen`() {
        assertEquals(false, sollImKalender(zeile(erinnerung = null), hauptschalterAn = true))
    }

    // -------------------------------------------------------------- die Taten

    @Test
    fun `ohne Termin wird einer angelegt`() {
        val taten = taten(listOf(zeile()), hauptschalterAn = true)

        assertEquals(
            listOf(Kalendertat.Anlegen("n1", "Zahnarzt", 5_000L)),
            taten,
        )
    }

    @Test
    fun `ein vorhandener Termin wird nachgezogen`() {
        val taten = taten(listOf(zeile(termin = 42L)), hauptschalterAn = true)

        assertEquals(
            listOf(Kalendertat.Aendern("n1", 42L, "Zahnarzt", 5_000L)),
            taten,
        )
    }

    /**
     * Der Ausschalter muss auch aufräumen.
     *
     * Ein Schalter, der nur neue Termine verhindert und die alten stehen lässt,
     * ist kein Ausschalter. Man legt ihn um, weil man die Termine nicht mehr im
     * Kalender haben will.
     */
    @Test
    fun `beim Abschalten fliegen die eingetragenen Termine raus`() {
        val taten = taten(listOf(zeile(termin = 42L)), hauptschalterAn = false)

        assertEquals(listOf(Kalendertat.Entfernen("n1", 42L)), taten)
    }

    @Test
    fun `eine ausgenommene Notiz verliert ihren Termin`() {
        val taten = taten(listOf(zeile(an = false, termin = 42L)), hauptschalterAn = true)

        assertEquals(listOf(Kalendertat.Entfernen("n1", 42L)), taten)
    }

    @Test
    fun `eine weggeworfene Notiz verliert ihren Termin`() {
        val taten = taten(listOf(zeile(geloescht = 99L, termin = 42L)), hauptschalterAn = true)

        assertEquals(listOf(Kalendertat.Entfernen("n1", 42L)), taten)
    }

    @Test
    fun `eine geloeschte Erinnerung nimmt den Termin mit`() {
        val taten = taten(listOf(zeile(erinnerung = null, termin = 42L)), hauptschalterAn = true)

        assertEquals(listOf(Kalendertat.Entfernen("n1", 42L)), taten)
    }

    @Test
    fun `wo nichts zu tun ist, wird nichts getan`() {
        // Keine Erinnerung, kein Termin: die Zeile kommt aus der Abfrage nur
        // deshalb, weil sie einmal beides hatte.
        val taten = taten(listOf(zeile(erinnerung = null)), hauptschalterAn = true)

        assertEquals(emptyList<Kalendertat>(), taten)
    }

    // ------------------------------------------------------- die Beschriftung

    /**
     * Eine Notiz darf im Eingang titellos sein, ein Termin nicht.
     *
     * Ein leerer Balken im Kalender lässt einen raten, wofür er steht, und man
     * müsste die App öffnen, um es herauszufinden.
     */
    @Test
    fun `eine titellose Notiz bekommt trotzdem eine Beschriftung`() {
        val taten = taten(listOf(zeile(titel = "  ")), hauptschalterAn = true)

        assertEquals("Notiz ohne Titel", (taten.single() as Kalendertat.Anlegen).titel)
    }

    @Test
    fun `ein vorhandener Titel bleibt unangetastet`() {
        assertEquals("Zahnarzt", beschriftung("Zahnarzt"))
    }

    // ====================================== Welchen Kalender man vor sich hat

    private fun kalender(
        id: Long,
        name: String,
        konto: String = "ich@example.com",
        haupt: Boolean = false,
    ) = Kalenderwahl(id = id, name = name, konto = konto, haupt = haupt)

    /**
     * Der Hauptkalender heisst beim Anbieter wie die Adresse des Kontos.
     *
     * Beides untereinander zu schreiben ergaebe zweimal dasselbe, und in einer
     * Liste sieht eine nackte Mailadresse zwischen Namen wie „Arbeit" aus, als
     * stuenden dort Konten zur Auswahl statt Kalender.
     */
    @Test
    fun `der Hauptkalender heisst Hauptkalender und nicht wie das Konto`() {
        val wahl = kalender(1, "ich@example.com", haupt = true)

        assertEquals("Hauptkalender", kalenderbeschriftung(wahl))
    }

    @Test
    fun `jeder andere Kalender behaelt seinen Namen`() {
        assertEquals("Arbeit", kalenderbeschriftung(kalender(2, "Arbeit")))
    }

    /**
     * Der Fehler vom 2026-08-23.
     *
     * Vorgeschlagen wurde einfach der erste der Liste, und die war alphabetisch
     * sortiert. Wer neben seinem Konto noch einen zweiten Kalender hat, bekam
     * den vorgeschlagen und musste erst merken, dass er umstellen muss.
     */
    @Test
    fun `vorgeschlagen wird der Hauptkalender, nicht der erste der Liste`() {
        val liste = listOf(
            kalender(2, "Arbeit"),
            kalender(1, "ich@example.com", haupt = true),
        )

        assertEquals(1L, vorschlag(liste)?.id)
    }

    @Test
    fun `ohne Hauptkalender wird der erste vorgeschlagen`() {
        val liste = listOf(kalender(2, "Arbeit"), kalender(3, "Verein"))

        assertEquals(2L, vorschlag(liste)?.id)
    }

    @Test
    fun `ohne Kalender gibt es keinen Vorschlag`() {
        assertEquals(null, vorschlag(emptyList()))
    }

    @Test
    fun `der Hauptkalender steht in der Auswahl oben`() {
        val liste = listOf(
            kalender(2, "Arbeit"),
            kalender(3, "Verein"),
            kalender(1, "ich@example.com", haupt = true),
        )

        assertEquals(listOf(1L, 2L, 3L), sortiert(liste).map { it.id })
    }

    @Test
    fun `bei zwei Konten bleiben deren Kalender beieinander`() {
        val liste = listOf(
            kalender(4, "Arbeit", konto = "b@example.com"),
            kalender(2, "Arbeit", konto = "a@example.com"),
            kalender(3, "Verein", konto = "a@example.com"),
        )

        assertEquals(listOf(2L, 3L, 4L), sortiert(liste).map { it.id })
    }
}
