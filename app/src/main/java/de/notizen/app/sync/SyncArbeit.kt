package de.notizen.app.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.sync.Abgleich
import de.notizen.core.sync.Tagesdurchlauf
import de.notizen.core.sync.Abgleichergebnis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein Abgleich im Hintergrund.
 *
 * **Braucht Netz, sonst nichts.** Anders als der nächtliche Aufräumlauf soll
 * der Abgleich zeitnah passieren — er wartet nicht auf Ladekabel und Leerlauf.
 * Was er kostet, ist ein kurzer HTTP-Aufruf pro geänderter Notiz.
 */
@HiltWorker
class SyncArbeit @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameter: WorkerParameters,
    private val abgleich: Abgleich,
    private val anmeldung: Anmeldung,
) : CoroutineWorker(context, parameter) {

    override suspend fun doWork(): Result {
        val zugang = anmeldung.zugang()
        if (zugang !is Zugang.Erteilt) {
            // Kein erneuter Versuch: Ohne Anmeldung hilft kein Wiederholen,
            // sondern nur der Nutzer. Der Zustand steht im Sync-Screen.
            return Result.success()
        }

        return when (abgleich.lauf(zugang.token)) {
            is Abgleichergebnis.Fertig -> Result.success()

            // Netzprobleme sind vorübergehend -- WorkManager wartet und
            // versucht es mit wachsendem Abstand erneut.
            Abgleichergebnis.KeinNetz -> Result.retry()

            Abgleichergebnis.AnmeldungNoetig -> Result.success()

            // Ein echter Fehler wiederholt sich meist. Ein Dauerlauf, der
            // stündlich in denselben Fehler rennt, kostet nur Akku.
            is Abgleichergebnis.Fehler -> Result.failure()
        }
    }

    companion object {
        const val SOFORT = "abgleich-sofort"

        /**
         * Netz, und auf Wunsch ungetaktetes.
         *
         * Mehr nicht: Auf Ladekabel oder Leerlauf zu warten wäre bei einem
         * Abgleich, der zeitnah passieren soll, eine Bedingung, auf die niemand
         * warten will. Das ist der Unterschied zum nächtlichen Aufräumlauf.
         */
        private fun bedingungen(nurWlan: Boolean) = Constraints.Builder()
            .setRequiredNetworkType(
                if (nurWlan) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()

        /**
         * Wie lange nach einer Änderung gewartet wird, bevor es losgeht.
         *
         * **Die Verzögerung IST die Entprellung.** `REPLACE` auf eindeutig
         * benannter Arbeit heißt: Wer während des Tippens zwanzigmal speichert,
         * verschiebt den einen geplanten Lauf zwanzigmal, statt zwanzig Läufe
         * anzustoßen.
         *
         * Fünf Sekunden statt der früheren zehn. Zehn waren spürbar: Man löscht
         * eine Notiz, sieht in Drive nach, und sie ist noch da. Fünf reichen
         * immer noch, um durchgehendes Tippen zusammenzufassen — der Autosave
         * selbst wartet ja schon 800 ms.
         */
        const val ENTPRELLUNG = 5L

        /**
         * Stößt einen Abgleich an.
         *
         * [sekunden] `0` heißt sofort. Das ist der richtige Wert für die Momente,
         * in denen der Nutzer fertig ist: App in den Vordergrund geholt, App
         * verlassen, Knopf gedrückt. Da gibt es nichts zu entprellen.
         */
        fun anstossen(context: Context, nurWlan: Boolean, sekunden: Long = ENTPRELLUNG) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SOFORT,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncArbeit>()
                    .setConstraints(bedingungen(nurWlan))
                    .setInitialDelay(sekunden, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}

/**
 * Merkt, wenn sich etwas Ungesichertes ansammelt, und stößt den Abgleich an.
 *
 * **Eine Stelle statt vieler.** Der Wunsch war, nach jedem Speichern zu
 * synchronisieren. Das ließe sich in jedes ViewModel schreiben, das etwas
 * ändert — und genau dort vergisst man es beim nächsten neuen Bildschirm. Der
 * Zähler der ungesicherten Einträge steigt dagegen **bei jeder** Schreiboperation,
 * ganz gleich wer sie ausgelöst hat: Editor, Wischgeste, Mehrfachauswahl,
 * Auto-Archiv.
 *
 * `drop(1)` überspringt den ersten Wert. Der kommt beim Start und beschreibt
 * den Bestand, nicht eine Änderung — sonst liefe bei jedem App-Start ein
 * Abgleich los, auch wenn niemand etwas angefasst hat.
 */
/**
 * Wie lange nach einer Änderung gewartet wird, bevor abgeglichen wird.
 *
 * Zwei Sekunden. Der Autosave selbst wartet schon 800 ms, und im Vordergrund
 * kostet ein Lauf nichts als ein paar Anfragen — die frühere Wartezeit von fünf
 * Sekunden plus Planer war am Gerät als Hänger zu spüren.
 */
private const val ENTPRELLUNG_MS = 2_000L

/**
 * Wie lange nach dem Schließen eines Editors gewartet wird.
 *
 * Kurz, aber nicht null: Das harte Speichern beim Verlassen und das Verwerfen
 * einer leeren Notiz laufen noch, während der Editor schon zu ist. Ein Lauf
 * genau in diesem Moment lüde einen Stand hoch, der eine Sekunde später schon
 * wieder ein anderer ist.
 */
private const val NACH_EDITOR_MS = 1_200L

@Singleton
class Syncwaechter @Inject constructor(
    private val syncDao: SyncDao,
    private val einstellungen: Einstellungen,
    private val anmeldung: Anmeldung,
    private val abgleich: Abgleich,
    private val tagesdurchlauf: Tagesdurchlauf,
    private val bearbeitung: Bearbeitung,
) {
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Ob gerade jemand hinsieht. Entscheidet, welchen Weg der Abgleich nimmt. */
    private var imVordergrund = false

    private var geplant: Job? = null

    fun beobachten(context: Context) {
        kontext = context.applicationContext
        bereich.launch {
            syncDao.observeUnsyncedCount()
                .distinctUntilChanged()
                .drop(1)
                .collect { offen -> if (offen > 0) planen(context, ENTPRELLUNG_MS) }
        }

        // Und sobald der letzte Editor zu ist: Was während des Schreibens
        // aufgelaufen ist, geht jetzt in EINEM Zug hoch.
        //
        // Gefragt wird die Datenbank, nicht ein gemerktes Flag. Ein Flag hätte
        // eine Lücke: `observeUnsyncedCount` meldet sich nur, wenn die ZAHL sich
        // ändert. Wer eine Notiz bearbeitet, die ohnehin schon aussteht, ändert
        // die Zahl nicht — das Flag bliebe falsch, und beim Schließen passierte
        // nichts.
        bereich.launch {
            bearbeitung.offen.collect { offeneEditoren ->
                if (offeneEditoren > 0) return@collect
                if (syncDao.observeUnsyncedCount().first() > 0) planen(context, NACH_EDITOR_MS)
            }
        }
    }

    /**
     * Plant einen Lauf, und zwar auf dem schnelleren der beiden Wege.
     *
     * **Im Vordergrund läuft der Abgleich direkt, nicht über WorkManager.** Das
     * ist der Grund, warum das Sicherungssymbol vorher gefühlt ewig auf
     * „Hochladen" stand: WorkManager ist ein *Planer*. Er verspricht, die Arbeit
     * zu erledigen, nicht sie sofort zu erledigen — er bündelt sie mit anderen
     * Aufträgen im System, und das können auf einem echten Gerät Minuten sein.
     * Für Arbeit, die eine laufende App auslöst und deren Ergebnis dieselbe App
     * gerade anzeigt, ist das der falsche Weg: Sie stirbt ja nicht weg.
     *
     * Im Hintergrund bleibt es bei WorkManager. Dort ist genau das seine Stärke:
     * Der Lauf überlebt, dass die App beendet wird.
     *
     * Die Verzögerung ist die Entprellung, jetzt in einer Coroutine statt im
     * Planer. `cancel` auf den vorigen Auftrag heißt: Wer während des Tippens
     * zwanzigmal speichert, verschiebt den einen Lauf zwanzigmal.
     */
    /**
     * Von Hand ausgelöst: sofort, ohne Entprellung.
     *
     * Nimmt denselben Weg wie alles andere. Ein zweiter Anstoßweg nur für den
     * Knopf wäre eine zweite Fassung derselben Entscheidung — und die beiden
     * liefen irgendwann auseinander.
     */
    /**
     * Der Knopf „Jetzt abgleichen".
     *
     * **Er raeumt erst auf und laeuft dann.** Alles, was noch als geplanter
     * oder eingereihter Abgleich herumliegt, wird abbestellt: die eigene
     * Entprellung und der Auftrag beim Planer. Sonst folgt auf den Lauf, den
     * der Nutzer gerade angestossen hat, gleich noch einer, der dasselbe noch
     * einmal tut.
     *
     * Was **gerade laeuft**, wird nicht abgeschossen. `Abgleich` haelt ein
     * Schloss, der neue Lauf wartet also und faengt danach von vorn an. Einen
     * Lauf mitten im Hochladen abzubrechen liesse Drive in einem halben Zustand
     * zurueck, und gewonnen waere nichts: Der wartende Lauf sieht ohnehin alles
     * noch einmal durch.
     */
    fun jetzt(context: Context) {
        geplantesAbbestellen(context)
        planen(context, 0, trotzBearbeitung = true)
    }

    /**
     * Bestellt ab, was noch als Abgleich herumliegt.
     *
     * Die eigene Entprellung und der Auftrag beim Planer. Wer von Hand
     * abgleicht, soll danach nicht noch einen zweiten Lauf hinterherlaufen
     * haben, der dasselbe noch einmal tut.
     */
    fun geplantesAbbestellen(context: Context) {
        geplant?.cancel()
        WorkManager.getInstance(context).cancelUniqueWork(SyncArbeit.SOFORT)
    }

    private fun planen(context: Context, verzoegerung: Long, trotzBearbeitung: Boolean = false) {
        // Solange ein Editor offen ist, geht nichts hoch. Der Autosave feuert
        // beim Tippen staendig; ohne diese Sperre ginge bei jeder Denkpause die
        // ganze Notizdatei nach Drive. Verloren geht dabei nichts: Sobald der
        // letzte Editor zu ist, sieht der Beobachter oben nach, ob etwas
        // aussteht, und schickt alles in EINEM Zug.
        if (!trotzBearbeitung && bearbeitung.offen.value > 0) return

        geplant?.cancel()
        geplant = bereich.launch {
            // Steht die Automatik auf "aus", geht auch nach dem Speichern nichts
            // zu Google. Wer sie abstellt, meint sie ganz.
            if (!einstellungen.syncAutomatisch().first()) return@launch

            delay(verzoegerung)
            if (imVordergrund) direkt() else {
                SyncArbeit.anstossen(context, einstellungen.syncNurWlan().first(), sekunden = 0)
            }
        }
    }

    /**
     * Gleicht sofort ab, aus der laufenden App heraus.
     *
     * Ohne Netzbedingung: Fehlt das Netz, kommt `KeinNetz` zurück, und der
     * nächste Auslöser versucht es erneut. Eine Bedingung, die den Lauf
     * verschiebt, wäre hier wieder der Planer — und den wollen wir gerade
     * umgehen.
     *
     * `Abgleich` selbst lässt nur einen Lauf gleichzeitig zu; ein zweiter
     * Auslöser wartet, statt eine zweite Fassung derselben Notiz hochzuladen.
     */
    private suspend fun direkt() {
        val zugang = anmeldung.zugang()
        if (zugang !is Zugang.Erteilt) return

        // DER TAEGLICHE DURCHLAUF (SYNC.md 9): einmal je Kalendertag beim
        // ersten Oeffnen der App statt eines gewoehnlichen Laufs. Er tut
        // alles, was der Lauf tut, und dazu Pruefbericht, Purge und Snapshot.
        if (!tagesdurchlauf.heuteSchonGelaufen()) {
            val nurWlan = einstellungen.syncNurWlan().first()
            tagesdurchlauf.ausfuehren(
                zugang.token,
                mitSnapshot = !nurWlan || netzUngetaktet(),
            )
        } else {
            abgleich.lauf(zugang.token)
        }
    }

    /** Ob das aktive Netz ungetaktet ist (WLAN). Ohne Netz: nein. */
    private fun netzUngetaktet(): Boolean {
        val manager = kontext?.getSystemService(ConnectivityManager::class.java) ?: return false
        val netz = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(netz)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }

    /** Der Anwendungskontext, gemerkt beim Beobachten. */
    private var kontext: Context? = null

    /**
     * Gleicht ab, wenn die App in den Vordergrund kommt — und wenn sie ihn verlässt.
     *
     * **Das ist der Unterschied zwischen „es synchronisiert" und „es fühlt sich
     * synchron an".** Der regelmäßige Lauf greift frühestens alle fünfzehn
     * Minuten; wer die App öffnet, um eine Notiz zu lesen, die er eben am
     * Rechner geschrieben hat, will nicht warten, bis das System soweit ist.
     *
     * Beim **Verlassen** noch einmal, weil das der ehrlichste „ich bin fertig"-
     * Moment ist: Was gerade getippt wurde, ist dann sicher.
     *
     * Ohne Verzögerung in beiden Fällen. Entprellt werden muss nur, was während
     * des Tippens passiert.
     *
     * **Mit `ActivityLifecycleCallbacks` statt `ProcessLifecycleOwner`.** Das
     * ist dieselbe Auskunft ohne eine weitere Abhängigkeit — und die hätte hier
     * einen Preis gehabt: `lifecycle-process` mit ins Modul zu nehmen brachte
     * die Unit-Tests des App-Moduls zum Scheitern (die eigenen Klassen fehlten
     * plötzlich im Testpfad). Der Zähler unten leistet dasselbe.
     */
    fun vordergrundBeobachten(anwendung: Application) {
        anwendung.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var sichtbar = 0

                override fun onActivityStarted(activity: Activity) {
                    // Nur der Uebergang von "gar nichts sichtbar" auf "etwas
                    // sichtbar" ist das Oeffnen der App. Ohne diesen Zaehler
                    // loeste jeder Wechsel zwischen zwei Bildschirmen einen
                    // Lauf aus.
                    imVordergrund = true
                    if (sichtbar++ == 0) planen(anwendung, 0)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (--sichtbar == 0) {
                        // ZUERST den Schalter umlegen: Der geplante Lauf soll
                        // jetzt den Weg ueber WorkManager nehmen, denn gleich
                        // gibt es diese App womoeglich nicht mehr.
                        imVordergrund = false
                        // Hier ausdruecklich TROTZ offenem Editor: Gleich gibt
                        // es diese App womoeglich nicht mehr. Ein
                        // Zwischenstand in Drive ist besser als ein
                        // verlorener.
                        planen(anwendung, 0, trotzBearbeitung = true)
                    }
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityResumed(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            },
        )
    }
}
