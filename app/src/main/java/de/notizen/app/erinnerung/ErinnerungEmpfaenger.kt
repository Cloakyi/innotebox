package de.notizen.app.erinnerung

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

const val KANAL_ERINNERUNGEN = "erinnerungen"

/**
 * Legt den Benachrichtigungskanal an.
 *
 * Muss beim Start passieren, nicht erst beim ersten Klingeln: ohne Kanal
 * verschluckt Android die Benachrichtigung wortlos, und der Nutzer soll den
 * Kanal in den Systemeinstellungen schon vorher finden können.
 */
fun kanalAnlegen(context: Context) {
    val kanal = NotificationChannel(
        KANAL_ERINNERUNGEN,
        "Erinnerungen",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Meldet sich zur eingestellten Zeit an einer Notiz"
    }
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(kanal)
}

/**
 * Klingelt.
 *
 * Läuft im Broadcast-Empfänger, also unter Zeitdruck: `goAsync()` hält den
 * Prozess so lange am Leben, bis die Notiz gelesen und die Erinnerung
 * abgehakt ist. Ohne das kann Android den Prozess mitten in der
 * Datenbankoperation beenden, und die Erinnerung klingelt beim nächsten Start
 * erneut.
 */
@AndroidEntryPoint
class ErinnerungEmpfaenger : BroadcastReceiver() {

    // KEIN super.onReceive() hier -- anders, als die Hilt-Dokumentation es fuer
    // Java zeigt. Die generierte Basisklasse traegt
    // @OnReceiveBytecodeInjectionMarker, und das Hilt-Gradle-Plugin setzt den
    // Injektionsaufruf beim Umschreiben des Bytecodes selbst an den Anfang von
    // onReceive. Ein handgeschriebenes super waere hier ausserdem gar nicht
    // uebersetzbar: zum Zeitpunkt des Kotlin-Compilers ist die Basismethode
    // noch die ABSTRAKTE aus BroadcastReceiver. Nachgesehen 2026-08-21 in der
    // erzeugten Hilt_ErinnerungEmpfaenger.java.


    @Inject lateinit var notizen: NoteRepository

    @Inject lateinit var erinnerungen: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ErinnerungPlaner.SCHLUESSEL_ID) ?: return
        val notizId = intent.getStringExtra(ErinnerungPlaner.SCHLUESSEL_NOTIZ) ?: return
        val mitgegeben = intent.getStringExtra(ErinnerungPlaner.SCHLUESSEL_TITEL).orEmpty()

        val fertig = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notiz = notizen.get(notizId)
                // Die Notiz kann seit dem Stellen des Weckers geloescht worden
                // sein. Dann still abhaken statt an etwas zu erinnern, das es
                // nicht mehr gibt.
                if (notiz != null && notiz.note.deletedAt == null) {
                    val titel = notiz.note.title.ifBlank { mitgegeben.ifBlank { "Notiz" } }
                    zeigen(context, notizId, titel, vorschau(notiz.note.body))
                }
                erinnerungen.abhaken(id)
            } finally {
                fertig.finish()
            }
        }
    }

    private fun vorschau(body: String): String =
        body.lineSequence().firstOrNull { it.isNotBlank() }?.take(120).orEmpty()

    private fun zeigen(context: Context, notizId: String, titel: String, text: String) {
        val erlaubt = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!erlaubt) return

        val oeffnen = PendingIntent.getActivity(
            context,
            notizId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIZ_OEFFNEN, notizId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val meldung = NotificationCompat.Builder(context, KANAL_ERINNERUNGEN)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(titel)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notizId.hashCode(), meldung)
    }

    companion object {
        /** Sagt der Activity, welche Notiz sie öffnen soll. */
        const val EXTRA_NOTIZ_OEFFNEN = "notizOeffnen"
    }
}

/**
 * Stellt die Wecker nach einem Neustart wieder.
 *
 * Android vergisst beim Ausschalten **alle** Alarme. Ohne diesen Empfänger
 * wäre jede Erinnerung nach dem nächsten Neustart des Geräts still
 * verschwunden — und zwar so, dass man es erst merkt, wenn sie nicht kommt.
 */
@AndroidEntryPoint
class NeustartEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var notizen: NoteRepository

    @Inject lateinit var erinnerungen: ReminderRepository

    @Inject lateinit var planer: ErinnerungPlaner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val fertig = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jetzt = System.currentTimeMillis()
                erinnerungen.offene().forEach { erinnerung ->
                    val notiz = notizen.get(erinnerung.noteId) ?: return@forEach
                    if (notiz.note.deletedAt != null) return@forEach

                    if (erinnerung.triggerAt <= jetzt) {
                        // Waehrend das Geraet aus war faellig geworden. Nicht
                        // nachtraeglich klingeln, aber auch nicht so tun, als
                        // waere nichts gewesen: die Erinnerung bleibt offen und
                        // wird auf gleich gestellt.
                        planer.stellen(
                            erinnerung.copy(triggerAt = jetzt + VERZUG),
                            notiz.note.title,
                        )
                    } else {
                        planer.stellen(erinnerung, notiz.note.title)
                    }
                }
            } finally {
                fertig.finish()
            }
        }
    }

    private companion object {
        /** Kurz nach dem Hochfahren, nicht mittendrin. */
        const val VERZUG = 30_000L
    }
}
