package de.notizen.app.kalender

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ui.UndoRequest
import de.notizen.app.ui.theme.NotizenScaffold
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KalenderEinstellungenViewModel @Inject constructor(
    private val repo: KalenderRepository,
    private val einstellungen: Einstellungen,
    private val wecker: Terminwecker,
) : ViewModel() {

    val kalender: StateFlow<List<EventCalendarEntity>> = repo.beobachteKalender()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val standardkalender: StateFlow<String> = einstellungen.standardkalender()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val standardErinnerung: StateFlow<Int> = einstellungen.standardErinnerung()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)
    val notizen: StateFlow<Boolean> = einstellungen.erinnerungsnotizenImKalender()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val symbolKi: StateFlow<Boolean> = einstellungen.terminsymbolKi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val dauer: StateFlow<Int> = einstellungen.terminDauer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 60)
    val kalenderwochen: StateFlow<Boolean> = einstellungen.kalenderwochen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val wochenendeAus: StateFlow<Boolean> = einstellungen.wochenendeAus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val vorgaben: StateFlow<Map<String, Kalendervorgabe>> = einstellungen.kalendervorgaben()
        .map { m -> m.mapValues { Kalendervorgabe.lesen(it.value) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val tippen: StateFlow<Boolean> = einstellungen.tippenLegtAn()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val kiAn: StateFlow<Boolean> = einstellungen.kiAktiv()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun anlegen(name: String, farbe: Int) = viewModelScope.launch { repo.kalenderAnlegen(name, farbe) }
    fun aendern(id: String, name: String, farbe: Int) = viewModelScope.launch { repo.kalenderAendern(id, name, farbe) }

    /** Loescht weich; die Termine gehen mit und kommen mit dem Zuruecknehmen wieder. */
    fun loeschen(id: String, danach: (UndoRequest) -> Unit) = viewModelScope.launch {
        val termine = repo.kalenderLoeschen(id)
        danach(UndoRequest("Kalender gelöscht") { repo.kalenderWiederherstellen(id, termine) })
    }

    fun setStandardkalender(id: String) = viewModelScope.launch { einstellungen.setStandardkalender(id) }
    fun setStandardErinnerung(m: Int) = viewModelScope.launch { einstellungen.setStandardErinnerung(m) }
    fun setNotizen(an: Boolean) = viewModelScope.launch { einstellungen.setErinnerungsnotizenImKalender(an) }
    fun setSymbolKi(an: Boolean) = viewModelScope.launch { einstellungen.setTerminsymbolKi(an) }
    fun setVorgabe(id: String, v: Kalendervorgabe) = viewModelScope.launch { einstellungen.setKalendervorgabe(id, v.kodiert()) }
    fun setTippen(an: Boolean) = viewModelScope.launch { einstellungen.setTippenLegtAn(an) }
    fun setDauer(m: Int) = viewModelScope.launch { einstellungen.setTerminDauer(m) }
    fun setKalenderwochen(an: Boolean) = viewModelScope.launch { einstellungen.setKalenderwochen(an) }
    fun setWochenendeAus(aus: Boolean) = viewModelScope.launch { einstellungen.setWochenendeAus(aus) }

    fun darfExaktWecken() = wecker.darfExaktWecken()
    fun darfVollbild() = wecker.darfVollbild()
    fun exaktEinstellungen() = wecker.exaktEinstellungen()
    fun vollbildEinstellungen() = wecker.vollbildEinstellungen()
}

/**
 * Die Einstellungen des Kalenders: Kalender anlegen, umbenennen, umfaerben
 * und loeschen, dazu die Voreinstellungen fuer neue Termine und die
 * Erlaubnisse fuer den Wecker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalenderEinstellungenScreen(
    onZurueck: () -> Unit,
    zeigeUndo: (UndoRequest) -> Unit,
    viewModel: KalenderEinstellungenViewModel = hiltViewModel(),
) {
    val kalender by viewModel.kalender.collectAsStateWithLifecycle()
    val standard by viewModel.standardkalender.collectAsStateWithLifecycle()
    val erinnerung by viewModel.standardErinnerung.collectAsStateWithLifecycle()
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val symbolKi by viewModel.symbolKi.collectAsStateWithLifecycle()
    val kiAn by viewModel.kiAn.collectAsStateWithLifecycle()
    val dauer by viewModel.dauer.collectAsStateWithLifecycle()
    val kalenderwochen by viewModel.kalenderwochen.collectAsStateWithLifecycle()
    val wochenendeAus by viewModel.wochenendeAus.collectAsStateWithLifecycle()
    val tippen by viewModel.tippen.collectAsStateWithLifecycle()
    val vorgaben by viewModel.vorgaben.collectAsStateWithLifecycle()
    var dauerWahl by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var bearbeiten by remember { mutableStateOf<EventCalendarEntity?>(null) }
    var neu by remember { mutableStateOf(false) }
    var standardWahl by remember { mutableStateOf(false) }
    var erinnerungWahl by remember { mutableStateOf(false) }
    var pruefung by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        pruefung++
        onPauseOrDispose { }
    }
    val exakt = remember(pruefung) { viewModel.darfExaktWecken() }
    val vollbild = remember(pruefung) { viewModel.darfVollbild() }
    val oeffnen: (android.content.Intent) -> Unit = {
        try {
            context.startActivity(it)
        } catch (_: ActivityNotFoundException) {
        }
    }

    NotizenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") }
                },
                title = { Text("Kalendereinstellungen") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Abschnitt("Meine Kalender")
            kalender.forEach { k ->
                Eintragszeile(k.name, if (hatVorgaben(k.id)) "Tippe für Name, Farbe und Vorgaben" else "Tippe, um Name und Farbe zu ändern", Color(k.colorArgb)) { bearbeiten = k }
            }
            Row(
                Modifier.fillMaxWidth().clickable { neu = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 20.dp))
                Text("Kalender erstellen", style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Abschnitt("Neue Termine")
            Eintragszeile(
                "Standardkalender",
                kalender.firstOrNull { it.id == standard }?.name ?: kalender.firstOrNull()?.name ?: "Mein Kalender",
            ) { standardWahl = true }
            Eintragszeile("Standardbenachrichtigung", erinnerungstext(erinnerung, false)) { erinnerungWahl = true }
            Eintragszeile("Standarddauer", dauertext(dauer)) { dauerWahl = true }
            Schalterzeile(
                "Per Tippen ins Raster anlegen",
                if (tippen) {
                    "Ein Tipp auf eine freie Stelle in Tag, 3 Tage oder Woche legt dort einen Termin an."
                } else {
                    "Neue Termine gibt es nur über das Plus unten rechts. Ein Tipp ins Raster tut nichts."
                },
                tippen,
            ) { viewModel.setTippen(it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Abschnitt("Anzeige")
            Schalterzeile(
                "Erinnerungen aus Notizen",
                "Notizen mit Erinnerung erscheinen im Kalender. Antippen öffnet die Notiz.",
                notizen,
            ) { viewModel.setNotizen(it) }
            Schalterzeile(
                "Symbol per KI wählen",
                if (kiAn) {
                    "Die KI auf dem Gerät sucht zu jedem Termin ein passendes Zeichen aus. Es erscheint auch im Wecker."
                } else {
                    "Die KI ist in den Einstellungen der App aus. Dann wählt InNoteBox das Zeichen nur anhand der Wörter."
                },
                symbolKi,
            ) { viewModel.setSymbolKi(it) }
            Schalterzeile(
                "Kalenderwochen",
                "Der Monat zeigt vor jeder Woche ihre Nummer. Die anderen Ansichten zeigen sie oben links.",
                kalenderwochen,
            ) { viewModel.setKalenderwochen(it) }
            Schalterzeile(
                "Wochenende ausblenden",
                "Die Woche zeigt nur Montag bis Freitag. Der Monat bleibt vollständig.",
                wochenendeAus,
            ) { viewModel.setWochenendeAus(it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Abschnitt("Wecker für wichtige Termine")
            Eintragszeile(
                "Pünktliche Wecker",
                if (exakt) "Erlaubt" else "Nicht erlaubt. Ohne diese Erlaubnis klingelt kein Termin.",
            ) { oeffnen(viewModel.exaktEinstellungen()) }
            Eintragszeile(
                "Wecker auf dem ganzen Bildschirm",
                if (vollbild) "Erlaubt" else "Nicht erlaubt. Dann erscheint der Wecker nur als Meldung oben.",
            ) { oeffnen(viewModel.vollbildEinstellungen()) }
            Spacer(Modifier.height(32.dp))
        }
    }

    bearbeiten?.let { k ->
        KalenderDialog(
            titel = "Kalender bearbeiten",
            name = k.name,
            farbe = k.colorArgb,
            vorgabe = if (hatVorgaben(k.id)) vorgaben[k.id] ?: Kalendervorgabe() else null,
            onVorgabe = { viewModel.setVorgabe(k.id, it) },
            onAbbrechen = { bearbeiten = null },
            onFertig = { name, farbe ->
                bearbeiten = null
                viewModel.aendern(k.id, name, farbe)
            },
            onLoeschen = if (kalender.size > 1) {
                {
                    bearbeiten = null
                    viewModel.loeschen(k.id, zeigeUndo)
                }
            } else {
                null
            },
        )
    }
    if (neu) {
        KalenderDialog(
            titel = "Kalender erstellen",
            name = "",
            farbe = FARBE_STANDARD,
            onAbbrechen = { neu = false },
            onFertig = { name, farbe ->
                neu = false
                viewModel.anlegen(name, farbe)
            },
        )
    }
    if (standardWahl) {
        Auswahl(
            titel = "Standardkalender",
            eintraege = kalender.map { it.id to it.name },
            gewaehlt = standard.ifBlank { kalender.firstOrNull()?.id.orEmpty() },
            onZu = { standardWahl = false },
        ) {
            standardWahl = false
            viewModel.setStandardkalender(it)
        }
    }
    if (erinnerungWahl) {
        Auswahl(
            titel = "Standardbenachrichtigung",
            eintraege = erinnerungsvorschlaege(false).map { it to erinnerungstext(it, false) },
            gewaehlt = erinnerung,
            onZu = { erinnerungWahl = false },
        ) {
            erinnerungWahl = false
            viewModel.setStandardErinnerung(it)
        }
    }
    if (dauerWahl) {
        Auswahl(
            titel = "Standarddauer",
            eintraege = DAUERN.map { it to dauertext(it) },
            gewaehlt = dauer,
            onZu = { dauerWahl = false },
        ) {
            dauerWahl = false
            viewModel.setDauer(it)
        }
    }
}


@Composable
private fun Abschnitt(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Eintragszeile(titel: String, unter: String, farbe: Color? = null, onKlick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onKlick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (farbe != null) {
            Box(Modifier.padding(end = 20.dp).size(20.dp).clip(CircleShape).background(farbe))
        }
        Column {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(unter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Schalterzeile(titel: String, unter: String, an: Boolean, onWechsel: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onWechsel(!an) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(unter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = an, onCheckedChange = onWechsel)
    }
}

@Composable
private fun <T> Auswahl(titel: String, eintraege: List<Pair<T, String>>, gewaehlt: T, onZu: () -> Unit, onWahl: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onZu,
        title = { Text(titel) },
        text = {
            Column {
                eintraege.forEach { (wert, text) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onWahl(wert) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = wert == gewaehlt, onClick = { onWahl(wert) })
                        Text(text)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onZu) { Text("Abbrechen") } },
    )
}
