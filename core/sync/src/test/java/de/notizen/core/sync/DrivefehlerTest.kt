package de.notizen.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Welche Antwort von Drive zu welchem Fehler wird.
 *
 * Davon hängt ab, was der Abgleich tut: neu anmelden lassen, später noch einmal
 * versuchen oder aufgeben. Ein vorübergehender Fehler, der als endgültiger
 * durchgeht, lässt Änderungen bis zum nächsten Öffnen der App liegen.
 */
class DrivefehlerTest {

    @Test
    fun `401 heisst neu anmelden`() {
        assertTrue(drivefehlerZu(401, "") is Drivefehler.NichtErlaubt)
    }

    @Test
    fun `403 ohne Kontingent heisst neu anmelden`() {
        assertTrue(drivefehlerZu(403, """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}""") is Drivefehler.NichtErlaubt)
    }

    @Test
    fun `403 mit ueberschrittenem Kontingent ist ueberlastet`() {
        val fehler = drivefehlerZu(403, """{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}""")

        assertTrue(fehler is Drivefehler.Ueberlastet)
        assertEquals(403, (fehler as Drivefehler.Ueberlastet).code)
    }

    @Test
    fun `429 und alles ab 500 ist ueberlastet`() {
        listOf(429, 500, 502, 503, 504).forEach { code ->
            val fehler = drivefehlerZu(code, "")
            assertTrue("$code", fehler is Drivefehler.Ueberlastet)
            assertEquals(code, (fehler as Drivefehler.Ueberlastet).code)
        }
    }

    @Test
    fun `andere Fehler sind abgelehnt und werden nicht wiederholt`() {
        listOf(400, 404, 409, 412).forEach { code ->
            val fehler = drivefehlerZu(code, "")
            assertTrue("$code", fehler is Drivefehler.Abgelehnt)
            assertEquals(code, (fehler as Drivefehler.Abgelehnt).code)
        }
    }
}
