package de.notizen.app.kalender

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.outlined.Image
import de.notizen.app.bild.rememberBild
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import de.notizen.app.ui.components.tastaturAbstand
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.core.data.kalender.Serie
import de.notizen.core.data.kalender.TAG_MS
import de.notizen.core.data.model.Terminart
import java.time.LocalDate

/** Welcher Dialog des Editors offen ist. */
private enum class Wahl { START_TAG, START_ZEIT, END_TAG, END_ZEIT, WIEDERHOLUNG, EIGENE_WIEDERHOLUNG, ERINNERUNG, FARBE, VERWERFEN }

/** Hoehe des Kopfes im Blatt: Pfeil, Schliessen und Speichern. */
val BLATTKOPF = 76.dp

/**
 * Der Editor eines Termins als Blatt ueber dem Kalender. Halb offen sieht man
 * den Kalender dahinter und den Block des Termins darin, den man dort mit
 * dem Finger verschieben kann.
 */
@Composable
fun TerminEditorBlatt(
    viewModel: TerminEditorViewModel,
    stufe: Blattstufe,
    onStufe: (Blattstufe) -> Unit,
    onZu: () -> Unit,
) {
    val entwurfOderNull by viewModel.entwurf.collectAsStateWithLifecycle()
    val kalender by viewModel.kalender.collectAsStateWithLifecycle()
    val speichert by viewModel.speichert.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fokus = LocalFocusManager.current
    val tastatur = LocalSoftwareKeyboardController.current
    var wahl by remember { mutableStateOf<Wahl?>(null) }
    var pruefung by remember { mutableIntStateOf(0) }

    // Erst die Tastatur weg, dann zu: Ein Feld mit Fokus hielte das Blatt sonst fest.
    LaunchedEffect(viewModel) {
        viewModel.fertig.collect {
            fokus.clearFocus()
            tastatur?.hide()
            onStufe(Blattstufe.ZU)
        }
    }
    // Nach der Rueckkehr aus den Systemeinstellungen neu fragen.
    LifecycleResumeEffect(Unit) {
        pruefung++
        onPauseOrDispose { }
    }

    val zurueck = {
        if (viewModel.geaendert()) wahl = Wahl.VERWERFEN else onStufe(Blattstufe.ZU)
    }
    BackHandler(enabled = stufe != Blattstufe.ZU, onBack = zurueck)

    Ziehblatt(
        stufe = stufe,
        onStufe = onStufe,
        darfZu = {
            if (viewModel.geaendert()) {
                wahl = Wahl.VERWERFEN
                false
            } else {
                true
            }
        },
        onZu = onZu,
        kopf = { ziehen ->
            Column(ziehen.fillMaxWidth().height(BLATTKOPF)) {
                Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (stufe == Blattstufe.VOLL) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                        contentDescription = if (stufe == Blattstufe.VOLL) "Blatt verkleinern" else "Blatt vergrößern",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onStufe(if (stufe == Blattstufe.VOLL) Blattstufe.HALB else Blattstufe.VOLL) }
                            .padding(horizontal = 16.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = zurueck) { Icon(Icons.Outlined.Close, contentDescription = "Schließen") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = viewModel::speichern,
                        enabled = entwurfOderNull != null && !speichert,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Speichern") }
                }
            }
        },
        inhalt = { flaeche ->
            val e = entwurfOderNull
            if (e == null) {
                Box(flaeche)
            } else {
                Editorfelder(e, kalender, pruefung, viewModel, context, flaeche) { wahl = it }
            }
        },
    )

    val e = entwurfOderNull ?: return
    Dialoge(e, wahl, kalender, viewModel, onWahl = { wahl = it }, onVerwerfen = {
        wahl = null
        onStufe(Blattstufe.ZU)
    })
}

