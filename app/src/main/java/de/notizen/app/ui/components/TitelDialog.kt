package de.notizen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ai.KiTitelState
import de.notizen.app.ai.KiTitelViewModel
import de.notizen.app.ai.KiZustand

/**
 * Titel nachtragen, bevor eine Notiz den Eingang verlaesst.
 *
 * **Das Feld ist leer** (Phase 15, Nutzer 2026-09-14). Bis dahin stand der
 * abgeleitete Titel schon darin, und man bestaetigte ihn, ohne ihn zu lesen.
 * Jetzt steht ein Platzhalter als ganzer Satz, darunter ein Ladekreis, bis
 * der Vorschlag der KI da ist; Antippen uebernimmt ihn ins Feld. Bei KI aus
 * oder nicht verfuegbar steht darunter gar nichts, kein Hinweis.
 *
 * **Ohne Titel kein Verschieben.** Der Knopf bleibt tot, solange das Feld leer
 * ist, und „Abbrechen" verschiebt nicht. Der Dialog ist ein Angebot, einen
 * Titel zu vergeben, keine Sperre, die man mit Leerlauf umgeht.
 *
 * Der Dialog wartet trotzdem nie auf die KI: Wer tippen will, tippt sofort.
 * [quelle] ist der Notiztext fuer das Modell, [fallback] der ohne KI
 * abgeleitete Vorschlag; er steht nicht mehr im Feld, sondern ist nur noch
 * der Merker, aus dem der Aufrufer die Anfrage gebaut hat.
 */
@Composable
fun TitelDialog(
    ueberschrift: String,
    erklaerung: String,
    fallback: String,
    quelle: String,
    bestaetigenText: String,
    onAbbrechen: () -> Unit,
    onBestaetigen: (String) -> Unit,
    kiViewModel: KiTitelViewModel = hiltViewModel(),
) {
    var titel by rememberSaveable(fallback) { mutableStateOf("") }
    val ki by kiViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(quelle) { kiViewModel.starte(quelle) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(ueberschrift) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = erklaerung,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = titel,
                    onValueChange = { titel = it },
                    placeholder = { Text("Gib der Notiz einen Titel.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                KiBereich(
                    state = ki,
                    onUebernehmen = { titel = it },
                    onLaden = kiViewModel::modellLaden,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBestaetigen(titel.trim()) },
                enabled = titel.isNotBlank(),
            ) {
                Text(bestaetigenText)
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        },
    )
}

/**
 * Der KI-Teil des Dialogs (Phase 15).
 *
 * Solange die KI arbeitet, ein Ladekreis; danach der Vorschlag zum Antippen.
 * Bei KI aus, nicht verfuegbar, ohne Ergebnis oder mit Fehler steht hier
 * NICHTS: Der Nutzer soll einen Titel vergeben, nicht ueber die KI lesen.
 * Einzige Ausnahme ist das ladbare Modell, denn Laden ist ein Weg und kein
 * Hindernis (docs/ENTSCHEIDUNGEN.md, `DOWNLOADABLE`).
 */
@Composable
private fun KiBereich(
    state: KiTitelState,
    onUebernehmen: (String) -> Unit,
    onLaden: () -> Unit,
) {
    val vorschlag = state.vorschlag

    when {
        vorschlag != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            TextButton(onClick = { onUebernehmen(vorschlag) }) {
                Text(
                    text = vorschlag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        state.laeuft -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            if (state.zustand == KiZustand.LAEDT) {
                Text(
                    text = "Das Modell wird geladen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Das Modell liegt nicht auf dem Geraet. Nicht heimlich laden -- das
        // kann ein echter, grosser Download sein.
        state.zustand == KiZustand.LADBAR -> TextButton(onClick = onLaden) {
            Text("Modell für Vorschläge laden")
        }

        else -> Unit
    }
}
