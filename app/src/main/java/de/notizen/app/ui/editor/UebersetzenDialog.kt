package de.notizen.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.notizen.app.uebersetzung.SPRACHEN
import de.notizen.app.uebersetzung.Uebersetzungsergebnis
import de.notizen.app.uebersetzung.sprachname
import de.notizen.core.data.model.Uebersetzungsweg

/**
 * Der Dialog zum Übersetzen (Phase 16).
 *
 * Drei Schritte in einem Dialog: Sprachen wählen und starten, warten, dann
 * das Ergebnis mit „Ersetzen" oder „Darunter anhängen". Der Nutzer wählt;
 * nichts wird von selbst eingesetzt, und das Original geht nie verloren
 * (Ersetzen hat eine Undo-Leiste).
 *
 * Fehlt ein Sprachpaar, steht das hier mit dem Weg dorthin: die
 * Systemeinstellung (Übersetzung des Systems) oder der Knopf zum Laden
 * (ML Kit). Ein Weg, der nicht geht, sagt warum.
 */
@Composable
fun UebersetzenDialog(
    anfrage: Uebersetzungsanfrage,
    /** Ob es die Systemeinstellung fuer Sprachpakete gibt (nur beim Weg ueber das System). */
    systemeinstellungDa: Boolean,
    onSprachen: (von: String, nach: String) -> Unit,
    onStarten: (laden: Boolean) -> Unit,
    onSystemeinstellung: () -> Unit,
    onErsetzen: () -> Unit,
    onAnhaengen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val ergebnis = anfrage.ergebnis
    val fertig = anfrage.fertig

    AlertDialog(
        onDismissRequest = { if (!anfrage.laeuft) onAbbrechen() },
        title = { Text("Übersetzen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Sprachwahl(
                        beschriftung = "Von",
                        code = anfrage.von,
                        aktiv = !anfrage.laeuft,
                        onWahl = { onSprachen(it, anfrage.nach) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onSprachen(anfrage.nach, anfrage.von) },
                        enabled = !anfrage.laeuft,
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = "Sprachen tauschen")
                    }
                    Sprachwahl(
                        beschriftung = "Nach",
                        code = anfrage.nach,
                        aktiv = !anfrage.laeuft,
                        onWahl = { onSprachen(anfrage.von, it) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.size(12.dp))

                when {
                    anfrage.laeuft -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Übersetzt …",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }

                    fertig != null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(12.dp),
                            )
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(
                            text = fertig.filter { it.isNotBlank() }.joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    ergebnis is Uebersetzungsergebnis.SprachpaarFehlt -> Text(
                        text = if (ergebnis.ladbar) {
                            "Das Sprachpaar " + sprachname(anfrage.von) + " nach " +
                                sprachname(anfrage.nach) + " ist noch nicht auf dem Gerät."
                        } else {
                            "Das Sprachpaar " + sprachname(anfrage.von) + " nach " +
                                sprachname(anfrage.nach) + " gibt es auf diesem Weg nicht."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    ergebnis is Uebersetzungsergebnis.NichtVerfuegbar -> Text(
                        text = ergebnis.grund,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    ergebnis is Uebersetzungsergebnis.Fehler -> Text(
                        text = ergebnis.meldung,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> Text(
                        text = when (anfrage.bereich) {
                            is Uebersetzungsanfrage.Bereich.Auswahl -> "Übersetzt die Auswahl im Text."
                            Uebersetzungsanfrage.Bereich.GanzerText -> "Übersetzt den ganzen Text der Notiz."
                            is Uebersetzungsanfrage.Bereich.Eintraege -> "Übersetzt alle Einträge der Liste."
                        } + " Danach wählst du, ob die Übersetzung das Original ersetzt oder darunter steht.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when {
                anfrage.laeuft -> Unit
                fertig != null -> Row {
                    TextButton(onClick = onAnhaengen) { Text("Darunter anhängen") }
                    TextButton(onClick = onErsetzen) { Text("Ersetzen") }
                }
                ergebnis is Uebersetzungsergebnis.SprachpaarFehlt && ergebnis.ladbar -> when (anfrage.weg) {
                    // Das System laedt seine Pakete in der eigenen Einstellung;
                    // ML Kit laedt ueber die Bibliothek, auf Knopfdruck.
                    Uebersetzungsweg.SYSTEM -> if (systemeinstellungDa) {
                        TextButton(onClick = onSystemeinstellung) { Text("Sprachpaket laden") }
                    }
                    Uebersetzungsweg.MLKIT ->
                        TextButton(onClick = { onStarten(true) }) { Text("Sprachpaket laden") }
                    Uebersetzungsweg.GERAETE_KI -> Unit
                }
                ergebnis is Uebersetzungsergebnis.NichtVerfuegbar -> Unit
                else -> TextButton(onClick = { onStarten(false) }) { Text("Übersetzen") }
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen, enabled = !anfrage.laeuft) { Text("Abbrechen") }
        },
    )
}

/** Eine Sprache waehlen: Beschriftung, gewaehlter Name, Pfeil, Menue. */
@Composable
private fun Sprachwahl(
    beschriftung: String,
    code: String,
    aktiv: Boolean,
    onWahl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = aktiv) { offen = true }
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = beschriftung,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sprachname(code),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DropdownMenu(
            expanded = offen,
            onDismissRequest = { offen = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            SPRACHEN.forEach { sprache ->
                DropdownMenuItem(
                    text = { Text(sprache.name) },
                    onClick = {
                        onWahl(sprache.code)
                        offen = false
                    },
                    trailingIcon = {
                        if (sprache.code == code) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    },
                )
            }
        }
    }
}
