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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.app.ui.tags.Farbpunkt
import de.notizen.app.ui.tags.TAG_FARBEN

/** Wie weit ein Ordner je Ebene eingerueckt wird. */
private val EINRUECKUNG = 16.dp

/**
 * Ab welcher Tiefe nicht weiter eingerueckt wird.
 *
 * Ohne Grenze bliebe in einem tiefen Baum fuer den Namen kein Platz mehr, und
 * ausgerechnet die innersten Ordner waeren nicht mehr zu lesen. Die Tiefe steht
 * dann trotzdem noch im Weg darueber.
 */
private const val TIEFE_MAX = 6

/**
 * Alle Dialoge der Ordneransicht an einer Stelle.
 *
 * Sie liegen zusammen, weil sie dieselbe Quelle haben: das ViewModel sagt,
 * welcher offen ist. Ueber die Screens verstreut waere jeder fuer sich
 * einfacher und das Ganze schwerer zu ueberblicken.
 */
@Composable
fun OrdnerDialogHost(ordner: OrdnerViewModel, zeigeHinweis: (String) -> Unit) {
    val offen by ordner.dialog.collectAsStateWithLifecycle()

    when (val dialog = offen) {
        null -> Unit

        is Ordnerdialog.Neu -> OrdnerNameDialog(
            ueberschrift = "Neuer Ordner",
            erklaerung = "Ordner können ineinander liegen. " +
                "Dieser entsteht an der Stelle, an der du gerade stehst.",
            vorgabe = "",
            bestaetigenText = "Anlegen",
            onAbbrechen = ordner::schliesse,
            onBestaetigen = { name -> ordner.anlegen(name, dialog.elternId) },
        )

        is Ordnerdialog.Umbenennen -> OrdnerNameDialog(
            ueberschrift = "Ordner umbenennen",
            erklaerung = "Die Notizen darin bleiben, wo sie sind.",
            vorgabe = dialog.ordner.name,
            bestaetigenText = "Übernehmen",
            onAbbrechen = ordner::schliesse,
            onBestaetigen = { name -> ordner.umbenennen(dialog.ordner.id, name) },
        )

        is Ordnerdialog.Verschieben -> OrdnerAuswahlDialog(
            ueberschrift = "„" + dialog.ordner.name + "\" verschieben nach",
            erklaerung = "Ordner, die dadurch in sich selbst landen würden, " +
                "stehen hier nicht zur Wahl.",
            zeilen = ordner.zieleFuer(dialog.ordner.id),
            ausgeschlossen = dialog.ordner.parentId,
            // Liegt er schon ganz oben, ist "ganz oben" kein Ziel.
            zeigeHauptordner = dialog.ordner.parentId != null,
            onAbbrechen = ordner::schliesse,
            onWahl = { zielId, _ ->
                ordner.verschieben(dialog.ordner.id, zielId) { geklappt ->
                    if (!geklappt) {
                        zeigeHinweis(
                            "Das Verschieben ging nicht. " +
                                "Der Zielordner liegt inzwischen selbst in diesem Ordner.",
                        )
                    }
                }
            },
        )

        is Ordnerdialog.Farbe -> OrdnerFarbeDialog(
            ordner = dialog.ordner,
            onAbbrechen = ordner::schliesse,
            onWahl = { farbe -> ordner.umfaerben(dialog.ordner.id, farbe) },
        )

        is Ordnerdialog.Loeschen -> Loeschdialog(
            name = dialog.ordner.name,
            onAbbrechen = ordner::schliesse,
            onNurOrdner = { ordner.oeffne(Ordnerdialog.Loeschziel(dialog.ordner)) },
            onMitInhalt = { ordner.loeschenMitInhalt(dialog.ordner) },
        )

        is Ordnerdialog.Loeschziel -> OrdnerAuswahlDialog(
            ueberschrift = "Wohin mit dem Inhalt?",
            erklaerung = "Die Notizen und Unterordner aus „" + dialog.ordner.name +
                "\" wandern dorthin. Der Ordner selbst geht in den Papierkorb.",
            // Der Ordner, der gerade weggeht, steht nicht zur Wahl -- und seine
            // Unterordner auch nicht, denn die wandern ja selbst mit.
            zeilen = ordner.zieleFuer(dialog.ordner.id)
                .filter { it.ordner.id != dialog.ordner.id },
            ausgeschlossen = dialog.ordner.id,
            zeigeHauptordner = true,
            onAbbrechen = ordner::schliesse,
            onWahl = { zielId, _ -> ordner.loeschen(dialog.ordner.id, zielId) },
        )
    }
}

