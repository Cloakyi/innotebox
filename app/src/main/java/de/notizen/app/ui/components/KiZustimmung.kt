package de.notizen.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.notizen.app.ai.KiZustimmungstext

/**
 * Die Frage, bevor die KI auf dem Gerät arbeiten darf (seit Alpha 9).
 *
 * Warum eine Zustimmung und nicht bloß ein Schalter: Die KI läuft über ML
 * Kit, und ML Kit schickt Google Kennzahlen über die Nutzung (Gerät, App,
 * Leistung, Fehlercodes, Sprachen; nach Googles Angaben nie Inhalte). Für
 * das Auslesen dieser Angaben verlangt § 25 TDDDG eine Einwilligung, und die
 * Bedingungen von Google verlangen, dass die Nutzer davon erfahren. Vor der
 * Zustimmung ruft die App ML Kit gar nicht erst auf.
 *
 * Das Häkchen zum Alter kommt aus den Zusatzbedingungen für ML Kit GenAI:
 * Die Schnittstellen sind nur für Anwendungen, die sich nicht an Menschen
 * unter 18 richten. Ohne Häkchen bleibt „Einschalten" aus.
 *
 * Die Zustimmung wird versiegelt (`Zustimmungssiegel`): Sie gilt nur mit
 * einem Schlüssel dieses Geräts und nur für genau den Text aus
 * [KiZustimmungstext]. Ausschalten löscht Zustimmung und Schlüssel.
 *
 * Derselbe Dialog steht beim Start (einmal, solange nicht entschieden ist) und
 * hinter dem KI-Schalter in den Einstellungen. [onSchliessen] ist das Tippen
 * daneben oder Zurück: Beim Start heißt das „nicht jetzt" und nicht „nein",
 * sonst schaltete ein verirrter Finger die KI für immer ab.
 */
@Composable
fun KiZustimmungDialog(
    ablehnenText: String,
    onZustimmen: () -> Unit,
    onAblehnen: () -> Unit,
    onSchliessen: () -> Unit = onAblehnen,
) {
    val context = LocalContext.current
    var volljaehrig by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text(KiZustimmungstext.TITEL) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Genau diese Sätze hält das Siegel über ihre Prüfsumme fest.
                KiZustimmungstext.ABSAETZE.forEach { Absatz(it) }
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, DATENSCHUTZ.toUri()))
                        }
                    },
                ) { Text("Datenschutzerklärung lesen") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = volljaehrig,
                            role = Role.Checkbox,
                            onValueChange = { volljaehrig = it },
                        ),
                ) {
                    Checkbox(checked = volljaehrig, onCheckedChange = null)
                    Text(
                        text = KiZustimmungstext.ALTER,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onZustimmen, enabled = volljaehrig) { Text("Einschalten") }
        },
        dismissButton = {
            TextButton(onClick = onAblehnen) { Text(ablehnenText) }
        },
    )
}

/** Die Datenschutzerklärung auf der Webseite. */
const val DATENSCHUTZ = "https://innotebox.de/datenschutz"

@Composable
private fun Absatz(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
