package de.notizen.app.sync

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.prefs.Einstellungen
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.sync.Abgleich
import de.notizen.core.sync.Pruefbericht
import de.notizen.core.sync.pruefberichtAus
import de.notizen.core.sync.Tagesdurchlauf
import de.notizen.core.sync.Tagesergebnis
import de.notizen.core.sync.Abgleichschritt
import de.notizen.core.sync.Abgleichergebnis
import de.notizen.core.sync.Drive
import de.notizen.core.sync.Drivefehler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was der Sync-Screen anzeigt. */
data class Synczustand(
    val prueft: Boolean = true,
    val verbunden: Boolean = false,
    val laeuft: Boolean = false,
    val ordnerId: String? = null,
    val meldung: String? = null,
    /** Ob der Abgleich von selbst laeuft (SYNC.md 5). */
    val automatisch: Boolean = true,
    val nurWlan: Boolean = false,
    /** UTC-Millis des letzten erfolgreichen Abgleichs. `0` = noch nie. */
    val zuletzt: Long = 0,
    /** Der letzte Pruefbericht (SYNC.md 9), oder null, wenn es noch keinen gab. */
    val bericht: Pruefbericht? = null,
)

/**
 * Verbindung zu Google Drive: prüfen, herstellen, lösen.
 *
 * Der Verbindungszustand wird nicht gespeichert, sondern erfragt. Ein
 * gemerktes „ist verbunden" wäre eine zweite Wahrheit neben Googles eigener,
 * und die beiden laufen auseinander, sobald der Zugriff woanders widerrufen
 * wird (im Google-Konto, auf einem anderen Gerät, oder weil im Testmodus die
 * sieben Tage um sind). Ein stiller Aufruf kostet nichts und sagt die Wahrheit.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val anmeldung: Anmeldung,
    private val drive: Drive,
    private val abgleich: Abgleich,
    private val tagesdurchlauf: Tagesdurchlauf,
    private val einstellungen: Einstellungen,
    private val syncwaechter: Syncwaechter,
) : ViewModel() {

    private val _state = MutableStateFlow(Synczustand())
    val state: StateFlow<Synczustand> = _state.asStateFlow()

    /**
     * Woran der Abgleich gerade arbeitet.
     *
     * Kommt unveraendert aus dem Abgleich selbst. Ein Vorgang, der eine halbe
     * Minute dauert und dabei nichts von sich sagt, ist von einem haengenden
     * nicht zu unterscheiden.
     */
    val schritt: StateFlow<Abgleichschritt?> = abgleich.schritt

    init {
        pruefen()
        viewModelScope.launch {
            combine(
                einstellungen.syncAutomatisch(),
                einstellungen.letzterAbgleich(),
                einstellungen.syncNurWlan(),
                einstellungen.letzterPruefbericht(),
            ) { automatisch, wann, nurWlan, bericht ->
                Synczustand(
                    automatisch = automatisch,
                    zuletzt = wann,
                    nurWlan = nurWlan,
                    bericht = pruefberichtAus(bericht),
                )
            }.collect { neu ->
                _state.update {
                    it.copy(
                        automatisch = neu.automatisch,
                        zuletzt = neu.zuletzt,
                        nurWlan = neu.nurWlan,
                        bericht = neu.bericht,
                    )
                }
            }
        }
    }

    fun setAutomatisch(an: Boolean) {
        viewModelScope.launch { einstellungen.setSyncAutomatisch(an) }
    }

    /**
     * Der Knopf „Jetzt sichern" (SYNC.md 9 und 10): der ganze Tagesdurchlauf
     * auf Knopfdruck, mit Snapshot mit Uhrzeit im Namen. Mit Befund gibt es
     * keinen Snapshot, und das steht dann auch da.
     */
    fun jetztSichern() {
        syncwaechter.geplantesAbbestellen(context)
        viewModelScope.launch {
            _state.update { it.copy(laeuft = true, meldung = null) }
            val zugang = anmeldung.zugang()
            if (zugang !is Zugang.Erteilt) {
                _state.update {
                    it.copy(laeuft = false, verbunden = false, meldung = "Bitte zuerst verbinden.")
                }
                return@launch
            }
            val meldung = when (val ergebnis = tagesdurchlauf.ausfuehren(zugang.token, vonHand = true)) {
                is Tagesergebnis.Fertig -> when {
                    ergebnis.snapshot != null ->
                        "Gesichert als " + ergebnis.snapshot + " im Ordner InNoteBox-Backup." +
                            if (ergebnis.entferntImPurge > 0) " Dabei wurden ${ergebnis.entferntImPurge} alte Grabsteine aufgeräumt." else ""
                    ergebnis.snapshotFehler != null ->
                        "Der Abgleich war in Ordnung, die Sicherung ist gescheitert: " + ergebnis.snapshotFehler
                    else ->
                        "Keine Sicherung geschrieben: Der Prüfbericht hat einen Befund. Sieh ihn dir unten an."
                }
                Tagesergebnis.AnmeldungNoetig -> "Der Zugriff ist abgelaufen. Bitte neu verbinden."
                Tagesergebnis.KeinNetz -> "Keine Verbindung ins Netz. Später noch einmal."
                is Tagesergebnis.Fehler -> "Die Sicherung ist gescheitert: " + ergebnis.grund
            }
            _state.update { it.copy(laeuft = false, verbunden = ergebnisIstOk(meldung), meldung = meldung) }
        }
    }

    fun setNurWlan(nurWlan: Boolean) {
        viewModelScope.launch { einstellungen.setSyncNurWlan(nurWlan) }
    }

    /** Fragt lautlos nach, ob der Zugriff steht. Zeigt nie einen Dialog. */
    fun pruefen() {
        viewModelScope.launch {
            _state.update { it.copy(prueft = true) }
            val zugang = anmeldung.zugang()
            _state.update {
                it.copy(
                    prueft = false,
                    verbunden = zugang is Zugang.Erteilt,
                    // Bei `AnmeldungNoetig` und `Getrennt` bleibt die Meldung
                    // leer: beides sind normale Zustaende und keine Fehler.
                    meldung = (zugang as? Zugang.Fehler)?.grund,
                )
            }
        }
    }

    /**
     * Stellt die Verbindung her.
     *
     * Gibt eine [PendingIntent] zurück, wenn Google seinen Dialog zeigen will,
     * die muss die Oberfläche starten. Ein ViewModel kann das nicht, und
     * es soll es auch nicht können.
     */
    fun verbinden(dann: (PendingIntent?) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(laeuft = true, meldung = null) }

            // `verbinden` hebt ein frueheres Trennen auf und fragt Google auch
            // dann, wenn auf diesem Geraet noch nie verbunden war.
            when (val zugang = anmeldung.verbinden()) {
                is Zugang.Erteilt -> {
                    _state.update { it.copy(laeuft = false, verbunden = true) }
                    dann(null)
                }

                is Zugang.AnmeldungNoetig -> {
                    _state.update { it.copy(laeuft = false) }
                    dann(zugang.absicht)
                }

                is Zugang.Fehler -> {
                    _state.update { it.copy(laeuft = false, meldung = zugang.grund) }
                    dann(null)
                }

                // Kann nach `verbinden` nicht vorkommen. Die Zweige stehen
                // trotzdem da, damit ein neuer Zustand im `when` auffaellt und
                // nicht stillschweigend durchrutscht.
                Zugang.Getrennt, Zugang.NieVerbunden -> {
                    _state.update { it.copy(laeuft = false) }
                    dann(null)
                }
            }
        }
    }

    /** Verarbeitet, was aus Googles Dialog zurückkommt. */
    fun ausDialog(daten: Intent?) {
        viewModelScope.launch {
            when (val zugang = anmeldung.ausDialog(daten)) {
                is Zugang.Erteilt ->
                    _state.update { it.copy(verbunden = true, meldung = null) }

                is Zugang.Fehler ->
                    _state.update { it.copy(verbunden = false, meldung = zugang.grund) }

                // Zweimal hintereinander eine Anmeldung zu verlangen hiesse, den
                // Nutzer im Kreis zu schicken. Wir behandeln es als Abbruch.
                is Zugang.AnmeldungNoetig ->
                    _state.update { it.copy(verbunden = false, meldung = "Abgebrochen.") }

                Zugang.Getrennt, Zugang.NieVerbunden ->
                    _state.update { it.copy(verbunden = false) }
            }
        }
    }

    /**
     * Legt den Ordner `/Notizen-App/` an oder findet ihn.
     *
     * Das ist der erste echte Drive-Aufruf und damit der eigentliche Test der
     * ganzen Kette: Anmeldung, Token, Netz, Drive-API, Berechtigung. Geht er
     * durch, steht die Grundlage; geht er schief, sagt die Meldung wo.
     */
    fun ordnerPruefen() {
        viewModelScope.launch {
            _state.update { it.copy(laeuft = true, meldung = null) }

            val zugang = anmeldung.zugang()
            if (zugang !is Zugang.Erteilt) {
                _state.update {
                    it.copy(
                        laeuft = false,
                        verbunden = false,
                        meldung = "Nicht verbunden. Bitte zuerst das Konto bei Google verknüpfen.",
                    )
                }
                return@launch
            }

            try {
                val id = drive.ordner(zugang.token)
                _state.update {
                    it.copy(laeuft = false, ordnerId = id, meldung = "Ordner steht in Drive bereit.")
                }
            } catch (fehler: Drivefehler.NichtErlaubt) {
                // Nicht als „kaputt" melden: Im Testmodus laeuft der Zugriff
                // nach sieben Tagen ab, und dann ist neu anmelden der richtige
                // naechste Schritt, kein Fehlersuchen.
                _state.update {
                    it.copy(
                        laeuft = false,
                        verbunden = false,
                        meldung = "Der Zugriff ist abgelaufen. Bitte neu verbinden.",
                    )
                }
            } catch (fehler: Drivefehler.KeinNetz) {
                _state.update {
                    it.copy(laeuft = false, meldung = "Keine Verbindung. Später noch einmal.")
                }
            } catch (fehler: Throwable) {
                _state.update { it.copy(laeuft = false, meldung = fehler.message ?: "Fehlgeschlagen.") }
            }
        }
    }

    /**
     * Gleicht sofort ab, ohne auf die Entprellung zu warten.
     *
     * Der Knopf dafuer ist kein Notbehelf: Wer wissen will, ob der Sync
     * funktioniert, will nicht zehn Sekunden raten.
     */
    fun jetztAbgleichen() {
        // Erst aufraeumen, dann laufen. Alles, was noch als geplanter oder
        // eingereihter Abgleich herumliegt, wird abbestellt -- sonst folgt auf
        // den Lauf, den der Nutzer gerade angestossen hat, gleich noch einer.
        syncwaechter.geplantesAbbestellen(context)

        viewModelScope.launch {
            _state.update { it.copy(laeuft = true, meldung = null) }

            val zugang = anmeldung.zugang()
            if (zugang !is Zugang.Erteilt) {
                _state.update {
                    it.copy(
                        laeuft = false,
                        verbunden = false,
                        meldung = if (zugang is Zugang.Getrennt) {
                            "Der Abgleich ist getrennt. Verbinde zuerst wieder."
                        } else {
                            "Bitte neu verbinden."
                        },
                    )
                }
                return@launch
            }

            val meldung = when (val ergebnis = abgleich.lauf(zugang.token)) {
                is Abgleichergebnis.Fertig -> ergebnisText(ergebnis)

                Abgleichergebnis.AnmeldungNoetig -> "Der Zugriff ist abgelaufen. Bitte neu verbinden."
                Abgleichergebnis.KeinNetz -> "Keine Verbindung ins Netz. Später noch einmal."
                is Abgleichergebnis.Fehler -> "Der Abgleich ist gescheitert: ${ergebnis.grund}"
            }

            _state.update {
                it.copy(
                    laeuft = false,
                    verbunden = ergebnisIstOk(meldung),
                    meldung = meldung,
                )
            }
        }
    }

    private fun ergebnisIstOk(meldung: String) = !meldung.startsWith("Der Zugriff ist abgelaufen")

    /**
     * Was ein Lauf bewirkt hat, in einem Satz.
     *
     * „0 hoch, 0 geholt" ist eine Zahlenkolonne ohne Aussage. Wenn nichts zu tun
     * war, steht genau das da.
     */
    private fun ergebnisText(fertig: Abgleichergebnis.Fertig): String {
        val teile = buildList {
            if (fertig.hochgeladen > 0) add("${fertig.hochgeladen} ${notizen(fertig.hochgeladen)} gesichert")
            if (fertig.geholt > 0) add("${fertig.geholt} geholt")
            if (fertig.geloescht > 0) add("${fertig.geloescht} gelöscht")
        }
        if (teile.isEmpty()) return "Alles war schon auf dem gleichen Stand."

        val satz = "Abgeglichen: " + teile.joinToString(", ") + "."
        return if (fertig.konflikte > 0) {
            val k = fertig.konflikte
            satz + " " + if (k == 1) {
                "Eine Notiz wurde doppelt bearbeitet, die zweite Fassung liegt im Eingang."
            } else {
                "$k Notizen wurden doppelt bearbeitet, die zweiten Fassungen liegen im Eingang."
            }
        } else {
            satz
        }
    }

    private fun notizen(anzahl: Int) = if (anzahl == 1) "Notiz" else "Notizen"

    /**
     * Löst die Verbindung.
     *
     * Der Zustand wird nicht auf Anfang gesetzt: Takt und Zeitpunkt des
     * letzten Abgleichs kommen aus den Einstellungen und gelten weiter. Wer sie
     * hier zurücksetzte, zeigte im Auswahlfeld eine Zahl an, die nirgends
     * gespeichert ist.
     */
    fun trennen() {
        viewModelScope.launch {
            _state.update { it.copy(laeuft = true, meldung = null) }
            val wie = anmeldung.abmelden()
            _state.update {
                it.copy(
                    laeuft = false,
                    // Getrennt ist getrennt. Ab jetzt geht nichts mehr zu
                    // Google, ganz gleich wie der Widerruf ausgegangen ist.
                    verbunden = false,
                    ordnerId = null,
                    meldung = when (wie) {
                        Trennung.Vollstaendig -> "Die Verbindung ist gelöst."
                        Trennung.NurLokal ->
                            "Die Verbindung ist gelöst. Die Erlaubnis in deinem " +
                                "Google-Konto ließ sich gerade nicht widerrufen."
                    },
                )
            }
        }
    }

    fun meldungGelesen() = _state.update { it.copy(meldung = null) }
}
