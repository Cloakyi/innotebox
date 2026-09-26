package de.notizen.app.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.first
import de.notizen.app.util.warten
import javax.inject.Inject
import javax.inject.Singleton

/** Der Drive-Scope. Nicht-sensibel, deckt nur Dateien ab, die diese App angelegt hat. */
const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** Was beim Beschaffen eines Zugriffstokens herauskam. */
sealed interface Zugang {

    /** Ein gültiges, kurzlebiges Zugriffstoken. */
    data class Erteilt(val token: String) : Zugang

    /**
     * Der Nutzer muss selbst tätig werden, beim ersten Verbinden, nach einem
     * Widerruf, oder wenn das Token abgelaufen ist.
     *
     * Die [absicht] öffnet Googles eigenen Dialog. Sie muss aus einer
     * Activity heraus gestartet werden; ein Hintergrundlauf kann das nicht und
     * darf es auch nicht versuchen.
     */
    data class AnmeldungNoetig(val absicht: PendingIntent) : Zugang

    /** Kein Netz, kein Play-Dienst, oder etwas anderes ging schief. */
    data class Fehler(val grund: String) : Zugang

    /**
     * Der Nutzer hat die Verbindung selbst gelöst.
     *
     * Kein Fehler und keine fehlende Anmeldung, sondern eine Entscheidung. Es
     * wird deshalb auch gar nicht erst bei Google nachgefragt: Ein stiller
     * Aufruf würde bei erteilter Erlaubnis sofort ein neues Token liefern, und
     * die App hielte sich wieder für verbunden.
     */
    data object Getrennt : Zugang

    /**
     * Drive wurde auf diesem Gerät noch nie verbunden.
     *
     * Dann fragt die App gar nicht erst bei Google nach, auch nicht lautlos.
     * Erst wer „Verbinden" antippt, spricht mit Google ([Anmeldung.verbinden]).
     */
    data object NieVerbunden : Zugang
}

/**
 * Beschafft Zugriffstokens für Google Drive.
 *
 * Warum `AuthorizationClient` und nicht Credential Manager: Die beiden
 * beantworten verschiedene Fragen. Credential Manager klärt, wer der Nutzer
 * ist, das brauchen wir gar nicht. `AuthorizationClient` klärt, ob die App auf
 * Drive zugreifen darf, und genau darum geht es hier. Das alte
 * `GoogleSignIn` ist abgelöst und kommt nicht in Frage.
 *
 * Es gibt kein Refresh-Token, und das ist Absicht. Google gibt einer
 * Android-App nur kurzlebige Zugriffstokens. Der Ersatz für „erneuern" ist,
 * [zugang] einfach wieder aufzurufen: Ist der Zugriff schon erteilt, kommt
 * lautlos ein frisches Token zurück, ohne dass jemand etwas sieht. Erst wenn
 * die Erlaubnis fehlt oder widerrufen wurde, meldet sich
 * [Zugang.AnmeldungNoetig].
 *
 * Ein abgelaufener Zugang ist ein sichtbarer Zustand, kein stiller
 * Fehlschlag (SYNC.md 3). Der Hintergrundlauf kann keinen Dialog zeigen, er
 * setzt stattdessen den Sync-Status und hört auf. Alles andere wäre ein Sync,
 * der wochenlang nichts tut und nichts sagt.
 */
