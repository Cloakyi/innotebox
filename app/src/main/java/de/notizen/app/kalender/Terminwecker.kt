package de.notizen.app.kalender

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.app.MainActivity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.kalender.Ausloeser
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

const val KANAL_TERMINE = "termine"

/** Eigener Kanal mit Weckerton. Die Id traegt eine Fassung, weil Android den Ton eines Kanals danach nicht mehr aendern laesst. */
const val KANAL_WECKER = "wichtige_termine_1"

/**
 * Legt die beiden Kanaele der Termine an: den gewoehnlichen und den fuer
 * wichtige Termine, der wie ein Wecker klingt.
 *
 * Der Ton des Weckerkanals ist der Weckerton des Systems mit der Verwendung
 * ALARM. Damit laeuft er ueber die Lautstaerke des Weckers und auch dann, wenn
 * „Bitte nicht stören“ Wecker durchlaesst, was die Voreinstellung ist.
 */
fun terminKanaeleAnlegen(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(KANAL_TERMINE, "Termine", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Erinnert an Termine, Aufgaben und Geburtstage im Kalender"
        },
    )
    manager.createNotificationChannel(
        NotificationChannel(KANAL_WECKER, "Wichtige Termine", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Klingelt wie ein Wecker und zeigt den Termin auf dem ganzen Bildschirm"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        },
    )
}

/**
 * Stellt die Wecker der Termine.
 *
 * Es steht immer nur EIN Alarm je Sorte: der naechste gewoehnliche und der
 * naechste wichtige. Klingelt er, zeigt der Empfaenger alles, was seit dem
 * letzten Mal faellig wurde, und stellt den naechsten. So muss sich niemand
 * merken, welcher von hundert Serienterminen gerade einen Alarm hat, und eine
 * geaenderte oder geloeschte Serie braucht kein Aufraeumen: Es wird einfach
 * neu gerechnet.
 *
 * Gewoehnliche Termine gehen wie die Erinnerungen der Notizen ueber
 * `setExactAndAllowWhileIdle`. Wichtige ueber `setAlarmClock`: Das ist der
 * Weg eines Weckers, Android verschiebt ihn nie, und das Weckersymbol in der
 * Statusleiste ist hier gewollt, weil es ein Wecker ist.
 */
