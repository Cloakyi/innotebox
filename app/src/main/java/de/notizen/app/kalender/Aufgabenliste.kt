package de.notizen.app.kalender

import de.notizen.core.data.kalender.serie
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.app.ui.theme.NotizenScaffold
import de.notizen.core.data.model.Wiederholung
import java.time.LocalDate
import java.time.ZoneId

/**
 * Alle Aufgaben der Kalender auf einen Blick, nach Zeitpunkt geordnet: was
 * ueberfaellig ist, heute, morgen und spaeter, dazu die Aufgaben ohne Datum,
 * die nur hier stehen. Die Listen in Notizen gehoeren nicht dazu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aufgabenliste(
    gruppen: List<Aufgabengruppe>,
    heute: LocalDate,
    zone: ZoneId,
    onZurueck: () -> Unit,
    onAufgabe: (Aufgabe) -> Unit,
    onErledigt: (String, Boolean) -> Unit,
    onNeu: () -> Unit,
) {
    BackHandler(onBack = onZurueck)
    NotizenScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück") }
                },
                title = { Text("Aufgaben") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNeu,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Aufgabe") },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        if (gruppen.isEmpty()) {
            Hinweisblock(
                titel = "Keine Aufgaben",
                text = "Lege mit dem Knopf unten eine an. Ein Datum braucht sie nicht.",
                modifier = Modifier.padding(padding).padding(top = 48.dp, start = 32.dp, end = 32.dp),
            )
            return@NotizenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            gruppen.forEach { g ->
                item(key = "g:" + g.titel) {
                    Text(
                        text = g.titel,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (g.titel == "Überfällig") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp).animateItem(),
                    )
                }
                items(g.aufgaben, key = { (if (g.erledigt) "e:" else "o:") + it.termin.id }) { a ->
                    Aufgabenzeile(
                        a = a,
                        mitTag = g.tag == null && a.tag != null,
                        heute = heute,
                        zone = zone,
                        onKlick = { onAufgabe(a) },
                        onErledigt = { onErledigt(a.termin.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Aufgabenzeile(
    a: Aufgabe,
    mitTag: Boolean,
    heute: LocalDate,
    zone: ZoneId,
    onKlick: () -> Unit,
    onErledigt: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val farbe = Color(a.farbe)
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onKlick).padding(start = 8.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = a.termin.done,
            onCheckedChange = onErledigt,
            colors = CheckboxDefaults.colors(checkedColor = farbe, uncheckedColor = farbe, checkmarkColor = aufFarbe(farbe)),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                text = a.termin.title.ifBlank { "Ohne Titel" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (a.termin.done) TextDecoration.LineThrough else null,
                color = if (a.termin.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val teile = buildList {
                if (mitTag && a.tag != null) add(Kalenderformat.tagNah(a.tag, heute))
                a.zeit?.let { add(Kalenderformat.uhrzeit(it, zone)) }
                if (a.termin.recurrence != Wiederholung.KEINE) add(wiederholungstext(a.termin.serie))
                if (a.termin.description.isNotBlank()) add(a.termin.description.lineSequence().first())
            }
            if (teile.isNotEmpty()) {
                Text(
                    text = teile.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
