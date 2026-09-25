package de.notizen.app.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ai.fallbackTitel
import de.notizen.app.audio.AufnahmeDienst
import de.notizen.app.audio.aufnahmeDatei
import de.notizen.app.ui.UndoRequest
import de.notizen.app.ui.components.FarbAuswahlBlatt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.notizen.app.ui.components.TagAuswahlDialog
import de.notizen.app.ui.ordner.OrdnerAuswahlDialog
import de.notizen.app.ui.ordner.OrdnerViewModel
import de.notizen.app.ui.ordner.ZurueckholenDialog
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.app.ui.tags.TAG_FARBEN
import de.notizen.app.ui.tags.TagDialog
import de.notizen.app.ui.theme.FAVORIT_GOLD
import de.notizen.app.ui.components.Bildbetrachter
import de.notizen.app.ui.components.Bildraster
import de.notizen.app.ui.theme.BILD_FARBE
import de.notizen.app.ui.theme.markiererZu
import de.notizen.app.ui.theme.alsNotizFarbe
import de.notizen.app.ui.util.teileNotizen
import de.notizen.app.ui.components.TitelDialog
import de.notizen.app.ui.components.ZiehbareSpalte
import de.notizen.app.ui.components.tastaturAbstand
import de.notizen.app.ui.components.tastaturOffen
import de.notizen.app.ui.theme.NotizFarbe
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.model.NoteType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Der Editor. Vollflaechig, ohne Speichern-Knopf.
 *
 * Der Hintergrund ist hier `surface` und nicht `surfaceContainerLow` -- der
 * Editor IST die aufgeklappte Karte, also traegt er deren Farbe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onUndoRequest: (UndoRequest) -> Unit,
    /** Vom ausgegrauten Transkript zum KI-Schalter in den Einstellungen (Phase 15). */
    onEinstellungen: () -> Unit = {},
    // Nur fuer die Liste der Ordner im Menue. Der Editor selbst schreibt sie
    // nicht, er legt seine Notiz nur in einen davon.
    ordnerViewModel: OrdnerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val farbBlattOffen by viewModel.farbBlattOffen.collectAsStateWithLifecycle()
    val erlaubnisFehlt by viewModel.erlaubnisFehlt.collectAsStateWithLifecycle()
    var erinnerungDialogOffen by remember { mutableStateOf(false) }

    val aufnahme by viewModel.aufnahme.aufnahme.collectAsStateWithLifecycle()
    val transkript by viewModel.aufnahme.transkript.collectAsStateWithLifecycle()
    val hatAufnahme by viewModel.hatAufnahme.collectAsStateWithLifecycle()
    val wellenform by viewModel.wellenform.collectAsStateWithLifecycle()
    val abspielen by viewModel.abspielstatus.collectAsStateWithLifecycle()
    var audioseite by rememberSaveable { mutableStateOf(Audioseite.AUFNAHME) }
    var fassung by rememberSaveable { mutableStateOf(Fassung.BEARBEITET) }
    var bearbeitungsmodus by rememberSaveable { mutableStateOf(false) }
    // Die Liste hat zwei Zustaende (Phase 18): lesen (nur abhaken) und
    // bearbeiten (ziehen, entfernen, umschreiben). Umgeschaltet wird oben
    // rechts, der Haken speichert.
    var listeBearbeiten by rememberSaveable { mutableStateOf(false) }
    val rohtranskript by viewModel.rohtranskript.collectAsStateWithLifecycle()
    val abschnitte by viewModel.sprechabschnitte.collectAsStateWithLifecycle()
    val stilleUeberspringen by viewModel.stilleUeberspringen.collectAsStateWithLifecycle()
    val aufnahmedauer by viewModel.aufnahmedauer.collectAsStateWithLifecycle()
    val aufnahmeDatei = remember(viewModel.noteId) { aufnahmeDatei(context, viewModel.noteId) }
    val modell by viewModel.modell.collectAsStateWithLifecycle()
    val kiVerfuegbar by viewModel.kiVerfuegbar.collectAsStateWithLifecycle()
    val ordnermodus by viewModel.ordnermodus.collectAsStateWithLifecycle()
    val kiSchalter by viewModel.kiSchalter.collectAsStateWithLifecycle()
    val bereitetAuf by viewModel.bereitetAuf.collectAsStateWithLifecycle()
    val hinweis by viewModel.hinweis.collectAsStateWithLifecycle()
    val uebersetzungVerfuegbar by viewModel.uebersetzungVerfuegbar.collectAsStateWithLifecycle()
    val uebersetzungsanfrage by viewModel.uebersetzungsanfrage.collectAsStateWithLifecycle()

    // Das Mikrofon wird erst gefragt, wenn es gebraucht wird -- und die Antwort
    // startet die Aufnahme direkt, sonst muesste man zweimal tippen.
    val mikrofonFrage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { erlaubt ->
        if (erlaubt) AufnahmeDienst.aufnehmen(context, viewModel.noteId)
    }

    // Kamera und Galerie. Beide brauchen KEINE Laufzeitberechtigung: das Foto
    // macht die Kamera-App, und der Systembildwaehler gibt genau die Bilder
    // heraus, die der Nutzer dort antippt. Wer hier READ_MEDIA_IMAGES erfragt,
    // verlangt Zugriff auf die GANZE Galerie fuer eine Aufgabe, die ein
    // einzelnes Bild braucht.
    val kamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { erfolg -> viewModel.kameraFertig(erfolg) }

    val galerie = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(BILDER_HOECHSTENS),
    ) { adressen -> viewModel.bilderHinzufuegen(adressen) }

    // Eigener Waehler fuer den Hintergrund: genau ein Bild, und es wird als
    // Flaeche eingetragen statt als Bild der Notiz.
    val hintergrundGalerie = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { adresse -> adresse?.let { viewModel.hintergrundWaehlen(it) } }

    var anhangBlattOffen by remember { mutableStateOf(false) }
    var menueBlattOffen by remember { mutableStateOf(false) }
    var tagDialogOffen by remember { mutableStateOf(false) }
    var ordnerWahlOffen by remember { mutableStateOf(false) }
    var neuerTagOffen by remember { mutableStateOf(false) }
    var offenesBild by remember { mutableStateOf<String?>(null) }

    val alleTags by viewModel.alleTags.collectAsStateWithLifecycle()
    val meineTags by viewModel.meineTags.collectAsStateWithLifecycle()

    // Der Notiztext braucht hier einen TextFieldValue und nicht nur einen
    // String: Die Formatierungsleiste wirkt auf die AUSWAHL, und die steckt
    // nicht im Text. Die Wahrheit bleibt trotzdem `state.body` -- dieser Wert
    // ist ein Spiegel davon, der zusaetzlich Cursor und Auswahl fuehrt.
    var textwert by remember { mutableStateOf(TextFieldValue(state.body)) }
    var textFokus by remember { mutableStateOf(false) }

    // Zieht Aenderungen nach, die NICHT aus dem Feld kommen: geladene Notiz,
    // uebernommenes Transkript, Zuruecksetzen aufs Original. Der Vergleich
    // verhindert die Schleife -- schreibt das Feld selbst, sind beide gleich
    // und hier passiert nichts.
    LaunchedEffect(state.body) {
        if (state.body != textwert.text) {
            textwert = TextFieldValue(state.body, TextRange(state.body.length))
        }
    }

    // Zwei Wege in denselben Zustand, und der Unterschied ist Absicht:
    //
    //  * `textSetzen` schreibt roh -- so arbeitet die Formatierungsleiste. Sie
    //    DARF ein leeres Zeichenpaar hinterlassen; genau da soll ja gleich
    //    getippt werden.
    //  * `textAendern` raeumt leere Paare weg -- so arbeitet das Textfeld. Wer
    //    den Text eines fetten Wortes wieder wegnimmt, bliebe sonst auf einem
    //    `****` sitzen und muesste es einzeln loeschen.
    val textSetzen: (TextFieldValue) -> Unit = { neu ->
        textwert = neu
        viewModel.onBodyChanged(neu.text)
    }
    val textAendern: (TextFieldValue) -> Unit = { neu ->
        textSetzen(Auszeichnung.aufraeumen(neu))
    }

    // Audionotizen schreiben in die Transkriptseite, Listen in ihre Eintraege --
    // beide haben kein durchgehendes Textfeld, auf das eine Leiste passen wuerde.
    val formatierbar = state.type != NoteType.AUDIO && state.type != NoteType.LIST

    // Der Editor IST die aufgeklappte Karte -- also traegt er auch deren Farbe.
    val hintergrund = state.hintergrund
    // Mit Hintergrundbild gilt die Palette nicht mehr: ueber einem beliebigen
    // Foto laesst sich fuer keine Palettenfarbe Lesbarkeit zusagen. Weiss auf
    // Abdunkler ist die einzige Kombination, die man verantworten kann --
    // nachgerechnet in HintergrundScrimTest.
    val farbe = if (hintergrund != null) BILD_FARBE else state.colorId.alsNotizFarbe()
    // Die Akzentfarbe der Notiz: bei DEFAULT die des Themes, sonst aus der
    // Notizfarbe abgeleitet. `primary` auf einer gelben Karte waere ein
    // Fremdkoerper -- und je nach Farbe kaum zu lesen.
    val markiererFarbe = if (hintergrund != null) {
        Color.White.copy(alpha = 0.28f)
    } else {
        markiererZu(state.colorId)
    }
    var titelDialogOffen by remember { mutableStateOf(false) }

    // Einmal definiert, an drei Stellen benutzt: Aufnahmeseite, Transkriptseite
    // und der Platzhalter, wenn es noch nichts gibt.
    val aufnehmenStarten: () -> Unit = {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            AufnahmeDienst.aufnehmen(context, viewModel.noteId)
        } else {
            mikrofonFrage.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val verlassen = { viewModel.verlassen(onClose) }

    // Einmal definiert, aus der Leiste und aus dem Menue benutzt.
    val inDenPapierkorb: () -> Unit = {
        viewModel.inDenPapierkorb {
            onUndoRequest(
                UndoRequest("Notiz in den Papierkorb verschoben") {
                    viewModel.wiederherstellen()
                },
            )
            onClose()
        }
    }
    BackHandler { verlassen() }
    // Steht nach dem allgemeinen Zurueck, damit es Vorrang hat: Im
    // Bearbeitungszustand der Liste tut Zurueck dasselbe wie der Haken.
    BackHandler(enabled = listeBearbeiten) {
        listeBearbeiten = false
        viewModel.speichereJetzt()
    }

    if (titelDialogOffen) {
        TitelDialog(
            ueberschrift = "Titel vorschlagen",
            // Seit Phase 15 leer, wie beim Verschieben: Die KI schlaegt unten
            // vor, sobald sie etwas hat; sonst tippt man selbst.
            erklaerung = "Die KI schlägt einen Titel vor, sobald sie einen hat. " +
                "Antippen übernimmt ihn, oder du schreibst selbst.",
            fallback = fallbackTitel(
                viewModel.titelQuelleAusTranskript,
                state.items.map { it.text },
            ),
            quelle = viewModel.titelQuelleAusTranskript.ifBlank { state.titelQuelle },
            bestaetigenText = "Übernehmen",
            onAbbrechen = { titelDialogOffen = false },
            onBestaetigen = {
                viewModel.onTitleChosen(it)
                titelDialogOffen = false
            },
        )
    }

    if (erinnerungDialogOffen) {
        ErinnerungDialog(
            vorbelegung = state.erinnerungAn,
            onAbbrechen = { erinnerungDialogOffen = false },
            onBestaetigen = {
                viewModel.setzeErinnerung(it)
                erinnerungDialogOffen = false
            },
        )
    }

    if (erlaubnisFehlt) {
        ExakteAlarmeFehlen(
            onSchliessen = viewModel::erlaubnisHinweisSchliessen,
            onEinstellungen = {
                viewModel.erlaubnisHinweisSchliessen()
                context.startActivity(viewModel.einstellungenFuerAlarme())
            },
        )
    }

    hinweis?.let { text ->
        LaunchedEffect(text) {
            onUndoRequest(UndoRequest(text) { })
            viewModel.hinweisGelesen()
        }
    }

    // Der Modellzustand steht fest, BEVOR der Transkript-Knopf erscheint.
    LaunchedEffect(Unit) {
        viewModel.modellPruefen()
        viewModel.aufnahmePruefen(aufnahmeDatei)
        // Erst fragen, dann anbieten: „Uebersetzen" gibt es nur, wenn ein
        // Weg dafuer da ist (Phase 16).
        viewModel.uebersetzungPruefen()
    }

    // Nach dem Aufnehmen liegt eine neue Datei da, wo vorher eine andere war --
    // die alte Wellenform gilt dann nicht mehr.
    LaunchedEffect(aufnahme.laeuft) {
        if (!aufnahme.laeuft) viewModel.aufnahmePruefen(aufnahmeDatei, neuAufgenommen = true)
    }

    // Hart speichern, sobald die App in den Hintergrund geht. Der Debounce
    // allein wuerde die letzten Sekunden verlieren.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.speichereJetzt() }

    // Beim Zurueckkommen aus den Systemeinstellungen den Wecker nachziehen.
    // Sonst bliebe eine gerade erlaubte Erinnerung trotzdem stumm.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.weckerNachziehen() }

    // Das Bild liegt HINTER dem Scaffold, nicht darin: Ein Hintergrund, der im
    // Inhaltsbereich sitzt, endet an der Top Bar und an der unteren Leiste, und
    // die Notiz saehe aus wie ein Foto in einem Rahmen statt wie ein Blatt.
    Box(Modifier.fillMaxSize()) {
        hintergrund?.let { anhang ->
            Hintergrundbild(anhang)
        }

    Scaffold(
        // OHNE DAS LIEGT DIE TASTATUR UEBER DEM TEXTFELD. Die App laeuft
        // randlos (`enableEdgeToEdge`), und `Scaffold` beruecksichtigt von sich
        // aus nur Systemleisten und Display-Aussparung -- die Tastatur gehoert
        // nicht dazu. `adjustResize` im Manifest allein reicht dafuer nicht.
        // Am Geraet sah das so aus, als liesse sich der Text nicht weit genug
        // scrollen; tatsaechlich lag er unter der Tastatur.
        //
        // Seit Phase 18 nicht mehr `imePadding()`, sondern `tastaturAbstand()`:
        // Compose blieb nach einer Textauswahl gelegentlich auf der Hoehe der
        // Tastatur haengen, und die untere Leiste stand bis zum Neustart
        // mitten im Bild. Die Ursache und der Weg drumherum stehen dort.
        modifier = Modifier.tastaturAbstand(),
        containerColor = farbe.container,
        contentColor = farbe.onContainer,
        topBar = {
            // Die Flaeche liegt HINTER der Top Bar, nicht in ihr: `TopAppBar`
            // blendet seine eigene Containerfarbe animiert ein, und beim
            // Umfaerben der Notiz lief die Leiste dadurch sichtbar der Karte
            // hinterher. Transparent gestellt uebernimmt sie den Wechsel im
            // selben Bild wie alles andere.
            Box(Modifier.background(farbe.container)) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = verlassen) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    actions = {
                        // Nur bei einer Liste: der Stift oeffnet den
                        // Bearbeitungszustand, der Haken speichert und schliesst
                        // ihn (Phase 18).
                        if (state.type == NoteType.LIST) {
                            IconButton(
                                onClick = {
                                    if (listeBearbeiten) {
                                        listeBearbeiten = false
                                        viewModel.speichereJetzt()
                                    } else {
                                        listeBearbeiten = true
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (listeBearbeiten) {
                                        Icons.Outlined.Check
                                    } else {
                                        Icons.Outlined.Edit
                                    },
                                    contentDescription = if (listeBearbeiten) {
                                        "Liste fertig bearbeitet"
                                    } else {
                                        "Liste bearbeiten"
                                    },
                                )
                            }
                        }
                        IconButton(onClick = viewModel::toggleFavorit) {
                            Icon(
                                imageVector = if (state.istFavorit) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarBorder
                                },
                                contentDescription = if (state.istFavorit) {
                                    "Favorit aufheben"
                                } else {
                                    "Als Favorit merken"
                                },
                                // Der Stern traegt die Akzentfarbe, nicht die
                                // Notizfarbe: er ist ein Zustand, kein Teil des
                                // Blattes.
                                tint = if (state.istFavorit) FAVORIT_GOLD else farbe.onContainer,
                            )
                        }
                        IconButton(onClick = { titelDialogOffen = true }) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "Titel vorschlagen",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = farbe.onContainer,
                        actionIconContentColor = farbe.onContainer,
                    ),
                )
            }
        },
        bottomBar = {
            Column(Modifier.background(farbe.container)) {
                // Die Leiste kommt und geht mit der Tastatur, und zwar
                // eingeblendet statt schlagartig (Phase 18). Das Mass ist die
                // Tastatur, nicht nur der Fokus: Wer sie mit Zurueck
                // schliesst, will den Text lesen, nicht formatieren. Ihre
                // Hoehe waechst von unten, der Text darueber bleibt stehen.
                AnimatedVisibility(
                    visible = formatierbar && textFokus && tastaturOffen(),
                    enter = expandVertically(animationSpec = tween(LEISTE_MS)) +
                        fadeIn(animationSpec = tween(LEISTE_MS)),
                    exit = shrinkVertically(animationSpec = tween(LEISTE_MS)) +
                        fadeOut(animationSpec = tween(LEISTE_MS)),
                ) {
                    Formatierungsleiste(
                        aktiv = Auszeichnung.aktiv(textwert),
                        stufe = Auszeichnung.stufeAn(textwert),
                        farbe = farbe.onContainer,
                        onArt = { textSetzen(Auszeichnung.umschalten(textwert, it)) },
                        onStufe = { textSetzen(Auszeichnung.setzeStufe(textwert, it)) },
                    )
                }

            // Untere Aktionsleiste. Die Palette gehoert laut Spezifikation
            // Abschnitt 5a auch hierher, damit man zum Einfaerben nicht erst
            // zurueck in die Liste muss.
            BottomAppBar(
                containerColor = farbe.container,
                contentColor = farbe.onContainer,
                actions = {
                    IconButton(onClick = { anhangBlattOffen = true }) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Hinzufügen")
                    }
                    IconButton(onClick = viewModel::oeffneFarbAuswahl) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Einfärben")
                    }
                    IconButton(onClick = { erinnerungDialogOffen = true }) {
                        Icon(
                            imageVector = if (state.erinnerungAn != null) {
                                Icons.Filled.Notifications
                            } else {
                                Icons.Outlined.Notifications
                            },
                            contentDescription = "Erinnerung",
                            // Auch hier die Notizfarbe: `primary` waere auf
                            // einer eingefaerbten Karte ein Fremdkoerper.
                            tint = farbe.onContainer,
                        )
                    }
                    // Die Leiste traegt, was man beim Schreiben staendig
                    // braucht. Alles Seltene und Folgenreiche -- Loeschen,
                    // Teilen, Kopieren, Tags -- liegt hinter den drei Punkten:
                    // Loeschen einen Fingerbreit neben Einfaerben zu stellen
                    // waere eine Einladung zum Verklicken.
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { menueBlattOffen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Weitere Aktionen")
                    }
                },
            )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Die Bilder stehen UEBER dem Titel. Ein Bild ist bei einer
            // Bildnotiz der Inhalt, und Inhalt unter einem leeren Titelfeld zu
            // verstecken hiesse, die Notiz falsch herum aufzuschlagen.
            Bildraster(
                bilder = state.bilder,
                hintergrundId = state.hintergrundId,
                onBild = { offenesBild = it.id },
                modifier = Modifier.padding(bottom = 4.dp),
            )

            NahtlosesFeld(
                value = state.title,
                onValueChange = viewModel::onTitleChanged,
                placeholder = "Titel",
                textStyle = MaterialTheme.typography.headlineSmall,
            )

            state.erinnerungAn?.let { zeitpunkt ->
                Erinnerungszeile(
                    zeitpunkt = zeitpunkt,
                    farbe = farbe.onContainer,
                    onAendern = { erinnerungDialogOffen = true },
                    onEntfernen = viewModel::entferneErinnerung,
                )
            }

            if (state.titelFehlt) {
                Text(
                    text = "Ab dem Workspace braucht eine Notiz einen Titel. Ohne Eingabe " +
                        "wird beim Verlassen einer aus dem Text abgeleitet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = farbe.onContainer.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }

            when (state.type) {
                NoteType.AUDIO -> {
                    Seitenumschalter(
                        seite = audioseite,
                        hatTranskript = transkript.teile.isNotEmpty() ||
                            state.body.isNotBlank(),
                        farbe = farbe.onContainer,
                        onWechsel = { audioseite = it },
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    when (audioseite) {
                        Audioseite.AUFNAHME -> when {
                            aufnahme.laeuft || !hatAufnahme -> AufnahmeBereich(
                                aufnahme = aufnahme,
                                transkript = transkript,
                                hatAufnahme = hatAufnahme,
                                modell = modell,
                                kiVerfuegbar = kiVerfuegbar,
                                transkriptErlaubt = kiSchalter,
                                laeuftAufbereitung = bereitetAuf,
                                farbe = farbe.onContainer,
                                onStarten = aufnehmenStarten,
                                onStoppen = { AufnahmeDienst.stoppen(context) },
                                onTranskript = {
                                    audioseite = Audioseite.TRANSKRIPT
                                    AufnahmeDienst.transkribieren(context, viewModel.noteId)
                                },
                                onLaden = viewModel::modellLaden,
                                onAufbereiten = viewModel::aufbereiten,
                                onEinstellungen = onEinstellungen,
                            )

                            else -> Aufnahmeseite(
                                wellenform = wellenform,
                                abschnitte = abschnitte,
                                status = abspielen,
                                notizId = viewModel.noteId,
                                dauerMs = aufnahmedauer,
                                stilleUeberspringen = stilleUeberspringen,
                                farbe = farbe.onContainer,
                                onAbspielen = { viewModel.abspielen(aufnahmeDatei) },
                                onSpringen = viewModel::springeZu,
                                onSpringenUm = viewModel::springenUm,
                                onStilleUmschalten = viewModel::stilleUeberspringenUmschalten,
                                onNeuAufnehmen = aufnehmenStarten,
                            )
                        }

                        Audioseite.TRANSKRIPT -> Column {
                            AufnahmeBereich(
                            aufnahme = aufnahme,
                            transkript = transkript,
                            hatAufnahme = hatAufnahme,
                            modell = modell,
                            kiVerfuegbar = kiVerfuegbar,
                            transkriptErlaubt = kiSchalter,
                            laeuftAufbereitung = bereitetAuf,
                            farbe = farbe.onContainer,
                            onStarten = aufnehmenStarten,
                            onStoppen = { AufnahmeDienst.stoppen(context) },
                            onTranskript = {
                                AufnahmeDienst.transkribieren(context, viewModel.noteId)
                            },
                            onLaden = viewModel::modellLaden,
                            onAufbereiten = viewModel::aufbereiten,
                            onEinstellungen = onEinstellungen,
                                onTitel = { titelDialogOffen = true },
                                nurTranskript = true,
                            )

                            if (rohtranskript.isNotBlank() || state.body.isNotBlank()) {
                                Transkriptansicht(
                                    original = rohtranskript,
                                    bearbeitet = state.body,
                                    fassung = fassung,
                                    bearbeitungsmodus = bearbeitungsmodus,
                                    farbe = farbe.onContainer,
                                    // Der Akzent kommt aus der Notizfarbe, nicht
                                    // aus dem Theme: Auf einer eingefaerbten
                                    // Karte waere `primary` schlicht die falsche
                                    // Farbe -- und moeglicherweise unlesbar.
                                    markierer = markiererFarbe,
                                    onFassung = { fassung = it },
                                    onBearbeiten = { bearbeitungsmodus = true },
                                    onFertig = {
                                        bearbeitungsmodus = false
                                        viewModel.speichereJetzt()
                                    },
                                    onZuruecksetzen = viewModel::aufOriginalZuruecksetzen,
                                    bearbeitungsfeld = { anzeige ->
                                        NahtlosesFeld(
                                            value = state.body,
                                            onValueChange = viewModel::onBodyChanged,
                                            placeholder = "Transkript bearbeiten",
                                            textStyle = MaterialTheme.typography.bodyLarge,
                                            anzeige = anzeige,
                                        )
                                    },
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
                NoteType.IMAGE -> if (state.bilder.isEmpty()) {
                    // Nur solange noch nichts da ist. Sobald ein Bild in der
                    // Notiz steht, waere der Hinweis eine Aufforderung zu etwas,
                    // das schon geschehen ist.
                    BildEinladung(
                        farbe = farbe.onContainer,
                        onHinzufuegen = { anhangBlattOffen = true },
                    )
                }
                else -> Unit
            }

            // Sobald das Transkript fertig ist, wandert es in den Text --
            // ohne Rueckfrage, denn genau dafuer hat man es erstellt.
            LaunchedEffect(transkript.laeuft, transkript.teile.size) {
                if (!transkript.laeuft && transkript.teile.isNotEmpty()) {
                    viewModel.transkriptUebernehmen()
                }
            }

            // Bei einer Audionotiz steht der Text auf der Transkriptseite --
            // hier waere er ein zweites Mal dasselbe, und auf der Aufnahmeseite
            // haette er ueberhaupt nichts zu suchen.
            val eigenesTextfeld = state.type == NoteType.AUDIO

            when {
                eigenesTextfeld -> Unit
                state.type == NoteType.LIST -> Checkliste(
                    eintraege = state.items,
                    bearbeiten = listeBearbeiten,
                    farbe = farbe,
                    onAbhaken = viewModel::onItemCheckedChanged,
                    onText = viewModel::onItemTextChanged,
                    onHinzufuegen = viewModel::eintragHinzufuegen,
                    onVerschieben = viewModel::eintragVerschieben,
                    onEntfernen = { id ->
                        viewModel.removeItem(id)?.let { (eintrag, stelle) ->
                            onUndoRequest(
                                UndoRequest(
                                    if (eintrag.text.isBlank()) {
                                        "Eintrag entfernt"
                                    } else {
                                        "„" + eintrag.text + "\" entfernt"
                                    },
                                ) { viewModel.eintragZurueck(eintrag, stelle) },
                            )
                        }
                    },
                )
                else -> {
                    // Die schwebende Auswahlleiste bekommt „Uebersetzen"
                    // (Phase 16), aber nur, wenn es einen Weg gibt; sonst
                    // bleibt die von Compose.
                    val auswahl by rememberUpdatedState(textwert.selection)
                    val ansicht = LocalView.current
                    val leiste = remember(ansicht) {
                        UebersetzenTextToolbar(ansicht) {
                            viewModel.uebersetzenAnfragen(auswahl.min to auswahl.max)
                        }
                    }
                    CompositionLocalProvider(
                        LocalTextToolbar provides if (uebersetzungVerfuegbar) leiste else LocalTextToolbar.current,
                    ) {
                        AusgezeichnetesFeld(
                            value = textwert,
                            onValueChange = textAendern,
                            placeholder = "Schreib einfach los",
                            textStyle = MaterialTheme.typography.bodyLarge,
                            onFokus = { textFokus = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Nur so viel, dass die letzte Zeile nicht an der unteren Leiste
            // klebt. Frueher standen hier 96 dp -- zusammen mit einem hohen
            // Bild war das sichtbar Leerlauf, durch den man scrollen musste,
            // ohne dass dort etwas stand.
            Spacer(Modifier.height(24.dp))
        }
    }

    }

    if (menueBlattOffen) {
        NotizMenueBlatt(
            anzahlTags = meineTags.size,
            abgleichAn = state.abgleichAn,
            kalenderAn = state.kalenderAn,
            hatErinnerung = state.erinnerungAn != null,
            zeigeOrdner = ordnermodus,
            ordnername = ordnerViewModel.baumzeilen()
                .firstOrNull { it.ordner.id == state.ordnerId }?.ordner?.name,
            imArchiv = state.imOrdnerArchiv,
            onOrdner = {
                menueBlattOffen = false
                ordnerWahlOffen = true
            },
            onArchivieren = {
                menueBlattOffen = false
                viewModel.imOrdnerArchivieren()
            },
            onZurueckholen = {
                menueBlattOffen = false
                viewModel.zurueckholenAnfragen()
            },
            onAbgleich = {
                menueBlattOffen = false
                viewModel.abgleichUmschalten()
            },
            onKalender = {
                menueBlattOffen = false
                viewModel.kalenderUmschalten()
            },
            onJetztSichern = {
                menueBlattOffen = false
                viewModel.jetztSichern()
            },
            onTags = {
                menueBlattOffen = false
                tagDialogOffen = true
            },
            onKopie = {
                menueBlattOffen = false
                viewModel.kopieErstellen()
            },
            onTeilen = {
                menueBlattOffen = false
                // Vor dem Teilen hart speichern, sonst fehlt das zuletzt
                // Getippte im geteilten Text.
                viewModel.speichereJetzt()
                viewModel.teile(context)
            },
            zeigeUebersetzen = uebersetzungVerfuegbar && state.type != NoteType.IMAGE,
            onUebersetzen = {
                menueBlattOffen = false
                viewModel.uebersetzenAnfragen()
            },
            onLoeschen = {
                menueBlattOffen = false
                inDenPapierkorb()
            },
            onSchliessen = { menueBlattOffen = false },
        )
    }

    // Der Uebersetzungsdialog (Phase 16).
    uebersetzungsanfrage?.let { anfrage ->
        UebersetzenDialog(
            anfrage = anfrage,
            systemeinstellungDa = viewModel.sprachpaketEinstellung() != null,
            onSprachen = viewModel::uebersetzungSprachen,
            onStarten = viewModel::uebersetzungStarten,
            onSystemeinstellung = {
                viewModel.sprachpaketEinstellung()?.let { runCatching { it.send() } }
                // Danach steht wieder der Knopf „Uebersetzen" da: Wer aus der
                // Systemeinstellung zurueckkommt, versucht es noch einmal.
                viewModel.uebersetzungSprachen(anfrage.von, anfrage.nach)
            },
            onErsetzen = {
                viewModel.uebersetzungErsetzen()?.let { zurueck ->
                    onUndoRequest(UndoRequest("Übersetzung eingesetzt", zurueck))
                }
            },
            onAnhaengen = viewModel::uebersetzungAnhaengen,
            onAbbrechen = viewModel::uebersetzungAbbrechen,
        )
    }

    // Das Zurueckholen aus dem Archiv des Ordnermodus (14b): dieselbe Frage
    // wie in der Archivansicht, mit Vorschlag und freier Wahl.
    val rueckholAnfrage by viewModel.rueckholAnfrage.collectAsStateWithLifecycle()
    rueckholAnfrage?.let { anfrage ->
        val normalerBaum by produceState(emptyList<Ordnerzeile>(), anfrage) { value = viewModel.normalerBaum() }
        ZurueckholenDialog(
            anfrage = anfrage,
            zeilen = normalerBaum,
            onWahl = viewModel::zurueckholenNach,
            onNeuAnlegen = viewModel::herkunftsordnerNeuAnlegen,
            onAbbrechen = viewModel::zurueckholenAbgebrochen,
        )
    }

    if (ordnerWahlOffen) {
        OrdnerAuswahlDialog(
            ueberschrift = "In einen Ordner legen",
            erklaerung = "Eine Notiz liegt in höchstens einem Ordner. " +
                "Der Hauptordner nimmt sie aus jedem Ordner wieder heraus.",
            zeilen = ordnerViewModel.baumzeilen(),
            ausgeschlossen = state.ordnerId,
            zeigeHauptordner = state.ordnerId != null,
            onAbbrechen = { ordnerWahlOffen = false },
            onWahl = { zielId, name ->
                ordnerWahlOffen = false
                viewModel.setOrdner(zielId, name)
            },
        )
    }

    if (tagDialogOffen) {
        TagAuswahlDialog(
            tags = alleTags,
            zugewiesen = meineTags.toSet(),
            anzahl = 1,
            onUmschalten = viewModel::tagUmschalten,
            onNeuerTag = {
                tagDialogOffen = false
                neuerTagOffen = true
            },
            onFertig = { tagDialogOffen = false },
        )
    }

    if (neuerTagOffen) {
        TagDialog(
            ueberschrift = "Neuer Tag",
            startName = "",
            startFarbe = TAG_FARBEN.first(),
            andereTags = alleTags,
            verwendungen = 0,
            onAbbrechen = { neuerTagOffen = false },
            onSpeichern = { name, farbe ->
                viewModel.neuerTag(name, farbe)
                neuerTagOffen = false
            },
            // Beim Anlegen gibt es weder etwas zusammenzuführen noch zu löschen.
            onZusammenfuehren = null,
            onLoeschen = null,
        )
    }

    if (anhangBlattOffen) {
        AnhangBlatt(
            kameraVorhanden = viewModel.kameraVorhanden,
            onFoto = {
                anhangBlattOffen = false
                viewModel.kameraZiel()?.let { kamera.launch(it) }
            },
            onGalerie = {
                anhangBlattOffen = false
                galerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onSchliessen = { anhangBlattOffen = false },
        )
    }

    offenesBild?.let { id ->
        // Aus dem Zustand nachgeschlagen und nicht mitgereicht: Wird das Bild
        // geloescht, verschwindet es hier von selbst, statt als Kopie
        // stehenzubleiben, die auf eine geloeschte Datei zeigt.
        val anhang = state.bilder.firstOrNull { it.id == id }
        if (anhang == null) {
            offenesBild = null
        } else {
            Bildbetrachter(
                anhang = anhang,
                istHintergrund = anhang.id == state.hintergrundId,
                onHintergrund = {
                    viewModel.setzeHintergrund(
                        if (anhang.id == state.hintergrundId) null else anhang.id,
                    )
                },
                onLoeschen = {
                    offenesBild = null
                    viewModel.bildEntfernen(anhang.id)
                },
                onSchliessen = { offenesBild = null },
            )
        }
    }

    if (farbBlattOffen) {
        FarbAuswahlBlatt(
            aktuell = state.colorId,
            anzahl = 1,
            onWahl = viewModel::setFarbe,
            onSchliessen = viewModel::schliesseFarbAuswahl,
            hintergrund = state.hintergrund,
            onHintergrundWaehlen = {
                viewModel.schliesseFarbAuswahl()
                hintergrundGalerie.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onHintergrundEntfernen = { viewModel.setzeHintergrund(null) },
        )
    }
}

/**
 * Zeigt, wann erinnert wird -- und lässt es ändern oder abbestellen.
 *
 * Steht sichtbar über dem Text und nicht nur als Symbol in der Leiste: eine
 * gestellte Erinnerung ist etwas, das später von selbst passiert, und so etwas
 * darf man beim Aufschlagen der Notiz nicht übersehen.
 */
@Composable
private fun Erinnerungszeile(
    zeitpunkt: Long,
    farbe: Color,
    onAendern: () -> Unit,
    onEntfernen: () -> Unit,
) {
    val vergangen = zeitpunkt < System.currentTimeMillis()

    Surface(
        color = farbe.copy(alpha = 0.10f),
        contentColor = farbe,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onAendern),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                // Vergangene Erinnerungen werden nicht versteckt: sie sind der
                // Beleg dafuer, dass geklingelt hat.
                text = if (vergangen) {
                    "Erinnert am ${zeitpunktText(zeitpunkt)}"
                } else {
                    "Erinnerung am ${zeitpunktText(zeitpunkt)}"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            IconButton(onClick = onEntfernen) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Erinnerung entfernen",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Wie lange die Formatierleiste zum Ein- und Ausblenden braucht. */
private const val LEISTE_MS = 200

/** Datum und Uhrzeit, wie man sie hinschreiben würde. */
private fun zeitpunktText(millis: Long): String =
    SimpleDateFormat("EEEE, d. MMMM, HH:mm", Locale.GERMANY).format(Date(millis))

/**
 * Was passiert, wenn das System exakte Alarme verweigert.
 *
 * Ein Dialog und keine Snackbar: die Erinnerung ist gespeichert, klingelt aber
 * nicht. Das nach drei Sekunden wegrutschen zu lassen wäre die Sorte Fehler,
 * die man erst bemerkt, wenn es zu spät ist.
 */
@Composable
private fun ExakteAlarmeFehlen(onSchliessen: () -> Unit, onEinstellungen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Erinnerung ist gespeichert, klingelt aber nicht") },
        text = {
            Text(
                "Seit Android 14 muss das Wecken zur genauen Zeit einmalig erlaubt " +
                    "werden. Ohne diese Erlaubnis könnte die App nur ungefähr " +
                    "erinnern, irgendwann in der nächsten Stunde -- und das wäre " +
                    "keine Erinnerung, sondern ein Zufall.",
            )
        },
        confirmButton = {
            TextButton(onClick = onEinstellungen) { Text("Einstellungen öffnen") }
        },
        dismissButton = {
            TextButton(onClick = onSchliessen) { Text("Später") }
        },
    )
}

/**
 * Ehrlicher Hinweis fuer Notiztypen, deren Editor noch nicht existiert.
 *
 * Die Typen tauchen im FAB bereits auf, damit die Bedienstruktur vollstaendig
 * sichtbar ist. Ohne diesen Hinweis saehe eine Audio-Notiz aber einfach aus
 * wie eine kaputte Textnotiz.
 */
@Composable
private fun NochNichtGebaut(text: String) {
    // Abgeleitet aus der Textfarbe der Notiz, nicht aus dem Theme: ein fester
    // grauer Kasten auf einer gelben Notiz sieht aus wie hineingerutscht.
    val darauf = LocalContentColor.current
    Surface(
        color = darauf.copy(alpha = 0.10f),
        contentColor = darauf.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/**
 * Die Eintraege einer Listennotiz, in zwei Zustaenden (Phase 18).
 *
 * **Lesen** ist der Normalfall: Ein Eintrag laesst sich abhaken und wieder
 * oeffnen, sonst nichts. Kein X, kein Griff, der Text ist kein Textfeld. Das
 * ist die Liste, mit der man durch den Laden geht.
 *
 * **Bearbeiten** (Stift oben rechts): links der Griff zum Ziehen, rechts das
 * X, und der Text ist wieder ein Textfeld. Vorher stand das X immer da und
 * loeschte sofort; ein Fehlgriff, und der Eintrag war weg. Googles Weg (das X
 * erscheint, sobald der Eintrag den Fokus hat) schuetzt nicht, weil man dann
 * ohnehin im Eintrag steht.
 *
 * **Hinzufuegen geht immer**, in beiden Zustaenden, ueber das Feld unten.
 * Fertig wird ein Eintrag mit der Eingabetaste oder wenn das Feld den Fokus
 * verliert, damit nichts Getipptes verloren geht.
 */
@Composable
private fun Checkliste(
    eintraege: List<NoteItemEntity>,
    bearbeiten: Boolean,
    farbe: NotizFarbe,
    onAbhaken: (id: String, abgehakt: Boolean) -> Unit,
    onText: (id: String, text: String) -> Unit,
    onHinzufuegen: (String) -> Unit,
    onVerschieben: (von: Int, nach: Int) -> Unit,
    onEntfernen: (id: String) -> Unit,
) {
    val textfarbe = farbe.onContainer
    // Die Kaestchen tragen die Notizfarbe, nicht `primary`: Sie sitzen auf der
    // Notiz, und auf einer gelben Karte waere Blau ein Fremdkoerper.
    val kaestchen = CheckboxDefaults.colors(
        checkedColor = textfarbe,
        uncheckedColor = textfarbe.copy(alpha = 0.7f),
        checkmarkColor = farbe.container,
    )

    if (bearbeiten) {
        ZiehbareSpalte(
            eintraege = eintraege,
            kennung = { it.id },
            onVerschieben = onVerschieben,
            modifier = Modifier.fillMaxWidth(),
        ) { item, griff, gezogen ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (gezogen) textfarbe.copy(alpha = 0.10f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = "Eintrag verschieben",
                    tint = textfarbe.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .then(griff),
                )
                NahtlosesFeld(
                    value = item.text,
                    onValueChange = { onText(item.id, it) },
                    placeholder = "Eintrag",
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEntfernen(item.id) }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Eintrag entfernen",
                        tint = textfarbe.copy(alpha = 0.72f),
                    )
                }
            }
        }
    } else {
        eintraege.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAbhaken(item.id, !item.isChecked) },
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onAbhaken(item.id, it) },
                    colors = kaestchen,
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    // Abgehakt heisst durchgestrichen und blasser, wie ueberall
                    // bei Listen; die Farbe bleibt die der Notiz.
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    color = if (item.isChecked) textfarbe.copy(alpha = 0.6f) else textfarbe,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp, bottom = 12.dp, end = 16.dp),
                )
            }
        }
    }

    NeuerEintrag(textfarbe = textfarbe, onHinzufuegen = onHinzufuegen)
}

/** Das Feld „Eintrag hinzufuegen" unter der Liste, in beiden Zustaenden. */
@Composable
private fun NeuerEintrag(textfarbe: Color, onHinzufuegen: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val uebernehmen = {
        if (text.isNotBlank()) {
            onHinzufuegen(text)
            text = ""
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = textfarbe.copy(alpha = 0.72f),
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
        )
        NahtlosesFeld(
            value = text,
            onValueChange = { text = it },
            placeholder = "Eintrag hinzufügen",
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                // Verlaesst man das Feld mit Text darin, wird er uebernommen:
                // nichts Getipptes darf verloren gehen.
                .onFocusChanged { if (!it.isFocused) uebernehmen() },
            einzeilig = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { uebernehmen() }),
        )
    }
}

/**
 * Textfeld ohne Rahmen, ohne Fuellfarbe, ohne Unterstrich -- es soll aussehen
 * wie Text auf der Karte, nicht wie ein Formular.
 */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * Wie [NahtlosesFeld], aber mit Auswahl und Auszeichnung.
 *
 * Eigene Fassung statt eines weiteren Parameters an [NahtlosesFeld]: Die beiden
 * unterscheiden sich im Werttyp (`String` gegen `TextFieldValue`), und ein Feld,
 * das beides kann, haette zwei sich ausschliessende Zustaende.
 */
@Composable
private fun AusgezeichnetesFeld(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    onFokus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textfarbe = LocalContentColor.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFokus(it.isFocused) },
        placeholder = {
            Text(text = placeholder, style = textStyle, color = textfarbe.copy(alpha = 0.6f))
        },
        textStyle = textStyle,
        visualTransformation = remember { Auszeichnungsanzeige() },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = textfarbe,
            focusedTextColor = textfarbe,
            unfocusedTextColor = textfarbe,
            selectionColors = LocalTextSelectionColors.current,
        ),
    )
}

@Composable
private fun NahtlosesFeld(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    anzeige: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    einzeilig: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // Auf einer eingefaerbten Karte waere onSurface die falsche Farbe -- der
    // Text muss zur Flaeche passen, auf der er steht.
    val textfarbe = LocalContentColor.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = einzeilig,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        placeholder = {
            Text(
                text = placeholder,
                style = textStyle,
                color = textfarbe.copy(alpha = 0.6f),
            )
        },
        textStyle = textStyle,
        visualTransformation = anzeige,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = textfarbe,
            focusedTextColor = textfarbe,
            unfocusedTextColor = textfarbe,
            selectionColors = LocalTextSelectionColors.current,
        ),
    )
}