@Singleton
class Terminwecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repo: KalenderRepository,
) {
    private val manager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val stand = context.getSharedPreferences("terminwecker", Context.MODE_PRIVATE)
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val schloss = Mutex()

    fun darfExaktWecken(): Boolean = manager.canScheduleExactAlarms()

    fun exaktEinstellungen(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))

    /** Ob der Wecker den ganzen Bildschirm zeigen darf. Ab Android 14 eine eigene Erlaubnis. */
    fun darfVollbild(): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun vollbildEinstellungen(): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /** Beobachtet die Termine und stellt bei jeder Aenderung neu. */
    @OptIn(FlowPreview::class)
    fun beobachten() {
        bereich.launch {
            repo.beobachteTermine().debounce(500).collect { planen(it) }
        }
    }

    /** Neu rechnen, etwa nach einem Neustart oder nachdem ein Alarm klingelte. */
    fun neuPlanen() {
        bereich.launch { planen(repo.termine()) }
    }

    /** Bis wohin schon gemeldet wurde. Beim allerersten Mal: jetzt, damit nichts Altes klingelt. */
    fun gemeldetBis(wichtig: Boolean): Long {
        val schluessel = if (wichtig) "bis_wichtig" else "bis_normal"
        val wert = stand.getLong(schluessel, 0L)
        if (wert > 0) return wert
        val jetzt = System.currentTimeMillis()
        stand.edit().putLong(schluessel, jetzt).apply()
        return jetzt
    }

    fun gemeldet(wichtig: Boolean, bis: Long) {
        stand.edit().putLong(if (wichtig) "bis_wichtig" else "bis_normal", bis).apply()
    }

    /**
     * Was jetzt faellig ist. Hoechstens zwei Stunden zurueck: Nach einem
     * langen Ausschalten soll nicht ein ganzer Tag Erinnerungen nachklingeln.
     */
    suspend fun faellig(wichtig: Boolean, jetzt: Long): List<Ausloeser> {
        val ab = maxOf(gemeldetBis(wichtig), jetzt - NACHHOLEN)
        return Terminregeln.ausloeser(
            repo.termine().filter { it.important == wichtig },
            ab, jetzt + VORLAUF, ZoneId.systemDefault(),
        )
    }

    private suspend fun planen(termine: List<EventEntity>) = schloss.withLock {
        val zone = ZoneId.systemDefault()
        for (wichtig in listOf(false, true)) {
            val nach = maxOf(gemeldetBis(wichtig), System.currentTimeMillis() - NACHHOLEN)
            val naechster = Terminregeln.naechster(termine, nach, zone, nurWichtige = wichtig)
            val absicht = absicht(wichtig)
            if (naechster == null || !darfExaktWecken()) {
                manager.cancel(absicht)
                continue
            }
            val zeit = maxOf(naechster.zeit, System.currentTimeMillis() + 1000)
            if (wichtig) {
                val zeigen = PendingIntent.getActivity(
                    context, ANFRAGE_ZEIGEN,
                    Intent(context, MainActivity::class.java).putExtra(EXTRA_KALENDER_OEFFNEN, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(zeit, zeigen), absicht)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zeit, absicht)
            }
        }
    }

    /** Schlummern: derselbe Termin in [minuten] noch einmal, als Wecker. */
    fun schlummern(terminId: String, schluessel: Long, minuten: Int) {
        if (!darfExaktWecken()) return
        val zeit = System.currentTimeMillis() + minuten * 60_000L
        val intent = Intent(context, TerminEmpfaenger::class.java)
            .setAction(AKTION_SCHLUMMERN_KLINGELT)
            .putExtra(EXTRA_TERMIN, terminId)
            .putExtra(EXTRA_SCHLUESSEL, schluessel)
        val absicht = PendingIntent.getBroadcast(
            context, ANFRAGE_SCHLUMMERN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val zeigen = PendingIntent.getActivity(
            context, ANFRAGE_ZEIGEN,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_KALENDER_OEFFNEN, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(zeit, zeigen), absicht)
    }

    private fun absicht(wichtig: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (wichtig) ANFRAGE_WICHTIG else ANFRAGE_NORMAL,
            Intent(context, TerminEmpfaenger::class.java)
                .setAction(AKTION_KLINGELT)
                .putExtra(EXTRA_WICHTIG, wichtig),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val AKTION_KLINGELT = "de.notizen.app.termin.KLINGELT"
        const val AKTION_SCHLUMMERN_KLINGELT = "de.notizen.app.termin.SCHLUMMERN_KLINGELT"
        const val AKTION_AUS = "de.notizen.app.termin.AUS"
        const val AKTION_SCHLUMMERN = "de.notizen.app.termin.SCHLUMMERN"
        const val EXTRA_WICHTIG = "wichtig"
        const val EXTRA_TERMIN = "termin"
        const val EXTRA_SCHLUESSEL = "schluessel"
        const val EXTRA_MELDUNG = "meldung"

        /** Sagt der Activity, dass der Kalender aufgehen soll. */
        const val EXTRA_KALENDER_OEFFNEN = "kalenderOeffnen"

        const val SCHLUMMERZEIT = 10

        private const val ANFRAGE_NORMAL = 7101
        private const val ANFRAGE_WICHTIG = 7102
        private const val ANFRAGE_SCHLUMMERN = 7103
        private const val ANFRAGE_ZEIGEN = 7104
        private const val NACHHOLEN = 2 * 60 * 60_000L
        private const val VORLAUF = 1_000L
    }
}