@Singleton
class Anmeldung @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val einstellungen: Einstellungen,
) {

    private val klient by lazy { Identity.getAuthorizationClient(context) }

    private val anfrage: AuthorizationRequest
        get() = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .build()

    /**
     * Ein Zugriffstoken, wenn möglich ohne Zutun des Nutzers.
     *
     * Der Aufruf ist billig genug, um ihn vor jedem Abgleich zu machen, das
     * ist der vorgesehene Weg, ein Token zu erneuern.
     */
    suspend fun zugang(): Zugang = try {
        when {
            einstellungen.syncGetrennt().first() -> Zugang.Getrennt
            !einstellungen.syncJeVerbunden().first() -> Zugang.NieVerbunden
            else -> deuten(klient.authorize(anfrage).warten())
        }
    } catch (fehler: Throwable) {
        Zugang.Fehler(lesbar(fehler))
    }

    /**
     * Verbindet, weil der Nutzer „Verbinden" angetippt hat.
     *
     * Hebt ein früheres Trennen auf und fragt Google auch dann, wenn noch nie
     * verbunden war. Ohne das Aufheben bliebe der Ausschalter stehen, und das
     * Verbinden führte sichtbar zu nichts.
     */
    suspend fun verbinden(): Zugang = try {
        einstellungen.setSyncGetrennt(false)
        merken(deuten(klient.authorize(anfrage).warten()))
    } catch (fehler: Throwable) {
        Zugang.Fehler(lesbar(fehler))
    }

    /** Ab dem ersten erteilten Zugang gilt Drive auf diesem Gerät als verbunden. */
    private suspend fun merken(zugang: Zugang): Zugang {
        if (zugang is Zugang.Erteilt) einstellungen.setSyncJeVerbunden(true)
        return zugang
    }

    /**
     * Das Ergebnis, nachdem der Nutzer Googles Dialog durchlaufen hat.
     *
     * Die Activity reicht das zurückkommende [Intent] hier herein.
     */
    suspend fun ausDialog(daten: Intent?): Zugang = try {
        if (daten == null) {
            Zugang.Fehler("Der Anmeldedialog kam ohne Ergebnis zurück.")
        } else {
            merken(deuten(klient.getAuthorizationResultFromIntent(daten)))
        }
    } catch (fehler: Throwable) {
        Zugang.Fehler(lesbar(fehler))
    }

    /**
     * Deutet ein [AuthorizationResult].
     *
     * Ausgelagert und ohne Google-Aufruf, damit die Fallunterscheidung prüfbar
     * ist: `hasResolution` schlägt alles, denn ein Ergebnis kann beides tragen,
     * eine Absicht UND ein altes Token. Wer zuerst aufs Token schaut, arbeitet
     * mit einem, das gerade widerrufen wurde.
     */
    private fun deuten(ergebnis: AuthorizationResult): Zugang = when {
        ergebnis.hasResolution() ->
            ergebnis.pendingIntent
                ?.let { Zugang.AnmeldungNoetig(it) }
                ?: Zugang.Fehler("Google verlangt eine Anmeldung, nennt aber keinen Weg dorthin.")

        ergebnis.accessToken.isNullOrBlank() ->
            Zugang.Fehler("Google hat kein Zugriffstoken geliefert.")

        // Der Nutzer darf im Dialog einzelne Rechte abwählen. Ohne diese Prüfung
        // hielte die App sich für verbunden und bekäme bei jedem Drive-Aufruf
        // einen 403 -- weit weg von der Stelle, an der es schiefging.
        DRIVE_SCOPE !in ergebnis.grantedScopes ->
            Zugang.Fehler("Der Zugriff auf Google Drive wurde nicht erteilt.")

        else -> Zugang.Erteilt(ergebnis.accessToken!!)
    }

    /**
     * Nimmt der App den Zugriff.
     *
     * Zwei Schritte, und beide sind nötig. `clearToken` wirft das
     * zwischengespeicherte Token weg, `revokeAccess` nimmt die Erlaubnis
     * zurück. Ohne den zweiten Schritt besorgt der nächste `authorize`-Aufruf
     * lautlos ein frisches Token, und „Trennen" hätte nichts getrennt.
     *
     * `clearToken` braucht das Token, das es wegwerfen soll. Der frühere
     * Aufruf ohne `setToken` baute eine Anfrage über nichts. Er schlug still
     * fehl, und die App sagte trotzdem „Verbindung gelöst", der Screen zeigte
     * beim nächsten Öffnen wieder „verbunden", weil er nachfragt statt zu raten.
     * Nachgesehen im AAR von play-services-auth 21.4.0 am 2026-08-23.
     */
    suspend fun abmelden(): Trennung {
        // ZUERST der lokale Schalter, und zwar unabhaengig davon, was Google
        // gleich sagt. Frueher haing das Trennen am Widerruf -- schlug der fehl,
        // meldete die App "Das Trennen hat nicht geklappt" und blieb verbunden.
        // Das ist der falsche Vorrang: Ob diese App zu Google spricht, ist eine
        // Entscheidung des Nutzers und keine Bitte an Google.
        einstellungen.setSyncGetrennt(true)

        return try {
            val ergebnis = klient.authorize(anfrage).warten()
            if (ergebnis.hasResolution()) return Trennung.Vollstaendig

            ergebnis.accessToken?.takeIf { it.isNotBlank() }?.let { token ->
                klient.clearToken(ClearTokenRequest.builder().setToken(token).build()).warten()
            }

            klient.revokeAccess(
                RevokeAccessRequest.builder()
                    .setScopes(listOf(Scope(DRIVE_SCOPE)))
                    .apply {
                        // Ohne Konto widerruft Google fuer das zuletzt
                        // verwendete. Wir nennen es, wenn wir es kennen.
                        ergebnis.toGoogleSignInAccount()?.account?.let { setAccount(it) }
                    }
                    .build(),
            ).warten()
            Trennung.Vollstaendig
        } catch (fehler: Throwable) {
            // Die App schweigt ab jetzt, aber die Erlaubnis im Google-Konto
            // steht noch. Das gehoert gesagt, nicht verschwiegen.
            Trennung.NurLokal
        }
    }
}

/** Wie weit ein Trennen gekommen ist. */
enum class Trennung {
    /** Lokal aus und die Erlaubnis bei Google widerrufen. */
    Vollstaendig,

    /** Lokal aus. Der Widerruf bei Google ging nicht durch. */
    NurLokal,
}

