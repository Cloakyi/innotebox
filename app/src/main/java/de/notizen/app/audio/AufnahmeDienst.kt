package de.notizen.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import de.notizen.app.MainActivity
import de.notizen.app.erinnerung.ErinnerungEmpfaenger
import de.notizen.core.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

const val KANAL_AUFNAHME = "aufnahme"

/**
 * Legt den Kanal für die Dienst-Benachrichtigung an.
 *
 * `IMPORTANCE_LOW`, damit sie ohne Ton und ohne Einblendung erscheint: sie ist
 * eine Anzeige, dass etwas läuft, keine Meldung.
 */
fun aufnahmeKanalAnlegen(context: Context) {
    val kanal = NotificationChannel(
        KANAL_AUFNAHME,
        "Aufnahme und Transkript",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Zeigt an, dass mitgeschnitten oder ein Transkript erstellt wird"
        setShowBadge(false)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
}

/** Wo die Aufnahme einer Notiz liegt. */
fun aufnahmeDatei(context: Context, notizId: String): File =
    File(File(context.filesDir, "audio").apply { mkdirs() }, "$notizId.wav")

/**
 * Nimmt auf und transkribiert — beides im Vordergrunddienst, beides einzeln.
 *
 * **Ein Vordergrunddienst ist hier Pflicht, nicht Vorsicht.** Ohne ihn nimmt
 * Android der App das Mikrofon, sobald der Bildschirm ausgeht — mitten im Satz,
 * ohne Meldung. Beim Transkribieren gilt dasselbe für die Rechenzeit: eine
 * halbe Stunde Aufnahme braucht seine Zeit, und wer die App dabei weglegt,
 * soll nicht von vorn anfangen müssen.
 *
 * Die beiden Aufgaben teilen sich den Dienst, aber **nicht den Typ**: Beim
 * Aufnehmen läuft er als `microphone`, beim Transkribieren als `dataSync`. Den
 * Mikrofon-Typ für eine reine Rechenaufgabe zu benutzen wäre falsch angemeldet
 * — und es zeigte dem Nutzer ein Mikrofonsymbol, obwohl nichts mithört.
 */
@AndroidEntryPoint
class AufnahmeDienst : Service() {

    @Inject lateinit var sitzung: AufnahmeSitzung

    @Inject lateinit var transkriptor: Transkriptor

    @Inject lateinit var transkription: Transkription

    @Inject lateinit var audio: AudioRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var arbeit: Job? = null

    @Volatile
    private var nimmtAuf = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notizId = intent?.getStringExtra(EXTRA_NOTIZ)

        when (intent?.action) {
            AKTION_STOPP -> {
                nimmtAuf = false
                return START_NOT_STICKY
            }

            AKTION_TRANSKRIPT -> {
                if (notizId != null) transkribieren(notizId)
                return START_NOT_STICKY
            }

            else -> {
                // START_NOT_STICKY, und ohne Notiz sofort wieder weg: Android
                // startet einen STICKY-Dienst nach einem Prozesstod mit
                // leerem Intent neu. Der haette dann nichts zu tun, wuerde aber
                // auch nie `startForeground` aufrufen -- und genau dafuer
                // beendet das System den Prozess nach fuenf Sekunden hart.
                if (notizId == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                aufnehmen(notizId)
                return START_NOT_STICKY
            }
        }
    }

    // ------------------------------------------------------------- Aufnehmen

    private fun aufnehmen(notizId: String) {
        if (nimmtAuf) return
        nimmtAuf = true

        vordergrund(
            meldung(notizId, "Aufnahme läuft", stoppbar = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        sitzung.beginnen(notizId)

        arbeit = scope.launch {
            val ziel = aufnahmeDatei(this@AufnahmeDienst, notizId)
            val mitschnitt = Mitschnitt(ziel)
            try {
                mitschnitt.starten()
                while (nimmtAuf) {
                    if (!mitschnitt.blockLesen()) break
                    sitzung.fortschritt(
                        dauerMs = mitschnitt.dauerMs,
                        pegel = mitschnitt.pegel,
                        hatTon = mitschnitt.hatTon,
                        feinpegel = mitschnitt.feinpegel,
                    )
                }
            } catch (fehler: Throwable) {
                sitzung.aufnahmeFehler(fehler.message ?: fehler::class.java.simpleName)
            } finally {
                // Die Schleife ist zu Ende -- egal ob durch "Fertig", durch
                // einen Fehler oder weil das Mikrofon wegbrach. Ab hier laeuft
                // nichts mehr, und das muss auch nach aussen sichtbar werden.
                nimmtAuf = false
                mitschnitt.close()
                // Der Anhang wird sofort eingetragen, auch ohne Transkript. Die
                // Aufnahme ist fuer sich genommen schon der Inhalt der Notiz.
                runCatching {
                    audio.aufnahmeSichern(notizId, ziel.takeIf { it.exists() }, emptyList())
                }
                sitzung.beenden()
                withContext(Dispatchers.Main) { beenden() }
            }
        }
    }

    // --------------------------------------------------------- Transkribieren

    private fun transkribieren(notizId: String) {
        vordergrund(
            meldung(notizId, "Transkript wird erstellt", stoppbar = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        arbeit = scope.launch {
            val datei = aufnahmeDatei(this@AufnahmeDienst, notizId)
            if (!datei.exists()) {
                sitzung.transkriptFehler("Zu dieser Notiz gibt es keine Aufnahme.")
                sitzung.transkriptFertig()
                withContext(Dispatchers.Main) { beenden() }
                return@launch
            }

            val (zustand, modus) = transkription.zustand()
            if (zustand !is Modellzustand.Bereit) {
                sitzung.transkriptFehler(
                    "Das Sprachmodell steht gerade nicht bereit. " +
                        "Die Aufnahme bleibt erhalten, versuch es später erneut.",
                )
                sitzung.transkriptFertig()
                withContext(Dispatchers.Main) { beenden() }
                return@launch
            }

            sitzung.transkriptBeginnen(modus)
            val gesammelt = mutableListOf<Transkriptteil>()

            transkriptor.transkribieren(datei, modus, transkription.sprache())
                .collect { schritt ->
                when (schritt) {
                    is Transkriptschritt.Zerlegt -> sitzung.zerlegt(schritt.abschnitte)
                    is Transkriptschritt.Fortschritt ->
                        sitzung.transkriptFortschritt(schritt.fertig, schritt.gesamt)
                    is Transkriptschritt.Teil -> {
                        gesammelt += schritt.teil
                        sitzung.teil(schritt.teil)
                    }
                    is Transkriptschritt.Fehler -> sitzung.transkriptFehler(schritt.text)
                    Transkriptschritt.Fertig -> Unit
                }
            }

            runCatching {
                audio.transkriptSetzen(
                    noteId = notizId,
                    abschnitte = gesammelt.map { Triple(it.text, it.startMs, it.endeMs) },
                )
            }
            sitzung.transkriptFertig()
            withContext(Dispatchers.Main) { beenden() }
        }
    }

    // ------------------------------------------------------------------ Rest

    private fun vordergrund(meldung: Notification, typ: Int) {
        ServiceCompat.startForeground(this, MELDUNG_ID, meldung, typ)
    }

    private fun beenden() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        nimmtAuf = false
        arbeit?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun meldung(notizId: String, titel: String, stoppbar: Boolean): Notification {
        val oeffnen = PendingIntent.getActivity(
            this,
            notizId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ErinnerungEmpfaenger.EXTRA_NOTIZ_OEFFNEN, notizId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, KANAL_AUFNAHME)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(titel)
            .setContentText("Antippen öffnet die Notiz")
            .setOngoing(true)
            .setContentIntent(oeffnen)
            .apply {
                if (stoppbar) {
                    addAction(
                        android.R.drawable.ic_media_pause,
                        "Beenden",
                        PendingIntent.getService(
                            this@AufnahmeDienst,
                            1,
                            Intent(this@AufnahmeDienst, AufnahmeDienst::class.java)
                                .setAction(AKTION_STOPP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            .build()
    }

    companion object {
        private const val MELDUNG_ID = 4711
        private const val EXTRA_NOTIZ = "notizId"
        private const val AKTION_STOPP = "de.notizen.app.AUFNAHME_STOPP"
        private const val AKTION_TRANSKRIPT = "de.notizen.app.TRANSKRIPT"

        fun aufnehmen(context: Context, notizId: String) {
            context.startForegroundService(
                Intent(context, AufnahmeDienst::class.java).putExtra(EXTRA_NOTIZ, notizId),
            )
        }

        fun stoppen(context: Context) {
            context.startService(
                Intent(context, AufnahmeDienst::class.java).setAction(AKTION_STOPP),
            )
        }

        fun transkribieren(context: Context, notizId: String) {
            context.startForegroundService(
                Intent(context, AufnahmeDienst::class.java)
                    .setAction(AKTION_TRANSKRIPT)
                    .putExtra(EXTRA_NOTIZ, notizId),
            )
        }
    }
}
