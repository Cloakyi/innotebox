package de.notizen.app.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ai.Geraetepruefung
import de.notizen.app.ai.KiZustimmung
import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.model.Geraetestand
import de.notizen.app.ui.components.KiZustimmungDialog
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was beim Start ueber die KI des Geraets zu sagen ist. */
sealed interface Geraetefrage {
    /** Nichts zu sagen: alles da, oder der Stand ist bekannt und unveraendert. */
    data object Nichts : Geraetefrage

    /** Ob die KI ueberhaupt arbeiten darf. Kommt vor jeder Messung (seit Alpha 9). */
    data object Zustimmung : Geraetefrage

    /** Etwas laesst sich nachladen. */
    data class Freischalten(val stand: Geraetestand, val laedt: Boolean = false) : Geraetefrage

    /** Etwas geht auf diesem Geraet nicht. */
    data class Fehlt(val stand: Geraetestand) : Geraetefrage

    /** Das Geraet hat sich veraendert; danach folgt die volle Pruefung. */
    data class Veraendert(val neu: Geraetestand) : Geraetefrage
}

/**
 * Gerätestand statt Rundumprüfung bei jedem Start (Phase 15).
 *
 * **Beim ersten Start die volle Pruefung mit ihren Dialogen**, danach bei jedem
 * Start nur die leichte: dieselben `checkStatus`-Aufrufe, aber ohne Dialog.
 * Weicht das Ergebnis vom gemerkten Stand ab, sagt die App es und prueft
 * wieder voll. So versucht sie nie, eine Funktion zu laden, die es nicht mehr
 * gibt, und sie fragt nicht bei jedem Start alles durch.
 *
 * **Gemessen wird nur mit Zustimmung** (seit Alpha 9). Die Messung ruft ML Kit
 * auf, und ML Kit meldet Kennzahlen an Google. Solange die KI nicht
 * eingeschaltet ist, fragt die App beim Start einmal nach
 * ([Geraetefrage.Zustimmung]) oder, nach einem Nein, gar nichts; gemessen
 * wird dann nicht.
 */
@HiltViewModel
class GeraetestandViewModel @Inject constructor(
    private val pruefung: Geraetepruefung,
    private val einstellungen: Einstellungen,
    private val kiZustimmung: KiZustimmung,
) : ViewModel() {

    private val _frage = MutableStateFlow<Geraetefrage>(Geraetefrage.Nichts)
    val frage: StateFlow<Geraetefrage> = _frage.asStateFlow()

    private var geprueft = false

    /** Einmal je Prozess. Ein zweiter Aufruf (Drehen, Rueckkehr) tut nichts. */
    fun pruefen() {
        if (geprueft) return
        geprueft = true
        viewModelScope.launch {
            if (einstellungen.kiFrageOffen().first()) {
                _frage.value = Geraetefrage.Zustimmung
                return@launch
            }
            messen()
        }
    }

    /** Die leichte Messung, nur bei eingeschalteter KI. Ohne sie kein Aufruf an ML Kit. */
    private suspend fun messen() {
        if (!einstellungen.kiAktiv().first()) return
        val alt = einstellungen.geraetestand().first()
        val neu = pruefung.messen()
        when {
            alt == null -> vollePruefung(neu)
            neu.weichtAbVon(alt) -> _frage.value = Geraetefrage.Veraendert(neu)
            else -> einstellungen.setGeraetestand(neu)
        }
    }

    /**
     * „Einschalten" im Dialog zur KI: Zustimmung mit neuem Schlüssel versiegeln,
     * dann wie gewohnt messen. Streikt der Schlüsselspeicher, bleibt die KI aus,
     * und die App fragt beim nächsten Start wieder.
     */
    fun kiZustimmen() {
        viewModelScope.launch {
            _frage.value = Geraetefrage.Nichts
            if (kiZustimmung.erteilen()) messen()
        }
    }

    /** Daneben getippt oder Zurück: keine Entscheidung, beim nächsten Start wieder fragen. */
    fun kiSpaeter() {
        _frage.value = Geraetefrage.Nichts
    }

    /** „Ohne KI": ausschalten, Zustimmung und Schlüssel löschen, nicht wieder fragen. */
    fun kiAblehnen() {
        viewModelScope.launch {
            kiZustimmung.widerrufen()
            _frage.value = Geraetefrage.Nichts
        }
    }

    /** Nach der Meldung „hat sich veraendert": die volle Pruefung mit Dialog. */
    fun veraenderungGesehen() {
        val f = _frage.value as? Geraetefrage.Veraendert ?: return
        viewModelScope.launch { vollePruefung(f.neu) }
    }

    private suspend fun vollePruefung(stand: Geraetestand) {
        _frage.value = when {
            stand.allesVerfuegbar -> {
                einstellungen.setGeraetestand(stand)
                Geraetefrage.Nichts
            }
            stand.etwasFehlt -> Geraetefrage.Fehlt(stand)
            else -> Geraetefrage.Freischalten(stand)
        }
    }

    /** Der Knopf zum Nachladen. Die App laedt nur, weil der Nutzer es sagt. */
    fun nachladen() {
        val f = _frage.value as? Geraetefrage.Freischalten ?: return
        _frage.value = f.copy(laedt = true)
        viewModelScope.launch {
            val danach = pruefung.nachladen(f.stand)
            einstellungen.setGeraetestand(danach)
            _frage.value = if (danach.etwasLadbar || danach.etwasFehlt) {
                Geraetefrage.Fehlt(danach)
            } else {
                Geraetefrage.Nichts
            }
        }
    }

    /** „Später" bzw. „Verstanden": der Stand wird so gemerkt, wie er ist. */
    fun bestaetigen() {
        val stand = when (val f = _frage.value) {
            is Geraetefrage.Freischalten -> f.stand
            is Geraetefrage.Fehlt -> f.stand
            is Geraetefrage.Veraendert -> f.neu
            Geraetefrage.Nichts, Geraetefrage.Zustimmung -> null
        }
        viewModelScope.launch {
            stand?.let { einstellungen.setGeraetestand(it) }
            _frage.value = Geraetefrage.Nichts
        }
    }
}