/** Die Felder des Editors. */
@Composable
private fun Editorfelder(
    e: Entwurf,
    kalender: List<de.notizen.core.data.db.entity.EventCalendarEntity>,
    pruefung: Int,
    viewModel: TerminEditorViewModel,
    context: android.content.Context,
    modifier: Modifier,
    onWahl: (Wahl) -> Unit,
) {
    val aktuellerKalender = kalender.firstOrNull { it.id == e.kalenderId }
    val farbe = Color(e.farbe ?: aktuellerKalender?.colorArgb ?: FARBE_STANDARD)
    Column(
        modifier
            .tastaturAbstand()
            .verticalScroll(rememberScrollState()),
    ) {
        TextField(
            value = e.titel,
            onValueChange = { t -> viewModel.aendern { it.copy(titel = t) } },
            placeholder = {
                Text(
                    if (e.art == Terminart.GEBURTSTAG) "Wessen Geburtstag?" else "Titel hinzufügen",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            textStyle = MaterialTheme.typography.headlineSmall,
            colors = eingabefeld(),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        )
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Terminart.entries.forEach { art ->
                FilterChip(
                    selected = e.art == art,
                    onClick = { viewModel.setArt(art) },
                    label = { Text(artText(art)) },
                )
            }
        }
        // Die Kalender als Reihe zum Wischen, der gewaehlte gefuellt.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kalender.forEach { k ->
                val gewaehlt = k.id == e.kalenderId
                FilterChip(
                    selected = gewaehlt,
                    onClick = { viewModel.setKalender(k.id) },
                    label = { Text(k.name, maxLines = 1) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(k.colorArgb)))
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Trenner()

        // Zeit
        if (e.art == Terminart.AUFGABE) {
            Zeile(
                Icons.Outlined.EventBusy,
                "Ohne Datum",
                unter = "Steht dann nur in der Liste der Aufgaben.",
                onKlick = { viewModel.setOhneDatum(!e.ohneDatum) },
            ) {
                Switch(checked = e.ohneDatum, onCheckedChange = viewModel::setOhneDatum)
            }
        }
        AnimatedVisibility(visible = e.datiert, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
        Zeile(Icons.Outlined.Schedule, "Ganztägig", onKlick = if (e.art == Terminart.GEBURTSTAG) null else ({ viewModel.setGanztaegig(!e.ganztaegig) })) {
            Switch(
                checked = e.ganztaegig,
                onCheckedChange = viewModel::setGanztaegig,
                enabled = e.art != Terminart.GEBURTSTAG,
            )
        }
        Zeitzeile(
            tag = e.startTag,
            minute = e.startMinute.takeUnless { e.ganztaegig },
            onTag = { onWahl(Wahl.START_TAG) },
            onZeit = { onWahl(Wahl.START_ZEIT) },
        )
        if (e.mitEnde) {
            Zeitzeile(
                tag = e.endTag,
                minute = e.endMinute.takeUnless { e.ganztaegig },
                onTag = { onWahl(Wahl.END_TAG) },
                onZeit = { onWahl(Wahl.END_ZEIT) },
            )
        }
        Zeile(Icons.Outlined.EventRepeat, wiederholungstext(e.serie), onKlick = { onWahl(Wahl.WIEDERHOLUNG) })
        Trenner()

        // Wichtig
        Zeile(
            Icons.Outlined.Alarm,
            "Wichtig",
            unter = "Klingelt wie ein Wecker und zeigt den Termin auf dem ganzen Bildschirm.",
            onKlick = { viewModel.aendern { it.copy(wichtig = !it.wichtig) } },
        ) {
            Switch(checked = e.wichtig, onCheckedChange = { an -> viewModel.aendern { it.copy(wichtig = an) } })
        }
        if (e.wichtig) {
            // Gelesen bei jedem Zurueckkommen, siehe pruefung.
            val exakt = remember(pruefung) { viewModel.darfExaktWecken() }
            val vollbild = remember(pruefung) { viewModel.darfVollbild() }
            if (e.erinnerungen.isEmpty()) {
                Hinweis("Ohne Benachrichtigung klingelt nichts. Füge unten eine hinzu.")
            }
            if (!exakt) {
                Hinweis("Für pünktliche Wecker braucht InNoteBox die Erlaubnis für Wecker und Erinnerungen.", "Erlauben") {
                    oeffnen(context, viewModel.exaktEinstellungen())
                }
            }
            if (!vollbild) {
                Hinweis("Damit der Wecker den ganzen Bildschirm zeigen darf, braucht InNoteBox diese Erlaubnis.", "Erlauben") {
                    oeffnen(context, viewModel.vollbildEinstellungen())
                }
            }
        }
        Trenner()

        // Benachrichtigungen
        e.erinnerungen.forEachIndexed { i, m ->
            Zeile(
                if (i == 0) Icons.Outlined.Notifications else null,
                erinnerungstext(m, e.ganztaegig),
            ) {
                IconButton(onClick = { viewModel.erinnerungWeg(m) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Entfernen")
                }
            }
        }
        Zeile(
            if (e.erinnerungen.isEmpty()) Icons.Outlined.Notifications else null,
            "Benachrichtigung hinzufügen",
            gedimmt = true,
            onKlick = { onWahl(Wahl.ERINNERUNG) },
        )
        Trenner()
        }
        }

        // Farbe
        Zeile(
            Icons.Outlined.Palette,
            if (e.farbe == null) "Standardfarbe" else KALENDERFARBEN.firstOrNull { it.first == e.farbe }?.second ?: "Eigene Farbe",
            onKlick = { onWahl(Wahl.FARBE) },
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(farbe))
        }
        Trenner()

        // Eigenes Bild ueber dem Termin
        Bildzeile(e.bild?.let(viewModel::bilddatei), onWahl = viewModel::bildWaehlen, onWeg = viewModel::bildEntfernen)
        Trenner()

        // Ort und Beschreibung
        Feld(Icons.Outlined.LocationOn, e.ort, "Ort hinzufügen") { t -> viewModel.aendern { it.copy(ort = t) } }
        Feld(Icons.AutoMirrored.Outlined.Notes, e.beschreibung, "Beschreibung hinzufügen", einzeilig = false) { t ->
            viewModel.aendern { it.copy(beschreibung = t) }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun Dialoge(
    e: Entwurf,
    wahl: Wahl?,
    kalender: List<de.notizen.core.data.db.entity.EventCalendarEntity>,
    viewModel: TerminEditorViewModel,
    onWahl: (Wahl?) -> Unit,
    onVerwerfen: () -> Unit,
) {
    when (wahl) {
        Wahl.START_TAG -> Datumsdialog(e.startTag, { onWahl(null) }) {
            onWahl(null)
            viewModel.setBeginn(it, e.startMinute)
        }
        Wahl.END_TAG -> Datumsdialog(e.endTag, { onWahl(null) }) {
            onWahl(null)
            viewModel.setEnde(it, e.endMinute)
        }
        Wahl.START_ZEIT -> Zeitdialog(e.startMinute, { onWahl(null) }) {
            onWahl(null)
            viewModel.setBeginn(e.startTag, it)
        }
        Wahl.END_ZEIT -> Zeitdialog(e.endMinute, { onWahl(null) }) {
            onWahl(null)
            viewModel.setEnde(e.endTag, it)
        }
        Wahl.WIEDERHOLUNG -> Wiederholungsdialog(
            serie = e.serie,
            onAbbrechen = { onWahl(null) },
            onEinfach = { w ->
                onWahl(null)
                viewModel.setSerie(Serie(w))
            },
            onBenutzerdefiniert = { onWahl(Wahl.EIGENE_WIEDERHOLUNG) },
        )
        Wahl.EIGENE_WIEDERHOLUNG -> EigeneWiederholung(e.serie, e.startTag, onZurueck = { onWahl(null) }) { neu ->
            onWahl(null)
            viewModel.setSerie(neu)
        }
        Wahl.ERINNERUNG -> Listendialog(
            titel = "Benachrichtigung",
            eintraege = erinnerungsvorschlaege(e.ganztaegig).filter { it !in e.erinnerungen }
                .map { it to erinnerungstext(it, e.ganztaegig) },
            gewaehlt = null,
            onAbbrechen = { onWahl(null) },
        ) { m ->
            onWahl(null)
            viewModel.erinnerungDazu(m)
        }
        Wahl.FARBE -> AlertDialog(
            onDismissRequest = { onWahl(null) },
            title = { Text("Farbe") },
            text = {
                Farbwahl(e.farbe, mitStandard = true) { f ->
                    onWahl(null)
                    viewModel.aendern { it.copy(farbe = f) }
                }
            },
            confirmButton = { TextButton(onClick = { onWahl(null) }) { Text("Fertig") } },
        )
        Wahl.VERWERFEN -> AlertDialog(
            onDismissRequest = { onWahl(null) },
            title = { Text("Änderungen verwerfen?") },
            text = { Text(if (viewModel.neu) "Der Termin wird nicht angelegt." else "Deine Änderungen gehen verloren.") },
            confirmButton = {
                TextButton(onClick = onVerwerfen) { Text("Verwerfen") }
            },
            dismissButton = { TextButton(onClick = { onWahl(null) }) { Text("Weiter bearbeiten") } },
        )
        null -> Unit
    }
}

private fun oeffnen(context: android.content.Context, intent: android.content.Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Ohne passende Seite in den Einstellungen bleibt nur der Hinweis stehen.
    }
}

/** Ein Eingabefeld, das man als solches erkennt: eigene Flaeche, runde Ecken, kein Strich. */
@Composable
private fun eingabefeld() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

/** Ein eigenes Bild als Banner: waehlen, ansehen, wechseln, entfernen. */
@Composable
private fun Bildzeile(datei: java.io.File?, onWahl: (android.net.Uri) -> Unit, onWeg: () -> Unit) {
    val waehler = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onWahl)
    }
    val oeffnen = { waehler.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    if (datei == null) {
        Zeile(Icons.Outlined.Image, "Bild hinzufügen", gedimmt = true, onKlick = oeffnen)
        return
    }
    val bild = rememberBild(datei, 400.dp)
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = oeffnen),
        ) {
            if (bild != null) {
                Image(bild, contentDescription = "Bild des Termins", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(140.dp))
            }
        }
        Row {
            TextButton(onClick = oeffnen) { Text("Anderes Bild") }
            TextButton(onClick = onWeg) { Text("Entfernen") }
        }
    }
}

