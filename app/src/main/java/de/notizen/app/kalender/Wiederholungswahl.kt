package de.notizen.app.kalender

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.notizen.core.data.kalender.Serie
import de.notizen.core.data.model.Wiederholung
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val EINFACHE = listOf(
    Wiederholung.KEINE to "Einmalig",
    Wiederholung.TAEGLICH to "Täglich",
    Wiederholung.WOECHENTLICH to "Wöchentlich",
    Wiederholung.MONATLICH to "Monatlich",
    Wiederholung.JAEHRLICH to "Jährlich",
)

private val WERKTAGE = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

/**
 * Die Auswahl: die einfachen Serien, darunter die eigene, wenn
 * eine gesetzt ist, und zuletzt „Benutzerdefiniert…“.
 */
@Composable
internal fun Wiederholungsdialog(
    serie: Serie,
    onAbbrechen: () -> Unit,
    onEinfach: (Wiederholung) -> Unit,
    onBenutzerdefiniert: () -> Unit,
) {
    val eigen = serie.benutzerdefiniert || serie.einheit == Wiederholung.WERKTAGS
    AlertDialog(
        onDismissRequest = onAbbrechen,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EINFACHE.forEach { (w, text) ->
                    Auswahl(text, gewaehlt = !eigen && serie.einheit == w) { onEinfach(w) }
                }
                if (eigen) Auswahl(wiederholungstext(serie), gewaehlt = true, onKlick = onAbbrechen)
                Auswahl("Benutzerdefiniert…", gewaehlt = false, onKlick = onBenutzerdefiniert)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun Auswahl(text: String, gewaehlt: Boolean, onKlick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onKlick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gewaehlt, onClick = onKlick)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * „Benutzerdefinierte Wiederholung“ als ganze Seite: Abstand und Einheit,
 * bei Wochen die Tage, dazu das Ende. Erst „Fertig“ uebernimmt die Serie.
 */
@Composable
internal fun EigeneWiederholung(
    anfang: Serie,
    start: LocalDate,
    onZurueck: () -> Unit,
    onFertig: (Serie) -> Unit,
) {
    var einheit by remember {
        mutableStateOf(
            when (anfang.einheit) {
                Wiederholung.KEINE, Wiederholung.WERKTAGS -> Wiederholung.WOECHENTLICH
                else -> anfang.einheit
            },
        )
    }
    var alleText by remember { mutableStateOf(if (anfang.einheit == Wiederholung.KEINE) "1" else anfang.alle.toString()) }
    var tage by remember {
        mutableStateOf(
            when {
                anfang.einheit == Wiederholung.WERKTAGS -> WERKTAGE
                anfang.tage.isNotEmpty() -> anfang.tage
                else -> setOf(start.dayOfWeek)
            },
        )
    }
    // 0 nie, 1 am Datum, 2 nach Anzahl
    var ende by remember { mutableIntStateOf(if (anfang.bis != null) 1 else if (anfang.anzahl != null) 2 else 0) }
    var bis by remember { mutableStateOf(anfang.bis ?: start.plusMonths(1)) }
    var anzahlText by remember { mutableStateOf((anfang.anzahl ?: 10).toString()) }
    var datumWahl by remember { mutableStateOf(false) }
    var einheitMenue by remember { mutableStateOf(false) }
    val alle = alleText.toIntOrNull()?.coerceIn(1, 999) ?: 1
    val anzahl = anzahlText.toIntOrNull()?.coerceIn(1, 999) ?: 1

    Dialog(
        onDismissRequest = onZurueck,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                    Text(
                        "Benutzerdefinierte Wiederholung",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    TextButton(
                        onClick = {
                            onFertig(
                                Serie(
                                    einheit = einheit,
                                    alle = alle,
                                    tage = if (einheit == Wiederholung.WOECHENTLICH) tage else emptySet(),
                                    bis = if (ende == 1) bis else null,
                                    anzahl = if (ende == 2) anzahl else null,
                                ),
                            )
                        },
                    ) { Text("Fertig") }
                }

                Ueberschrift("Wiederholung alle:")
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = alleText,
                        onValueChange = { t -> alleText = t.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                    Box(Modifier.width(168.dp)) {
                        OutlinedTextField(
                            value = einheitText(einheit, alle),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Das Feld selbst nimmt den Tipp sonst fuer die Tastatur.
                        Box(
                            Modifier
                                .matchParentSize()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { einheitMenue = true },
                        )
                        DropdownMenu(expanded = einheitMenue, onDismissRequest = { einheitMenue = false }) {
                            listOf(
                                Wiederholung.TAEGLICH,
                                Wiederholung.WOECHENTLICH,
                                Wiederholung.MONATLICH,
                                Wiederholung.JAEHRLICH,
                            ).forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(einheitText(w, alle)) },
                                    onClick = {
                                        einheit = w
                                        einheitMenue = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (einheit == Wiederholung.WOECHENTLICH) {
                    Ueberschrift("Wiederholt sich am")
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DayOfWeek.values().forEach { d ->
                            val an = d in tage
                            val name = d.getDisplayName(TextStyle.FULL, Locale.GERMANY)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (an) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (an) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    )
                                    // Der letzte Tag bleibt: ohne Tag gaebe es keine Serie.
                                    .clickable { tage = if (an) (tage - d).ifEmpty { tage } else tage + d }
                                    .semantics {
                                        contentDescription = name
                                        selected = an
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.getDisplayName(TextStyle.NARROW, Locale.GERMANY),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (an) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Ueberschrift("Endet")
                Endzeile(gewaehlt = ende == 0, onKlick = { ende = 0 }) {
                    Text("Nie", style = MaterialTheme.typography.bodyLarge)
                }
                Endzeile(gewaehlt = ende == 1, onKlick = { ende = 1 }) {
                    Text("Am", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            ende = 1
                            datumWahl = true
                        },
                    ) {
                        Text(
                            Kalenderformat.datum(bis),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (ende == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                Endzeile(gewaehlt = ende == 2, onKlick = { ende = 2 }) {
                    Text("Nach", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                    OutlinedTextField(
                        value = anzahlText,
                        onValueChange = { t ->
                            anzahlText = t.filter(Char::isDigit).take(3)
                            ende = 2
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                    )
                    Text(
                        if (anzahl == 1) "Termin" else "Terminen",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.size(32.dp))
            }
        }
    }

    if (datumWahl) {
        Datumsdialog(bis, { datumWahl = false }) {
            datumWahl = false
            bis = maxOf(it, start)
        }
    }
}

@Composable
private fun Ueberschrift(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
    )
}

@Composable
private fun Endzeile(gewaehlt: Boolean, onKlick: () -> Unit, inhalt: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onKlick).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gewaehlt, onClick = onKlick)
        Spacer(Modifier.width(8.dp))
        inhalt()
    }
}
