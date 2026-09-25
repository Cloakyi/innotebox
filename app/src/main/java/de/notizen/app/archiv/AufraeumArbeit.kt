package de.notizen.app.archiv

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.AutoArchiv
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Der nächtliche Aufräumlauf: Auto-Archiv und Papierkorb.
 *
 * **Ein Job für beides**, obwohl es zwei Aufgaben sind. Sie teilen sich
 * Auslöser, Bedingungen und Benachrichtigung; zwei getrennte Jobs wären zwei
 * Weckrufe für dieselbe Nacht und zwei Meldungen am Morgen. Jede der beiden
 * Aufgaben prüft **selbst**, ob sie eingeschaltet ist — der Job läuft also
 * immer und tut meistens nichts, und das ist billiger als ein Zeitplan, der
 * mit den Einstellungen synchron gehalten werden muss.
 *
 * **Bedingungen: nur beim Laden und im Leerlauf.** Beides ist Absicht. Das
 * Archivieren ist nichts, worauf jemand wartet, und es kann eine Weile dauern,
 * wenn die KI Titel vergibt. Am Gerät zu rechnen, während jemand es benutzt,
 * wäre die falsche Reihenfolge.
 *
 * **Titel VOR dem Verschieben.** Im Archiv wird gesucht, und gesucht wird über
 * Titel — eine unbetitelt weggewanderte Notiz ist praktisch verloren. Deshalb
 * ist das Planen ein eigener Schritt: Zwischen „wer kommt weg" und „weg damit"
 * passt die Beschriftung.
 */
@HiltWorker
class AufraeumArbeit @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameter: WorkerParameters,
    private val auto: AutoArchiv,
    private val notes: NoteRepository,
    private val ordner: FolderRepository,
    private val tags: TagRepository,
    private val einstellungen: Einstellungen,
    private val beschriftung: Beschriftung,
) : CoroutineWorker(context, parameter) {

    /** Was ein Lauf bewirkt hat. `batchId` ist null, wenn nichts archiviert wurde. */
    private data class Ergebnis(val batchId: String? = null, val anzahl: Int = 0)

    override suspend fun doWork(): Result {
        val archiv = archivieren()
        val frist = einstellungen.papierkorbFrist().first()
        val geleert = notes.papierkorbAufraeumen(frist)
        // Ordner mit derselben Frist. Bis zum 2026-09-14 blieben sie fuer immer
        // liegen, weil das Leeren nur Notizen kannte.
        val ordnerGeleert = ordner.papierkorbAufraeumen(frist)

        // Auch das Leeren des Papierkorbs wird gemeldet, obwohl es niemand
        // rueckgaengig machen kann -- gerade deshalb. Endgueltiges Loeschen
        // spurlos geschehen zu lassen waere das Schlimmste an dieser Automatik.
        if (archiv.batchId != null || geleert > 0 || ordnerGeleert > 0) {
            aufraeumMeldung(
                context = applicationContext,
                batchId = archiv.batchId,
                archiviert = archiv.anzahl,
                geleert = geleert,
                ordnerGeleert = ordnerGeleert,
            )
        }
        return Result.success()
    }

    private suspend fun archivieren(): Ergebnis {
        // IM ORDNERMODUS RUHT DAS ARCHIVIEREN.
        //
        // Es schiebt Notizen zwischen Stufen, die dort niemand sieht. Ein
        // naechtlicher Lauf, der eine Notiz aus dem Workspace ins Archiv legt,
        // waere in einer Ansicht ohne Stufen eine Veraenderung ohne sichtbaren
        // Grund -- und die Notiz stuende hinterher genau dort, wo sie vorher
        // stand, naemlich in ihrem Ordner.
        //
        // Der Papierkorb wird trotzdem geleert. Das haengt nicht am Fluss,
        // sondern an einer Frist, und die laeuft in beiden Modi.
        if (einstellungen.ordnermodus().first()) return Ergebnis()

        val plan = auto.planen()
        if (plan.leer) return Ergebnis()

        val kiErlaubt = einstellungen.kiAktiv().first()
        val vorhandeneTags = tags.getAll()

        plan.alle.forEach { notiz ->
            val voll = notes.get(notiz.id) ?: return@forEach

            beschriftung.titelFuer(voll, kiErlaubt)?.let { notes.setTitle(notiz.id, it) }

            beschriftung.tagFuer(voll, vorhandeneTags, kiErlaubt)?.let { tag ->
                notes.setTags(notiz.id, listOf(tag.id))
            }
        }

        val batchId = auto.ausfuehren(plan) ?: return Ergebnis()
        return Ergebnis(batchId, plan.alle.size)
    }

    companion object {
        private const val NAME = "aufraeumen"

        /**
         * Meldet den Lauf beim System an.
         *
         * `UPDATE` und nicht `KEEP`: Ändert sich hier etwas an den Bedingungen,
         * soll das beim nächsten Start greifen. `KEEP` würde den einmal
         * angemeldeten Lauf für immer festschreiben — samt der Bedingungen von
         * damals.
         *
         * Einmal am Tag ist die Obergrenze, nicht der Takt: WorkManager legt
         * den Lauf in dieses Fenster, wenn Laden und Leerlauf zusammenkommen.
         * Trifft das nie zu, läuft er nie — und das ist richtig so.
         */
        fun anmelden(context: Context) {
            val bedingungen = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AufraeumArbeit>(1, TimeUnit.DAYS)
                    .setConstraints(bedingungen)
                    .build(),
            )
        }
    }
}
