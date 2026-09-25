package de.notizen.app.ui.ordner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.stage.StageViewModel
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.Rueckkehr
import de.notizen.core.data.ordner.rueckkehr
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteRepository

/**
 * Die Frage beim Zurueckholen aus dem Archiv des Ordnermodus (Phase 14b).
 *
 * [vorschlag] ist die Rueckkehr zum Herkunftsordner, wenn alle gewaehlten
 * Notizen denselben haben; sonst `null`, dann bleibt nur die freie Wahl.
 * Nichts wandert automatisch dorthin, wo es vorher lag.
 */
data class RueckholAnfrage(
    val ids: List<String>,
    val herkunftId: String?,
    val vorschlag: Rueckkehr?,
)

/**
 * Rechnet die Frage fuer [ids] aus.
 *
 * Einen Vorschlag gibt es nur, wenn alle Notizen aus demselben Ordner kamen.
 * Bei gemischter Herkunft bleibt die freie Wahl; drei verschiedene „Zurueck
 * nach" auf einmal waeren keine Frage mehr.
 */
suspend fun rueckholAnfrageFuer(
    ids: List<String>,
    notes: NoteRepository,
    ordner: FolderRepository,
): RueckholAnfrage {
    val herkuenfte = ids.mapNotNull { notes.get(it) }.map { it.note.herkunftOrdnerId }.distinct()
    val herkunft = herkuenfte.singleOrNull()
    val vorschlag = if (herkuenfte.size == 1) rueckkehr(ordner.getAllIncludingDeleted(), herkunft) else null
    return RueckholAnfrage(ids, herkunft, vorschlag)
}

/**
 * Das Zurueckholen aus dem Archiv des Ordnermodus, fuer die Ordneransicht.
 * Der Dialog selbst ist [ZurueckholenDialog]; der Editor benutzt ihn ebenso.
 */
@Composable
fun ZurueckholenHost(stage: StageViewModel) {
    val anfrage by stage.rueckholAnfrage.collectAsStateWithLifecycle()
    val a = anfrage ?: return
    val baum by produceState(emptyList<Ordnerzeile>(), a) { value = stage.normalerBaum() }

    ZurueckholenDialog(
        anfrage = a,
        zeilen = baum,
        onWahl = stage::zurueckholenNach,
        onNeuAnlegen = stage::herkunftsordnerNeuAnlegen,
        onAbbrechen = stage::zurueckholenAbgebrochen,
    )
}

/**
 * **Immer eine Wahl, nie ein Automatismus.** Oben steht der Vorschlag „Zurueck
 * nach …" mit dem Pfad, darunter die freie Ordnerwahl. Nichts wandert von
 * allein dorthin, wo es vorher lag; der Vorschlag ist nur der kuerzeste Weg.
 *
 * Fehlt der Herkunftsordner, kommt zuerst die Dreifach-Frage: den Ordner neu
 * anlegen (die geloeschte Kette aus dem Papierkorb holen, ohne Notizen), in
 * den naechsten vorhandenen Ordner, oder einen anderen waehlen. Sie nennt den
 * fehlenden Pfad, damit man weiss, worum es geht.
 *
 * [zeilen] ist der normale Ordnerbaum. [onNeuAnlegen] bekommt den Namen des
 * Herkunftsordners fuer die Meldung.
 */
@Composable
fun ZurueckholenDialog(
    anfrage: RueckholAnfrage,
    zeilen: List<Ordnerzeile>,
    onWahl: (String?, String) -> Unit,
    onNeuAnlegen: (String) -> Unit,
    onAbbrechen: () -> Unit,
) {
    var freieWahl by remember(anfrage) { mutableStateOf(false) }
    val vorschlag = anfrage.vorschlag

    if (vorschlag is Rueckkehr.Fehlt && !freieWahl) {
        DreifachFrage(
            anzahl = anfrage.ids.size,
            fehlt = vorschlag,
            onNeuAnlegen = { onNeuAnlegen(vorschlag.pfad.last().name) },
            onNaechster = {
                val ziel = vorschlag.naechster
                onWahl(ziel?.id, ziel?.name ?: "Hauptordner")
            },
            onAndererOrdner = { freieWahl = true },
            onAbbrechen = onAbbrechen,
        )
        return
    }

    Rueckholwahl(
        anzahl = anfrage.ids.size,
        vorschlag = vorschlag.takeUnless { it is Rueckkehr.Fehlt },
        zeilen = zeilen,
        onWahl = onWahl,
        onAbbrechen = onAbbrechen,
    )
}

