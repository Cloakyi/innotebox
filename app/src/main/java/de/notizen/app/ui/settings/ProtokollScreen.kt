package de.notizen.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.components.LeererZustand
import de.notizen.core.data.db.entity.ArchiveRunEntity
import de.notizen.core.data.model.ArchiveTrigger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Was die Automatik in den letzten Nächten getan hat.
 *
 * Der eigentliche Zweck dieses Screens ist Nachvollziehbarkeit. Das
 * Archivieren passiert ohne Zutun, nachts, während das Gerät lädt. Stehen
 * morgens zwölf Notizen nicht mehr im Eingang, muss man nachsehen können, was
 * da passiert ist, sonst ist die Automatik eine Blackbox, die Dinge
 * verschwinden lässt.
 *
 * Zurücknehmen geht hier auch Wochen später noch, solange der Lauf in der
 * Liste steht. Die Benachrichtigung am Morgen ist der schnelle Weg, nicht der
 * einzige, wer nachts nicht aufs Handy schaut, hätte sonst keinen.
 */
@Composable
fun ProtokollScreen(
    innerPadding: PaddingValues,
    viewModel: AufraeumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.laeufe.isEmpty()) {
        LeererZustand(
            titel = "Noch keine Läufe",
            text = "Hier steht künftig, was die automatische Archivierung in " +
                "welcher Nacht verschoben hat. Jeder Lauf lässt sich von hier " +
                "aus zurücknehmen.",
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.laeufe, key = { it.batchId }) { lauf ->
            Lauf(lauf, onZuruecknehmen = { viewModel.zuruecknehmen(lauf.batchId) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun Lauf(lauf: ArchiveRunEntity, onZuruecknehmen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = zeitpunkt(lauf.runAt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = beschreibung(lauf),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Ein zurückgenommener Lauf behält seine Zeile: Dass etwas passiert ist
        // UND wieder rückgängig gemacht wurde, gehört genauso ins Protokoll.
        if (lauf.undoneAt == null) {
            TextButton(onClick = onZuruecknehmen) { Text("Rückgängig") }
        } else {
            Text(
                text = "zurückgenommen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

private fun beschreibung(lauf: ArchiveRunEntity): String {
    val anzahl = if (lauf.noteCount == 1) "1 Notiz" else "${lauf.noteCount} Notizen"
    val grund = when (lauf.trigger) {
        ArchiveTrigger.AGE -> "zu lange nicht geöffnet"
        ArchiveTrigger.COUNT -> "über der Mengengrenze"
        ArchiveTrigger.BOTH -> "Alter und Menge"
    }
    return "$anzahl · $grund"
}

private fun zeitpunkt(millis: Long): String =
    SimpleDateFormat("EEEE, d. MMMM, HH:mm", Locale.GERMANY).format(Date(millis))
