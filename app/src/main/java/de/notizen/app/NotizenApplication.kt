package de.notizen.app

import android.app.Application
import android.os.Build
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.notizen.app.archiv.AufraeumArbeit
import de.notizen.app.archiv.aufraeumKanalAnlegen
import de.notizen.app.audio.aufnahmeKanalAnlegen
import de.notizen.app.kalender.Kalenderspiegel
import de.notizen.app.sync.Syncwaechter
import de.notizen.app.erinnerung.kanalAnlegen
import javax.inject.Inject

@HiltAndroidApp
class NotizenApplication : Application(), Configuration.Provider {

    /**
     * Damit WorkManager die Worker über Hilt bauen kann.
     *
     * Ohne das scheitert `AufraeumArbeit` beim Erzeugen, mit einer Meldung, die
     * nach einem fehlenden Konstruktor aussieht und nichts mit Hilt zu tun zu
     * haben scheint.
     */
    @Inject
    lateinit var werkbank: HiltWorkerFactory

    @Inject
    lateinit var syncwaechter: Syncwaechter

    /**
     * Haelt den Kalender des Geraets an den Notizen.
     *
     * Startet hier und nicht in einem Bildschirm: Eine Erinnerung kann auch dann
     * entstehen, wenn niemand hinsieht -- ueber den Abgleich mit Drive oder eine
     * eingelesene Sicherung.
     */
    @Inject
    lateinit var kalenderspiegel: Kalenderspiegel

    /** Fuer den Geraetenamen in `index.json` (SYNC.md 3.4). */
    @Inject
    lateinit var einstellungen: Einstellungen

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(werkbank).build()

    override fun onCreate() {
        super.onCreate()
        // Beim Start und nicht erst beim ersten Klingeln: ohne Kanal verschluckt
        // Android die Benachrichtigung wortlos, und der Nutzer soll ihn in den
        // Systemeinstellungen schon vorher finden.
        kanalAnlegen(this)
        aufnahmeKanalAnlegen(this)
        aufraeumKanalAnlegen(this)

        // Bedingungslos anmelden. Ob wirklich etwas zu tun ist, prüft der Lauf
        // selbst -- ein Zeitplan, der mit den Einstellungen synchron gehalten
        // werden muss, wäre eine zweite Wahrheit über denselben Sachverhalt.
        AufraeumArbeit.anmelden(this)

        // Der Geraetename fuer die Geraeteliste in Drive. Nur zur Anzeige;
        // die Kennung des Geraets ist eine andere und aendert sich nie.
        CoroutineScope(Dispatchers.IO).launch {
            einstellungen.setGeraeteLabel((Build.MANUFACTURER + " " + Build.MODEL).trim())
        }

        // Und der Waechter stoesst nach jeder Aenderung einen an -- entprellt,
        // damit zwanzigmal Speichern nicht zwanzig Laeufe bedeutet.
        syncwaechter.beobachten(this)
        kalenderspiegel.beobachten()

        // Beim Oeffnen der App holen, beim Verlassen schicken. Ohne das haengt
        // alles am Fuenfzehn-Minuten-Takt, und eine Notiz vom Rechner waere
        // beim Aufschlagen des Handys noch nicht da.
        syncwaechter.vordergrundBeobachten(this)
    }
}
