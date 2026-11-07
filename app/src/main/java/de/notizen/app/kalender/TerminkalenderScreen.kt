package de.notizen.app.kalender

import de.notizen.core.data.kalender.serie
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.UndoRequest
import de.notizen.app.ui.theme.NotizenScaffold
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** So viele Seiten hat das Blaettern nach jeder Seite; die Mitte ist der Anker. */
private const val SEITEN = 2001
private const val MITTE = 1000

/**
 * Der Kalender der App: eigene Seitenspalte mit
 * den Ansichten und den Kalendern zum Ein- und Ausblenden, oben der Monat
 * mit aufklappbarem Mini-Monat, Suche, Aufgaben und Heute, unten rechts das
 * Plus fuer Termin, Aufgabe und Geburtstag.
 *
 * Darueber liegen in dieser Reihenfolge: die Aufgabenliste, das Blatt des
 * Editors und die Detailansicht eines Eintrags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminkalenderScreen(
    snackbarHostState: SnackbarHostState,
    onNotiz: (String) -> Unit,
    onEinstellungen: () -> Unit,
    onZuDenNotizen: () -> Unit,
    zeigeUndo: (UndoRequest) -> Unit,
    zeigeHinweis: (String) -> Unit,
    viewModel: TerminkalenderViewModel = hiltViewModel(),
    editor: TerminEditorViewModel = hiltViewModel(),
) {
    val modusOderNull by viewModel.modus.collectAsStateWithLifecycle()
    val modus = modusOderNull ?: return
    val anker by viewModel.anker.collectAsStateWithLifecycle()
    val kalender by viewModel.kalender.collectAsStateWithLifecycle()
    val termine by viewModel.termine.collectAsStateWithLifecycle()
    val ausgeblendet by viewModel.ausgeblendet.collectAsStateWithLifecycle()
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val terminDauer by viewModel.terminDauer.collectAsStateWithLifecycle()
    val kalenderwochen by viewModel.kalenderwochen.collectAsStateWithLifecycle()
    val wochenendeAus by viewModel.wochenendeAus.collectAsStateWithLifecycle()
    val tippenLegtAn by viewModel.tippenLegtAn.collectAsStateWithLifecycle()
    val notizenImKalender by viewModel.notizenImKalender.collectAsStateWithLifecycle()
    val entwurf by editor.entwurf.collectAsStateWithLifecycle()
    val zone = viewModel.zone
    val heute = remember { LocalDate.now() }
    val dichte = LocalDensity.current
    val scope = rememberCoroutineScope()
    val schublade = rememberDrawerState(DrawerValue.Closed)
    val rasterScroll = rememberScrollState()

    var miniMonat by rememberSaveable { mutableStateOf(false) }
    var plusOffen by rememberSaveable { mutableStateOf(false) }
    var suche by rememberSaveable { mutableStateOf<String?>(null) }
    var aufgabenOffen by rememberSaveable { mutableStateOf(false) }
    var gewaehlt by remember { mutableStateOf<Pair<Eintrag, Rect?>?>(null) }
    var neuerKalender by rememberSaveable { mutableStateOf(false) }
    var blattOffen by rememberSaveable { mutableStateOf(false) }
    var blatt by rememberSaveable { mutableStateOf(Blattstufe.ZU) }

    // Der Tag, auf dem das Blaettern aufsetzt. Ein Sprung (Heute, Mini-Monat,
    // andere Ansicht) setzt ihn neu und damit den Pager zurueck auf die Mitte.
    var basisTag by remember { mutableStateOf(anker) }
    val springen: (LocalDate) -> Unit = { tag ->
        viewModel.setAnker(tag)
        basisTag = tag
    }
    val ansicht: (Kalendermodus, LocalDate) -> Unit = { neu, tag ->
        viewModel.setAnker(tag)
        basisTag = tag
        viewModel.setModus(neu)
    }

    val alle: (LocalDate, LocalDate) -> List<Eintrag> = { von, bis ->
        eintraege(termine, kalender, ausgeblendet, notizen, von, bis, zone)
    }
    val farbeVon: (String) -> Int = { id -> kalender.firstOrNull { it.id == id }?.colorArgb ?: FARBE_STANDARD }

    fun neu(art: Terminart, tag: LocalDate, minute: Int?, stufe: Blattstufe, ohneDatum: Boolean = false) {
        plusOffen = false
        editor.oeffnen(null, art, tag, minute, ohneDatum)
        blattOffen = true
        blatt = stufe
    }

    fun bearbeiten(id: String) {
        gewaehlt = null
        editor.oeffnen(id)
        blattOffen = true
        blatt = Blattstufe.VOLL
    }

    // Halb offen liegt das Blatt ueber der unteren Haelfte; der neue Block soll darueber stehen.
    fun zeigeMinute(minute: Int) {
        val y = with(dichte) { (minute / 60f * STUNDE.toPx() - 96.dp.toPx()).toInt() }
        scope.launch { rasterScroll.animateScrollTo(y.coerceAtLeast(0)) }
    }

    val entwurfblock = entwurf?.takeIf { blattOffen && blatt != Blattstufe.ZU && it.datiert && !it.ganztaegig }?.let { e ->
        Entwurfblock(
            beginn = e.beginn,
            ende = if (e.mitEnde && e.ende.isAfter(e.beginn)) e.ende else e.beginn.plusMinutes(30),
            farbe = e.farbe ?: farbeVon(e.kalenderId),
            titel = e.titel,
        )
    }
    val bearbeitet = if (blattOffen) editor.terminId else null
    val verborgen: (Eintrag) -> Boolean = { e ->
        e.schluessel == gewaehlt?.first?.schluessel ||
            (bearbeitet != null && (e.herkunft as? Herkunft.Termin)?.vorkommen?.termin?.id == bearbeitet)
    }

    BackHandler(enabled = suche != null || plusOffen || miniMonat) {
        when {
            plusOffen -> plusOffen = false
            suche != null -> suche = null
            else -> miniMonat = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = schublade,
            drawerContent = {
                Kalenderspalte(
                    modus = modus,
                    kalender = kalender,
                    ausgeblendet = ausgeblendet,
                    notizenImKalender = notizenImKalender,
                    onModus = {
                        ansicht(it, anker)
                        scope.launch { schublade.close() }
                    },
                    onSichtbar = viewModel::sichtbar,
                    onAktualisieren = {
                        viewModel.aktualisieren()
                        zeigeHinweis("Der Abgleich läuft.")
                        scope.launch { schublade.close() }
                    },
                    onNeuerKalender = {
                        neuerKalender = true
                        scope.launch { schublade.close() }
                    },
                    onEinstellungen = {
                        scope.launch { schublade.close() }
                        onEinstellungen()
                    },
                    onZuDenNotizen = {
                        scope.launch { schublade.close() }
                        onZuDenNotizen()
                    },
                )
            },
        ) {
            NotizenScaffold(
                topBar = {
                    AnimatedContent(
                        targetState = suche != null,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "kopf",
                    ) { suchen ->
                        if (suchen) {
                            Suchleiste(suche.orEmpty(), { suche = it }, { suche = null })
                        } else {
                            Kopfleiste(
                                monat = YearMonth.from(anker),
                                miniMonat = miniMonat,
                                heute = heute,
                                onMenue = { scope.launch { schublade.open() } },
                                onTitel = { miniMonat = !miniMonat },
                                onSuche = { suche = "" },
                                onAufgaben = { aufgabenOffen = true },
                                onHeute = { springen(LocalDate.now()) },
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    val s = suche
                    if (s != null) {
                        Suchergebnisse(
                            suche = s,
                            termine = termine,
                            kalender = kalender,
                            onTermin = { t, lage ->
                                gewaehlt = eintragFuer(t, farbeVon(t.calendarId), heute, zone) to lage
                            },
                        )
                        return@Column
                    }
                    AnimatedVisibility(
                        visible = miniMonat,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        val punkte = remember(termine, kalender, ausgeblendet, notizen, anker) {
                            val m = YearMonth.from(anker)
                            alle(rasterstart(m.minusMonths(1)), rasterstart(m.plusMonths(2)))
                                .flatMap { e -> generateSequence(e.ersterTag) { it.plusDays(1) }.takeWhile { !it.isAfter(e.letzterTag) }.take(40).toList() }
                                .toHashSet()
                        }
                        MiniMonat(
                            gewaehlt = anker,
                            heute = heute,
                            mitEintrag = { it in punkte },
                            onTag = {
                                springen(it)
                                miniMonat = false
                            },
                        )
                    }
                    AnimatedContent(
                        targetState = modus,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.96f)) togetherWith
                                fadeOut(tween(110))
                        },
                        label = "ansicht",
                    ) { m ->
                        when (m) {
                            Kalendermodus.UEBERSICHT -> {
                                val von = remember(anker) { anker.minusDays(60) }
                                val bis = remember(anker) { anker.plusDays(400) }
                                val zeilen = remember(termine, kalender, ausgeblendet, notizen, von) {
                                    uebersichtszeilen(alle(von, bis), von, bis, heute)
                                }
                                Terminuebersicht(
                                    zeilen = zeilen,
                                    anker = anker,
                                    heute = heute,
                                    zone = zone,
                                    onTag = { ansicht(Kalendermodus.TAG, it) },
                                    onEintrag = { e, lage -> gewaehlt = e to lage },
                                )
                            }
                            else -> {
                                val basis = remember(basisTag, m) { seitenstart(m, basisTag) }
                                key(m, basis) {
                                    val pager = rememberPagerState(initialPage = MITTE) { SEITEN }
                                    LaunchedEffect(pager) {
                                        snapshotFlow { pager.settledPage }.collect { seite ->
                                            val neu = blaettern(m, basis, seite - MITTE)
                                            if (m == Kalendermodus.MONAT) {
                                                // Im Monat bleibt der gewaehlte Tag, wenn er im Monat liegt.
                                                if (YearMonth.from(viewModel.anker.value) != YearMonth.from(neu)) viewModel.setAnker(neu)
                                            } else {
                                                viewModel.setAnker(neu)
                                            }
                                        }
                                    }
                                    HorizontalPager(state = pager, beyondViewportPageCount = 1) { seite ->
                                        val start = blaettern(m, basis, seite - MITTE)
                                        if (m == Kalendermodus.MONAT) {
                                            val monat = YearMonth.from(start)
                                            val liste = remember(termine, kalender, ausgeblendet, notizen, monat) {
                                                alle(rasterstart(monat), rasterstart(monat).plusDays(RASTERTAGE.toLong()))
                                            }
                                            Monatsseite(monat, liste, heute, kalenderwochen) { ansicht(Kalendermodus.TAG, it) }
                                        } else {
                                            val ohneWochenende = m == Kalendermodus.WOCHE && wochenendeAus
                                            val tage = remember(start, m, ohneWochenende) {
                                                (0 until m.tage).map { start.plusDays(it.toLong()) }
                                                    .filter { !ohneWochenende || it.dayOfWeek.value <= 5 }
                                            }
                                            val liste = remember(termine, kalender, ausgeblendet, notizen, start, m, ohneWochenende) {
                                                alle(tage.first(), tage.last().plusDays(1))
                                            }
                                            Zeitraster(
                                                tage = tage,
                                                kalenderwoche = kalenderwochen,
                                                eintraege = liste,
                                                heute = heute,
                                                zone = zone,
                                                scroll = rasterScroll,
                                                entwurf = entwurfblock,
                                                verborgen = verborgen,
                                                onTag = { if (!blattOffen) ansicht(Kalendermodus.TAG, it) },
                                                onLeer = { tag, minute ->
                                                    val e = entwurf
                                                    if (blattOffen && e != null) {
                                                        // Bei offenem Blatt wandert der Block an die getippte Stelle.
                                                        val beginn = tag.atTime(minute / 60, minute % 60)
                                                        val dauer = if (e.datiert && !e.ganztaegig && e.mitEnde && e.ende.isAfter(e.beginn)) {
                                                            java.time.Duration.between(e.beginn, e.ende)
                                                        } else {
                                                            java.time.Duration.ofMinutes(terminDauer.toLong())
                                                        }
                                                        editor.setZeitraum(beginn, beginn.plus(dauer))
                                                    } else if (tippenLegtAn) {
                                                        neu(Terminart.TERMIN, tag, minute, Blattstufe.HALB)
                                                        zeigeMinute(minute)
                                                    }
                                                },
                                                onEintrag = { e, lage -> if (!blattOffen) gewaehlt = e to lage },
                                                onEntwurf = { b, en -> editor.setZeitraum(b, en) },
                                                onEntwurfTipp = { blatt = Blattstufe.VOLL },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Beim ersten Aufgehen zur aktuellen Stunde scrollen, sonst zum Morgen.
        LaunchedEffect(Unit) {
            val stunde = if (anker == heute) maxOf(0, LocalTime.now().hour - 1) else 7
            rasterScroll.scrollTo(with(dichte) { (stunde * STUNDE.toPx()).toInt() })
        }

        Plusmenue(
            sichtbar = suche == null && !aufgabenOffen && !blattOffen && gewaehlt == null,
            offen = plusOffen,
            onOffen = { plusOffen = it },
            onArt = { art -> neu(art, if (modus == Kalendermodus.UEBERSICHT) heute else anker, null, Blattstufe.VOLL) },
        )

        AnimatedVisibility(
            visible = aufgabenOffen,
            enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200)),
        ) {
            val gruppen = remember(termine, kalender) { aufgabengruppen(termine, kalender, emptySet(), heute, zone) }
            Aufgabenliste(
                gruppen = gruppen,
                heute = heute,
                zone = zone,
                onZurueck = { aufgabenOffen = false },
                onAufgabe = { a -> gewaehlt = eintragFuer(a.termin, a.farbe, heute, zone) to null },
                onErledigt = viewModel::erledigt,
                onNeu = { neu(Terminart.AUFGABE, heute, null, Blattstufe.VOLL, ohneDatum = true) },
            )
        }

        if (blattOffen) {
            TerminEditorBlatt(
                viewModel = editor,
                stufe = blatt,
                onStufe = { blatt = it },
                onZu = {
                    blattOffen = false
                    blatt = Blattstufe.ZU
                    editor.schliessen()
                },
            )
        }

        gewaehlt?.let { (e, lage) ->
            key(e.schluessel) {
                Detailansicht(
                    e = e,
                    von = lage,
                    termin = (e.herkunft as? Herkunft.Termin)?.let { h -> termine.firstOrNull { it.id == h.vorkommen.termin.id } },
                    bilddatei = editor::bilddatei,
                    kalender = kalender,
                    zone = zone,
                    onZu = { gewaehlt = null },
                    onBearbeiten = { id -> bearbeiten(id) },
                    onNotiz = { id ->
                        gewaehlt = null
                        onNotiz(id)
                    },
                    onErledigt = { id, fertig ->
                        gewaehlt = null
                        viewModel.erledigt(id, fertig)
                    },
                    onLoeschen = { id ->
                        viewModel.loeschen(id)
                        zeigeUndo(UndoRequest("Termin gelöscht") { viewModel.wiederherstellen(id) })
                    },
                    onVorkommenLoeschen = { id, schluessel ->
                        viewModel.vorkommenLoeschen(id, schluessel)
                        zeigeUndo(UndoRequest("Termin gelöscht") { viewModel.vorkommenWiederherstellen(id, schluessel) })
                    },
                )
            }
        }
    }

    if (neuerKalender) {
        KalenderDialog(
            titel = "Kalender erstellen",
            name = "",
            farbe = FARBE_STANDARD,
            onAbbrechen = { neuerKalender = false },
            onFertig = { name, farbe ->
                neuerKalender = false
                viewModel.kalenderAnlegen(name, farbe)
            },
        )
    }
}

// ------------------------------------------------------------- Kopfleiste

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kopfleiste(
    monat: YearMonth,
    miniMonat: Boolean,
    heute: LocalDate,
    onMenue: () -> Unit,
    onTitel: () -> Unit,
    onSuche: () -> Unit,
    onAufgaben: () -> Unit,
    onHeute: () -> Unit,
) {
    val pfeil by animateFloatAsState(if (miniMonat) 180f else 0f, label = "pfeil")
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        navigationIcon = {
            IconButton(onClick = onMenue) { Icon(Icons.Outlined.Menu, contentDescription = "Menü") }
        },
        title = {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTitel).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Der Monatsname rollt beim Blaettern in die Richtung, in die es geht.
                AnimatedContent(
                    targetState = monat,
                    transitionSpec = {
                        val vor = targetState > initialState
                        (slideInVertically(tween(220)) { if (vor) it else -it } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(220)) { if (vor) -it else it } + fadeOut(tween(160)))
                    },
                    label = "monat",
                ) { m ->
                    Text(Kalenderformat.monat(m, heute), style = MaterialTheme.typography.titleLarge)
                }
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = if (miniMonat) "Monat zuklappen" else "Monat aufklappen",
                    modifier = Modifier.rotate(pfeil),
                )
            }
        },
        actions = {
            IconButton(onClick = onSuche) { Icon(Icons.Outlined.Search, contentDescription = "Suchen") }
            IconButton(onClick = onAufgaben) { Icon(Icons.Outlined.TaskAlt, contentDescription = "Aufgaben") }
            // Ein kleines Kalenderblatt mit der heutigen Zahl.
            IconButton(onClick = onHeute) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            heute.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    )
}

/** Die Suche als deutlich abgesetzte Blase. */
@Composable
private fun Suchleiste(text: String, onText: (String) -> Unit, onZu: () -> Unit) {
    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fokus.requestFocus() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onZu) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Suche schließen") }
            TextField(
                value = text,
                onValueChange = onText,
                placeholder = { Text("Termine suchen") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).focusRequester(fokus),
            )
            AnimatedVisibility(visible = text.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton(onClick = { onText("") }) { Icon(Icons.Outlined.Close, contentDescription = "Suche leeren") }
            }
        }
    }
}

