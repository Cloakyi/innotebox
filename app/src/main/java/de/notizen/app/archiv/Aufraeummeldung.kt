package de.notizen.app.archiv

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
import de.notizen.core.data.repository.ArchiveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

const val KANAL_AUFRAEUMEN = "aufraeumen"

private const val MELDUNG_ID = 4711
private const val EXTRA_BATCH = "batchId"

/**
 * Kanal für die Meldung des nächtlichen Aufräumlaufs.
 *
 * **`IMPORTANCE_LOW`, anders als bei Erinnerungen.** Eine Erinnerung ist ein
 * Termin und darf klingeln; das Aufräumen ist ein Bericht über etwas, das schon
 * passiert ist. Es gehört in die Leiste, nicht auf den Bildschirm — nachts um
 * drei erst recht.
 */
fun aufraeumKanalAnlegen(context: Context) {
    val kanal = NotificationChannel(
        KANAL_AUFRAEUMEN,
        "Automatisches Aufräumen",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Bericht über automatisch archivierte Notizen und den Papierkorb"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
}

/**
 * Meldet, was der Lauf getan hat.
 *
 * **Eine Meldung für den ganzen Lauf**, nicht eine je Notiz. Zwölf
 * Benachrichtigungen am Morgen liest niemand, und der Undo-Knopf gilt ohnehin
 * für den gesamten Batch.
 *
 * Der Rücknahme-Knopf erscheint nur, wenn wirklich archiviert wurde. Das Leeren
 * des Papierkorbs lässt sich nicht zurücknehmen — ein Knopf, der bei manchen
 * Meldungen die Hälfte kann, wäre schlimmer als keiner.
 */
fun aufraeumMeldung(
    context: Context,
    batchId: String?,
    archiviert: Int,
    geleert: Int,
    ordnerGeleert: Int = 0,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val zeilen = buildList {
        if (archiviert > 0) {
            add(if (archiviert == 1) "1 Notiz archiviert" else "$archiviert Notizen archiviert")
        }
        if (geleert > 0) {
            add(
                if (geleert == 1) {
                    "1 Notiz endgültig gelöscht"
                } else {
                    "$geleert Notizen endgültig gelöscht"
                },
            )
        }
        if (ordnerGeleert > 0) {
            add(
                if (ordnerGeleert == 1) {
                    "1 Ordner endgültig gelöscht"
                } else {
                    "$ordnerGeleert Ordner endgültig gelöscht"
                },
            )
        }
    }
    if (zeilen.isEmpty()) return

    val oeffnen = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val bau = NotificationCompat.Builder(context, KANAL_AUFRAEUMEN)
        .setSmallIcon(android.R.drawable.ic_menu_agenda)
        .setContentTitle("Aufgeräumt")
        .setContentText(zeilen.joinToString(" · "))
        .setContentIntent(oeffnen)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    if (batchId != null) {
        val zurueck = PendingIntent.getBroadcast(
            context,
            batchId.hashCode(),
            Intent(context, ArchivZuruecknehmen::class.java).putExtra(EXTRA_BATCH, batchId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        bau.addAction(android.R.drawable.ic_menu_revert, "Rückgängig", zurueck)
    }

    NotificationManagerCompat.from(context).notify(MELDUNG_ID, bau.build())
}

/**
 * Nimmt einen kompletten Lauf zurück.
 *
 * Jede Notiz landet in **ihrer** ursprünglichen Stufe — nicht pauschal im
 * Eingang. Das weiß `archive_run_items`, und dafür gibt es diese Tabelle.
 *
 * `goAsync()` hält den Prozess am Leben, bis die Datenbank fertig ist. Ohne das
 * kann Android ihn mitten in der Transaktion beenden, und der Lauf wäre halb
 * zurückgenommen.
 */
@AndroidEntryPoint
class ArchivZuruecknehmen : BroadcastReceiver() {

    // KEIN super.onReceive() -- siehe ErinnerungEmpfaenger. Das Hilt-Plugin
    // setzt den Injektionsaufruf beim Umschreiben des Bytecodes selbst ein.

    @Inject
    lateinit var archiv: ArchiveRepository

    override fun onReceive(context: Context, intent: Intent) {
        val batchId = intent.getStringExtra(EXTRA_BATCH) ?: return
        val fertig = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                archiv.undo(batchId)
                NotificationManagerCompat.from(context).cancel(MELDUNG_ID)
            } finally {
                fertig.finish()
            }
        }
    }
}
