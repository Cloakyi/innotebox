package de.notizen.app

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.notizen.app.erinnerung.ErinnerungEmpfaenger
import de.notizen.app.kalender.Kalenderzugang
import de.notizen.app.ui.NotizenApp
import de.notizen.app.ui.start.Berechtigungsdialog
import de.notizen.app.ui.start.Geraetestanddialog
import de.notizen.app.ui.start.Abdeckung
import de.notizen.app.ui.start.Sperrbildschirm
import de.notizen.app.sicherheit.Sperre
import de.notizen.core.data.prefs.Einstellungen
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import de.notizen.app.archiv.Nachbeschriftung
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import de.notizen.app.ui.theme.NotizenTheme
import de.notizen.app.ui.theme.ThemeViewModel
import de.notizen.core.data.model.ThemeWahl

/** Wie lange nach dem Sichtbarwerden die Beschriftung nachgeholt wird. */
private const val NACHHOLEN_NACH_MS = 3_000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Die Notiz, die eine angetippte Benachrichtigung öffnen soll.
     *
     * Als Zustand und nicht als Startparameter: die Activity läuft `singleTask`,
     * bekommt den Auftrag also auch dann, wenn sie schon offen ist -- und
     * genau dann gibt es kein `onCreate` mehr, in dem man ihn lesen könnte.
     *
     * `singleTask` und nicht mehr `singleTop` (seit 2026-09-15): Mit `singleTop`
     * legte ein Dateimanager, der eine `.notesbak` hereinreichte, eine ZWEITE
     * InNoteBox in seiner eigenen Aufgabe an, und in der Übersicht der letzten
     * Apps standen zwei. `singleTask` holt stattdessen die eine bestehende
     * Aufgabe nach vorn und liefert den Auftrag über `onNewIntent`.
     */
    private val zuOeffnen = mutableStateOf<String?>(null)

    /**
     * Die Sicherungsdatei, die aus einem Dateimanager hereingereicht wurde.
     *
     * Die Leseerlaubnis für diese Adresse gilt nur, solange diese Aufgabe lebt.
     * Sie wird deshalb weder gemerkt noch weitergereicht, sondern sofort
     * eingelesen.
     */
    private val zuLesen = mutableStateOf<Uri?>(null)

    /** Die Sperre der App, ein Singleton, damit eine Drehung sie nicht vergisst. */
    @Inject lateinit var sperre: Sperre

    @Inject lateinit var einstellungen: Einstellungen

    @Inject lateinit var nachbeschriftung: Nachbeschriftung

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        auftragLesen(intent)

        // AUFNAHMESCHUTZ: `FLAG_SECURE` unterbindet Bildschirmfotos,
        // Bildschirmaufnahmen und das Vorschaubild in der Uebersicht der
        // letzten Apps. Folgt der Einstellung, sobald sie sich aendert.
        //
        // Mit eingeschalteter Sperre zeigt die Uebersicht der letzten Apps
        // ebenfalls kein Vorschaubild, auch ohne Aufnahmeschutz: Sonst stuende
        // dort der letzte Bildschirm, waehrend die App selbst gesperrt ist. Ab
        // Android 13 gibt es dafuer einen eigenen Schalter, der Bildschirmfotos
        // erlaubt laesst; davor geht das nur ueber `FLAG_SECURE`.
        lifecycleScope.launch {
            combine(einstellungen.aufnahmeschutzAn(), einstellungen.sperreAn()) { schutz, sperre ->
                schutz to sperre
            }.collect { (schutz, sperreAn) ->
                val ohneVorschau = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                if (ohneVorschau) setRecentsScreenshotEnabled(!sperreAn)
                if (schutz || (sperreAn && !ohneVorschau)) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val einstellung by themeViewModel.state.collectAsStateWithLifecycle()

            val dunkel = when (einstellung.wahl) {
                ThemeWahl.SYSTEM -> isSystemInDarkTheme()
                ThemeWahl.HELL -> false
                ThemeWahl.DUNKEL -> true
            }

            NotizenTheme(
                darkTheme = dunkel,
                dynamicColor = einstellung.dynamicColor,
            ) {
                // Alle Nachfragen auf einmal, beim Start (2026-09-16). Ersetzt
                // die fruehere einzelne Frage nach Benachrichtigungen hier und
                // die Fragen an den Stellen, an denen es gebraucht wurde.
                val berechtigungenOffen = Berechtigungsdialog()

                // Der Geraetestand: beim ersten Start die volle
                // Pruefung mit Dialog, danach nur die leichte. Wartet, bis der
                // Berechtigungsdialog zu ist, sonst laegen zwei uebereinander.
                Geraetestanddialog(aktiv = !berechtigungenOffen)

                val gesperrt by sperre.gesperrt.collectAsStateWithLifecycle()
                val entschieden by sperre.entschieden.collectAsStateWithLifecycle()

                NotizenApp(
                    oeffneNotiz = zuOeffnen.value,
                    // Zurueckgemeldet, sobald geoeffnet wurde. Sonst spraenge
                    // die App bei jeder Drehung wieder in dieselbe Notiz.
                    onGeoeffnet = { zuOeffnen.value = null },
                    // Eine hereingereichte Sicherung erst nach dem Entsperren.
                    // Sonst laege die Rueckfrage zum Einlesen ueber der Sperre.
                    lieseSicherung = zuLesen.value.takeIf { entschieden && !gesperrt },
                    onSicherungGelesen = { zuLesen.value = null },
                )

                // DIE SPERRE: ueber allem, als eigenes Fenster. Die
                // App darunter bleibt stehen, damit nach dem Entsperren alles
                // dort ist, wo es war.
                if (gesperrt) {
                    Sperrbildschirm(onEntsperrt = sperre::entsperrt)
                } else if (!entschieden) {
                    // Bis feststeht, ob gesperrt wird, bleibt der Inhalt verdeckt.
                    Abdeckung()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { sperre.beimStart() }

        // Was die KI nachts nicht durfte, holt sie jetzt nach, solange die App
        // vorn ist. Mit einem Moment Abstand, damit der Start nicht wartet.
        lifecycleScope.launch {
            delay(NACHHOLEN_NACH_MS)
            nachbeschriftung.nachholen { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        }
    }

    override fun onStop() {
        sperre.beimStopp(konfigurationswechsel = isChangingConfigurations)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        auftragLesen(intent)
    }

    private fun auftragLesen(intent: Intent) {
        intent.getStringExtra(ErinnerungEmpfaenger.EXTRA_NOTIZ_OEFFNEN)?.let {
            zuOeffnen.value = it
            // Aus dem Intent entfernen, damit ein spaeteres Wiederaufnehmen der
            // Activity denselben Auftrag nicht noch einmal ausfuehrt.
            intent.removeExtra(ErinnerungEmpfaenger.EXTRA_NOTIZ_OEFFNEN)
        }

        // Eine Sicherungsdatei aus dem Dateimanager. Aus demselben Grund wie
        // oben wird die Adresse aus dem Intent genommen: Sonst laege sie beim
        // naechsten Drehen des Geraets noch darin und wuerde ein zweites Mal
        // eingelesen.
        //
        // Nur content-Adressen sind Sicherungen. Ein Link auf
        // https://innotebox.de/app kommt ueber denselben ACTION_VIEW herein und
        // soll nur die App oeffnen; als Sicherung gelesen ergaebe er die
        // Meldung, die Datei sei keine.
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                if (it.scheme == "content") zuLesen.value = it
                intent.data = null
            }
        }

        // Ein Termin, den jemand in Google Kalender angetippt hat. Unsere
        // Adresse steht als Extra darin, nicht als Daten: Die Daten des Intents
        // gehoeren dem Kalendereintrag.
        if (intent.action == CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) {
            val adresse = intent.getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI)
            notizAus(adresse)?.let {
                zuOeffnen.value = it
                intent.removeExtra(CalendarContract.EXTRA_CUSTOM_APP_URI)
            }
        }
    }

    /**
     * Die Notizkennung aus `innotebox://notiz/<kennung>`.
     *
     * Geprueft statt geglaubt. Der Intent kommt zwar von der Kalender-App, aber
     * die Adresse darin ist am Ende eine Zeichenkette aus einer Datenbank, die
     * auch jemand anders beschrieben haben kann. Passt sie nicht, passiert
     * nichts.
     */
    private fun notizAus(adresse: String?): String? {
        val uri = adresse?.toUri() ?: return null
        if (uri.scheme != Kalenderzugang.SCHEMA) return null
        if (uri.host != "notiz") return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
