package de.notizen.app.kalender

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.notizen.app.MainActivity
import de.notizen.core.data.kalender.Ausloeser
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Klingelt fuer Termine.
 *
 * Gewoehnliche Termine werden eine Benachrichtigung. Wichtige werden ein
 * Wecker: eine Benachrichtigung mit Weckerton, die so lange klingelt, bis sie
 * jemand ausschaltet, und die ueber `fullScreenIntent` den ganzen Bildschirm
 * oeffnet ([WeckerActivity]). Ist das Handy gerade in Benutzung, zeigt Android
 * statt des Vollbilds eine Meldung oben; der Ton laeuft in beiden Faellen,
 * weil er an der Benachrichtigung haengt und nicht am Bildschirm.
 */
@AndroidEntryPoint
class TerminEmpfaenger : BroadcastReceiver() {

    // Kein super.onReceive(), siehe ErinnerungEmpfaenger.

    @Inject lateinit var wecker: Terminwecker

    @Inject lateinit var repo: KalenderRepository

    @Inject lateinit var einstellungen: Einstellungen

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Terminwecker.AKTION_AUS -> {
                NotificationManagerCompat.from(context).cancel(intent.getIntExtra(Terminwecker.EXTRA_MELDUNG, 0))
                return
            }
            Terminwecker.AKTION_SCHLUMMERN -> {
                NotificationManagerCompat.from(context).cancel(intent.getIntExtra(Terminwecker.EXTRA_MELDUNG, 0))
                val id = intent.getStringExtra(Terminwecker.EXTRA_TERMIN) ?: return
                wecker.schlummern(id, intent.getLongExtra(Terminwecker.EXTRA_SCHLUESSEL, 0L), Terminwecker.SCHLUMMERZEIT)
                return
            }
        }

        val fertig = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val verbergen = einstellungen.sperreAn().first()
                when (intent.action) {
                    Terminwecker.AKTION_SCHLUMMERN_KLINGELT -> {
                        val id = intent.getStringExtra(Terminwecker.EXTRA_TERMIN) ?: return@launch
                        val schluessel = intent.getLongExtra(Terminwecker.EXTRA_SCHLUESSEL, 0L)
                        geschlummert(id, schluessel)?.let { zeigen(context, it, true, verbergen) }
                    }
                    Terminwecker.AKTION_KLINGELT -> {
                        val wichtig = intent.getBooleanExtra(Terminwecker.EXTRA_WICHTIG, false)
                        val jetzt = System.currentTimeMillis()
                        // Mehrere Erinnerungen desselben Vorkommens auf einmal (etwa
                        // nach einem Neustart) melden sich nur einmal.
                        wecker.faellig(wichtig, jetzt)
                            .distinctBy { it.vorkommen.termin.id to it.vorkommen.schluessel }
                            .forEach { zeigen(context, it, wichtig, verbergen) }
                        wecker.gemeldet(wichtig, jetzt + 1_000)
                        wecker.neuPlanen()
                    }
                }
            } finally {
                fertig.finish()
            }
        }
    }

    /** Das Vorkommen zu einem geschlummerten Wecker, neu aus dem Termin gerechnet. */
    private suspend fun geschlummert(id: String, schluessel: Long): Ausloeser? {
        val t = repo.termin(id) ?: return null
        if (t.deletedAt != null) return null
        val zone = ZoneId.systemDefault()
        val tag = if (t.allDay) Terminregeln.datumAus(schluessel) else Terminregeln.tagVon(schluessel, zone)
        val v = Terminregeln.vorkommen(t, tag.minusDays(1), tag.plusDays(2), zone)
            .firstOrNull { it.schluessel == schluessel } ?: return null
        return Ausloeser(v, 0, System.currentTimeMillis())
    }

    private suspend fun zeigen(context: Context, a: Ausloeser, wichtig: Boolean, verbergen: Boolean) {
        val erlaubt = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!erlaubt) return

        val t = a.vorkommen.termin
        val zone = ZoneId.systemDefault()
        val kalenderfarbe = repo.kalender().firstOrNull { it.id == t.calendarId }?.colorArgb ?: FARBE_STANDARD
        val eintrag = eintragVon(a.vorkommen, kalenderfarbe)
        val titel = if (verbergen) NEUTRAL else eintrag.titel
        val zeit = Kalenderformat.zeitraum(eintrag, zone)
        val text = if (verbergen || t.location.isBlank()) zeit else "$zeit · ${t.location}"
        val meldungsId = ("termin:" + t.id + ":" + a.vorkommen.schluessel).hashCode()

        val oeffnen = PendingIntent.getActivity(
            context, meldungsId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Terminwecker.EXTRA_KALENDER_OEFFNEN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val neutral = NotificationCompat.Builder(context, if (wichtig) KANAL_WECKER else KANAL_TERMINE)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(NEUTRAL)
            .setContentText(zeit)
            .build()

        if (!wichtig) {
            val meldung = NotificationCompat.Builder(context, KANAL_TERMINE)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(titel)
                .setContentText(text)
                .setColor(eintrag.farbe)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(neutral)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(oeffnen)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(meldungsId, meldung)
            return
        }

        val vollbild = PendingIntent.getActivity(
            context, meldungsId,
            Intent(context, WeckerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(WeckerActivity.TITEL, titel)
                putExtra(WeckerActivity.ZEIT, zeit)
                putExtra(WeckerActivity.ORT, if (verbergen) "" else t.location)
                putExtra(WeckerActivity.SYMBOL, if (verbergen) null else t.symbol)
                putExtra(WeckerActivity.ART, t.art.name)
                putExtra(WeckerActivity.FARBE, eintrag.farbe)
                putExtra(Terminwecker.EXTRA_TERMIN, t.id)
                putExtra(Terminwecker.EXTRA_SCHLUESSEL, a.vorkommen.schluessel)
                putExtra(Terminwecker.EXTRA_MELDUNG, meldungsId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val aus = PendingIntent.getBroadcast(
            context, meldungsId,
            Intent(context, TerminEmpfaenger::class.java)
                .setAction(Terminwecker.AKTION_AUS)
                .putExtra(Terminwecker.EXTRA_MELDUNG, meldungsId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val schlummern = PendingIntent.getBroadcast(
            context, meldungsId + 1,
            Intent(context, TerminEmpfaenger::class.java)
                .setAction(Terminwecker.AKTION_SCHLUMMERN)
                .putExtra(Terminwecker.EXTRA_MELDUNG, meldungsId)
                .putExtra(Terminwecker.EXTRA_TERMIN, t.id)
                .putExtra(Terminwecker.EXTRA_SCHLUESSEL, a.vorkommen.schluessel),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val meldung = NotificationCompat.Builder(context, KANAL_WECKER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titel)
            .setContentText(text)
            .setColor(eintrag.farbe)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(neutral)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(vollbild, true)
            .setContentIntent(vollbild)
            .setDeleteIntent(aus)
            .setOngoing(true)
            .setTimeoutAfter(KLINGELDAUER)
            .addAction(0, "Schlummern", schlummern)
            .addAction(0, "Ausschalten", aus)
            .build()
        // Laeuft der Ton in einer Schleife, bis jemand ausschaltet, wie bei einem Wecker.
        meldung.flags = meldung.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(meldungsId, meldung)
    }

    private companion object {
        const val NEUTRAL = "Termin"

        /** Nach zehn Minuten hoert er von selbst auf, wie ein Wecker, der niemanden weckt. */
        const val KLINGELDAUER = 10 * 60_000L
    }
}