@Composable
private fun Suchergebnisse(
    suche: String,
    termine: List<EventEntity>,
    kalender: List<EventCalendarEntity>,
    onTermin: (EventEntity, Rect?) -> Unit,
) {
    val treffer = remember(suche, termine) {
        termine.filter { it.deletedAt == null && passt(it, suche) }.sortedByDescending { it.startAt }
    }
    val farben = remember(kalender) { kalender.associate { it.id to it.colorArgb } }
    if (suche.isBlank()) return
    if (treffer.isEmpty()) {
        de.notizen.app.ui.components.LeererZustand(
            titel = "Nichts gefunden",
            text = "Gesucht wird in Titel, Ort und Beschreibung aller Termine.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(treffer, key = { it.id }) { t ->
            val lage = remember { Lage() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .merkeLage(lage)
                    .clickable { onTermin(t, lage.grenzen) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateItem(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(t.colorArgb ?: farben[t.calendarId] ?: FARBE_STANDARD)),
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(t.title.ifBlank { "Ohne Titel" }, style = MaterialTheme.typography.bodyLarge)
                    val tag = if (t.allDay) Terminregeln.datumAus(t.startAt) else Terminregeln.tagVon(t.startAt, java.time.ZoneId.systemDefault())
                    Text(
                        (if (t.undated) "Ohne Datum" else Kalenderformat.tag(tag)) +
                            if (t.recurrence != Wiederholung.KEINE) " · " + wiederholungstext(t.serie) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- Plus

/**
 * Das Plus unten rechts. Offen faechert es Termin, Aufgabe und Geburtstag
 * nacheinander auf, das Kreuz entsteht aus dem gedrehten Plus.
 */
@Composable
private fun Plusmenue(sichtbar: Boolean, offen: Boolean, onOffen: (Boolean) -> Unit, onArt: (Terminart) -> Unit) {
    AnimatedVisibility(visible = offen && sichtbar, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOffen(false) },
        )
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = sichtbar,
            enter = scaleIn(spring(dampingRatio = 0.7f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val arten = listOf(Terminart.GEBURTSTAG, Terminart.AUFGABE, Terminart.TERMIN)
                arten.forEachIndexed { i, art ->
                    // Der unterste kommt zuerst, die anderen folgen.
                    val warten = (arten.size - 1 - i) * 40
                    AnimatedVisibility(
                        visible = offen,
                        enter = fadeIn(tween(160, delayMillis = warten)) +
                            slideInVertically(tween(240, delayMillis = warten, easing = FastOutSlowInEasing)) { it / 2 } +
                            scaleIn(tween(240, delayMillis = warten), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0.5f)),
                        exit = fadeOut(tween(90)) + slideOutVertically(tween(120)) { it / 3 },
                    ) {
                        when (art) {
                            Terminart.GEBURTSTAG -> Plusknopf("Geburtstag", Icons.Outlined.Cake) { onArt(art) }
                            Terminart.AUFGABE -> Plusknopf("Aufgabe", Icons.Outlined.TaskAlt) { onArt(art) }
                            Terminart.TERMIN -> Plusknopf("Termin", Icons.Outlined.Event) { onArt(art) }
                        }
                    }
                }
                val drehung by animateFloatAsState(if (offen) 135f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "plus")
                FloatingActionButton(onClick = { onOffen(!offen) }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = if (offen) "Schließen" else "Neu",
                        modifier = Modifier.rotate(drehung),
                    )
                }
            }
        }
    }
}

@Composable
private fun Plusknopf(text: String, zeichen: ImageVector, onKlick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(end = 16.dp).clickable(onClick = onKlick),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        SmallFloatingActionButton(onClick = onKlick, modifier = Modifier.padding(end = 4.dp)) {
            Icon(zeichen, contentDescription = text)
        }
    }
}

// ---------------------------------------------------------- Seitenspalte

@Composable
private fun Kalenderspalte(
    modus: Kalendermodus,
    kalender: List<EventCalendarEntity>,
    ausgeblendet: Set<String>,
    notizenImKalender: Boolean,
    onModus: (Kalendermodus) -> Unit,
    onSichtbar: (String, Boolean) -> Unit,
    onAktualisieren: () -> Unit,
    onNeuerKalender: () -> Unit,
    onEinstellungen: () -> Unit,
    onZuDenNotizen: () -> Unit,
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Kalender",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null) },
                label = { Text("Zurück zu den Notizen") },
                selected = false,
                onClick = onZuDenNotizen,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Kalendermodus.entries.forEach { m ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            when (m) {
                                Kalendermodus.UEBERSICHT -> Icons.Outlined.ViewAgenda
                                Kalendermodus.TAG -> Icons.Outlined.ViewDay
                                Kalendermodus.DREI_TAGE -> Icons.Outlined.CalendarViewDay
                                Kalendermodus.WOCHE -> Icons.Outlined.CalendarViewWeek
                                Kalendermodus.MONAT -> Icons.Outlined.CalendarViewMonth
                            },
                            contentDescription = null,
                        )
                    },
                    label = { Text(m.text) },
                    selected = m == modus,
                    onClick = { onModus(m) },
                )
            }
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                label = { Text("Aktualisieren") },
                selected = false,
                onClick = onAktualisieren,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Meine Kalender",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            kalender.forEach { k ->
                Ebene(k.name, Color(k.colorArgb), k.id !in ausgeblendet) { onSichtbar(k.id, it) }
            }
            if (notizenImKalender) {
                Ebene("Erinnerungen aus Notizen", Color(FARBE_NOTIZEN), EBENE_NOTIZEN !in ausgeblendet) {
                    onSichtbar(EBENE_NOTIZEN, it)
                }
            }
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                label = { Text("Kalender erstellen") },
                selected = false,
                onClick = onNeuerKalender,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                label = { Text("Einstellungen") },
                selected = false,
                onClick = onEinstellungen,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Ebene(name: String, farbe: Color, an: Boolean, onWechsel: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onWechsel(!an) }
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = an,
            onCheckedChange = onWechsel,
            colors = CheckboxDefaults.colors(checkedColor = farbe, uncheckedColor = farbe, checkmarkColor = aufFarbe(farbe)),
        )
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

