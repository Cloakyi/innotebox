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
 * Was geschieht, bevor die App etwas aus dem Netz laden darf (Opt-in).
 *
 * Zustimmen geht erst, wenn beides erfüllt ist: Der Text ist bis ganz
 * unten gescrollt, und zehn Sekunden sind vergangen. Ein Dialog, den man in
 * einer halben Sekunde wegtippt, ist keine Zustimmung, sondern ein Reflex.
 * Der Knopf sagt, was noch fehlt.
 *
 * Ein Hinweis, keine Nutzungsbedingungen (seit Alpha 9; vorher stand
 * hier ein Platzhalter). [NETZ_HINWEIS] sagt, was der Schalter heute tut:
 * Sprachpakete von ML Kit laden, und was dabei an Google geht. Kommt später
 * etwas dazu (etwa eine KI im Netz), deckt die alte Zustimmung das nicht:
 * Dann ändert sich der Text, und der Schalter fragt neu
 * (docs/ENTSCHEIDUNGEN.md, Abschnitt 7).
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
                    text = "Mit diesem Schalter darf die App etwas aus dem Netz laden, und " +
                        "zwar nur das, was hier steht. Lies den Text bis zum Ende.",
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
                        text = NETZ_HINWEIS,
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
 * Was der Schalter heute tut und was dabei an Google geht. Die Angaben zu ML
 * Kit stammen aus Googles Offenlegung (developers.google.com/ml-kit/android-data-disclosure),
 * die Größe der Sprachpakete aus der Dokumentation der Übersetzung.
 */
const val NETZ_HINWEIS: String =
    "Was der Schalter erlaubt: Die App darf für die Übersetzung über ML Kit " +
        "Sprachpakete laden. Jedes Paket ist rund 30 MB groß und kommt von Servern von " +
        "Google. Geladen wird erst, wenn du beim Übersetzen auf „Sprachpaket laden“ tippst.\n\n" +
        "Was dabei an Google geht: Beim Laden erfährt Google deine IP-Adresse und " +
        "welches Sprachpaket du holst. ML Kit schickt Google außerdem Angaben über die " +
        "Nutzung: Gerät und App, Leistung, Fehlercodes und die eingestellten Sprachen. " +
        "Deine Texte und Notizen sind nicht dabei, übersetzt wird auf dem Gerät.\n\n" +
        "Ausschalten kannst du jederzeit. Danach lädt die App nichts mehr, und geladene " +
        "Sprachpakete bleiben auf dem Gerät. Mehr dazu steht in der " +
        "Datenschutzerklärung auf innotebox.de."
