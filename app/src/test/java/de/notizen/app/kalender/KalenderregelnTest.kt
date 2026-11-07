package de.notizen.app.kalender

import de.notizen.core.data.db.relation.Kalenderzeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wer einen Termin im Kalender bekommt und wer keinen.
 *
 * Beide Richtungen sind teuer. Ein Termin zu viel steht in einem Kalender,
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
}