@Composable
private fun Trenner() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Zeile(
    zeichen: ImageVector?,
    text: String,
    unter: String? = null,
    gedimmt: Boolean = false,
    onKlick: (() -> Unit)? = null,
    rechts: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onKlick != null) Modifier.clickable(onClick = onKlick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp)) {
            if (zeichen != null) Icon(zeichen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (gedimmt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (unter != null) {
                Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        rechts?.invoke()
    }
}

@Composable
private fun Zeitzeile(tag: LocalDate, minute: Int?, onTag: () -> Unit, onZeit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Kalenderformat.tag(tag),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).clickable(onClick = onTag).padding(vertical = 12.dp),
        )
        if (minute != null) {
            Text(
                "%d:%02d".format(minute / 60, minute % 60),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onZeit).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Hinweis(text: String, knopf: String? = null, onKnopf: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (knopf != null) TextButton(onClick = onKnopf) { Text(knopf) }
    }
}

@Composable
private fun Feld(zeichen: ImageVector, wert: String, platzhalter: String, einzeilig: Boolean = true, onWert: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(40.dp).padding(top = 16.dp)) {
            Icon(zeichen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = wert,
            onValueChange = onWert,
            placeholder = { Text(platzhalter) },
            singleLine = einzeilig,
            minLines = if (einzeilig) 1 else 3,
            shape = RoundedCornerShape(12.dp),
            colors = eingabefeld(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Datumsdialog(tag: LocalDate, onAbbrechen: () -> Unit, onWahl: (LocalDate) -> Unit) {
    // Der Waehler rechnet in UTC-Mitternacht; ein Datum ist hier genau das.
    val zustand = rememberDatePickerState(initialSelectedDateMillis = tag.toEpochDay() * TAG_MS)
    DatePickerDialog(
        onDismissRequest = onAbbrechen,
        confirmButton = {
            TextButton(
                onClick = { zustand.selectedDateMillis?.let { onWahl(LocalDate.ofEpochDay(Math.floorDiv(it, TAG_MS))) } },
                enabled = zustand.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    ) {
        DatePicker(state = zustand)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Zeitdialog(minute: Int, onAbbrechen: () -> Unit, onWahl: (Int) -> Unit) {
    val zustand = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onAbbrechen,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = zustand)
            }
        },
        confirmButton = { TextButton(onClick = { onWahl(zustand.hour * 60 + zustand.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

@Composable
private fun <T> Listendialog(
    titel: String,
    eintraege: List<Pair<T, String>>,
    gewaehlt: T?,
    farben: Map<T, Int> = emptyMap(),
    onAbbrechen: () -> Unit,
    onWahl: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                eintraege.forEach { (wert, text) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onWahl(wert) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (gewaehlt != null) RadioButton(selected = wert == gewaehlt, onClick = { onWahl(wert) })
                        farben[wert]?.let { f ->
                            Box(Modifier.padding(end = 12.dp).size(14.dp).clip(CircleShape).background(Color(f)))
                        }
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}