/**
 * Die Frage beim Loeschen eines Ordners: mit oder ohne Inhalt.
 *
 * **Ein Blatt von unten, wie das Notizmenue, keine Dialogbox.** Bis zum
 * 2026-09-14 war es ein `AlertDialog` mit zwei nackten Textzeilen, und das
 * sah nach nichts in dieser App aus. Jetzt liegen die beiden Wege
 * als Karten auf dem Blatt, in derselben Form wie die Ordnerzeilen darueber.
 *
 * **Zwei Wege statt eines Hakens.** Ein Haken „Inhalt mitloeschen" waere
 * kleiner, aber man liest ihn im Zweifel nicht, und der Unterschied ist hier
 * dreissig Notizen gross. Zwei Karten, die beide sagen, was danach passiert,
 * lassen sich nicht ueberlesen.
 *
 * **Kein Weg fuehrt hier an einer Rueckholmoeglichkeit vorbei.** Beide Wege
 * legen den Ordner in den Papierkorb, und der zweite die Notizen dazu. Und
 * ein leerer Ordner kommt gar nicht erst hierher (`loeschenAnfragen`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Loeschdialog(
    name: String,
    onAbbrechen: () -> Unit,
    onNurOrdner: () -> Unit,
    onMitInhalt: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onAbbrechen,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = "„" + name + "\" löschen",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Der Ordner hat Inhalt. Was soll damit passieren?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Loeschweg(
                symbol = Icons.AutoMirrored.Outlined.DriveFileMove,
                titel = "Nur den Ordner",
                erklaerung = "Die Notizen und Unterordner bleiben und ziehen um. " +
                    "Wohin, wählst du im nächsten Schritt.",
                onKlick = onNurOrdner,
            )
            Loeschweg(
                symbol = Icons.Outlined.DeleteSweep,
                titel = "Ordner und Inhalt",
                erklaerung = "Alles wandert in den Papierkorb, auch die Unterordner. " +
                    "Von dort holst du es zusammen wieder heraus.",
                onKlick = onMitInhalt,
                farbe = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Ein Weg auf dem Loeschblatt: eine Karte in der Form der Ordnerzeilen. */
@Composable
private fun Loeschweg(
    symbol: ImageVector,
    titel: String,
    erklaerung: String,
    onKlick: () -> Unit,
    farbe: Color = MaterialTheme.colorScheme.primary,
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
                tint = farbe,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(text = titel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = erklaerung,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Die Farbe eines Ordners (Phase 14e), aus der Tagpalette.
 *
 * Dieselbe Palette wie bei den Tags, und aus demselben Grund frei waehlbar:
 * Eine Ordnerfarbe ist eine Markierung, keine Flaeche mit Text darauf. Wo sie
 * doch zu Schrift wird (die gewaehlte Zeile in der Seitenspalte), rechnet
 * `lesbareFarbe` den Kontrast nach.
 *
 * „Keine Farbe" steht mit zur Wahl, sonst kaeme man von einer Farbe nicht
 * mehr los.
 */
@Composable
private fun OrdnerFarbeDialog(
    ordner: de.notizen.core.data.db.entity.FolderEntity,
    onAbbrechen: () -> Unit,
    onWahl: (Int?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Farbe von „" + ordner.name + "\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Das Ordnersymbol trägt die Farbe überall: in der Seitenspalte, " +
                        "in der Ordnerzeile und in der Ordnerauswahl.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TAG_FARBEN.chunked(7).forEach { reihe ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        reihe.forEach { wert ->
                            Farbpunkt(wert, wert == ordner.colorArgb) { onWahl(wert) }
                        }
                    }
                }
                TextButton(onClick = { onWahl(null) }, enabled = ordner.colorArgb != null) {
                    Text("Keine Farbe")
                }
            }
        },
        confirmButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/**
 * Ein Name fuer einen Ordner.
 *
 * Der Knopf bleibt tot, solange nichts dasteht. Ein Ordner ohne Namen waere in
 * der Liste eine leere Zeile, die man nicht mehr auseinanderhaelt.
 */
@Composable
fun OrdnerNameDialog(
    ueberschrift: String,
    erklaerung: String,
    vorgabe: String,
    bestaetigenText: String,
    onAbbrechen: () -> Unit,
    onBestaetigen: (String) -> Unit,
) {
    var name by remember { mutableStateOf(vorgabe) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(ueberschrift) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = erklaerung,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBestaetigen(name) },
                enabled = name.isNotBlank(),
            ) { Text(bestaetigenText) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

/**
 * Die Auswahl eines Ordners, eingerueckt wie der Baum.
 *
 * **Der Hauptordner steht mit zur Wahl**, ganz oben. Ohne ihn gaebe es keinen
 * Weg zurueck nach ganz oben, und eine Notiz, die einmal in einem Ordner lag,
 * bliebe fuer immer in irgendeinem.
 *
 * [ausgeschlossen] ist die Stelle, an der das Verschobene schon liegt. Sie
 * wird nicht angeboten: Ein Ziel, das nichts aendert, ist kein Ziel. Bei einer
 * Mehrfachauswahl gibt es diese eine Stelle nicht, dann steht hier `null`.
 */
@Composable
fun OrdnerAuswahlDialog(
    ueberschrift: String,
    erklaerung: String,
    zeilen: List<Ordnerzeile>,
    ausgeschlossen: String?,
    zeigeHauptordner: Boolean,
    onAbbrechen: () -> Unit,
    onWahl: (String?, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(ueberschrift) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = erklaerung,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    if (zeigeHauptordner) {
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

                    items(
                        items = zeilen.filter { it.ordner.id != ausgeschlossen },
                        key = { it.ordner.id },
                    ) { zeile ->
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

@Composable
internal fun Zielzeile(
    name: String,
    tiefe: Int,
    istWurzel: Boolean,
    farbe: Int?,
    onKlick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onKlick)
            .padding(
                start = EINRUECKUNG * minOf(tiefe, TIEFE_MAX),
                top = 10.dp,
                bottom = 10.dp,
                end = 8.dp,
            ),
    ) {
        Icon(
            imageVector = if (istWurzel) Icons.Outlined.Home else Icons.Outlined.Folder,
            contentDescription = null,
            tint = farbe?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
