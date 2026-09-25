package de.notizen.app.ui.suche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.NoteTypeIcons
import de.notizen.app.ui.StageUi
import de.notizen.app.ui.components.LeererZustand
import de.notizen.app.ui.theme.alsNotizFarbe
import de.notizen.app.ui.theme.anzeigename
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.sichtbar
import de.notizen.core.data.repository.Suchtreffer
import de.notizen.core.data.search.Suchanfrage
import de.notizen.core.data.search.Zeitraum

/**
 * Die Suche über alle Stufen hinweg.
 *
 * Kein eigener Suchbereich je Stufe: Der Fluss aus Abschnitt 4 hat drei
 * Stationen, aber nur EINE Suche. Wer sucht, weiß in der Regel nicht mehr, in
 * welcher Station etwas liegt -- genau deshalb sucht er.
 *
 * Die Stufe, aus der man kommt, steht als abwählbarer Chip vorne. Die Suche
 * fängt dort an, hört dort aber nicht auf.
 */
@Composable
fun SucheScreen(
    viewModel: SucheViewModel,
    innerPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
    onZurueck: () -> Unit,
) {
    val anfrage by viewModel.anfrage.collectAsStateWithLifecycle()
    val treffer by viewModel.treffer.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val ordnermodus by viewModel.ordnermodus.collectAsStateWithLifecycle()
    val ordnerbaum by viewModel.ordnerbaum.collectAsStateWithLifecycle()
    val gewaehlteOrdner by viewModel.gewaehlteOrdnernamen.collectAsStateWithLifecycle()

    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fokus.requestFocus() }

    var ordnerwahlOffen by remember { mutableStateOf(false) }
    if (ordnerwahlOffen) {
        OrdnerfilterDialog(
            baum = ordnerbaum,
            gewaehlt = anfrage.ordnerIds,
            onUmschalten = viewModel::toggleOrdner,
            onSchliessen = { ordnerwahlOffen = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        Suchfeld(
            wert = anfrage.text,
            onWert = viewModel::setText,
            fokus = fokus,
            onZurueck = onZurueck,
        )

        Filterleiste(
            anfrage = anfrage,
            tags = tags,
            viewModel = viewModel,
            zeigeOrdner = ordnermodus,
            gewaehlteOrdner = gewaehlteOrdner,
            onOrdner = { ordnerwahlOffen = true },
        )

        Ergebnis(
            treffer = treffer,
            anfrage = anfrage,
            onOpenNote = onOpenNote,
            unten = innerPadding.calculateBottomPadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Suchfeld(
    wert: String,
    onWert: (String) -> Unit,
    fokus: FocusRequester,
    onZurueck: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            TextField(
                value = wert,
                onValueChange = onWert,
                placeholder = {
                    Text("In allen Notizen suchen", style = MaterialTheme.typography.bodyMedium)
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    selectionColors = LocalTextSelectionColors.current,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(fokus),
            )
            IconButton(
                // Leeres Feld: schließen. Gefülltes Feld: erst leeren. Sonst
                // verlässt man die Suche versehentlich, wenn man nur den
                // Suchbegriff loswerden wollte.
                onClick = { if (wert.isEmpty()) onZurueck() else onWert("") },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = if (wert.isEmpty()) "Suche schließen" else "Suche leeren",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Die Filterleiste.
 *
 * Waagerecht scrollend statt umbrechend: sechs Kategorien mit allen Werten
 * ausgeklappt würden den halben Bildschirm fressen, und die Trefferliste ist
 * das Wichtigere. Was gewählt ist, steht deshalb vorne -- die Leiste springt
 * bei jeder Änderung zurück an den Anfang, damit die aktive Auswahl sichtbar
 * bleibt.
 */
@Composable
private fun Filterleiste(
    anfrage: Suchanfrage,
    tags: List<TagEntity>,
    viewModel: SucheViewModel,
    zeigeOrdner: Boolean,
    gewaehlteOrdner: List<String>,
    onOrdner: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (anfrage.hatFilter) {
            AssistChipZuruecksetzen(
                anzahl = anfrage.anzahlFilter,
                onClick = viewModel::filterZuruecksetzen,
            )
        }

        FilterChip(
            selected = anfrage.nurFavoriten,
            onClick = viewModel::toggleFavoriten,
            label = { Text("Favoriten") },
            leadingIcon = if (anfrage.nurFavoriten) {
                { Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(18.dp)) }
            } else {
                null
            },
        )

        // DER ORDNERFILTER, nur im Ordnermodus (14d). Kein Menue wie bei den
        // anderen, sondern ein Dialog mit dem Baum: Ein Ordnerbaum mit Pfeilen
        // zum Aufklappen passt in kein Aufklappmenue. Die Stufen bleiben
        // daneben stehen; beide Systeme sind durchsuchbar.
        if (zeigeOrdner) {
            FilterChip(
                selected = gewaehlteOrdner.isNotEmpty(),
                onClick = onOrdner,
                label = {
                    Text(
                        when {
                            gewaehlteOrdner.isEmpty() -> "Ordner"
                            gewaehlteOrdner.size == 1 -> gewaehlteOrdner.first()
                            else -> "${gewaehlteOrdner.first()} +${gewaehlteOrdner.size - 1}"
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, Modifier.size(18.dp)) },
                trailingIcon = {
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, Modifier.size(18.dp))
                },
            )
        }

        Auswahlchip(
            titel = "Stufe",
            gewaehlt = anfrage.stufen.map { StageUi.label(it) },
            optionen = Stage.entries,
            beschriftung = { StageUi.label(it) },
            istGewaehlt = { it in anfrage.stufen },
            onWahl = viewModel::toggleStufe,
        )

        Auswahlchip(
            titel = "Tag",
            gewaehlt = tags.filter { it.id in anfrage.tagIds }.map { it.name },
            optionen = tags,
            beschriftung = { it.name },
            istGewaehlt = { it.id in anfrage.tagIds },
            onWahl = { viewModel.toggleTag(it.id) },
        )

        Auswahlchip(
            titel = "Farbe",
            gewaehlt = anfrage.farben.map { it.anzeigename() },
            optionen = NoteColor.entries,
            beschriftung = { it.anzeigename() },
            istGewaehlt = { it in anfrage.farben },
            onWahl = viewModel::toggleFarbe,
            // Farbnamen allein sind im Menue schwer auseinanderzuhalten --
            // "Dunkelgrün" und "Grün" stehen untereinander.
            symbol = { Farbpunkt(it) },
        )

        Auswahlchip(
            titel = "Art",
            gewaehlt = anfrage.typen.map { NoteTypeIcons.label(it) },
            optionen = NoteType.entries,
            beschriftung = { NoteTypeIcons.label(it) },
            istGewaehlt = { it in anfrage.typen },
            onWahl = viewModel::toggleTyp,
        )

        Auswahlchip(
            titel = "Zeitraum",
            gewaehlt = if (anfrage.zeitraum == Zeitraum.EGAL) {
                emptyList()
            } else {
                listOf(anfrage.zeitraum.beschriftung)
            },
            optionen = Zeitraum.entries,
            beschriftung = { it.beschriftung },
            istGewaehlt = { it == anfrage.zeitraum },
            // Einfachauswahl: ein zweites Antippen desselben Wertes hebt ihn auf.
            onWahl = {
                viewModel.setZeitraum(if (it == anfrage.zeitraum) Zeitraum.EGAL else it)
            },
        )

        // Der Papierkorb, standardmaessig aus (14d). Eingeschaltet kommen die
        // weggeworfenen Notizen dazu, in der Liste als solche gekennzeichnet.
        FilterChip(
            selected = anfrage.mitPapierkorb,
            onClick = viewModel::togglePapierkorb,
            label = { Text("Papierkorb") },
            leadingIcon = if (anfrage.mitPapierkorb) {
                { Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(18.dp)) }
            } else {
                null
            },
        )
    }
}

/**
 * Der Ordnerbaum als Filter (14d): dieselben Zeilen wie in der Seitenspalte,
 * mit Pfeil zum Aufklappen und Haken fuer die Wahl. Mehrfachauswahl; ein
 * gewaehlter Ordner schliesst seine Unterordner ein, das rechnet das
 * Repository. Das Archiv steht als eigener Baum darunter.
 */
@Composable
private fun OrdnerfilterDialog(
    baum: Ordnerfilterbaum,
    gewaehlt: Set<String>,
    onUmschalten: (String) -> Unit,
    onSchliessen: () -> Unit,
) {
    // Anfangs alles aufgeklappt: Wer filtert, will sehen, was es gibt. Wer es
    // zuklappt, behaelt das beim Drehen.
    var aufgeklappt by rememberSaveable(baum.alle.size) {
        mutableStateOf(baum.alle.map { it.id }.toSet())
    }
    val sichtbarNormal = remember(baum, aufgeklappt) { sichtbar(baum.ordner, aufgeklappt) }
    val sichtbarArchiv = remember(baum, aufgeklappt) { sichtbar(baum.archiv, aufgeklappt) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Ordner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ein gewählter Ordner schließt seine Unterordner ein. " +
                        "Mehrere lassen sich wählen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (baum.alle.isEmpty()) {
                    Text(
                        text = "Noch keine Ordner angelegt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(sichtbarNormal, key = { "o:" + it.ordner.id }) { zeile ->
                        Filterzweig(
                            zeile = zeile,
                            hatKinder = zeile.ordner.id in baum.mitKindern,
                            offen = zeile.ordner.id in aufgeklappt,
                            gewaehlt = zeile.ordner.id in gewaehlt,
                            onWahl = { onUmschalten(zeile.ordner.id) },
                            onAufklappen = {
                                val id = zeile.ordner.id
                                aufgeklappt = if (id in aufgeklappt) aufgeklappt - id else aufgeklappt + id
                            },
                        )
                    }
                    if (baum.archiv.isNotEmpty()) {
                        item(key = "archiv-kopf") {
                            Text(
                                text = "Archiv",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(sichtbarArchiv, key = { "a:" + it.ordner.id }) { zeile ->
                            Filterzweig(
                                zeile = zeile,
                                hatKinder = zeile.ordner.id in baum.mitKindern,
                                offen = zeile.ordner.id in aufgeklappt,
                                gewaehlt = zeile.ordner.id in gewaehlt,
                                onWahl = { onUmschalten(zeile.ordner.id) },
                                onAufklappen = {
                                    val id = zeile.ordner.id
                                    aufgeklappt = if (id in aufgeklappt) aufgeklappt - id else aufgeklappt + id
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSchliessen) { Text("Fertig") } },
    )
}

/** Eine Zeile des Filterbaums: Pfeil, Ordner, Haken. Zwei Trefferflaechen wie in der Seitenspalte. */
@Composable
private fun Filterzweig(
    zeile: Ordnerzeile,
    hatKinder: Boolean,
    offen: Boolean,
    gewaehlt: Boolean,
    onWahl: () -> Unit,
    onAufklappen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp * minOf(zeile.tiefe, 5)),
    ) {
        if (hatKinder) {
            IconButton(onClick = onAufklappen, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (offen) {
                        Icons.Outlined.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                    },
                    contentDescription = if (offen) "Zuklappen" else "Aufklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onWahl)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = zeile.ordner.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = zeile.ordner.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            if (gewaehlt) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "gewählt",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Ein Chip, der ein Menü aufklappt.
 *
 * Beschriftet sich selbst mit dem, was gewählt ist: „Farbe" wird zu „Rot" und
 * bei mehreren zu „Rot +2". Ein Filterchip, der immer gleich heißt, zwingt zum
 * Aufklappen, nur um zu sehen, was er gerade tut.
 */
@Composable
private fun <T> Auswahlchip(
    titel: String,
    gewaehlt: List<String>,
    optionen: List<T>,
    beschriftung: (T) -> String,
    istGewaehlt: (T) -> Boolean,
    onWahl: (T) -> Unit,
    symbol: (@Composable (T) -> Unit)? = null,
) {
    var offen by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = gewaehlt.isNotEmpty(),
            onClick = { offen = true },
            label = {
                Text(
                    when {
                        gewaehlt.isEmpty() -> titel
                        gewaehlt.size == 1 -> gewaehlt.first()
                        else -> "${gewaehlt.first()} +${gewaehlt.size - 1}"
                    },
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            if (optionen.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Noch keine Tags angelegt") },
                    onClick = { offen = false },
                    enabled = false,
                )
            }
            optionen.forEach { wert ->
                DropdownMenuItem(
                    text = { Text(beschriftung(wert)) },
                    // Das Menü bleibt offen: Mehrfachauswahl heißt mehrere
                    // Tipps, und nach jedem neu aufklappen zu müssen wäre Arbeit
                    // ohne Nutzen.
                    onClick = { onWahl(wert) },
                    leadingIcon = symbol?.let { { it(wert) } },
                    trailingIcon = {
                        if (istGewaehlt(wert)) {
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

@Composable
private fun AssistChipZuruecksetzen(anzahl: Int, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(if (anzahl == 1) "1 Filter" else "$anzahl Filter") },
        leadingIcon = {
            Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(18.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            iconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun Ergebnis(
    treffer: List<Suchtreffer>,
    anfrage: Suchanfrage,
    onOpenNote: (String) -> Unit,
    unten: Dp,
) {
    if (treffer.isEmpty()) {
        LeererZustand(
            titel = if (anfrage.istLeer) "Wonach suchst du?" else "Nichts gefunden",
            text = if (anfrage.istLeer) {
                "Gesucht wird in Titeln, Text, Checklisten und Transkripten, " +
                    "über alle drei Stufen hinweg."
            } else {
                "Kein Treffer für diese Eingabe. Vielleicht hilft ein kürzeres " +
                    "Wort, oder ein Filter steht noch im Weg."
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = unten + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(treffer, key = { it.notiz.note.id }) { t ->
            Trefferkarte(treffer = t, onClick = { onOpenNote(t.notiz.note.id) })
        }
    }
}

/** Der Farbpunkt in der Farbauswahl des Filters. */
@Composable
internal fun Farbpunkt(farbe: NoteColor, modifier: Modifier = Modifier) {
    val werte = farbe.alsNotizFarbe()
    Surface(
        color = werte.container,
        shape = CircleShape,
        modifier = modifier.size(16.dp),
        content = {},
    )
}
