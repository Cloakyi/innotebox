package de.notizen.app.sicherheit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/**
 * Der Systemdialog zum Entsperren (Phase 19).
 *
 * **`android.hardware.biometrics.BiometricPrompt` aus dem System, nicht
 * `androidx.biometric`.** Die Bibliothek ist ein Rückwärtsmantel um genau
 * diesen Dialog für Geräte vor Android 9 und verlangt eine `FragmentActivity`;
 * unser minSdk ist 31, und die Activity ist eine `ComponentActivity`. Die
 * Signaturen sind am 2026-09-19 gegen das `android.jar` der API 37 geprüft:
 * `BiometricPrompt.Builder(Context).setTitle().setSubtitle()
 * .setAllowedAuthenticators(int).build()`,
 * `authenticate(CancellationSignal, Executor, AuthenticationCallback)`,
 * `BiometricManager.canAuthenticate(int)`.
 *
 * **Biometrie oder das Passwort des Geräts**, wie bei Banking-Apps:
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`. WEAK schließt STRONG ein und lässt
 * auch die Gesichtserkennung zu, die auf manchen Geräten nur als WEAK gilt;
 * hier wird nichts entschlüsselt, also braucht es keine STRONG-Bindung. Mit
 * `DEVICE_CREDENTIAL` darf kein eigener Abbrechen-Knopf gesetzt werden, das
 * System zeigt seinen.
 */
object Entsperrung {

    private const val WEGE = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Ob das Gerät eine Bildschirmsperre oder Biometrie hat, mit der sich entsperren ließe. */
    fun moeglich(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        manager.canAuthenticate(WEGE) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** Warum es nicht geht, als Satz für die Einstellungen; `null`, wenn es geht. */
    fun hindernis(context: Context): String? = runCatching {
        val manager = context.getSystemService(BiometricManager::class.java)
            ?: return "Dieses Gerät bietet keine Entsperrung an."
        when (manager.canAuthenticate(WEGE)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Dieses Gerät hat noch keine Bildschirmsperre. Richte in den Einstellungen " +
                    "des Geräts erst eine ein, dann lässt sich die App sperren."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Dieses Gerät bietet keine Entsperrung an."
            else -> "Die Entsperrung steht auf diesem Gerät gerade nicht bereit."
        }
    }.getOrDefault("Die Entsperrung steht auf diesem Gerät gerade nicht bereit.")

    /**
     * Zeigt den Dialog. [onErfolg] nach dem Entsperren, [onAbbruch] mit dem
     * Satz des Systems, wenn der Nutzer abbricht oder das System verweigert.
     */
    fun anfordern(
        activity: Activity,
        titel: String,
        untertitel: String,
        onErfolg: () -> Unit,
        onAbbruch: (String) -> Unit,
    ) {
        val dialog = runCatching {
            BiometricPrompt.Builder(activity)
                .setTitle(titel)
                .setSubtitle(untertitel)
                .setAllowedAuthenticators(WEGE)
                .build()
        }.getOrElse {
            onAbbruch("Die Entsperrung ließ sich nicht öffnen.")
            return
        }
        dialog.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onErfolg()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onAbbruch(errString.toString())
                }
                // `onAuthenticationFailed` (ein Finger, der nicht passt) laesst
                // den Dialog offen; das System zaehlt selbst und meldet am
                // Ende einen Fehler.
            },
        )
    }
}

/** Die Activity hinter einem Compose-Context, durch alle Huellen hindurch. */
fun Context.alsActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
