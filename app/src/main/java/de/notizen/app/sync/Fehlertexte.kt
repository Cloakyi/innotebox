package de.notizen.app.sync

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Macht aus einem Play-Services-Fehler einen Satz.
 *
 * Warum es das braucht: Ein `ApiException` trägt als Meldung nur den
 * nackten Zahlencode, etwa `8: null`. Genau das stand nach einem Fehlversuch in
 * der App, eine Zahl, die niemandem sagt, ob er warten, neu anmelden oder etwas
 * einstellen soll. Und `8` bedeutet ausgerechnet „unbekannter interner Fehler",
 * also: gleich nochmal versuchen.
 *
 * Die Codes stammen aus `CommonStatusCodes`, nachgesehen im AAR von
 * play-services-basement 18.9.0 am 2026-08-23. Der Code steht trotzdem am Ende
 * jeder Meldung: Wenn es doch einmal ein Fall wird, den niemand vorhergesehen
 * hat, ist er die einzige Spur.
 */
fun lesbar(fehler: Throwable): String {
    val api = fehler as? ApiException
        ?: return fehler.message ?: fehler::class.java.simpleName

    val satz = when (api.statusCode) {
        CommonStatusCodes.NETWORK_ERROR ->
            "Keine Verbindung zu Google. Bitte später noch einmal."

        // Der Fall, der wirklich auftrat: Play Services hat sich verschluckt.
        // Ein zweiter Versuch hilft meistens, ein Neustart der App immer.
        CommonStatusCodes.INTERNAL_ERROR ->
            "Google Play-Dienste antworten gerade nicht. Bitte noch einmal versuchen."

        CommonStatusCodes.CANCELED -> "Abgebrochen."
        CommonStatusCodes.TIMEOUT -> "Google hat zu lange gebraucht. Bitte noch einmal."
        CommonStatusCodes.SIGN_IN_REQUIRED -> "Bitte melde dich bei Google an."
        CommonStatusCodes.INVALID_ACCOUNT -> "Dieses Google-Konto lässt sich nicht verwenden."
        ConnectionResult.SERVICE_DISABLED -> "Google Play-Dienste sind auf diesem Gerät abgeschaltet."

        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
            "Google Play-Dienste sind zu alt. Bitte im Play Store aktualisieren."

        // Kein Nutzerfehler, sondern einer in der Einrichtung: falscher
        // Fingerabdruck, falscher Paketname, falsche Client-Kennung. Das gehoert
        // so gesagt, sonst sucht man an der falschen Stelle.
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Die App ist bei Google nicht richtig eingetragen (Paketname oder Fingerabdruck)."

        CommonStatusCodes.API_NOT_CONNECTED ->
            "Google Play-Dienste stehen auf diesem Gerät nicht bereit."

        else -> "Der Zugriff auf Google ist gescheitert."
    }
    return "$satz (${api.statusCode})"
}
