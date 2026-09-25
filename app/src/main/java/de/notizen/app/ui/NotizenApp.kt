package de.notizen.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import de.notizen.app.sync.Sicherung
import de.notizen.app.sync.SicherungViewModel
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ui.components.FarbAuswahlBlatt
import de.notizen.app.kalender.KalenderScreen
import de.notizen.app.ui.ordner.OrdnerDialogHost
import de.notizen.app.ui.ordner.OrdnerScreen
import de.notizen.app.ui.ordner.OrdnerViewModel
import de.notizen.app.ui.ordner.OrdnerWahlHost
import de.notizen.app.ui.ordner.ZurueckholenHost
import de.notizen.app.ui.daten.BackupScreen
import de.notizen.app.ui.daten.SyncScreen
import de.notizen.app.ui.components.TagAuswahlDialog
import de.notizen.app.ui.components.TitelDialog
import de.notizen.app.ui.editor.EditorScreen
import de.notizen.app.ui.editor.EditorViewModel
import de.notizen.app.ui.settings.ProtokollScreen
import de.notizen.app.ui.settings.LizenzenScreen
import de.notizen.app.ui.settings.SettingsScreen
import de.notizen.app.ui.settings.HERVORHEBEN_KI
import de.notizen.app.ui.stage.ArchivScreen
import de.notizen.app.ui.suche.SucheScreen
import de.notizen.app.ui.suche.SucheViewModel
import de.notizen.app.ui.stage.StageScreen
import de.notizen.app.ui.stage.StageViewModel
import de.notizen.app.ui.tags.TagsScreen
import de.notizen.app.ui.trash.TrashScreen
import de.notizen.app.ui.trash.TrashViewModel
import de.notizen.app.ui.util.teileNotizen
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.Ordnersortierung
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.app.ui.theme.BLASEN_DECKUNG
import de.notizen.app.ui.theme.lesbareFarbe
import de.notizen.app.ui.theme.ueberblendet
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.outlined.Inbox
import de.notizen.core.data.model.Darstellung
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.repository.NoteSort
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.ordner.gesamtzahlen
import de.notizen.core.data.ordner.mitKindern
import de.notizen.core.data.ordner.sichtbar
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zaehlerstaende und Tags fuer den Drawer. */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val notes: NoteRepository,
    tags: TagRepository,
    private val ordner: FolderRepository,
    private val einstellungen: Einstellungen,
) : ViewModel() {

    /** Der Wechsel-Eintrag in der Seitenspalte (14e). Dasselbe wie der Schalter in den Einstellungen. */
    fun setOrdnermodus(an: Boolean) {
        viewModelScope.launch { einstellungen.setOrdnermodus(an) }
    }

    /** Wonach die Ordner in der Seitenspalte geordnet stehen (14e). */
    private val sortierung = einstellungen.ordnerReihenfolge()

    /**
     * Ob die App im Ordnermodus laeuft. `null` heisst: noch nicht gelesen.
     *
     * Der Unterschied ist wichtig genug fuer einen dritten Zustand. Der
     * NavHost braucht sein Startziel, bevor er etwas zeichnet; mit `false` als
     * Anfangswert saehe man im Ordnermodus fuer einen Augenblick den Eingang
     * und der Bildschirm spraenge danach um.
     */
    val ordnermodus: StateFlow<Boolean?> = einstellungen.ordnermodus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Alle Notizen ausserhalb des Papierkorbs. Im Ordnermodus die einzige Zahl. */
    val gesamt: StateFlow<Int> = notes.observeAnzahlGesamt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val anzahlen: StateFlow<Map<Stage, Int>> = combine(
        notes.observeCount(Stage.INBOX),
        notes.observeCount(Stage.WORKSPACE),
        notes.observeCount(Stage.ARCHIVE),
    ) { eingang, workspace, archiv ->
        mapOf(Stage.INBOX to eingang, Stage.WORKSPACE to workspace, Stage.ARCHIVE to archiv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val tags: StateFlow<List<TagEntity>> = tags.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Der ganze Ordnerbaum fuer die Seitenspalte, samt Zahlen.
     *
     * **Hier oben und nicht im OrdnerViewModel.** Die Seitenspalte steht ueber
     * allen Bildschirmen; sie darf nicht davon abhaengen, dass gerade ein
     * Ordner offen ist. Auf dem Einstellungsbildschirm gibt es keinen.
     */
    val ordnerbaum: StateFlow<List<Ordnerzeile>> = combine(ordner.observeAll(), sortierung) { alle, sort ->
        baum(alle, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Welche Ordner ueberhaupt Kinder haben. Nur die bekommen einen Pfeil. */
    val ordnerMitKindern: StateFlow<Set<String>> = ordner.observeAll()
        .map { mitKindern(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Notizen je Ordner, die Unterordner mitgezaehlt. */
    val ordnerzahlen: StateFlow<Map<String, Int>> =
        combine(ordner.observeAll(), ordner.observeZaehlung()) { alle, zaehlung ->
            gesamtzahlen(alle, zaehlung.associate { it.ordnerId to it.anzahl })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Der Archivbaum des Ordnermodus, fuer den Eintrag „Archiv" unter dem
     * Ordnerbaum (Phase 14b). Ein eigener Baum, dieselbe Bauart.
     */
    val archivbaum: StateFlow<List<Ordnerzeile>> = combine(ordner.observeAll(Bereich.ARCHIV), sortierung) { alle, sort ->
        baum(alle, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivMitKindern: StateFlow<Set<String>> = ordner.observeAll(Bereich.ARCHIV)
        .map { mitKindern(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val archivzahlen: StateFlow<Map<String, Int>> =
        combine(ordner.observeAll(Bereich.ARCHIV), ordner.observeZaehlung()) { alle, zaehlung ->
            gesamtzahlen(alle, zaehlung.associate { it.ordnerId to it.anzahl })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Wie viele Notizen im Archiv des Ordnermodus liegen, und wie viele nicht. */
    val archivGesamt: StateFlow<Int> = notes.observeAnzahlImOrdnerArchiv()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val ordnerGesamt: StateFlow<Int> = notes.observeAnzahlNichtArchiviert()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Ob ueberhaupt etwas im Papierkorb liegt, Notizen oder Ordner. */
    val papierkorbVoll: StateFlow<Boolean> =
        combine(notes.observeTrash(), ordner.observeImPapierkorb()) { n, o ->
            n.isNotEmpty() || o.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Leert den Papierkorb ganz: alle Notizen und alle Ordner darin.
     *
     * Ueber dieselben Wege wie von Hand, `purge` mit Grabsteinen und
     * `endgueltigLoeschen`. Es gibt bewusst kein Undo; die Rueckfrage davor
     * ist die Sicherung.
     */
    fun papierkorbLeeren() {
        viewModelScope.launch {
            val notizen = notes.observeTrash().first().map { it.note.id }
            if (notizen.isNotEmpty()) notes.purge(notizen)
            ordner.observeImPapierkorb().first().forEach { ordner.endgueltigLoeschen(it.id) }
        }
    }
}

/**
 * Wechselt auf eine Hauptdestination.
 *
 * ACHTUNG, HIER LAG EIN FEHLER: Ein `launchSingleTop = true` sieht harmlos aus,
 * verschluckt hier aber den Wechsel zwischen den Stufen. Alle drei teilen sich
 * das Routenmuster `stage/{stage}`, und launchSingleTop vergleicht nur das
 * MUSTER, nicht die Argumente -- Eingang und Workspace galten damit als
 * dieselbe Destination. Der Papierkorb funktionierte, weil er ein eigenes
 * Muster hat.
 *
 * Stattdessen wird bis zur Startdestination zurueckgeraeumt und neu
 * aufgesetzt. Der Stapel bleibt flach (Eingang plus hoechstens ein Ziel),
 * und Zurueck fuehrt vom Ziel wieder in den Eingang.
 */
private fun NavHostController.wechsleZu(ziel: String, istStartziel: Boolean) {
    navigate(ziel) {
        popUpTo(graph.startDestinationId) { inclusive = istStartziel }
    }
}

@Composable
fun NotizenApp(
    oeffneNotiz: String? = null,
    onGeoeffnet: () -> Unit = {},
    lieseSicherung: Uri? = null,
    onSicherungGelesen: () -> Unit = {},
) {
    val shellFuerModus: AppShellViewModel = hiltViewModel()
    val modus by shellFuerModus.ordnermodus.collectAsStateWithLifecycle()

    // Solange die Einstellung nicht gelesen ist, wird nichts gezeichnet. Das
    // sind Millisekunden aus dem DataStore; ein Startziel zu raten und danach
    // umzuspringen waere teurer als dieser Augenblick.
    val ordnermodus = modus ?: return

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shell: AppShellViewModel = shellFuerModus

    /**
     * Wo die App anfaengt.
     *
     * **Einmal gemerkt und danach nicht mehr angefasst.** Der Graph des
     * NavHost wird neu gebaut, sobald sich sein Startziel aendert, und dabei
     * fiele der ganze Stapel weg. Ein Wechsel des Modus fuehrt deshalb ueber
     * eine gewoehnliche Navigation weiter unten, nicht ueber einen neuen Graph.
     */
    val startziel = remember { if (ordnermodus) Routes.ordner() else Routes.stage(Stage.INBOX) }

    // Beim Umschalten in den anderen Modus geht es auf dessen Startseite. Der
    // Vergleich mit dem letzten Wert ist noetig, damit das nicht schon beim
    // ersten Zeichnen passiert: Dort steht die App ohnehin richtig.
    var letzterModus by remember { mutableStateOf(ordnermodus) }
    LaunchedEffect(ordnermodus) {
        if (ordnermodus != letzterModus) {
            letzterModus = ordnermodus
            navController.wechsleZu(
                if (ordnermodus) Routes.ordner() else Routes.stage(Stage.INBOX),
                true,
            )
        }
    }

    /**
     * Zentrale Undo-Snackbar. Ohne Speichern-Knopf und ohne Loeschabfrage ist
     * sie der einzige Rueckweg -- deshalb liegt sie hier oben und nicht in den
     * einzelnen Screens (Spezifikation Abschnitt 9).
     */
    val zeigeUndo: (UndoRequest) -> Unit = { anfrage ->
        scope.launch {
            // Immer nur EINE Snackbar. Ohne das reiht Material jede weitere
            // dahinter ein, und nach ein paar Aktionen arbeitet man einen
            // Stapel alter Meldungen ab, statt die neueste zu sehen.
            snackbarHostState.currentSnackbarData?.dismiss()

            val ergebnis = snackbarHostState.showSnackbar(
                message = anfrage.message,
                actionLabel = "Rückgängig",
                withDismissAction = true,
                // MUSS explizit gesetzt werden: Material 3 waehlt die Dauer
                // abhaengig davon, ob eine Aktion dabei ist -- mit actionLabel
                // ist der Standard INDEFINITE. Die Snackbar bliebe dann
                // dauerhaft stehen und wanderte beim Screenwechsel mit.
                duration = SnackbarDuration.Short,
            )
            if (ergebnis == SnackbarResult.ActionPerformed) anfrage.undo()
        }
    }

    /**
     * Fuer Bedienelemente, die absichtlich schon da sind, deren Logik aber noch
     * fehlt. Besser ein ehrlicher Hinweis als ein Knopf, bei dem nichts
     * passiert und man raten muss, ob er kaputt ist.
     */
    val zeigeHinweis: (String) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    // Eine angetippte Erinnerung fuehrt direkt in ihre Notiz. Der Auftrag wird
    // sofort quittiert, sonst spraenge die App bei jeder Drehung erneut dorthin.
    LaunchedEffect(oeffneNotiz) {
        oeffneNotiz?.let {
            navController.navigate(Routes.editor(it))
            onGeoeffnet()
        }
    }

    // Eine Sicherungsdatei aus dem Dateimanager fuehrt auf die Seite, auf der
    // das Einlesen stattfindet. Sie zeigt den Fortschritt und danach das
    // Ergebnis -- im Verborgenen einzulesen waere schneller und niemand wuesste
    // hinterher, was geschehen ist.
    LaunchedEffect(lieseSicherung) {
        if (lieseSicherung != null) navController.wechsleZu(Routes.BACKUP, false)
    }

    NavHost(navController = navController, startDestination = startziel) {

        composable(
            route = Routes.STAGE,
            arguments = listOf(
                navArgument("hervorheben") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val viewModel: StageViewModel = hiltViewModel()
            LaunchedEffect(viewModel) { viewModel.undoEvents.collect(zeigeUndo) }
            LaunchedEffect(viewModel) { viewModel.hinweisEvents.collect(zeigeHinweis) }

            // Das Archiv hat sein eigenes Suchfeld im Screen -- die Suchleiste
            // in der Top Bar waere daneben ein zweites, totes Feld.
            val istArchiv = viewModel.stage == Stage.ARCHIVE

            HomeGeruest(
                titel = StageUi.label(viewModel.stage),
                aktuelleStufe = viewModel.stage,
                aktuelleRoute = Routes.stage(viewModel.stage),
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = viewModel,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                zeigeSuchleiste = !istArchiv,
                onSuche = { navController.navigate(Routes.suche(stufe = viewModel.stage)) },
            ) { padding ->
                val oeffne: (String) -> Unit = { navController.navigate(Routes.editor(it)) }

                Box(Modifier.fillMaxSize()) {
                    if (istArchiv) {
                        ArchivScreen(
                            viewModel = viewModel,
                            innerPadding = padding,
                            onOpenNote = oeffne,
                            onSuche = {
                                navController.navigate(Routes.suche(stufe = Stage.ARCHIVE))
                            },
                        )
                    } else {
                        StageScreen(viewModel, padding, oeffne)
                    }
                    TitelDialogHost(viewModel)
                    FarbUndTagHost(viewModel, shell) { navController.wechsleZu(Routes.TAGS, false) }

                    // Nur im Ordnermodus, wie das Zeichen in der Auswahlleiste.
                    // Ohne diese Bedingung entstuende auf jedem Stufenbildschirm
                    // ein ViewModel, das die Ordner mitliest, obwohl sie nirgends
                    // zu sehen sind.
                    if (ordnermodus) OrdnerWahlHost(viewModel)
                }
            }
        }

        composable(
            route = Routes.ORDNER,
            arguments = listOf(
                navArgument("ordner") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("hervorheben") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // DASSELBE StageViewModel wie im Fluss. Es merkt am fehlenden
            // Stufenargument, dass hier ein Ordner gemeint ist.
            val viewModel: StageViewModel = hiltViewModel()
            val ordnerViewModel: OrdnerViewModel = hiltViewModel()
            LaunchedEffect(viewModel) { viewModel.undoEvents.collect(zeigeUndo) }
            LaunchedEffect(ordnerViewModel) { ordnerViewModel.undoEvents.collect(zeigeUndo) }

            val weg by ordnerViewModel.weg.collectAsStateWithLifecycle()

            HomeGeruest(
                titel = weg.lastOrNull()?.name ?: "Ordner",
                aktuelleStufe = null,
                aktuelleRoute = Routes.ordner(ordnerViewModel.ordnerId),
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = viewModel,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Aus einem Ordner heraus gesucht: Der Ordner steht als
                // abwaehlbarer Filter vorausgewaehlt (14d). Der Hauptordner
                // hat keine Kennung und damit keine Vorwahl.
                onSuche = { navController.navigate(Routes.suche(ordnerId = ordnerViewModel.ordnerId)) },
                ordnerViewModel = ordnerViewModel,
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    OrdnerScreen(
                        stage = viewModel,
                        ordner = ordnerViewModel,
                        innerPadding = padding,
                        onOpenNote = { navController.navigate(Routes.editor(it)) },
                        // Ein Unterordner kommt OBEN AUF den Stapel, nicht an
                        // dessen Stelle. Nur so fuehrt Zurueck eine Ebene
                        // hoeher, statt aus der Ansicht heraus.
                        onOrdner = { id -> navController.navigate(Routes.ordner(id)) },
                    )
                    OrdnerDialogHost(ordnerViewModel, zeigeHinweis)
                    FarbUndTagHost(viewModel, shell) { navController.wechsleZu(Routes.TAGS, false) }
                    OrdnerWahlHost(viewModel, ordnerViewModel)
                }
            }
        }

        // DAS ARCHIV DES ORDNERMODUS (Phase 14b). Dieselbe Ansicht wie ein
        // Ordner, nur ueber dem Archivbaum. Das feste Argument `bereich` sagt
        // beiden ViewModels, welcher Baum gemeint ist; sonst wuesste es nur
        // das Routenmuster, und das liest niemand zur Laufzeit.
        composable(
            route = Routes.ARCHIV,
            arguments = listOf(
                navArgument("ordner") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("bereich") {
                    type = NavType.StringType
                    defaultValue = Bereich.ARCHIV.name
                },
                navArgument("hervorheben") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val viewModel: StageViewModel = hiltViewModel()
            val ordnerViewModel: OrdnerViewModel = hiltViewModel()
            LaunchedEffect(viewModel) { viewModel.undoEvents.collect(zeigeUndo) }
            LaunchedEffect(ordnerViewModel) { ordnerViewModel.undoEvents.collect(zeigeUndo) }

            val weg by ordnerViewModel.weg.collectAsStateWithLifecycle()

            HomeGeruest(
                titel = weg.lastOrNull()?.name ?: "Archiv",
                aktuelleStufe = null,
                aktuelleRoute = Routes.archiv(ordnerViewModel.ordnerId),
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = viewModel,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                onSuche = { navController.navigate(Routes.suche(ordnerId = ordnerViewModel.ordnerId)) },
                ordnerViewModel = ordnerViewModel,
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    OrdnerScreen(
                        stage = viewModel,
                        ordner = ordnerViewModel,
                        innerPadding = padding,
                        onOpenNote = { navController.navigate(Routes.editor(it)) },
                        onOrdner = { id -> navController.navigate(Routes.archiv(id)) },
                    )
                    OrdnerDialogHost(ordnerViewModel, zeigeHinweis)
                    FarbUndTagHost(viewModel, shell) { navController.wechsleZu(Routes.TAGS, false) }
                    OrdnerWahlHost(viewModel, ordnerViewModel)
                    ZurueckholenHost(viewModel)
                }
            }
        }

        composable(
            route = Routes.SUCHE,
            arguments = listOf(
                navArgument("stufe") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("tag") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("ordner") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { eintrag ->
            val viewModel: SucheViewModel = hiltViewModel()
            val stufe = eintrag.arguments?.getString("stufe")?.let { Stage.valueOf(it) }
            val tag = eintrag.arguments?.getString("tag")
            val ordnerVorwahl = eintrag.arguments?.getString("ordner")
            LaunchedEffect(Unit) { viewModel.voreinstellen(stufe, tag, ordnerVorwahl) }

            HomeGeruest(
                titel = "Suche",
                aktuelleStufe = null,
                aktuelleRoute = Routes.SUCHE,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Die Suche IST das Suchfeld. Eine Suchleiste in der Top Bar
                // waere daneben ein zweites, das nichts tut.
                zeigeSuchleiste = false,
            ) { padding ->
                SucheScreen(
                    viewModel = viewModel,
                    innerPadding = padding,
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    onZurueck = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = Routes.TRASH,
            // Ein festes Argument ohne Platzhalter in der Route: Es steht immer
            // auf true und sagt dem StageViewModel, dass es den Papierkorb
            // bedient. Der dritte Modus neben Stufe und Ordner.
            arguments = listOf(
                navArgument("papierkorb") {
                    type = NavType.BoolType
                    defaultValue = true
                },
            ),
        ) {
            val viewModel: TrashViewModel = hiltViewModel()
            val stageViewModel: StageViewModel = hiltViewModel()
            LaunchedEffect(viewModel) { viewModel.undoEvents.collect(zeigeUndo) }
            LaunchedEffect(stageViewModel) { stageViewModel.undoEvents.collect(zeigeUndo) }

            HomeGeruest(
                titel = "Papierkorb",
                aktuelleStufe = null,
                aktuelleRoute = Routes.TRASH,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Gesucht
                // wird ab Phase 14d ueber die Suche mit dem Filter "Papierkorb".
                zeigeSuchleiste = false,
            ) { padding ->
                TrashScreen(
                    viewModel = viewModel,
                    stageViewModel = stageViewModel,
                    innerPadding = padding,
                    // Der Sprung zu einer Notiz, die jetzt woanders liegt (14c):
                    // im Ordnermodus in ihren Ordner (oder Archivordner), im
                    // Fluss in ihre Stufe. Dort blitzt sie kurz auf.
                    onSprungZurNotiz = { notiz ->
                        val ziel = when {
                            !ordnermodus -> Routes.stage(notiz.stage, hervorheben = notiz.id)
                            notiz.ordnerArchiviertAt != null -> Routes.archiv(notiz.folderId, hervorheben = notiz.id)
                            else -> Routes.ordner(notiz.folderId, hervorheben = notiz.id)
                        }
                        navController.navigate(ziel)
                    },
                    onSprungZumOrdner = { ordner ->
                        val ziel = if (ordner.bereich == Bereich.ARCHIV) {
                            Routes.archiv(ordner.id)
                        } else {
                            Routes.ordner(ordner.id)
                        }
                        if (ordnermodus) navController.navigate(ziel) else zeigeHinweis(
                            "Der Ordner „" + ordner.name + "\" ist im Ordnermodus zu sehen. " +
                                "Schalte ihn in den Einstellungen unter Ordnung ein.",
                        )
                    },
                )
            }
        }

        composable(Routes.KALENDER) {
            HomeGeruest(
                titel = "Kalender",
                aktuelleStufe = null,
                aktuelleRoute = Routes.KALENDER,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                KalenderScreen(
                    innerPadding = padding,
                    onNotiz = { navController.navigate(Routes.editor(it)) },
                    zeigeHinweis = zeigeHinweis,
                )
            }
        }

        composable(Routes.TAGS) {
            HomeGeruest(
                titel = "Tags",
                aktuelleStufe = null,
                aktuelleRoute = Routes.TAGS,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                TagsScreen(innerPadding = padding, zeigeHinweis = zeigeHinweis)
            }
        }

        composable(Routes.PROTOKOLL) {
            HomeGeruest(
                titel = "Protokoll der Läufe",
                aktuelleStufe = null,
                aktuelleRoute = Routes.PROTOKOLL,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                ProtokollScreen(innerPadding = padding)
            }
        }

        composable(Routes.SYNC) {
            HomeGeruest(
                titel = "Synchronisierung",
                aktuelleStufe = null,
                aktuelleRoute = Routes.SYNC,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                SyncScreen(innerPadding = padding, zeigeHinweis = zeigeHinweis)
            }
        }

        composable(Routes.BACKUP) {
            HomeGeruest(
                titel = "Sichern und Wiederherstellen",
                aktuelleStufe = null,
                aktuelleRoute = Routes.BACKUP,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                BackupScreen(
                    innerPadding = padding,
                    zeigeHinweis = zeigeHinweis,
                    lieseSicherung = lieseSicherung,
                    onSicherungGelesen = onSicherungGelesen,
                )
            }
        }

        composable(
            route = Routes.SETTINGS,
            arguments = listOf(
                navArgument("hervorheben") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { eintrag ->
            HomeGeruest(
                titel = "Einstellungen",
                aktuelleStufe = null,
                aktuelleRoute = Routes.settings(),
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                // Kein Suchfeld in der Kopfzeile: Dieser Bildschirm hat keine
                // Suche, und ein Feld, das beim Antippen nichts tut, kostet
                // genau das Vertrauen, das eine Oberflaeche braucht. Der
                // Papierkorb sucht in seinem eigenen Feld weiter unten.
                zeigeSuchleiste = false,
            ) { padding ->
                SettingsScreen(
                    innerPadding = padding,
                    onSync = { navController.wechsleZu(Routes.SYNC, false) },
                    onBackup = { navController.wechsleZu(Routes.BACKUP, false) },
                    onProtokoll = { navController.wechsleZu(Routes.PROTOKOLL, false) },
                    // Ein Schritt tiefer, nicht daneben: Zurueck fuehrt in die Einstellungen.
                    onLizenzen = { navController.navigate(Routes.LIZENZEN) },
                    hervorheben = eintrag.arguments?.getString("hervorheben"),
                )
            }
        }

        composable(Routes.LIZENZEN) {
            HomeGeruest(
                titel = "Lizenzen",
                aktuelleStufe = null,
                aktuelleRoute = Routes.LIZENZEN,
                navController = navController,
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
                shell = shell,
                stageViewModel = null,
                ordnermodus = ordnermodus,
                zeigeHinweis = zeigeHinweis,
                zeigeSuchleiste = false,
            ) { padding ->
                LizenzenScreen(innerPadding = padding)
            }
        }

        composable(Routes.EDITOR) {
            val viewModel: EditorViewModel = hiltViewModel()
            EditorScreen(
                viewModel = viewModel,
                onClose = { navController.popBackStack() },
                onUndoRequest = zeigeUndo,
                // Vom ausgegrauten Transkript direkt zum KI-Schalter (Phase 15).
                onEinstellungen = { navController.navigate(Routes.settings(HERVORHEBEN_KI)) },
            )
        }
    }
}

/**
 * Der Titeldialog der Stufen-Ansicht.
 *
 * Liegt hier oben und nicht in StageScreen bzw. ArchivScreen, weil ihn beide
 * brauchen und weil er von zwei Seiten ausgeloest wird: von der Wischgeste auf
 * der Karte und von der Kontext-Top-Bar.
 */
/**
 * Farbauswahl und Tag-Zuweisung fuer die aktuelle Mehrfachauswahl.
 *
 * Liegt hier oben aus demselben Grund wie der Titeldialog: beide Stufen-Screens
 * brauchen ihn, und ausgeloest wird er aus der Kontext-Top-Bar.
 */
@Composable
private fun FarbUndTagHost(
    viewModel: StageViewModel,
    shell: AppShellViewModel,
    onNeuerTag: () -> Unit,
) {
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val farbBlatt by viewModel.farbBlattOffen.collectAsStateWithLifecycle()
    val tagDialog by viewModel.tagDialogOffen.collectAsStateWithLifecycle()
    val tags by shell.tags.collectAsStateWithLifecycle()
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()

    if (farbBlatt) {
        FarbAuswahlBlatt(
            // notizen wird mitgelesen, damit sich der Haken nach einer
            // Aenderung neu berechnet.
            aktuell = remember(notizen, auswahl) { viewModel.gemeinsameFarbe() },
            anzahl = auswahl.size,
            onWahl = viewModel::setFarbe,
            onSchliessen = viewModel::schliesseFarbAuswahl,
        )
    }

    if (tagDialog) {
        TagAuswahlDialog(
            tags = tags,
            zugewiesen = remember(notizen, auswahl) { viewModel.gemeinsameTags() },
            anzahl = auswahl.size,
            onUmschalten = viewModel::tagUmschalten,
            onNeuerTag = {
                viewModel.schliesseTagAuswahl()
                onNeuerTag()
            },
            onFertig = viewModel::schliesseTagAuswahl,
        )
    }
}

@Composable
private fun TitelDialogHost(viewModel: StageViewModel) {
    val anfrage by viewModel.titelAnfrage.collectAsStateWithLifecycle()
    val offen = anfrage ?: return

    TitelDialog(
        ueberschrift = "Titel für ${StageUi.label(offen.ziel)}",
        erklaerung = "Ab dem Workspace bekommt jede Notiz einen Titel. " +
            "Im Archiv wird gesucht, und gesucht wird über Titel. Ohne Titel " +
            "bleibt die Notiz, wo sie ist.",
        fallback = offen.fallback,
        quelle = offen.quelle,
        bestaetigenText = "Verschieben",
        onAbbrechen = viewModel::titelAbgebrochen,
        onBestaetigen = viewModel::titelBestaetigt,
    )
}

/**
 * Drawer, Top Bar, FAB und Snackbar -- alles, was ueber den Hauptscreens
 * gleich bleibt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeGeruest(
    titel: String,
    aktuelleStufe: Stage?,
    aktuelleRoute: String,
    navController: NavHostController,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    shell: AppShellViewModel,
    stageViewModel: StageViewModel?,
    ordnermodus: Boolean,
    zeigeHinweis: (String) -> Unit,
    zeigeSuchleiste: Boolean = true,
    onSuche: () -> Unit = {},
    /** Nur in der Ordneransicht: das Sortiermenue bekommt die Ordnerreihenfolge dazu (14e). */
    ordnerViewModel: OrdnerViewModel? = null,
    inhalt: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ordnersortierung by (ordnerViewModel?.sortierung ?: remember { MutableStateFlow(Ordnersortierung.EIGENE) })
        .collectAsStateWithLifecycle()
    val neuAnordnen by (ordnerViewModel?.neuAnordnen ?: remember { MutableStateFlow<List<FolderEntity>?>(null) })
        .collectAsStateWithLifecycle()
    val context = LocalContext.current
    val anzahlen by shell.anzahlen.collectAsStateWithLifecycle()
    val gesamt by shell.gesamt.collectAsStateWithLifecycle()
    val ordnerbaum by shell.ordnerbaum.collectAsStateWithLifecycle()
    val ordnerMitKindern by shell.ordnerMitKindern.collectAsStateWithLifecycle()
    val ordnerzahlen by shell.ordnerzahlen.collectAsStateWithLifecycle()
    val ordnerGesamt by shell.ordnerGesamt.collectAsStateWithLifecycle()
    val archivbaum by shell.archivbaum.collectAsStateWithLifecycle()
    val archivMitKindern by shell.archivMitKindern.collectAsStateWithLifecycle()
    val archivzahlen by shell.archivzahlen.collectAsStateWithLifecycle()
    val archivGesamt by shell.archivGesamt.collectAsStateWithLifecycle()
    val tags by shell.tags.collectAsStateWithLifecycle()
    val papierkorbVoll by shell.papierkorbVoll.collectAsStateWithLifecycle()
    var papierkorbLeerenFragen by remember { mutableStateOf(false) }
    val auswahl by (stageViewModel?.auswahl ?: remember { MutableStateFlow(emptySet<String>()) })
        .collectAsStateWithLifecycle()
    val darstellung by (stageViewModel?.darstellung ?: remember { MutableStateFlow(Darstellung.RASTER) })
        .collectAsStateWithLifecycle()
    val sortierung by (stageViewModel?.sort ?: remember { MutableStateFlow(NoteSort.CREATED) })
        .collectAsStateWithLifecycle()

    var fabOffen by remember { mutableStateOf(false) }

    /**
     * Welche Ordner in der Seitenspalte aufgeklappt sind.
     *
     * Liegt hier und nicht in der Spalte selbst: Sie wird bei jedem Schliessen
     * abgeraeumt, und ein Baum, der sich dabei wieder zuklappt, waere genau die
     * Mühe, die er ersparen soll. `rememberSaveable`, damit auch das Drehen des
     * Geraets ihn stehen laesst.
     */
    var aufgeklappt by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val gehZu: (String, Boolean) -> Unit = { ziel, istStart ->
        scope.launch { drawerState.close() }
        // Die Suche wird ueber ihr Muster verglichen, weil die gebaute Route
        // ihre Parameter traegt ("suche" gegen "suche?stufe=..."). Ohne das
        // wuerde ein zweites Antippen im Drawer die Suche neu aufsetzen und die
        // gerade gesetzten Filter wegwerfen.
        val schonDa = ziel == aktuelleRoute ||
            (ziel.startsWith("suche") && aktuelleRoute == Routes.SUCHE)
        if (!schonDa) navController.wechsleZu(ziel, istStart)
    }

    if (papierkorbLeerenFragen) {
        AlertDialog(
            onDismissRequest = { papierkorbLeerenFragen = false },
            title = { Text("Papierkorb leeren?") },
            text = {
                Text(
                    "Alle Notizen und Ordner im Papierkorb werden endgültig gelöscht. " +
                        "Das lässt sich nicht rückgängig machen.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        papierkorbLeerenFragen = false
                        scope.launch { drawerState.close() }
                        shell.papierkorbLeeren()
                    },
                ) { Text("Endgültig löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { papierkorbLeerenFragen = false }) { Text("Abbrechen") }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NotizenDrawer(
                aktuelleRoute = aktuelleRoute,
                anzahlen = anzahlen,
                gesamt = gesamt,
                ordnermodus = ordnermodus,
                ordnerbaum = remember(ordnerbaum, aufgeklappt) {
                    sichtbar(ordnerbaum, aufgeklappt)
                },
                ordnerMitKindern = ordnerMitKindern,
                ordnerzahlen = ordnerzahlen,
                ordnerGesamt = ordnerGesamt,
                archivbaum = remember(archivbaum, aufgeklappt) {
                    sichtbar(archivbaum, aufgeklappt)
                },
                archivMitKindern = archivMitKindern,
                archivzahlen = archivzahlen,
                archivGesamt = archivGesamt,
                aufgeklappt = aufgeklappt,
                tags = tags,
                onStufe = { gehZu(Routes.stage(it), it == Stage.INBOX) },
                onOrdner = { gehZu(Routes.ordner(), true) },
                onUnterordner = { id -> gehZu(Routes.ordner(id), false) },
                onArchiv = { gehZu(Routes.archiv(), false) },
                onArchivordner = { id -> gehZu(Routes.archiv(id), false) },
                onAufklappen = { id ->
                    aufgeklappt = if (id in aufgeklappt) aufgeklappt - id else aufgeklappt + id
                },
                onSuche = { gehZu(Routes.suche(), false) },
                onPapierkorb = { gehZu(Routes.TRASH, false) },
                papierkorbVoll = papierkorbVoll,
                onPapierkorbLeeren = { papierkorbLeerenFragen = true },
                onKalender = { gehZu(Routes.KALENDER, false) },
                onTags = { gehZu(Routes.TAGS, false) },
                onEinstellungen = { gehZu(Routes.settings(), false) },
                onTagFilter = { tag ->
                    scope.launch { drawerState.close() }
                    navController.navigate(Routes.suche(tagId = tag.id))
                },
                onModusWechseln = {
                    scope.launch { drawerState.close() }
                    shell.setOrdnermodus(!ordnermodus)
                },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (auswahl.isNotEmpty() && stageViewModel != null) {
                    AuswahlLeiste(
                        anzahl = auswahl.size,
                        stufe = aktuelleStufe,
                        zeigeOrdner = ordnermodus,
                        onSchliessen = stageViewModel::auswahlAufheben,
                        onLoeschen = { stageViewModel.inDenPapierkorb(auswahl) },
                        onVerschieben = { ziel -> stageViewModel.verschiebeNach(auswahl, ziel) },
                        onFarbe = stageViewModel::oeffneFarbAuswahl,
                        onTags = stageViewModel::oeffneTagAuswahl,
                        onOrdner = stageViewModel::oeffneOrdnerAuswahl,
                        onTeilen = {
                            teileNotizen(context, stageViewModel.gewaehlteNotizen())
                            stageViewModel.auswahlAufheben()
                        },
                        istFavorit = stageViewModel.auswahlIstFavorit(auswahl),
                        onFavorit = { stageViewModel.toggleFavorit(auswahl) },
                        // Das Archiv des Ordnermodus (14b): in einem Ordner
                        // archivieren, im Archiv zurueckholen. Im Fluss gibt es
                        // dafuer die Stufenpfeile.
                        onArchivieren = if (ordnermodus && stageViewModel.imOrdner && !stageViewModel.imArchiv) {
                            { stageViewModel.imOrdnerArchivieren(auswahl) }
                        } else {
                            null
                        },
                        onZurueckholen = if (stageViewModel.imArchiv) {
                            { stageViewModel.zurueckholenAnfragen(auswahl) }
                        } else {
                            null
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (zeigeSuchleiste) {
                                Suchleiste(
                                    platzhalter = "In $titel suchen",
                                    zeigeUmschalter = stageViewModel != null,
                                    darstellung = darstellung,
                                    sortierung = sortierung,
                                    onSuche = { onSuche() },
                                    onAnsicht = { stageViewModel?.toggleDarstellung() },
                                    onSortieren = { stageViewModel?.setSort(it) },
                                    ordnersortierung = ordnerViewModel?.let { ordnersortierung },
                                    onOrdnerSortieren = { ordnerViewModel?.setSortierung(it) },
                                )
                            } else {
                                Text(titel)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Menü")
                            }
                        },
                        actions = {
                            if (stageViewModel != null) {
                                Sicherungssymbol(
                                    onKlick = { navController.wechsleZu(Routes.SYNC, false) },
                                )
                            }

                            // Ohne Suchleiste haetten Umschalter und Sortierung
                            // sonst keinen Platz -- sie wandern nach rechts.
                            if (!zeigeSuchleiste && stageViewModel != null) {
                                AnsichtsKnoepfe(
                                    darstellung = darstellung,
                                    sortierung = sortierung,
                                    onAnsicht = stageViewModel::toggleDarstellung,
                                    onSortieren = stageViewModel::setSort,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
            floatingActionButton = {
                // Beim Neuordnen (14e) wird aus dem Plus ein Haken, der die
                // Reihenfolge speichert. Dieselbe Stelle, dieselbe Groesse:
                // Man weiss, wo der Daumen hin muss.
                if (neuAnordnen != null && ordnerViewModel != null) {
                    LargeFloatingActionButton(onClick = ordnerViewModel::neuAnordnenSpeichern) {
                        Icon(Icons.Outlined.Check, contentDescription = "Reihenfolge speichern")
                    }
                } else if (stageViewModel != null) {
                    NeueNotizFab(
                        offen = fabOffen,
                        onToggle = { fabOffen = !fabOffen },
                        onTyp = { typ ->
                            fabOffen = false
                            stageViewModel.neueNotiz(typ) { id ->
                                navController.navigate(Routes.editor(id))
                            }
                        },
                    )
                }
            },
            content = { padding ->
                Box(Modifier.fillMaxSize()) {
                    inhalt(padding)

                    // Der Schleier liegt IM Inhalt und damit unter dem
                    // FAB-Bereich: die kleinen Knoepfe bleiben bedienbar,
                    // alles andere faengt der Schleier ab. Vorher liess sich
                    // das offene Menue nur ueber den Knopf selbst schliessen,
                    // was sich wie eine Sackgasse anfuehlte.
                    AnimatedVisibility(
                        visible = fabOffen,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
                                )
                                .clickable(
                                    // Ohne Wellenschlag: ein Aufblitzen ueber
                                    // den ganzen Bildschirm saehe aus, als
                                    // haette man etwas ausgeloest.
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { fabOffen = false },
                                ),
                        )
                    }
                }
            },
        )
    }

    // Zurueck schliesst erst das Menue, bevor es den Screen verlaesst.
    BackHandler(enabled = fabOffen) { fabOffen = false }
}

/**
 * Suchleiste in der Top Bar. Bewusst ein Knopf und kein Textfeld: getippt wird
 * auf dem Such-Screen, wo auch die Filter sind. Ein zweites Feld hier waere
 * eine zweite Stelle fuer dieselbe Sache.
 */
@Composable
private fun Suchleiste(
    platzhalter: String,
    zeigeUmschalter: Boolean,
    darstellung: Darstellung,
    sortierung: NoteSort,
    ordnersortierung: Ordnersortierung? = null,
    onOrdnerSortieren: (Ordnersortierung) -> Unit = {},
    onSuche: () -> Unit,
    onAnsicht: () -> Unit,
    onSortieren: (NoteSort) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = platzhalter,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSuche)
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            )
            if (zeigeUmschalter) {
                AnsichtsKnoepfe(darstellung, sortierung, onAnsicht, onSortieren, ordnersortierung, onOrdnerSortieren)
            }
        }
    }
}

@Composable
private fun AnsichtsKnoepfe(
    darstellung: Darstellung,
    sortierung: NoteSort,
    onAnsicht: () -> Unit,
    onSortieren: (NoteSort) -> Unit,
    /** Nur in der Ordneransicht (14e): die Reihenfolge der Ordner, im selben Menue darunter. */
    ordnersortierung: Ordnersortierung? = null,
    onOrdnerSortieren: (Ordnersortierung) -> Unit = {},
) {
    IconButton(onClick = onAnsicht, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = if (darstellung == Darstellung.RASTER) {
                Icons.AutoMirrored.Outlined.List
            } else {
                Icons.Outlined.GridView
            },
            contentDescription = "Ansicht wechseln",
            modifier = Modifier.size(20.dp),
        )
    }
    Box {
        var offen by remember { mutableStateOf(false) }

        IconButton(onClick = { offen = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = "Sortieren",
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            if (ordnersortierung != null) {
                Text(
                    text = "Notizen",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            NoteSort.entries.forEach { wert ->
                DropdownMenuItem(
                    text = { Text(wert.beschriftung()) },
                    onClick = {
                        onSortieren(wert)
                        offen = false
                    },
                    trailingIcon = {
                        if (wert == sortierung) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
            // DIE ORDNER HABEN IHRE EIGENE REIHENFOLGE (14e), im selben Menue,
            // damit man sie dort findet, wo man sortiert. Die Wahl gilt fuer
            // alle Ordneransichten und bleibt gespeichert.
            if (ordnersortierung != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text = "Ordner",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                )
                Ordnersortierung.entries.forEach { wert ->
                    DropdownMenuItem(
                        text = { Text(wert.beschriftung) },
                        onClick = {
                            onOrdnerSortieren(wert)
                            offen = false
                        },
                        trailingIcon = {
                            if (wert == ordnersortierung) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Kontext-Top-Bar im Auswahlmodus.
 *
 * Hier sitzt der Stufenwechsel fuer die Mehrfachauswahl -- das Gegenstueck zur
 * Wischgeste, die immer nur eine einzelne Karte bewegt. Angeboten werden genau
 * die Nachbarstufen der aktuellen; am Anfang und am Ende des Flusses faellt die
 * jeweilige Richtung weg, statt als toter Knopf dazustehen.
 *
 * **KEIN Text in dieser Leiste, nur Zeichen.** Material gibt dem Titel den
 * Platz, der nach den Zeichen uebrig bleibt -- und der war hier keiner mehr:
 * „3 ausgewaehlt" stand am Geraet mit einem Buchstaben je Zeile untereinander.
 * Seit dem 2026-08-25 so. Wie viele Karten gewaehlt sind, sieht
 * man ohnehin an ihren Rahmen; fuer die Sprachausgabe steht die Zahl an der
 * Beschreibung des Schliessen-Knopfes.
 *
 * **Jedes Zeichen hier kostet Breite.** Auf einem gewoehnlichen Telefon ist
 * nach sieben Schaltflaechen Schluss. Deshalb erscheint das Ordnerzeichen nur
 * im Ordnermodus -- im Fluss waere es das achte und zugleich eines, das dort
 * gar nichts zu suchen hat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuswahlLeiste(
    anzahl: Int,
    stufe: Stage?,
    zeigeOrdner: Boolean,
    onSchliessen: () -> Unit,
    onLoeschen: () -> Unit,
    onVerschieben: (Stage) -> Unit,
    onFarbe: () -> Unit,
    onTags: () -> Unit,
    onOrdner: () -> Unit,
    onTeilen: () -> Unit,
    istFavorit: Boolean,
    onFavorit: () -> Unit,
    onArchivieren: (() -> Unit)? = null,
    onZurueckholen: (() -> Unit)? = null,
) {
    TopAppBar(
        // Leer, siehe oben. Kein Platzhalter, kein Abstandhalter: Was hier
        // steht, nimmt den Zeichen daneben die Breite weg.
        title = {},
        navigationIcon = {
            IconButton(onClick = onSchliessen) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    // Die Zahl lebt hier weiter. Sie steht nicht mehr auf dem
                    // Bildschirm, aber wer sich vorlesen laesst, was unter dem
                    // Finger liegt, soll sie trotzdem hoeren.
                    contentDescription = if (anzahl == 1) {
                        "Auswahl aufheben, eine Notiz gewählt"
                    } else {
                        "Auswahl aufheben, $anzahl Notizen gewählt"
                    },
                )
            }
        },
        actions = {
            // ALLE anderen Stufen, nicht nur die Nachbarn (Phase 15): Mit
            // Titel darf direkt gesprungen werden, auch Eingang → Archiv. Es
            // sind immer zwei Zeichen, die Leiste bleibt bei sieben.
            val andereStufen = if (stufe == null) emptyList() else Stage.entries.filter { it != stufe }
            andereStufen.forEach { ziel ->
                IconButton(onClick = { onVerschieben(ziel) }) {
                    Icon(
                        imageVector = StageUi.icon(ziel),
                        contentDescription = "Nach ${StageUi.label(ziel)} verschieben",
                    )
                }
            }
            // Im Ordnermodus an der Stelle der Stufenpfeile: dieselbe Position,
            // dieselbe Bedeutung, ein anderer Baum. Zusammen mit den sechs
            // Zeichen darunter sind es sieben, die Grenze der Leiste.
            if (onArchivieren != null) {
                IconButton(onClick = onArchivieren) {
                    Icon(Icons.Outlined.Archive, contentDescription = "Archivieren")
                }
            }
            if (onZurueckholen != null) {
                IconButton(onClick = onZurueckholen) {
                    Icon(Icons.Outlined.Unarchive, contentDescription = "Aus dem Archiv zurückholen")
                }
            }
            IconButton(onClick = onFavorit) {
                Icon(
                    imageVector = if (istFavorit) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (istFavorit) {
                        "Favorit aufheben"
                    } else {
                        "Als Favorit merken"
                    },
                )
            }
            IconButton(onClick = onTeilen) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Teilen")
            }
            IconButton(onClick = onTags) {
                Icon(Icons.Outlined.Sell, contentDescription = "Tags zuweisen")
            }
            // NUR im Ordnermodus. Im Fluss gibt es keine Ordner zu sehen, und
            // ein Knopf, der Notizen in eine Ordnung einsortiert, die gerade
            // niemand anzeigt, waere eine Wirkung ohne sichtbares Ergebnis.
            if (zeigeOrdner) {
                IconButton(onClick = onOrdner) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                        contentDescription = "In einen Ordner legen",
                    )
                }
            }
            // Palettensymbol direkt neben dem Tag-Symbol
            // (Spezifikation Abschnitt 5a).
            IconButton(onClick = onFarbe) {
                Icon(Icons.Outlined.Palette, contentDescription = "Einfärben")
            }
            IconButton(onClick = onLoeschen) {
                Icon(Icons.Outlined.Delete, contentDescription = "In den Papierkorb")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/**
 * FAB, der zu den Notiztypen aufklappt.
 *
 * LargeFloatingActionButton statt des normalen: die Trefferflaeche liegt damit
 * bei 96 dp statt 56 dp und ist auch mit dem Daumen sicher zu treffen.
 */
@Composable
private fun NeueNotizFab(
    offen: Boolean,
    onToggle: () -> Unit,
    onTyp: (NoteType) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = offen) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 20.dp),
            ) {
                KleinerFab("Bild", Icons.Outlined.Image) { onTyp(NoteType.IMAGE) }
                KleinerFab("Audio", Icons.Outlined.Mic) { onTyp(NoteType.AUDIO) }
                KleinerFab("Liste", Icons.Outlined.Checklist) { onTyp(NoteType.LIST) }
                KleinerFab("Textnotiz", Icons.AutoMirrored.Outlined.Notes) { onTyp(NoteType.TEXT) }
            }
        }

        LargeFloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = if (offen) Icons.Outlined.Close else Icons.Outlined.Add,
                contentDescription = if (offen) "Schliessen" else "Neue Notiz",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun KleinerFab(beschriftung: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = beschriftung,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(icon, contentDescription = beschriftung)
        }
    }
}

/** Bewusst kurz gehalten -- kein "Alle Notizen" (Spezifikation Abschnitt 14). */
@Composable
private fun NotizenDrawer(
    aktuelleRoute: String,
    anzahlen: Map<Stage, Int>,
    gesamt: Int,
    ordnermodus: Boolean,
    /** Nur die gerade sichtbaren Zeilen, schon durch `sichtbar` gefiltert. */
    ordnerbaum: List<Ordnerzeile>,
    ordnerMitKindern: Set<String>,
    ordnerzahlen: Map<String, Int>,
    ordnerGesamt: Int,
    /** Der Archivbaum des Ordnermodus, ebenfalls schon gefiltert (Phase 14b). */
    archivbaum: List<Ordnerzeile>,
    archivMitKindern: Set<String>,
    archivzahlen: Map<String, Int>,
    archivGesamt: Int,
    aufgeklappt: Set<String>,
    tags: List<TagEntity>,
    onStufe: (Stage) -> Unit,
    onOrdner: () -> Unit,
    onUnterordner: (String) -> Unit,
    onArchiv: () -> Unit,
    onArchivordner: (String) -> Unit,
    onAufklappen: (String) -> Unit,
    onSuche: () -> Unit,
    onPapierkorb: () -> Unit,
    papierkorbVoll: Boolean,
    onPapierkorbLeeren: () -> Unit,
    onKalender: () -> Unit,
    onTags: () -> Unit,
    onEinstellungen: () -> Unit,
    onTagFilter: (TagEntity) -> Unit,
    onModusWechseln: () -> Unit,
) {
    // DIE SPALTE SCROLLT, UND ZWAR ALS LAZYCOLUMN.
    //
    // Bis zum 2026-09-14 war sie ein starrer Stapel. Ab etwa zwoelf Ordnern im
    // Baum schoben die Ordner "Papierkorb" und "Einstellungen" unter den
    // Bildschirmrand, und ohne Ordner zu loeschen kam man nicht mehr hin. Vom
    // Nutzer beim Anlegen vieler Ordner gefunden.
    //
    // LazyColumn und nicht `verticalScroll`: Der Baum kann lang werden, und die
    // Konvention fuer Listen gilt auch hier. Die festen Eintraege sind einzelne
    // `item`-Bloecke, die Ordner und Tags echte `items`.
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "kopf") {
                Text(
                    text = "Notizen",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
                )
            }

            // IM ORDNERMODUS GIBT ES DIE STUFEN NICHT.
            //
            // Sie stehen weiterhin an jeder Notiz und werden weiter gefuehrt, aber
            // sie kommen hier nicht mehr vor. Ein Fluss, den man sieht und nicht
            // benutzt, waere schlechter als keiner: Man raetselte, warum eine
            // Notiz in zwei Ordnungen gleichzeitig liegt.
            if (ordnermodus) {
                item(key = "ordner") {
                    NavigationDrawerItem(
                        label = { Text("Ordner") },
                        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        // Ohne das Archiv: Das hat seinen eigenen Eintrag und
                        // seine eigene Zahl darunter.
                        badge = { if (ordnerGesamt > 0) Badge { Text("$ordnerGesamt") } },
                        // Der Hauptordner, nicht der gerade offene. Ein Eintrag, der je
                        // nach Standort woandershin fuehrt, waere kein Eintrag mehr,
                        // sondern eine Ueberraschung.
                        selected = aktuelleRoute == Routes.ordner(),
                        onClick = onOrdner,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }

                // DER BAUM STEHT HIER, DAMIT MAN SICH NICHT DURCH IHN TIPPEN MUSS.
                //
                // Ohne ihn fuehrt der Weg zu einem tief liegenden Ordner ueber
                // jede Zwischenstufe, und jede davon laedt ihre Notizen. Hier
                // klappt man auf, tippt das Ziel an und ist dort. Seit dem
                // 2026-08-25, genau aus diesem Grund.
                items(ordnerbaum, key = { "ordner:" + it.ordner.id }) { zeile ->
                    Ordnerzweig(
                        zeile = zeile,
                        anzahl = ordnerzahlen[zeile.ordner.id] ?: 0,
                        hatKinder = zeile.ordner.id in ordnerMitKindern,
                        offen = zeile.ordner.id in aufgeklappt,
                        gewaehlt = aktuelleRoute == Routes.ordner(zeile.ordner.id),
                        onOeffnen = { onUnterordner(zeile.ordner.id) },
                        onAufklappen = { onAufklappen(zeile.ordner.id) },
                    )
                }

                // DAS ARCHIV DES ORDNERMODUS steht unter dem Ordnerbaum, mit
                // eigenem Baum darunter (Phase 14b). Ein eigener Bereich, nicht
                // ein Ordner unter den Ordnern: Was hier liegt, ist aus der
                // Arbeit heraus und soll die Arbeitsordner nicht verstopfen.
                item(key = "archiv") {
                    NavigationDrawerItem(
                        label = { Text("Archiv") },
                        icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                        badge = { if (archivGesamt > 0) Badge { Text("$archivGesamt") } },
                        selected = aktuelleRoute == Routes.archiv(),
                        onClick = onArchiv,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                items(archivbaum, key = { "archiv:" + it.ordner.id }) { zeile ->
                    Ordnerzweig(
                        zeile = zeile,
                        anzahl = archivzahlen[zeile.ordner.id] ?: 0,
                        hatKinder = zeile.ordner.id in archivMitKindern,
                        offen = zeile.ordner.id in aufgeklappt,
                        gewaehlt = aktuelleRoute == Routes.archiv(zeile.ordner.id),
                        onOeffnen = { onArchivordner(zeile.ordner.id) },
                        onAufklappen = { onAufklappen(zeile.ordner.id) },
                    )
                }
            } else {
                items(Stage.entries, key = { "stufe:" + it.name }) { stufe ->
                    NavigationDrawerItem(
                        label = { Text(StageUi.label(stufe)) },
                        icon = { Icon(StageUi.icon(stufe), contentDescription = null) },
                        badge = {
                            val anzahl = anzahlen[stufe] ?: 0
                            if (anzahl > 0) Badge { Text("$anzahl") }
                        },
                        selected = aktuelleRoute == Routes.stage(stufe),
                        onClick = { onStufe(stufe) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }

            // Die Suche ist zugleich die Ansicht "alle Notizen": ohne Suchtext und
            // ohne Filter steht dort alles, ueber alle drei Stufen hinweg. Deshalb
            // traegt sie die Gesamtzahl als Zeichen, statt einen zweiten Eintrag zu
            // bekommen, der dasselbe zeigt.
            //
            // Sie steht UNTER den Stufen und ist nicht das Startziel: der Fluss
            // bleibt der Hauptweg, die Suche ist der Ausweg fuer den Fall, dass man
            // nicht mehr weiss, wo etwas liegt.
            item(key = "suche") {
                NavigationDrawerItem(
                    label = { Text("Suche") },
                    icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    badge = { if (gesamt > 0) Badge { Text("$gesamt") } },
                    selected = aktuelleRoute == Routes.SUCHE,
                    onClick = onSuche,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            // Der Kalender gehoert in dieses Abteil und nicht zu den Tags, wo er
            // zuerst stand. Hier oben stehen die ANSICHTEN auf denselben Bestand:
            // die drei Stufen, die Suche ueber alles, und der Kalender als Blick
            // auf das, was einen Termin hat. Unter "Tags verwalten" sah er aus, als
            // gehoerte er zur Tagverwaltung.
            //
            // Kein Zeichen mit einer Zahl daneben, anders als bei den Stufen und
            // der Suche. Die zaehlen Notizen; hier waere die Zahl "Notizen mit
            // Erinnerung", und die haette neben den anderen dieselbe Form bei
            // anderer Bedeutung.
            item(key = "kalender") {
                NavigationDrawerItem(
                    label = { Text("Kalender") },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    selected = aktuelleRoute == Routes.KALENDER,
                    onClick = onKalender,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            item(key = "trenner1") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }

            if (tags.isEmpty()) {
                item(key = "keine-tags") {
                    Text(
                        text = "Noch keine Tags",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                    )
                }
            } else {
                items(tags, key = { "tag:" + it.id }) { tag ->
                    NavigationDrawerItem(
                        label = { Text(tag.name) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.colorArgb)),
                            )
                        },
                        selected = false,
                        onClick = { onTagFilter(tag) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }

            item(key = "tags-verwalten") {
                NavigationDrawerItem(
                    label = { Text("Tags verwalten") },
                    icon = { Icon(Icons.Outlined.Sell, contentDescription = null) },
                    selected = aktuelleRoute == Routes.TAGS,
                    onClick = onTags,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            item(key = "trenner2") {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }

            // DER WECHSEL ZWISCHEN DEN BEIDEN ORDNUNGEN (14e), unten bei den
            // festen Eintraegen. Tut dasselbe wie der Schalter in den
            // Einstellungen; der bleibt, denn dort steht auch die Erklaerung.
            item(key = "moduswechsel") {
                NavigationDrawerItem(
                    label = { Text(if (ordnermodus) "Zu den Stufen" else "Zu den Ordnern") },
                    icon = {
                        Icon(
                            imageVector = if (ordnermodus) Icons.Outlined.Inbox else Icons.Outlined.Folder,
                            contentDescription = null,
                        )
                    },
                    selected = false,
                    onClick = onModusWechseln,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            item(key = "papierkorb") {
                Papierkorbeintrag(
                    selected = aktuelleRoute == Routes.TRASH,
                    leerenMoeglich = papierkorbVoll,
                    onClick = onPapierkorb,
                    onLeeren = onPapierkorbLeeren,
                )
            }
            item(key = "einstellungen") {
                NavigationDrawerItem(
                    label = { Text("Einstellungen") },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    selected = aktuelleRoute == Routes.settings(),
                    onClick = onEinstellungen,
                    modifier = Modifier
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Der Eintrag "Papierkorb" in der Seitenspalte, mit langem Druecken.
 *
 * **Selbst gebaut, weil `NavigationDrawerItem` kein langes Druecken kennt.**
 * Es sieht aus wie die anderen Eintraege, denn Form, Hoehe und Farben kommen
 * aus `NavigationDrawerItemDefaults`; nur die Geste ist dazu. Langes Druecken
 * oeffnet ein kleines Menue mit "Papierkorb leeren", und das leert nach einer
 * Rueckfrage alles auf einmal. Seit dem 2026-09-14, weil das Leeren
 * sonst Ordner fuer Ordner ging.
 */
@Composable
private fun Papierkorbeintrag(
    selected: Boolean,
    leerenMoeglich: Boolean,
    onClick: () -> Unit,
    onLeeren: () -> Unit,
) {
    var menue by remember { mutableStateOf(false) }
    val farben = NavigationDrawerItemDefaults.colors()
    val hintergrund by farben.containerColor(selected)
    val symbolfarbe by farben.iconColor(selected)
    val textfarbe by farben.textColor(selected)

    Box(modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)) {
        Surface(
            color = hintergrund,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .combinedClickable(onClick = onClick, onLongClick = { menue = true })
                    .padding(start = 16.dp, end = 24.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = symbolfarbe)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Papierkorb",
                    style = MaterialTheme.typography.labelLarge,
                    color = textfarbe,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
            DropdownMenuItem(
                text = { Text("Papierkorb leeren") },
                leadingIcon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
                enabled = leerenMoeglich,
                onClick = {
                    menue = false
                    onLeeren()
                },
            )
        }
    }
}

/**
 * Ein Ordner im Baum der Seitenspalte.
 *
 * **Zwei Trefferflaechen, und das ist der ganze Sinn.** Der Pfeil klappt auf
 * und zu, der Rest der Zeile oeffnet den Ordner. Waere es nur eine, muesste man
 * sich zwischen Durchsehen und Hingehen entscheiden, bevor man tippt.
 *
 * Der Pfeil steht nur an Ordnern, die Kinder haben. An einem leeren waere er
 * ein Versprechen auf etwas, das nicht kommt. Wo keiner steht, bleibt sein
 * Platz trotzdem frei, sonst saessen die Namen einer Ebene nicht untereinander.
 */
@Composable
private fun Ordnerzweig(
    zeile: Ordnerzeile,
    anzahl: Int,
    hatKinder: Boolean,
    offen: Boolean,
    gewaehlt: Boolean,
    onOeffnen: () -> Unit,
    onAufklappen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp + ORDNER_EINZUG * minOf(zeile.tiefe, ORDNER_TIEFE_MAX),
                end = 12.dp,
                top = 1.dp,
                bottom = 1.dp,
            ),
    ) {
        if (hatKinder) {
            IconButton(onClick = onAufklappen, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (offen) {
                        Icons.Outlined.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                    },
                    contentDescription = if (offen) {
                        "${zeile.ordner.name} zuklappen"
                    } else {
                        "${zeile.ordner.name} aufklappen"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }

        // DIE GEWAEHLTE ZEILE TRAEGT DIE ORDNERFARBE (14e): die Blase als
        // halbdurchsichtige Flaeche der Farbe, die Schrift in der Farbe selbst,
        // aber nur so hell oder dunkel, dass sie auf der Blase lesbar bleibt.
        // Gerechnet, nicht gehofft: Gelb auf Hellblau waere sonst unlesbar.
        val ordnerfarbe = zeile.ordner.colorArgb
        val spalte = MaterialTheme.colorScheme.surfaceContainer.toArgb()
        val farben = if (ordnerfarbe != null) {
            val blase = ueberblendet(ordnerfarbe, spalte, BLASEN_DECKUNG)
            val schrift = Color(lesbareFarbe(ordnerfarbe, blase))
            NavigationDrawerItemDefaults.colors(
                selectedContainerColor = Color(blase),
                selectedTextColor = schrift,
                selectedIconColor = Color(ordnerfarbe),
                selectedBadgeColor = schrift,
                unselectedIconColor = Color(ordnerfarbe),
            )
        } else {
            NavigationDrawerItemDefaults.colors()
        }

        NavigationDrawerItem(
            label = { Text(zeile.ordner.name, maxLines = 1) },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            badge = { if (anzahl > 0) Badge { Text("$anzahl") } },
            selected = gewaehlt,
            onClick = onOeffnen,
            colors = farben,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Wie weit ein Ordner je Ebene in der Seitenspalte einrueckt. */
private val ORDNER_EINZUG = 12.dp

/**
 * Ab welcher Tiefe nicht weiter eingerueckt wird.
 *
 * Die Spalte ist schmal. Ohne Grenze bliebe in einem tiefen Baum fuer den Namen
 * kein Platz mehr, und ausgerechnet die innersten Ordner waeren nicht mehr zu
 * lesen. Dieselbe Ueberlegung wie in der Ordnerauswahl, nur enger.
 */
private const val ORDNER_TIEFE_MAX = 4

/**
 * Sagt in der Kopfzeile, ob alles gesichert ist.
 *
 * **Ein Symbol, kein Text.** Die Kopfzeile trägt Titel und Suchfeld; eine
 * Zeile „alle Notizen gesichert" wäre dort ständig im Weg für eine Auskunft,
 * die meistens „ja" lautet. Antippen sagt es in Worten und führt weiter.
 *
 * **Der graue Haken ist Absicht.** Grün wäre eine Belohnung für den
 * Normalzustand — und dann fiele der Ausnahmezustand weniger auf, nicht mehr.
 * Auffällig ist hier nur, was ansteht.
 */
@Composable
private fun Sicherungssymbol(
    onKlick: () -> Unit,
    viewModel: SicherungViewModel = hiltViewModel(),
) {
    val stand by viewModel.state.collectAsStateWithLifecycle()

    IconButton(onClick = onKlick) {
        Icon(
            imageVector = when (stand.stand) {
                Sicherung.AUS -> Icons.Outlined.CloudOff
                Sicherung.OFFEN -> Icons.Outlined.CloudUpload
                Sicherung.GESICHERT -> Icons.Outlined.CloudDone
            },
            contentDescription = stand.text,
            tint = when (stand.stand) {
                Sicherung.OFFEN -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