/**
 * Der Dialog zum Geraetestand beim Start (Phase 15).
 *
 * Drei Ausgaenge, siehe [Geraetefrage]. [aktiv] sagt, ob er ueberhaupt gezeigt
 * werden darf: Solange der Berechtigungsdialog offen ist, wartet er, sonst
 * laegen zwei Dialoge uebereinander.
 */
@Composable
fun Geraetestanddialog(aktiv: Boolean, viewModel: GeraetestandViewModel = hiltViewModel()) {
    val frage by viewModel.frage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.pruefen() }
    if (!aktiv) return

    when (val f = frage) {
        Geraetefrage.Nichts -> Unit

        Geraetefrage.Zustimmung -> KiZustimmungDialog(
            ablehnenText = "Ohne KI",
            onZustimmen = viewModel::kiZustimmen,
            onAblehnen = viewModel::kiAblehnen,
            onSchliessen = viewModel::kiSpaeter,
        )

        is Geraetefrage.Veraendert -> AlertDialog(
            onDismissRequest = viewModel::veraenderungGesehen,
            title = { Text("Etwas hat sich verändert") },
            text = {
                Text(
                    "Auf diesem Gerät hat sich etwas verändert. Die App prüft die " +
                        "KI-Funktionen neu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = { TextButton(onClick = viewModel::veraenderungGesehen) { Text("Weiter") } },
        )

        is Geraetefrage.Freischalten -> AlertDialog(
            onDismissRequest = { if (!f.laedt) viewModel.bestaetigen() },
            title = { Text("Du kannst die lokale KI freischalten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Dieses Gerät bringt die KI mit, ein Teil davon muss noch " +
                            "geladen werden. Alles läuft danach auf dem Gerät, deine Notizen " +
                            "verlassen es nicht. Die App lädt nur, wenn du es hier sagst.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Faehigkeitenliste(f.stand)
                    if (f.laedt) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Wird geladen. Das kann einen Moment dauern.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::nachladen, enabled = !f.laedt) { Text("Jetzt laden") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::bestaetigen, enabled = !f.laedt) { Text("Später") }
            },
        )

        is Geraetefrage.Fehlt -> AlertDialog(
            onDismissRequest = viewModel::bestaetigen,
            title = { Text("Was dieses Gerät nicht kann") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Nicht jede KI-Funktion gibt es auf jedem Gerät. Alles andere " +
                            "in der App geht unverändert: Notizen, Aufnahmen, Ordner, Suche, " +
                            "Abgleich.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Faehigkeitenliste(f.stand)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::bestaetigen) { Text("Verstanden") } },
        )
    }
}

/** Die drei Funktionen mit ihrem Stand, je mit einem Satz, was fehlt und wo man es holt. */
@Composable
private fun Faehigkeitenliste(stand: Geraetestand) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Faehigkeitszeile(
            name = "Spracherkennung",
            stand = stand.spracherkennung,
            wennLadbar = "Das Sprachmodell wird nachgeladen.",
            wennNicht = "Die Umwandlung von Aufnahmen in Text bringt nur Google auf wenigen " +
                "Geräten mit (AICore). Aufnehmen und Abspielen gehen trotzdem.",
        )
        Faehigkeitszeile(
            name = "Titel und Aufbereitung",
            stand = stand.textki,
            wennLadbar = "Das Sprachmodell für Titel und das Aufbereiten wird nachgeladen.",
            wennNicht = "Die KI auf dem Gerät (Gemini Nano über AICore) gibt es hier nicht. " +
                "Titel schlägt die App aus dem Text vor, das Aufbereiten entfällt.",
        )
        Faehigkeitszeile(
            name = "Übersetzung",
            stand = stand.uebersetzung,
            wennLadbar = "Der Übersetzungsdienst ist da, die Sprachpakete lädst du in den " +
                "Systemeinstellungen nach.",
            wennNicht = "Dieses Gerät hat keinen Übersetzungsdienst des Systems. Ein anderer " +
                "Weg kommt mit der Übersetzung selbst.",
        )
    }
}

@Composable
private fun Faehigkeitszeile(name: String, stand: Faehigkeit, wennLadbar: String, wennNicht: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when (stand) {
                Faehigkeit.VERFUEGBAR -> Icons.Outlined.CheckCircle
                Faehigkeit.LADBAR -> Icons.Outlined.CloudDownload
                Faehigkeit.NICHT -> Icons.Outlined.ErrorOutline
            },
            contentDescription = null,
            tint = when (stand) {
                Faehigkeit.NICHT -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(20.dp),
        )
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when (stand) {
                    Faehigkeit.VERFUEGBAR -> "Bereit."
                    Faehigkeit.LADBAR -> wennLadbar
                    Faehigkeit.NICHT -> wennNicht
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
