package de.notizen.app.sicherheit

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die Zeit seit dem Start des Geräts, in Millisekunden.
 *
 * Für die Sperrzeit und nicht die Uhrzeit: Wer die Uhr des Handys
 * zurückstellt, soll die Sperre damit nicht umgehen können. Diese Zeit läuft
 * unabhängig von der Einstellung weiter und lässt sich nicht verstellen.
 */
fun interface Laufuhr {
    fun jetzt(): Long
}

@Module
@InstallIn(SingletonComponent::class)
object LaufuhrModule {
    @Provides
    fun laufuhr(): Laufuhr = Laufuhr { SystemClock.elapsedRealtime() }
}

/**
 * Ob die App gerade gesperrt ist.
 *
 * Eine Funktion der App, nicht des Systems (seit 2026-09-19): wie bei
 * Banking-Apps und dem Tagebuch auf dem Pixel, nicht über Androids
 * vertrauliches Profil. Gesperrt wird beim Start des Prozesses und nach der
 * eingestellten Zeit im Hintergrund; entsperrt wird über den Systemdialog
 * ([Entsperrung]): Biometrie, das Passwort des Geräts als Rückfall.
 *
 * Lebt als Singleton außerhalb der Activity, weil die Activity beim Drehen neu
 * entsteht und eine Sperre, die dabei vergisst, dass sie entsperrt war, bei
 * jeder Drehung neu fragte. Eine Drehung ist kein Hintergrund
 * (`beimStopp(konfigurationswechsel = true)`).
 *
 * [entschieden] ist falsch, solange noch nicht feststeht, ob gesperrt wird:
 * beim Start, bevor die Einstellung gelesen ist, und nach jeder Rückkehr aus
 * dem Hintergrund. So lange liegt eine Abdeckung über der App, damit der
 * Inhalt nicht kurz aufblitzt, bevor die Sperre greift.
 *
 * Was hier NICHT ist: eine Verschlüsselung. Die Datenbank liegt weiter
 * unverschlüsselt in der Sandbox der App; die Sperre ist eine Sperre der
 * Oberfläche. Die Verschlüsselung ist der eigene Posten in docs/ENTSCHEIDUNGEN.md, Abschnitt 12,
 * und wird vor dem Bau einzeln geplant.
 */
@Singleton
class Sperre @Inject constructor(
    private val einstellungen: Einstellungen,
    private val laufuhr: Laufuhr,
) {
    private val _gesperrt = MutableStateFlow(false)
    val gesperrt: StateFlow<Boolean> = _gesperrt.asStateFlow()

    private val _entschieden = MutableStateFlow(false)
    val entschieden: StateFlow<Boolean> = _entschieden.asStateFlow()

    private var hintergrundSeit: Long? = null
    private var jeGeprueft = false

    /**
     * Wie viele Fenster der App gerade sichtbar sind. Meist eins. Startet die
     * App sich selbst neu (nach einem Update, beim Öffnen einer geteilten
     * Notiz), kommt das neue Fenster, BEVOR das alte sein `onStop` meldet. Ohne
     * diese Zählung hielt die Sperre das für den Weg in den Hintergrund, legte
     * die Abdeckung über das neue Fenster und nahm sie nie wieder weg; am Gerät
     * blieb nur das Logo stehen (2026-10-13).
     */
    private var sichtbar = 0

    /**
     * Beim Sichtbarwerden der Activity. Sperrt beim ersten Mal im Prozess
     * (App-Start) und danach, wenn die App lang genug im Hintergrund war.
     */
    suspend fun beimStart() {
        // Vor dem ersten Warten zählen: `onStart` ruft das in einer Coroutine
        // auf, und bis zum ersten Lesen der Einstellung läuft sie sofort.
        sichtbar++
        if (!einstellungen.sperreAn().first()) {
            jeGeprueft = true
            hintergrundSeit = null
            _entschieden.value = true
            return
        }
        val seit = hintergrundSeit
        val grenzeMs = einstellungen.sperrverzoegerung().first().minuten * 60_000L
        val sperren = !jeGeprueft || (seit != null && laufuhr.jetzt() - seit >= grenzeMs)
        jeGeprueft = true
        hintergrundSeit = null
        if (sperren) _gesperrt.value = true
        _entschieden.value = true
    }

    /**
     * Beim Verschwinden der Activity. Eine Drehung zaehlt nicht als Hintergrund,
     * und ebenso wenig ein Fenster, das geht, während ein anderes schon da ist.
     */
    fun beimStopp(konfigurationswechsel: Boolean) {
        sichtbar = (sichtbar - 1).coerceAtLeast(0)
        if (konfigurationswechsel || sichtbar > 0) return
        if (hintergrundSeit == null) hintergrundSeit = laufuhr.jetzt()
        _entschieden.value = false
    }

    fun entsperrt() {
        _gesperrt.value = false
    }

    /** Beim Einschalten der Sperre: ab jetzt gilt sie, aber der Nutzer hat sich gerade ausgewiesen. */
    fun eingeschaltet() {
        jeGeprueft = true
        _gesperrt.value = false
    }
}