// ----------------------------------------------------------- Kalenderdialog

/** Name und Farbe eines Kalenders, zum Anlegen und Aendern. */
@Composable
fun KalenderDialog(
    titel: String,
    name: String,
    farbe: Int,
    onAbbrechen: () -> Unit,
    onFertig: (String, Int) -> Unit,
    onLoeschen: (() -> Unit)? = null,
    vorgabe: Kalendervorgabe? = null,
    onVorgabe: (Kalendervorgabe) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf(name) }
    var wahl by rememberSaveable { mutableStateOf(farbe) }
    var vorgabeText by rememberSaveable { mutableStateOf(vorgabe?.kodiert()) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Farbwahl(wahl) { neu -> neu?.let { wahl = it } }
                vorgabeText?.let { v ->
                    Spacer(Modifier.height(8.dp))
                    VorgabenTeil(Kalendervorgabe.lesen(v)) { vorgabeText = it.kodiert() }
                }
                if (onLoeschen != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onLoeschen) {
                        Text("Kalender löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vorgabeText?.let { v -> if (v != vorgabe?.kodiert()) onVorgabe(Kalendervorgabe.lesen(v)) }
                    onFertig(text.trim(), wahl)
                },
                enabled = text.isNotBlank(),
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/** Die Kalenderfarben als Kreise in zwei Reihen. */
@Composable
fun Farbwahl(gewaehlt: Int?, mitStandard: Boolean = false, onWahl: (Int?) -> Unit) {
    val reihen = KALENDERFARBEN.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mitStandard) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onWahl(null) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = gewaehlt == null, onClick = { onWahl(null) })
                Text("Farbe des Kalenders")
            }
        }
        reihen.forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { (f, name) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(f))
                            .clickable { onWahl(f) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (f == gewaehlt) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = "$name gewählt", tint = aufFarbe(Color(f)), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
