package de.notizen.app.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import de.notizen.core.data.db.entity.NoteEntity
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ui.UndoRequest
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.app.ui.components.LeererZustand
import de.notizen.app.ui.components.NoteCard
import de.notizen.app.ui.stage.StageViewModel
import de.notizen.app.ui.stage.NotizenRaster
import de.notizen.app.ui.stage.Kopfzeile
import de.notizen.app.ui.components.WischbarerRahmen
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.model.WischZiel
import de.notizen.core.data.model.Stage
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein geloeschter Ordner im Papierkorb, mit dem, was mit ihm hineinging. */
data class Papierkorbordner(
    val ordner: FolderEntity,
    val notizen: Int,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val ordner: FolderRepository,
) : ViewModel() {

    /**
     * Der geoeffnete Ordner im Papierkorb, oder `null` fuer die Uebersicht.
     *
     * Kein eigener Bildschirm mit eigener Route: Ein Ordner im Papierkorb ist
     * kein Ort, an den man navigiert, sondern ein aufgeklappter Eintrag. Die
     * Zurueck-Taste soll aus dem Papierkorb herausfuehren und nicht in ihn
     * hinein.
     */
    private val _geoeffnet = MutableStateFlow<String?>(null)
    val geoeffnet: StateFlow<String?> = _geoeffnet.asStateFlow()

    fun oeffne(ordnerId: String?) {
        _geoeffnet.value = ordnerId
    }

    /**
     * Die geloeschten Ordner mit der Zahl ihrer weggeworfenen Notizen.
     *
     * Die Zahl zaehlt nur, was MIT dem Ordner hineingewandert ist. Wer den
     * Ordner allein geloescht hat, sieht hier eine Null, und das stimmt: Seine
     * Notizen liegen woanders und leben.
     */
    val papierkorbordner: StateFlow<List<Papierkorbordner>> =
        combine(
            ordner.observeImPapierkorb(),
            ordner.observeZaehlungImPapierkorb(),
        ) { liste, zaehlung ->
            val zahlen = zaehlung.associate { it.ordnerId to it.anzahl }
            liste.map { Papierkorbordner(it, zahlen[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Was in dem gerade geoeffneten Ordner liegt. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inhalt: StateFlow<List<NoteWithRelations>> = _geoeffnet
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else ordner.observeInhaltImPapierkorb(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Der FRUEHERE Inhalt des geoeffneten Ordners: Notizen und
     * Unterordner, die beim Loeschen „nur der Ordner" herausgerueckt sind und
     * jetzt woanders leben. Ausgegraut gezeigt, damit man sieht, was hier lag.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val fruehereNotizen: StateFlow<List<NoteWithRelations>> = _geoeffnet
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else ordner.observeHerausgerueckteNotizen(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val fruehereOrdner: StateFlow<List<FolderEntity>> = _geoeffnet
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else ordner.observeHerausgerueckteOrdner(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Legt eine herausgerueckte Notiz nachtraeglich mit in den Papierkorb, zu
     * dem Ordner, aus dem sie kam. Danach ist sie dort eine gewoehnliche
     * weggeworfene Notiz und kommt mit „Mit Notizen zurueck" wieder heraus.
     */
    fun inDenPapierkorbLegen(noteId: String, ordnerId: String) {
        viewModelScope.launch {
            notes.setOrdner(listOf(noteId), ordnerId)
            notes.trash(listOf(noteId))
            undoKanal.send(
                UndoRequest("Notiz in den Papierkorb gelegt") {
                    notes.restore(listOf(noteId))
                    notes.setOrdner(listOf(noteId), null)
                },
            )
        }
    }

    fun ordnerZurueckholen(id: String, mitNotizen: Boolean) {
        viewModelScope.launch {
            ordner.wiederherstellen(id, mitNotizen)
            _geoeffnet.value = null
        }
    }

    fun ordnerEndgueltigLoeschen(id: String) {
        viewModelScope.launch {
            ordner.endgueltigLoeschen(id)
            _geoeffnet.value = null
        }
    }

    /**
     * Die Wischgeste "Zurueckholen" an einem Ordner: alles kommt zurueck, auch
     * Unterordner und Notizen. Das ist das Gegenstueck zum Loeschen "samt
     * Inhalt" und der Fall, den man mit einem Wisch meint. Wer nur die Huelle
     * will, nimmt das Menue. Mit Undo, das den Ordner samt Inhalt wieder
     * wegwirft.
     */
    fun ordnerSamtInhaltZurueck(eintrag: Papierkorbordner) {
        viewModelScope.launch {
            ordner.wiederherstellenSamtInhalt(eintrag.ordner.id)
            undoKanal.send(
                UndoRequest("Ordner „" + eintrag.ordner.name + "\" zurückgeholt") {
                    ordner.loeschenMitInhalt(eintrag.ordner.id)
                },
            )
        }
    }

    /**
     * Die geloeschten Notizen.
     *
     * Die eigene Suchleiste, die vom 2026-08-25 bis zum 2026-09-14 hier stand,
     * ist weg: Sie passte nicht zum Rest der App und verschwand, sobald keine
     * Notiz mehr da war. Gesucht wird ueber die normale Suche mit
     * dem Filter „Papierkorb".
     */
    val notizen: StateFlow<List<NoteWithRelations>> = notes.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val undoKanal = Channel<UndoRequest>(Channel.BUFFERED)
    val undoEvents: Flow<UndoRequest> = undoKanal.receiveAsFlow()

    fun wiederherstellen(id: String) {
        viewModelScope.launch {
            notes.restore(listOf(id))
            undoKanal.send(
                UndoRequest("Notiz wiederhergestellt") { notes.trash(listOf(id)) },
            )
        }
    }

    /**
     * Endgueltig. Es gibt bewusst KEIN Undo dafuer -- ab hier ist die Notiz
     * weg, und ein Tombstone sorgt dafuer, dass der andere Client sie nicht
     * wiederbelebt.
     */
    fun endgueltigLoeschen(ids: List<String>) {
        viewModelScope.launch { notes.purge(ids) }
    }
}

/**
 * Der Papierkorb, seit dem 2026-09-15 mit der Oberflaeche des Eingangs.
 *
 * Dasselbe Raster, dieselben Karten, dieselben Gesten. Bis dahin hatte der
 * Papierkorb eine eigene Liste mit eigener Suchleiste, und die
 * sah nach nichts in dieser App aus. Jetzt liefert das `StageViewModel`
 * im Papierkorbmodus die Notizen, und `NotizenRaster` zeichnet sie wie
 * ueberall. Nach rechts ziehen legt "Endgueltig loeschen" frei (einrasten,
 * dann tippen), nach links ziehen holt zurueck (reisst ab, mit Undo). Tippen
 * holt ebenfalls zurueck.
 *
 * Die Ordner stehen als Kopfzeilen im selben Raster, wischbar mit
 * denselben Gesten. Sie stehen auch dann da, wenn keine Notiz mehr uebrig ist;
 * bis zum 2026-09-14 brach die alte Ansicht bei leerer Notizliste vor den
 * Ordnern ab, und die Ordner schienen verschwunden.
 */
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    stageViewModel: StageViewModel,
    innerPadding: PaddingValues,
    /** Der Sprung zu einer Notiz, die jetzt woanders liegt (14c). */
    onSprungZurNotiz: (NoteEntity) -> Unit,
    onSprungZumOrdner: (FolderEntity) -> Unit,
) {
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val papierkorbordner by viewModel.papierkorbordner.collectAsStateWithLifecycle()
    val geoeffnet by viewModel.geoeffnet.collectAsStateWithLifecycle()
    val inhalt by viewModel.inhalt.collectAsStateWithLifecycle()
    val fruehereNotizen by viewModel.fruehereNotizen.collectAsStateWithLifecycle()
    val fruehereOrdner by viewModel.fruehereOrdner.collectAsStateWithLifecycle()
    val rechts by stageViewModel.wischRechts.collectAsStateWithLifecycle()
    val links by stageViewModel.wischLinks.collectAsStateWithLifecycle()

    // Ein geoeffneter Ordner ersetzt die Uebersicht, statt sich darunter zu
    // schieben. Zurueck fuehrt der Pfeil in seiner Kopfzeile.
    val offenerOrdner = papierkorbordner.firstOrNull { it.ordner.id == geoeffnet }
    if (offenerOrdner != null) {
        GeoeffneterOrdner(
            eintrag = offenerOrdner,
            notizen = inhalt,
            fruehereNotizen = fruehereNotizen,
            fruehereOrdner = fruehereOrdner,
            innerPadding = innerPadding,
            onZurueck = { viewModel.oeffne(null) },
            onNotizZurueck = viewModel::wiederherstellen,
            onNotizLoeschen = { viewModel.endgueltigLoeschen(listOf(it)) },
            onOrdnerZurueck = { mitNotizen ->
                viewModel.ordnerZurueckholen(offenerOrdner.ordner.id, mitNotizen)
            },
            onOrdnerLoeschen = {
                viewModel.ordnerEndgueltigLoeschen(offenerOrdner.ordner.id)
            },
            onFruehereInDenPapierkorb = { viewModel.inDenPapierkorbLegen(it, offenerOrdner.ordner.id) },
            onFruehereOeffnen = onSprungZurNotiz,
            onFruehererOrdner = onSprungZumOrdner,
        )
        return
    }

    if (notizen.isEmpty() && papierkorbordner.isEmpty()) {
        LeererZustand(
            titel = "Der Papierkorb ist leer",
            text = "Gelöschte Notizen und Ordner liegen hier noch eine Weile, " +
                "falls du es dir anders überlegst.",
            modifier = Modifier.padding(innerPadding),
        )
        return
    }

    // ZUSAMMENKLAPPBAR, sonst scrollt man bei dreissig Ordnern an den Notizen
    // vorbei. Ab elf Ordnern sind sie von Anfang an zugeklappt; wer sie sehen
    // will, tippt die Ueberschrift an.
    var ordnerZugeklappt by rememberSaveable { mutableStateOf(papierkorbordner.size > 10) }

    // Welcher Ordner gerade seine Wischflaeche offen hat. Hoechstens einer.
    var offenerWisch by remember { mutableStateOf<String?>(null) }

    val kopfzeilen = buildList {
        add(
            Kopfzeile("hinweis") {
                Hinweisblock(
                    titel = "Zurückholen oder endgültig löschen",
                    text = "Zur Seite ziehen legt das Zurückholen oder das endgültige " +
                        "Löschen frei, ein Tippen darauf führt es aus. Antippen holt " +
                        "zurück. Welche Seite was tut, steht in den Einstellungen.",
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            },
        )
        if (papierkorbordner.isNotEmpty()) {
            add(
                Kopfzeile("kopf-ordner") {
                    Abschnitt(
                        text = "Ordner (" + papierkorbordner.size + ")",
                        zugeklappt = ordnerZugeklappt,
                        onUmschalten = { ordnerZugeklappt = !ordnerZugeklappt },
                    )
                },
            )
            if (!ordnerZugeklappt) {
                papierkorbordner.forEach { eintrag ->
                    val id = eintrag.ordner.id
                    add(
                        Kopfzeile("o-" + id) {
                            WischbarerRahmen(
                                stufe = Stage.INBOX,
                                auswahlAktiv = false,
                                rechts = rechts,
                                links = links,
                                eingerastet = offenerWisch == id,
                                onEinrasten = { offenerWisch = id },
                                onAusrasten = { offenerWisch = null },
                                onWisch = { ziel ->
                                    when (ziel) {
                                        WischZiel.ENDGUELTIG -> viewModel.ordnerEndgueltigLoeschen(id)
                                        WischZiel.WIEDERHERSTELLEN -> viewModel.ordnerSamtInhaltZurueck(eintrag)
                                        else -> Unit
                                    }
                                },
                            ) { modifier ->
                                PapierkorbOrdnerzeile(
                                    eintrag = eintrag,
                                    onOeffnen = {
                                        if (offenerWisch == id) offenerWisch = null else viewModel.oeffne(id)
                                    },
                                    onZurueck = { mitNotizen -> viewModel.ordnerZurueckholen(id, mitNotizen) },
                                    onLoeschen = { viewModel.ordnerEndgueltigLoeschen(id) },
                                    modifier = modifier,
                                )
                            }
                        },
                    )
                }
            }
            if (notizen.isNotEmpty()) {
                add(Kopfzeile("kopf-notizen") { Abschnitt("Notizen (" + notizen.size + ")") })
            }
        }
    }

    NotizenRaster(
        viewModel = stageViewModel,
        onOpenNote = viewModel::wiederherstellen,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 4.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp,
        ),
        kopfzeilen = kopfzeilen,
    )
}

/**
 * Ueberschrift ueber einer Gruppe im Papierkorb.
 *
 * Mit [onUmschalten] wird sie zum Klappgriff: Pfeil rechts, die ganze Zeile
 * tippbar. Ohne bleibt sie eine schlichte Ueberschrift.
 */
@Composable
private fun Abschnitt(
    text: String,
    zugeklappt: Boolean = false,
    onUmschalten: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onUmschalten != null) Modifier.clickable(onClick = onUmschalten) else Modifier)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (onUmschalten != null) {
            Icon(
                imageVector = if (zugeklappt) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = if (zugeklappt) "Ordner aufklappen" else "Ordner zuklappen",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Ein geloeschter Ordner in der Uebersicht des Papierkorbs.
 *
 * Antippen oeffnet ihn. Die drei Punkte und langes Druecken bieten den kurzen
 * Weg: zurueckholen oder endgueltig loeschen, ohne erst hineinzugehen. Bis
 * zum 2026-09-14 musste man fuer das endgueltige Loeschen in jeden Ordner
 * einzeln hinein; bei zwanzig Ordnern war das eine Zumutung.
 */
@Composable
private fun PapierkorbOrdnerzeile(
    eintrag: Papierkorbordner,
    onOeffnen: () -> Unit,
    onZurueck: (mitNotizen: Boolean) -> Unit,
    onLoeschen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menue by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(onClick = onOeffnen, onLongClick = { menue = true })
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = eintrag.ordner.colorArgb?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)) {
                Text(eintrag.ordner.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (eintrag.notizen) {
                        0 -> "Ohne Notizen weggeworfen"
                        1 -> "Mit einer Notiz weggeworfen"
                        else -> "Mit " + eintrag.notizen + " Notizen weggeworfen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menue = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Mehr zu " + eintrag.ordner.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                    DropdownMenuItem(
                        text = { Text("Mit Notizen zurück") },
                        leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null) },
                        enabled = eintrag.notizen > 0,
                        onClick = {
                            menue = false
                            onZurueck(true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Nur der Ordner zurück") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        onClick = {
                            menue = false
                            onZurueck(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Ordner endgültig löschen") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menue = false
                            onLoeschen()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Ein geoeffneter Ordner im Papierkorb.
 *
 * Hier steht beides nebeneinander: der ganze Ordner und jede einzelne
 * Notiz. Wer ihn versehentlich weggeworfen hat, holt alles mit einem Griff
 * zurueck. Wer aufgeraeumt hat, pickt sich die zwei Notizen heraus, die er
 * doch noch braucht, und laesst den Rest liegen.
 */
@Composable
private fun GeoeffneterOrdner(
    eintrag: Papierkorbordner,
    notizen: List<NoteWithRelations>,
    fruehereNotizen: List<NoteWithRelations>,
    fruehereOrdner: List<FolderEntity>,
    innerPadding: PaddingValues,
    onZurueck: () -> Unit,
    onNotizZurueck: (String) -> Unit,
    onNotizLoeschen: (String) -> Unit,
    onOrdnerZurueck: (Boolean) -> Unit,
    onOrdnerLoeschen: () -> Unit,
    onFruehereInDenPapierkorb: (String) -> Unit,
    onFruehereOeffnen: (NoteEntity) -> Unit,
    onFruehererOrdner: (FolderEntity) -> Unit,
) {
    // Die Wahl an einer ausgegrauten Notiz (14c): nachtraeglich mit in den
    // Papierkorb, oder hinspringen, wo sie jetzt liegt.
    var gewaehlteFruehere by remember { mutableStateOf<NoteWithRelations?>(null) }
    gewaehlteFruehere?.let { n ->
        FruehereNotizWahl(
            notiz = n,
            ordnername = eintrag.ordner.name,
            onInDenPapierkorb = {
                gewaehlteFruehere = null
                onFruehereInDenPapierkorb(n.note.id)
            },
            onOeffnen = {
                gewaehlteFruehere = null
                onFruehereOeffnen(n.note)
            },
            onAbbrechen = { gewaehlteFruehere = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp),
        ) {
            IconButton(onClick = onZurueck) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Zurück zum Papierkorb",
                )
            }
            Text(
                text = eintrag.ordner.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Der ganze Ordner",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOrdnerZurueck(true) }) {
                            Text("Mit Notizen zurück")
                        }
                        TextButton(onClick = { onOrdnerZurueck(false) }) {
                            Text("Nur der Ordner")
                        }
                    }
                    TextButton(onClick = onOrdnerLoeschen) {
                        Text(
                            text = "Ordner endgültig löschen",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = "Die Notizen bleiben dabei im Papierkorb liegen. " +
                            "Endgültig weg sind sie erst, wenn du sie einzeln entfernst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val hatFrueheres = fruehereNotizen.isNotEmpty() || fruehereOrdner.isNotEmpty()

            if (notizen.isEmpty() && !hatFrueheres) {
                item {
                    Hinweisblock(
                        titel = "Keine Notizen darin",
                        text = "Dieser Ordner wurde ohne seinen Inhalt weggeworfen. " +
                            "Die Notizen liegen weiter dort, wohin sie damals gewandert sind.",
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                return@LazyColumn
            }

            if (notizen.isNotEmpty()) {
                item { Abschnitt("Einzelne Notizen") }
                items(notizen, key = { it.note.id }) { n ->
                    NoteCard(
                        note = n,
                        selected = false,
                        onClick = { onNotizZurueck(n.note.id) },
                        onLongClick = { onNotizLoeschen(n.note.id) },
                    )
                }
            }

            // DER FRUEHERE INHALT, AUSGEGRAUT (14c). Was beim Loeschen „nur der
            // Ordner" herausgerueckt ist, lebt woanders weiter; hier steht es
            // abgeblasst, damit man sieht, was in diesem Ordner lag. Antippen
            // ist nicht das gewohnte Oeffnen, sondern eine Wahl.
            if (hatFrueheres) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Abschnitt("Früherer Inhalt")
                        Text(
                            text = if (fruehereOrdner.isEmpty()) {
                                "Diese Notizen liegen jetzt woanders."
                            } else {
                                "Diese Notizen und Ordner liegen jetzt woanders."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                items(fruehereOrdner, key = { "frueher-o-" + it.id }) { o ->
                    FruehererOrdner(ordner = o, onKlick = { onFruehererOrdner(o) })
                }
                items(fruehereNotizen, key = { "frueher-n-" + it.note.id }) { n ->
                    NoteCard(
                        note = n,
                        selected = false,
                        onClick = { gewaehlteFruehere = n },
                        onLongClick = { gewaehlteFruehere = n },
                        modifier = Modifier.alpha(AUSGEGRAUT),
                    )
                }
            }
        }
    }
}

/** Wie blass der fruehere Inhalt steht. Lesbar, aber sichtbar nicht von hier. */
private const val AUSGEGRAUT = 0.45f

/** Ein herausgerueckter Unterordner, ausgegraut. Antippen springt zu ihm. */
@Composable
private fun FruehererOrdner(ordner: FolderEntity, onKlick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(AUSGEGRAUT),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onKlick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = ordner.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(ordner.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Liegt jetzt woanders. Antippen führt hin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Die Wahl an einer ausgegrauten Notiz (14c).
 *
 * Zwei Wege, beide sagen, was danach ist: nachtraeglich mit in den Papierkorb
 * (dann gehoert sie wieder zu diesem Ordner und kommt mit ihm zurueck), oder
 * der Sprung dorthin, wo sie jetzt liegt.
 */
@Composable
private fun FruehereNotizWahl(
    notiz: NoteWithRelations,
    ordnername: String,
    onInDenPapierkorb: () -> Unit,
    onOeffnen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(notiz.note.title.ifBlank { "Notiz ohne Titel" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Diese Notiz lag in „" + ordnername + "\" und liegt jetzt woanders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Wahlzeile(
                    symbol = Icons.Outlined.Delete,
                    titel = "In den Papierkorb legen",
                    erklaerung = "Sie gehört dann wieder zu diesem Ordner und kommt mit ihm zurück.",
                    onKlick = onInDenPapierkorb,
                )
                Wahlzeile(
                    symbol = Icons.AutoMirrored.Outlined.OpenInNew,
                    titel = "Notiz öffnen",
                    erklaerung = "Springt dorthin, wo sie jetzt liegt.",
                    onKlick = onOeffnen,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

@Composable
private fun Wahlzeile(
    symbol: ImageVector,
    titel: String,
    erklaerung: String,
    onKlick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onKlick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = symbol,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = titel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = erklaerung,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