@Composable
private fun Rueckholwahl(
    anzahl: Int,
    vorschlag: Rueckkehr?,
    zeilen: List<Ordnerzeile>,
    onWahl: (String?, String) -> Unit,
    onAbbrechen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (anzahl == 1) "Notiz zurückholen" else "$anzahl Notizen zurückholen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (vorschlag) {
                    is Rueckkehr.Hauptordner -> Vorschlagzeile(
                        symbol = Icons.Outlined.Home,
                        titel = "Zurück in den Hauptordner",
                        unterzeile = if (anzahl == 1) {
                            "Dort lag die Notiz, bevor sie archiviert wurde."
                        } else {
                            "Dort lagen die Notizen, bevor sie archiviert wurden."
                        },
                        onKlick = { onWahl(null, "Hauptordner") },
                    )

                    is Rueckkehr.Vorhanden -> Vorschlagzeile(
                        symbol = Icons.Outlined.Unarchive,
                        titel = "Zurück nach „" + vorschlag.ordner.name + "\"",
                        unterzeile = pfadtext(vorschlag.pfad),
                        onKlick = { onWahl(vorschlag.ordner.id, vorschlag.ordner.name) },
                    )

                    else -> Unit
                }

                Text(
                    text = if (vorschlag == null) {
                        "Wähle den Ordner, in den es zurückkommen soll."
                    } else {
                        "Oder in einen anderen Ordner:"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 280.dp),
                ) {
                    if (vorschlag !is Rueckkehr.Hauptordner) {
                        item(key = "wurzel") {
                            Zielzeile(
                                name = "Hauptordner",
                                tiefe = 0,
                                istWurzel = true,
                                farbe = null,
                                onKlick = { onWahl(null, "Hauptordner") },
                            )
                        }
                    }
                    items(zeilen, key = { it.ordner.id }) { zeile ->
                        Zielzeile(
                            name = zeile.ordner.name,
                            tiefe = zeile.tiefe,
                            istWurzel = false,
                            farbe = zeile.ordner.colorArgb,
                            onKlick = { onWahl(zeile.ordner.id, zeile.ordner.name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/**
 * Die Dreifach-Frage, wenn der Herkunftsordner fehlt.
 *
 * „Ordner neu anlegen" gibt es nur, wenn die Kette noch im Papierkorb liegt.
 * Ist die Zeile endgueltig weg, kennt niemand mehr ihren Namen, und ein Knopf
 * dafuer verspraeche etwas, das die App nicht halten kann.
 */
@Composable
private fun DreifachFrage(
    anzahl: Int,
    fehlt: Rueckkehr.Fehlt,
    onNeuAnlegen: () -> Unit,
    onNaechster: () -> Unit,
    onAndererOrdner: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val pfad = pfadtext(fehlt.pfad)
    val kam = if (anzahl == 1) "Die Notiz kam" else "Die Notizen kamen"
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Der Ordner ist nicht mehr da") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (fehlt.pfad.isEmpty()) {
                        "$kam aus einem Ordner, den es nicht mehr gibt."
                    } else {
                        "$kam aus „" + pfad + "\". Dieser Ordner liegt im Papierkorb."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fehlt.wiederherstellbar) {
                    Vorschlagzeile(
                        symbol = Icons.Outlined.CreateNewFolder,
                        titel = "Ordner neu anlegen",
                        unterzeile = "Holt „" + pfad + "\" aus dem Papierkorb zurück, ohne die Notizen darin.",
                        onKlick = onNeuAnlegen,
                    )
                }
                Vorschlagzeile(
                    symbol = if (fehlt.naechster == null) Icons.Outlined.Home else Icons.Outlined.Unarchive,
                    titel = "In den nächsten vorhandenen Ordner",
                    unterzeile = fehlt.naechster?.let { "Das ist „" + it.name + "\"." }
                        ?: "Das ist der Hauptordner.",
                    onKlick = onNaechster,
                )
                Vorschlagzeile(
                    symbol = Icons.AutoMirrored.Outlined.DriveFileMove,
                    titel = "Anderen Ordner wählen",
                    unterzeile = "Du wählst aus dem Ordnerbaum.",
                    onKlick = onAndererOrdner,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/** Eine Karte mit Symbol, Titel und Erklaerung, wie die Wege beim Loeschen. */
@Composable
private fun Vorschlagzeile(
    symbol: ImageVector,
    titel: String,
    unterzeile: String,
    onKlick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onKlick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = symbol,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = titel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = unterzeile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** „Arbeit › Projekte › Kunde", oder „Hauptordner" fuer den leeren Pfad. */
private fun pfadtext(pfad: List<FolderEntity>): String =
    if (pfad.isEmpty()) "Hauptordner" else pfad.joinToString(" › ") { it.name }
