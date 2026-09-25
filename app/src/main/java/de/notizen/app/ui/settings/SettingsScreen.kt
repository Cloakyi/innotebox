package de.notizen.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.delay
import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.model.Geraetestand
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.kalender.KalenderViewModel
import de.notizen.app.ui.ordner.OrdnungViewModel
import de.notizen.app.kalender.Kalenderwahl
import de.notizen.app.kalender.kalenderbeschriftung
import de.notizen.app.sync.SyncViewModel
import de.notizen.app.ui.theme.ThemeViewModel
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.ThemeWahl
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.model.Uebersetzungsweg
import de.notizen.core.data.model.Sperrverzoegerung
import de.notizen.app.sicherheit.Entsperrung
import de.notizen.app.sicherheit.alsActivity
import de.notizen.core.data.model.WischZiel

/**
 * Einstellungen.
 *
 * Jede Zeile zeigt ihren WIRKLICHEN Zustand, keine feste Beschriftung. Die
 * Synchronisierungszeile stand lange auf „Nicht verbunden", ganz gleich was
 * eingestellt war -- ein Text, den beim Bauen niemand nachzieht, ist irgendwann
 * eine Behauptung ueber etwas, das es nicht mehr gibt.
 */
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    onSync: () -> Unit,
    onBackup: () -> Unit,
    onProtokoll: () -> Unit,
    onLizenzen: () -> Unit,
    /** Welcher Abschnitt beim Aufgehen angesprungen und kurz hervorgehoben wird (Phase 15). */
    hervorheben: String? = null,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    gestenViewModel: GestenViewModel = hiltViewModel(),
    kiViewModel: KiEinstellungenViewModel = hiltViewModel(),
    aufraeumViewModel: AufraeumViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel(),
    kalenderViewModel: KalenderViewModel = hiltViewModel(),
    ordnungViewModel: OrdnungViewModel = hiltViewModel(),
    sicherheitViewModel: SicherheitViewModel = hiltViewModel(),
) {
    val theme by themeViewModel.state.collectAsStateWithLifecycle()
    val gesten by gestenViewModel.state.collectAsStateWithLifecycle()
    val ki by kiViewModel.state.collectAsStateWithLifecycle()
    val aufraeumen by aufraeumViewModel.state.collectAsStateWithLifecycle()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val kalender by kalenderViewModel.lage.collectAsStateWithLifecycle()
    val kalenderAuswahl by kalenderViewModel.auswahl.collectAsStateWithLifecycle()
    val ordnermodus by ordnungViewModel.ordnermodus.collectAsStateWithLifecycle()
    val sicherheit by sicherheitViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var erklaerungOffen by remember { mutableStateOf(false) }
    var titelpflichtFrage by remember { mutableStateOf(false) }
    var netzDialog by remember { mutableStateOf(false) }

    // DER SPRUNG ZUM KI-SCHALTER (Phase 15): vom ausgegrauten Transkript her
    // kommt man mit `hervorheben = "ki"` hierher. Die Liste scrollt zum
    // Schalter, und er bekommt fuer eine Sekunde eine Flaeche in `primary`,
    // die verblasst. Dieselbe Mechanik wie das Aufblitzen der Karte in 14c.
    val scrollZustand = rememberScrollState()
    var kiSchalterY by remember { mutableIntStateOf(-1) }
    val kiHervorhebung = remember { Animatable(0f) }
    var hervorgehoben by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(kiSchalterY, hervorheben) {
        if (hervorheben != HERVORHEBEN_KI || hervorgehoben || kiSchalterY < 0) return@LaunchedEffect
        hervorgehoben = true
        scrollZustand.animateScrollTo(maxOf(0, kiSchalterY - 120))
        kiHervorhebung.snapTo(1f)
        delay(400)
        kiHervorhebung.animateTo(0f, tween(700))
    }

    // Die Liste der Kalender kennt keinen Fluss, der sich meldet. Einmal beim
    // Aufgehen des Bildschirms holen reicht: Ein Konto kommt nicht waehrend des
    // Hinsehens dazu.
    LaunchedEffect(Unit) { kalenderViewModel.kalenderLaden() }

    // Was das Geraet an KI kann, wird hier gefragt und nicht geraten. Auf einem
    // Geraet ohne AICore steht sonst ein Schalter, der nichts bewirkt, und
    // niemand erfaehrt warum.
    LaunchedEffect(Unit) { kiViewModel.geraetPruefen() }

    // Der Kalender wird zur Laufzeit erfragt, und erst dann, wenn jemand den
    // Schalter umlegt. Eine App, die beim Start nach allem fragt, was sie
    // irgendwann brauchen koennte, bekommt zu Recht ein Nein.
    val kalenderErlaubnis = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { ergebnis ->
        if (ergebnis.values.all { it }) kalenderViewModel.erlaubnisErteilt()
    }

    if (erklaerungOffen) {
        AlertDialog(
            onDismissRequest = { erklaerungOffen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            title = { Text("Automatisch archivieren") },
            text = {
                Text(
                    "Nachts, während das Gerät lädt, wandern Notizen aus Eingang und " +
                        "Workspace ins Archiv. Du entscheidest, ob nach Alter, nach " +
                        "Menge oder nach beidem.\n\n" +
                        "Gelöscht wird dabei nichts. Jeder Lauf steht im Protokoll und " +
                        "lässt sich vollständig zurücknehmen. Jede Notiz landet dann " +
                        "wieder in der Stufe, in der sie vorher war.\n\n" +
                        "Favoriten und Notizen mit einer offenen Erinnerung bleiben, " +
                        "wo sie sind.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        aufraeumViewModel.setAn(true)
                        erklaerungOffen = false
                    },
                ) { Text("Einschalten") }
            },
            dismissButton = {
                TextButton(onClick = { erklaerungOffen = false }) { Text("Abbrechen") }
            },
        )
    }
    if (titelpflichtFrage) {
        AlertDialog(
            onDismissRequest = { titelpflichtFrage = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            title = { Text("Titelpflicht abschalten?") },
            text = {
                Text(
                    "Im Archiv wird über Titel gesucht. Eine Notiz ohne Titel ist dort " +
                        "praktisch verloren: Du findest sie nur noch über ihren Text, und " +
                        "in der Liste steht „Ohne Titel\". Ohne die Pflicht fragt die App " +
                        "beim Verschieben nicht mehr nach.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        kiViewModel.setTitelpflicht(false)
                        titelpflichtFrage = false
                    },
                ) { Text("Ja, ich bin mir sicher") }
            },
            dismissButton = {
                TextButton(onClick = { titelpflichtFrage = false }) { Text("Abbrechen") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollZustand),
    ) {
        Abschnitt("Darstellung")
        AuswahlMenue(
            titel = "Erscheinungsbild",
            gewaehlt = theme.wahl,
            optionen = ThemeWahl.entries,
            beschriftung = { it.beschriftung() },
            onWahl = themeViewModel::setWahl,
        )
        EchterSchalter(
            titel = "Dynamic Color",
            hinweis = if (theme.dynamicColor) {
                "Farben kommen vom Hintergrundbild"
            } else {
                "Festes Farbschema der App"
            },
            an = theme.dynamicColor,
            onAendern = themeViewModel::setDynamicColor,
        )

        Abschnitt("Ordnung")
        EchterSchalter(
            titel = "Ordner statt Stufen",
            hinweis = if (ordnermodus) {
                "Du sortierst selbst in Ordner"
            } else {
                "Der Fluss von Eingang über Workspace ins Archiv"
            },
            an = ordnermodus,
            onAendern = ordnungViewModel::setOrdnermodus,
        )
        Hinweiszeile(
            if (ordnermodus) {
                "Eingang, Workspace und Archiv sind ausgeblendet, und das " +
                    "automatische Archivieren ruht. Jede Notiz behält ihre Stufe " +
                    "trotzdem. Schaltest du zurück, liegt alles wieder dort, wo es war."
            } else {
                "Mit dem Ordnersystem legst du deine Ordnung selbst an, " +
                    "statt dem Fluss zu folgen. Deine Notizen bleiben dabei " +
                    "vollständig erhalten."
            },
        )
        // Die Titelpflicht (Phase 15). Ausschalten fragt nach, Einschalten
        // nicht: Wer die Sicherung wieder einschaltet, braucht keine Warnung.
        EchterSchalter(
            titel = "Titel beim Verschieben verlangen",
            hinweis = if (ki.titelpflicht) {
                "Ab dem Workspace fragt die App nach einem Titel"
            } else {
                "Aus. Notizen wandern auch ohne Titel weiter"
            },
            an = ki.titelpflicht,
            onAendern = { an -> if (an) kiViewModel.setTitelpflicht(true) else titelpflichtFrage = true },
        )

        Abschnitt("Wischgesten auf Notizen")
        Text(
            text = "Was passiert, wenn du eine Karte zur Seite ziehst. " +
                "Der Papierkorb rastet ein und fragt mit einem zweiten Tipp nach.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )
        AuswahlMenue(
            titel = "Nach rechts ziehen",
            gewaehlt = gesten.rechts,
            optionen = WischZiel.waehlbar,
            beschriftung = { it.beschriftung() },
            onWahl = gestenViewModel::setRechts,
        )
        AuswahlMenue(
            titel = "Nach links ziehen",
            gewaehlt = gesten.links,
            optionen = WischZiel.waehlbar,
            beschriftung = { it.beschriftung() },
            onWahl = gestenViewModel::setLinks,
        )

        Abschnitt("Wischgesten im Papierkorb")
        Text(
            text = "Im Papierkorb gibt es keine Stufen, dafür das Zurückholen und das " +
                "endgültige Löschen. Beides rastet ein und fragt mit einem zweiten Tipp nach.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
        )
        AuswahlMenue(
            titel = "Nach rechts ziehen",
            gewaehlt = gesten.papierkorbRechts,
            optionen = WischZiel.imPapierkorbWaehlbar,
            beschriftung = { it.beschriftung() },
            onWahl = gestenViewModel::setPapierkorbRechts,
        )
        AuswahlMenue(
            titel = "Nach links ziehen",
            gewaehlt = gesten.papierkorbLinks,
            optionen = WischZiel.imPapierkorbWaehlbar,
            beschriftung = { it.beschriftung() },
            onWahl = gestenViewModel::setPapierkorbLinks,
        )

        Abschnitt("Automatische Archivierung")
        if (ordnermodus) {
            Hinweiszeile(
                "Diese Einstellung ruht, solange die Ordner an sind. " +
                    "Sie schiebt Notizen zwischen Stufen, die gerade niemand sieht.",
            )
        }
        EchterSchalter(
            titel = "Automatisch archivieren",
            hinweis = if (aufraeumen.an) {
                "Läuft nachts, während das Gerät lädt. Favoriten bleiben stehen"
            } else {
                "Aus. Es wird nichts von selbst verschoben"
            },
            an = aufraeumen.an,
            // Beim EINSCHALTEN erst erklären, beim Ausschalten nicht. Wer eine
            // Automatik abstellt, die ungefragt Notizen verschiebt, braucht
            // keine Rueckfrage. Die Entscheidung ist ja schon gefallen.
            onAendern = { an -> if (an) erklaerungOffen = true else aufraeumViewModel.setAn(false) },
        )

        // Nur wenn eingeschaltet: Sieben Auswahlfelder, die nichts bewirken,
        // sind schlimmer als keine -- man stellt etwas ein und wundert sich.
        if (aufraeumen.an) {
            listOf(
                Triple(Stage.INBOX, "Eingang", aufraeumen.eingang),
                Triple(Stage.WORKSPACE, "Workspace", aufraeumen.workspace),
            ).forEach { (stufe, name, grenzen) ->
                AuswahlMenue(
                    titel = "$name: nicht geöffnet seit",
                    gewaehlt = grenzen.tage,
                    optionen = AufraeumViewModel.TAGE,
                    beschriftung = { AufraeumViewModel.tageText(it) },
                    onWahl = { aufraeumViewModel.setAlter(stufe, it) },
                )
                AuswahlMenue(
                    titel = "$name: höchstens",
                    gewaehlt = grenzen.menge,
                    optionen = AufraeumViewModel.MENGEN,
                    beschriftung = { AufraeumViewModel.mengeText(it) },
                    onWahl = { aufraeumViewModel.setMenge(stufe, it) },
                )
            }
        }

        Eintrag(
            titel = "Protokoll der Läufe",
            wert = if (aufraeumen.laeufe.isEmpty()) {
                "Noch keine Läufe"
            } else {
                "${aufraeumen.laeufe.size} Läufe"
            },
            onClick = onProtokoll,
        )

        Abschnitt("Papierkorb")
        AuswahlMenue(
            titel = "Automatisch leeren",
            gewaehlt = aufraeumen.papierkorbTage,
            optionen = AufraeumViewModel.PAPIERKORB_TAGE,
            beschriftung = { AufraeumViewModel.tageText(it) },
            onWahl = aufraeumViewModel::setPapierkorbFrist,
        )
        Text(
            text = "Was länger im Papierkorb liegt, wird endgültig gelöscht. Ohne " +
                "Rückfrage und ohne Rückweg. Die Frist selbst ist die Gelegenheit, " +
                "es sich anders zu überlegen. Gezählt wird ab dem Wegwerfen, nicht " +
                "ab dem letzten Bearbeiten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )

        Abschnitt("Sprache und KI")
        AuswahlMenue(
            titel = "Sprache der Aufnahme",
            gewaehlt = ki.sprache,
            optionen = Transkriptsprache.entries,
            beschriftung = { it.beschriftung },
            onWahl = kiViewModel::setSprache,
        )
        Text(
            text = "Gilt für das Aufnehmen und für das Umwandeln in Text. Ob eine " +
                "Sprache auf diesem Gerät bereitsteht, sagt dir die Notiz beim " +
                "Umwandeln.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        if (ki.spracherkennungDa == false) {
            Hinweiszeile(
                "Auf diesem Gerät steht keine Spracherkennung bereit. " +
                    "Aufnehmen funktioniert trotzdem, nur der Text dazu entsteht nicht. " +
                    "Die Umwandlung in Text bringt bisher nur Google mit, und zwar auf " +
                    "wenigen Geräten.",
            )
        }

        Box(
            modifier = Modifier
                .onGloballyPositioned { kiSchalterY = it.positionInParent().y.toInt() }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f * kiHervorhebung.value)),
        ) {
            EchterSchalter(
                titel = "Von der KI aufbereiten lassen",
                hinweis = if (ki.aktiv) {
                    "Titelvorschläge, das Umwandeln in Text und das Aufbereiten von Transkripten"
                } else {
                    "Aus. Aufnahmen bleiben Aufnahmen, Titel kommen aus dem Text"
                },
                an = ki.aktiv,
                onAendern = kiViewModel::setAktiv,
            )
        }

        // DER GERAETESTAND (Phase 15): einmal gemessen, hier gezeigt. Kein
        // Schalter, eine Auskunft. Solange nie gemessen wurde, steht das da,
        // und nicht eine geratene Zusage.
        Hinweiszeile(geraetestandText(ki.geraetestand))

        // Die Messzeile "Uebersetzung auf dem Geraet" stand hier vom 2026-09-03
        // bis zum 2026-09-14. Sie hat ihre Frage beantwortet (Pixel: Dienst da,
        // Xiaomi: keiner) und kam danach wieder raus. Die
        // Messung selbst (`KiEinstellung.uebersetzung`) bleibt fuer Phase 16.

        // Der Schalter bleibt bedienbar, auch wenn das Geraet nichts kann. Ihn
        // auszugrauen hiesse zu behaupten, die Einstellung sei falsch -- sie
        // ist nur gerade wirkungslos, und das steht hier.
        if (ki.textkiDa == false) {
            Hinweiszeile(
                "Dieses Gerät bietet die KI auf dem Gerät nicht an. " +
                    "Titel schlägt die App dann aus dem Text der Notiz vor, " +
                    "und das Aufbereiten von Transkripten erscheint gar nicht erst. " +
                    "Alles andere funktioniert unverändert.",
            )
        }

        // DAS NETZ-OPT-IN (Phase 15). Einschalten nur ueber den Dialog mit den
        // Bedingungen (bis unten lesen, zehn Sekunden); Ausschalten sofort.
        EchterSchalter(
            titel = "Verarbeitung im Netz",
            hinweis = if (ki.netzErlaubt) {
                "An. Sprachpakete für die Übersetzung dürfen aus dem Netz geladen werden"
            } else {
                "Aus. Deine Notizen verlassen das Gerät nicht. Zum Einschalten liest du erst die Bedingungen"
            },
            an = ki.netzErlaubt,
            onAendern = { an -> if (an) netzDialog = true else kiViewModel.netzErlauben(false) },
        )
        if (netzDialog) {
            NetzbedingungenDialog(
                onZustimmen = {
                    netzDialog = false
                    kiViewModel.netzErlauben(true)
                },
                onAbbrechen = { netzDialog = false },
            )
        }

        Abschnitt("Übersetzung")
        Hinweiszeile(
            "Übersetzt wird im Editor: über die Auswahl im Text oder über das Menü der " +
                "Notiz. Hier wählst du den Weg. Was dieses Gerät nicht kann, steht blass da.",
        )
        Uebersetzungsweg.entries.forEach { weg ->
            val stand = ki.wege[weg]
            Wegzeile(
                titel = weg.beschriftung,
                hinweis = stand?.hinweis ?: "Wird geprüft …",
                gewaehlt = ki.uebersetzungsweg == weg,
                verfuegbar = stand?.verfuegbar ?: false,
                onWahl = { kiViewModel.setUebersetzungsweg(weg) },
            )
        }


        Abschnitt("Sicherheit")
        // DIE SPERRE (Phase 19): eine Funktion der App, nicht des Systems.
        // Ein- und Ausschalten nur nach einer erfolgreichen Entsperrung: Wer
        // sich nicht ausweisen kann, sperrt sich sonst aus, und wer die App
        // offen vorfindet, soll die Sperre nicht abschalten koennen.
        val hindernis = remember { Entsperrung.hindernis(context) }
        val ausweisen: (String, () -> Unit) -> Unit = { grund, dann ->
            val activity = context.alsActivity()
            if (activity != null) {
                Entsperrung.anfordern(
                    activity = activity,
                    titel = "Ausweisen",
                    untertitel = grund,
                    onErfolg = dann,
                    onAbbruch = {},
                )
            }
        }
        EchterSchalter(
            titel = "App entsperren",
            hinweis = when {
                hindernis != null -> hindernis
                sicherheit.sperreAn -> "An. Beim Öffnen fragt die App nach Fingerabdruck, Gesicht oder der Bildschirmsperre"
                else -> "Aus. Wer das Handy hat, sieht die Notizen"
            },
            an = sicherheit.sperreAn,
            onAendern = { an ->
                if (hindernis == null) {
                    ausweisen(if (an) "Zum Einschalten der Sperre" else "Zum Ausschalten der Sperre") {
                        sicherheitViewModel.setSperreAn(an)
                    }
                }
            },
        )
        if (sicherheit.sperreAn) {
            AuswahlMenue(
                titel = "Sperren nach",
                gewaehlt = sicherheit.verzoegerung,
                optionen = Sperrverzoegerung.entries,
                beschriftung = { it.beschriftung },
                onWahl = sicherheitViewModel::setVerzoegerung,
            )
            Hinweiszeile("Gezählt wird die Zeit im Hintergrund. Beim Start der App fragt sie immer.")
        }
        EchterSchalter(
            titel = "Aufnahmeschutz",
            hinweis = if (sicherheit.aufnahmeschutzAn) {
                "An. Keine Bildschirmfotos, keine Bildschirmaufnahme, kein Vorschaubild in den letzten Apps"
            } else {
                "Aus. Bildschirmfotos der App sind möglich"
            },
            an = sicherheit.aufnahmeschutzAn,
            onAendern = { an ->
                // Abschalten nur nach Entsperrung, solange die Sperre an ist.
                if (!an && sicherheit.sperreAn) {
                    ausweisen("Zum Abschalten des Aufnahmeschutzes") {
                        sicherheitViewModel.setAufnahmeschutz(false)
                    }
                } else {
                    sicherheitViewModel.setAufnahmeschutz(an)
                }
            },
        )

        Abschnitt("Abgleich")
        Eintrag(
            titel = "Google Drive",
            wert = when {
                sync.prueft -> "Wird geprüft"
                !sync.verbunden -> "Nicht verbunden"
                !sync.automatisch -> "Verbunden, Abgleich nur von Hand"
                else -> "Verbunden, Abgleich nach jeder Änderung"
            },
            onClick = onSync,
        )

        Abschnitt("Kalender")
        EchterSchalter(
            titel = "Erinnerungen im Kalender",
            hinweis = if (kalender.an) {
                "Jede Notiz mit Erinnerung bekommt einen Termin"
            } else {
                "Aus. Erinnerungen klingeln weiter, stehen aber nirgends"
            },
            an = kalender.an,
            onAendern = { an ->
                when {
                    !an -> kalenderViewModel.setAn(false)
                    kalenderViewModel.erlaubt() -> kalenderViewModel.setAn(true)
                    else -> kalenderErlaubnis.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                }
            },
        )

        if (kalender.an) {
            if (kalenderAuswahl.isEmpty()) {
                Hinweiszeile(
                    "Auf diesem Gerät ist kein Kalender eingerichtet, in den " +
                        "geschrieben werden darf. Richte in der Kalender-App ein " +
                        "Konto ein und komm dann hierher zurück.",
                )
            } else {
                Kalenderauswahl(
                    auswahl = kalenderAuswahl,
                    gewaehlt = kalender.gewaehlt,
                    onWahl = kalenderViewModel::setKalender,
                )

                val gewaehlterKalender = kalenderAuswahl.firstOrNull { it.id == kalender.gewaehlt }
                if (gewaehlterKalender != null && !gewaehlterKalender.synchronisiert) {
                    Hinweiszeile(
                        "Achtung: Dieser Kalender wird gerade nicht mit dem Konto " +
                            "abgeglichen. Die Termine bleiben dann auf diesem Gerät " +
                            "und tauchen in Google Kalender nicht auf.",
                    )
                }
            }

            Hinweiszeile(
                "Der Termin dauert eine halbe Stunde und trägt den Titel der " +
                    "Notiz. Tippst du ihn in Google Kalender an, öffnet sich die " +
                    "Notiz. Einzelne Notizen kannst du im Menü hinter den drei " +
                    "Punkten ausnehmen.",
            )
        }

        Abschnitt("Daten")
        Eintrag("Sichern und Wiederherstellen", "Export und Import als .notesbak", onBackup)

        Abschnitt("Über die App")
        UeberDieApp(onLizenzen)

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Version, Paketname und die Webseite.
 *
 * Die Version kommt aus dem Paket, nicht aus einer Konstanten im Code: Sonst
 * stuende hier irgendwann eine Nummer, die niemand nachgezogen hat. Der Name
 * traegt die Stufe (Alpha, Beta, Vollversion) und die Zaehlung, wie in
 * `app/build.gradle.kts` erklaert.
 */
@Composable
private fun UeberDieApp(onLizenzen: () -> Unit) {
    val context = LocalContext.current
    val paket = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val version = paket?.versionName ?: "unbekannt"
    val build = paket?.longVersionCode?.toString() ?: "unbekannt"

    Angabe("Version", "$version, Build $build")
    Angabe("Paketname", context.packageName)
    Eintrag(
        titel = "Webseite",
        wert = WEBSEITE.removePrefix("https://"),
        onClick = {
            // Die Adresse ohne Pfad geht immer in den Browser: Die App selbst
            // beansprucht nur https://innotebox.de/app (AndroidManifest.xml).
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, WEBSEITE.toUri())) }
        },
    )
    Eintrag("Lizenzen", "Freie Software und die Bibliotheken darin", onLizenzen)
}

private const val WEBSEITE = "https://innotebox.de"

/** Eine Zeile mit Titel und Wert, ohne Handlung dahinter. */
@Composable
private fun Angabe(titel: String, wert: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Die Auswahl des Kalenders.
 *
 * **Zwei Zeilen je Eintrag, nicht eine.** Der Hauptkalender eines Google-Kontos
 * heisst beim Anbieter genau wie die Adresse des Kontos. In einer einzeiligen
 * Liste stand deshalb eine Mailadresse zwischen Namen wie „Arbeit", und es sah
 * aus, als koenne man dort Konten waehlen statt Kalender. Aufgefallen am
 * 2026-08-23. Jetzt steht oben, WAS es ist, und darunter, ZU WEM es gehoert.
 *
 * Ein eigenes Menue statt [AuswahlMenue]: Das ist fuer einzeilige Werte gebaut,
 * und eine zweite Zeile hineinzuzwaengen machte es fuer alle anderen
 * Einstellungen komplizierter.
 */
@Composable
private fun Kalenderauswahl(
    auswahl: List<Kalenderwahl>,
    gewaehlt: Long,
    onWahl: (Long) -> Unit,
) {
    var offen by remember { mutableStateOf(false) }
    val aktuell = auswahl.firstOrNull { it.id == gewaehlt }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { offen = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (aktuell == null) "Bitte wählen" else kalenderbeschriftung(aktuell),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                aktuell?.konto?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            auswahl.forEach { wahl ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(kalenderbeschriftung(wahl))
                            if (wahl.konto.isNotBlank()) {
                                Text(
                                    text = wahl.konto,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onWahl(wahl.id)
                        offen = false
                    },
                    trailingIcon = {
                        // Der Platz bleibt immer frei, sonst wandern die Texte
                        // beim Umschalten.
                        if (wahl.id == gewaehlt) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

/** Der Wert von `hervorheben`, der zum KI-Schalter springt. */
const val HERVORHEBEN_KI = "ki"

/** Der Satz zum gemerkten Geraetestand in den Einstellungen (Phase 15). */
private fun geraetestandText(stand: Geraetestand?): String {
    if (stand == null) return "Was dieses Gerät an KI kann, wird beim nächsten Start geprüft."
    if (stand.allesVerfuegbar) return "Dieses Gerät bietet alle KI-Funktionen dieser App."
    val teile = buildList {
        fun satz(name: String, f: Faehigkeit) = when (f) {
            Faehigkeit.VERFUEGBAR -> null
            Faehigkeit.LADBAR -> "$name lässt sich nachladen"
            Faehigkeit.NICHT -> "$name gibt es auf diesem Gerät nicht"
        }
        satz("Die Spracherkennung", stand.spracherkennung)?.let { add(it) }
        satz("Die KI für Titel und Aufbereitung", stand.textki)?.let { add(it) }
        satz("Die Übersetzung des Systems", stand.uebersetzung)?.let { add(it) }
    }
    return teile.joinToString(". ") + ". Alles andere funktioniert unverändert."
}

/**
 * Ein Weg zum Uebersetzen: Radioknopf, Name, Satz dazu (Phase 16).
 *
 * Nicht verfuegbar heisst blass und nicht antippbar. Der Satz sagt, warum,
 * und wo noetig, was man dagegen tun kann.
 */
@Composable
private fun Wegzeile(
    titel: String,
    hinweis: String,
    gewaehlt: Boolean,
    verfuegbar: Boolean,
    onWahl: () -> Unit,
) {
    val deckung = if (verfuegbar) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = verfuegbar, onClick = onWahl)
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
    ) {
        RadioButton(selected = gewaehlt, onClick = onWahl, enabled = verfuegbar)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = titel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = deckung),
            )
            Text(
                text = hinweis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = deckung),
            )
        }
    }
}

/** Ein erklaerender Satz unter einer Einstellung. */
@Composable
private fun Hinweiszeile(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun Abschnitt(titel: String) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = titel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Eintrag(titel: String, wert: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Eine Einstellung mit mehreren Moeglichkeiten, als aufklappbares Menue.
 *
 * Vorher stand jede Moeglichkeit als eigene Zeile mit Radioknopf da. Bei drei
 * Einstellungen mit je drei bis vier Werten waren das elf Zeilen fuer drei
 * Entscheidungen, und die Liste las sich wie ein Formular. Eingeklappt zeigt
 * jede Einstellung nur noch, worauf sie steht.
 */
@Composable
private fun <T> AuswahlMenue(
    titel: String,
    gewaehlt: T,
    optionen: List<T>,
    beschriftung: (T) -> String,
    onWahl: (T) -> Unit,
) {
    var offen by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { offen = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = titel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = beschriftung(gewaehlt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            optionen.forEach { wert ->
                DropdownMenuItem(
                    text = { Text(beschriftung(wert)) },
                    onClick = {
                        onWahl(wert)
                        offen = false
                    },
                    trailingIcon = {
                        // Nur beim gewaehlten Wert, aber der Platz bleibt immer
                        // frei: sonst wandern die Texte beim Umschalten.
                        if (wert == gewaehlt) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EchterSchalter(
    titel: String,
    hinweis: String,
    an: Boolean,
    onAendern: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAendern(!an) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(hinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = an, onCheckedChange = onAendern)
    }
}

