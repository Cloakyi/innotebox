package de.notizen.app.sicherheit

import android.hardware.biometrics.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wie der Sperrbildschirm auf das Ende des Entsperrdialogs reagiert.
 *
 * Wegklicken ist kein Fehler und darf nach einem Drehen keinen neuen Dialog
 * auslösen; ein Abbruch durch das System beim Drehen dagegen schon.
 */
class EntsperrungTest {

    @Test
    fun `Wegklicken kommt vom Nutzer`() {
        assertEquals(Abbruchart.VOM_NUTZER, abbruchart(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED))
    }

    @Test
    fun `ein Abbruch durch das System ist kein Fehler`() {
        assertEquals(Abbruchart.VOM_SYSTEM, abbruchart(BiometricPrompt.BIOMETRIC_ERROR_CANCELED))
    }

    @Test
    fun `zu viele Versuche sind ein Fehler mit dem Satz des Systems`() {
        assertEquals(Abbruchart.FEHLER, abbruchart(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT))
        assertEquals(Abbruchart.FEHLER, abbruchart(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT))
    }
}
