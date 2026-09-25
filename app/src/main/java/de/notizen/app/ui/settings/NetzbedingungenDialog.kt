package de.notizen.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Die Bedingungen, bevor die App ins Netz darf (Phase 15, Opt-in).
 *
 * **Zustimmen geht erst, wenn beides erfüllt ist:** Der Text ist bis ganz
 * unten gescrollt, und zehn Sekunden sind vergangen. Ein Dialog, den man in
 * einer halben Sekunde wegtippt, ist keine Zustimmung, sondern ein Reflex.
 * Der Knopf sagt, was noch fehlt.
 *
 * **Der Text ist ein Platzhalter.** Die Nutzungsbedingungen liefert der
 * Nutzer; sie stehen in [NETZ_BEDINGUNGEN] und werden dort ausgetauscht.
 * Heute hängt am Schalter nur das Nachladen der Sprachpakete von ML Kit
 * (Phase 16); die Online-KI kommt dazu, sobald ein Anbieter gewählt ist.
 */
@Composable
fun NetzbedingungenDialog(
    onZustimmen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val scroll = rememberScrollState()
    var restSekunden by remember { mutableIntStateOf(WARTEZEIT_S) }
    LaunchedEffect(Unit) {
        while (restSekunden > 0) {
            delay(1_000)
            restSekunden--
        }
    }
    // Ein Text, der ganz hineinpasst, gilt als gelesen bis unten.
    val untenAngekommen = scroll.maxValue == 0 || scroll.value >= scroll.maxValue
    val bereit = untenAngekommen && restSekunden == 0

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Verarbeitung im Netz") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Bis hierher bleibt alles auf dem Gerät. Mit diesem Schalter darf die " +
                        "App Daten ins Netz geben, und zwar nur für das, was hier steht. Lies " +
                        "den Text bis zum Ende.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scroll),
                ) {
                    Text(
                        text = NETZ_BEDINGUNGEN,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onZustimmen, enabled = bereit) {
                Text(
                    when {
                        !untenAngekommen && restSekunden > 0 -> "Erst bis unten lesen ($restSekunden s)"
                        !untenAngekommen -> "Erst bis unten lesen"
                        restSekunden > 0 -> "Zustimmen in $restSekunden s"
                        else -> "Ich stimme zu"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        },
    )
}

/** So lange muss der Dialog offen sein, bevor Zustimmen geht. */
private const val WARTEZEIT_S = 10

/**
 * PLATZHALTER. Der Text der Nutzungsbedingungen folgt (docs/ENTSCHEIDUNGEN.md,
 * Abschnitt 7). Was hier steht, sagt nur, was der Schalter heute tut.
 */
const val NETZ_BEDINGUNGEN: String =
    "Dieser Text ist ein Platzhalter. Die Nutzungsbedingungen für die Verarbeitung im " +
        "Netz folgen.\n\n" +
        "Was der Schalter heute tut: Er erlaubt der App, Sprachpakete für die Übersetzung " +
        "über ML Kit aus dem Netz zu laden (rund 30 MB je Sprache). Die Pakete kommen von " +
        "Google. Deine Notizen verlassen dabei das Gerät nicht; übersetzt wird nach dem " +
        "Laden auf dem Gerät.\n\n" +
        "Was der Schalter später tun wird: Sobald ein Anbieter für die Verarbeitung im " +
        "Netz gewählt ist, dürfen Titelvorschläge und Übersetzungen an diesen Anbieter " +
        "gehen. Welcher das ist und was er mit den Daten tut, steht dann an dieser Stelle, " +
        "bevor du zustimmst.\n\n" +
        "Du kannst den Schalter jederzeit wieder ausschalten. Dann geht nichts mehr ins " +
        "Netz, und schon geladene Sprachpakete bleiben auf dem Gerät."
