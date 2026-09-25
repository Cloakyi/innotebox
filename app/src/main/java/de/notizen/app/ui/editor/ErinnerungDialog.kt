package de.notizen.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * Zeitpunkt einer Erinnerung wählen: erst der Tag, dann die Uhrzeit.
 *
 * Zwei Schritte statt eines gemeinsamen Dialogs, weil Material 3 genau diese
 * beiden Bausteine mitbringt und ein selbstgebauter Kombidialog hier nichts
 * gewinnen würde.
 *
 * Zeitzonen sind die Falle. Der `DatePicker` arbeitet in UTC, der Nutzer
 * denkt in seiner Ortszeit. Die Umrechnung steht in [Zeitpunkt] und ist dort
 * über mehrere Zeitzonen und über die Sommerzeitumstellung hinweg
 * nachgerechnet -- nicht behauptet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErinnerungDialog(
    vorbelegung: Long?,
    onAbbrechen: () -> Unit,
    onBestaetigen: (Long) -> Unit,
) {
    val start = vorbelegung ?: Zeitpunkt.naechsteVolleStunde()
    var tag by remember { mutableStateOf<Long?>(null) }

    if (tag == null) {
        val zustand = rememberDatePickerState(
            // Umgerechnet, NICHT durchgereicht: der Waehler liest den Wert als
            // UTC. Ein lokaler Zeitpunkt am fruehen Morgen liegt in UTC noch im
            // Vortag, und der Kalender stuende auf gestern.
            initialSelectedDateMillis = Zeitpunkt.alsWaehlertag(start),
        )
        DatePickerDialog(
            onDismissRequest = onAbbrechen,
            confirmButton = {
                TextButton(
                    onClick = { tag = zustand.selectedDateMillis },
                    enabled = zustand.selectedDateMillis != null,
                ) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = zustand)
        }
        return
    }

    val vorZeit = Calendar.getInstance().apply { timeInMillis = start }
    val zeitZustand = rememberTimePickerState(
        initialHour = vorZeit.get(Calendar.HOUR_OF_DAY),
        initialMinute = vorZeit.get(Calendar.MINUTE),
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Um wie viel Uhr?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                TimePicker(state = zeitZustand)
                Text(
                    text = "Die Erinnerung meldet sich genau zu dieser Zeit, " +
                        "auch wenn das Gerät gerade schläft.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onBestaetigen(
                        Zeitpunkt.ausWahl(tag!!, zeitZustand.hour, zeitZustand.minute),
                    )
                },
            ) { Text("Erinnern") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { tag = null }) { Text("Zurück") }
                TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
            }
        },
    )
}
