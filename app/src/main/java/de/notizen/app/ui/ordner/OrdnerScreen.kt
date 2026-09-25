package de.notizen.app.ui.ordner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.app.ui.stage.Kopfzeile
import de.notizen.app.ui.stage.NotizenRaster
import de.notizen.app.ui.stage.StageViewModel
import de.notizen.core.data.db.entity.FolderEntity

/**
 * Ein Ordner: seine Unterordner und die Notizen darin.
 *
 * **Alles in einem Scrollbereich.** Die Ordner sind Kopfzeilen des
 * Notizrasters, kein eigener Block darueber. Bei drei Ordnern saehe beides
 * gleich aus; bei dreissig waere ein eigener Block eine Wand, hinter der die
 * Notizen verschwinden.
 *
 * **Die Notizen sehen aus wie ueberall sonst**, mit ihren Farben, ihren
 * Bildern, ihren Wischgesten. Das ist der Grund, warum hier dasselbe
 * StageViewModel arbeitet wie im Fluss: An einer Karte haengt zu viel, um sie
 * ein zweites Mal zu bauen.
 */
@Composable
fun OrdnerScreen(
    stage: StageViewModel,
    ordner: OrdnerViewModel,
    innerPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
    onOrdner: (String?) -> Unit,
) {
    val unterordner by ordner.inhalt.collectAsStateWithLifecycle()
    val weg by ordner.weg.collectAsStateWithLifecycle()
    val notizen by stage.notizen.collectAsStateWithLifecycle()
    val neuAnordnen by ordner.neuAnordnen.collectAsStateWithLifecycle()

    // NEU ANORDNEN (14e): Solange es laeuft, steht statt Ordnern und Notizen
    // nur die Geschwisterreihe mit Griffen da. Zurueck bricht ab, der Haken
    // (unten rechts, an der Stelle des Plus) speichert.
    neuAnordnen?.let { reihe ->
        BackHandler { ordner.neuAnordnenAbbrechen() }
        NeuAnordnenListe(
            ordner = reihe,
            onVerschieben = ordner::neuAnordnenVerschieben,
            innerPadding = innerPadding,
        )
        return
    }

    // AB ZEHN ORDNERN ZUSAMMENGEKLAPPT (14e), zu einer Zeile „Ordner (14)" mit
    // Pfeil. Bei drei Ordnern waere das eine Huerde, bei dreissig sind die
    // Notizen sonst erst nach einer Bildschirmseite Ordner zu sehen. `null`
    // heisst: noch nicht von Hand umgeschaltet, dann entscheidet die Zahl.
    var zugeklapptVonHand by rememberSaveable(ordner.ordnerId) { mutableStateOf<Boolean?>(null) }
    val zugeklappt = zugeklapptVonHand ?: (unterordner.size >= ZUSAMMENKLAPPEN_AB)

    val kopfzeilen = buildList {
        if (weg.isNotEmpty()) {
            add(Kopfzeile("weg") { Wegzeile(weg = weg, imArchiv = ordner.imArchiv, onOrdner = onOrdner) })
        }

        // DER ANLEGEKNOPF STEHT OBEN, nicht unter der Liste. Am 2026-08-25 vom
        // Nutzer gewuenscht, und er hat recht: Unten stand er mal direkt unter
        // dem Weg, mal nach dreissig Ordnern, mal unter einem Hinweistext. Oben
        // steht er immer an derselben Stelle.
        add(
            Kopfzeile("neuer-ordner") {
                NeuerOrdnerZeile { ordner.oeffne(Ordnerdialog.Neu(ordner.ordnerId)) }
            },
        )

        if (unterordner.size >= ZUSAMMENKLAPPEN_AB) {
            add(
                Kopfzeile("ordner-kopf") {
                    Klappzeile(
                        text = "Ordner (" + unterordner.size + ")",
                        zugeklappt = zugeklappt,
                        onUmschalten = { zugeklapptVonHand = !zugeklappt },
                    )
                },
            )
        }
        if (!zugeklappt) {
            unterordner.forEach { karte ->
                add(
                    Kopfzeile("ordner-" + karte.ordner.id) {
                        Ordnerzeile(
                            karte = karte,
                            onOeffnen = { onOrdner(karte.ordner.id) },
                            onUmbenennen = { ordner.oeffne(Ordnerdialog.Umbenennen(karte.ordner)) },
                            onVerschieben = { ordner.oeffne(Ordnerdialog.Verschieben(karte.ordner)) },
                            onFarbe = { ordner.oeffne(Ordnerdialog.Farbe(karte.ordner)) },
                            onNeuAnordnen = if (unterordner.size > 1) ordner::neuAnordnenStarten else null,
                            onLoeschen = { ordner.loeschenAnfragen(karte) },
                        )
                    },
                )
            }
        }

        // Der Hinweis steht IN der Liste, nicht darueber gelegt. So bleibt der
        // Anlegeknopf oben erreichbar, und der leere Ordner sieht aus wie ein
        // Ordner und nicht wie ein anderer Bildschirm.
        if (unterordner.isEmpty() && notizen.isEmpty()) {
            add(
                Kopfzeile("leer") {
                    OrdnerLeer(imHauptordner = ordner.ordnerId == null, imArchiv = ordner.imArchiv)
                },
            )
        }
    }

    NotizenRaster(
        viewModel = stage,
        onOpenNote = onOpenNote,
        kopfzeilen = kopfzeilen,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            // Grosszuegig, damit der FAB die letzte Karte nicht verdeckt.
            bottom = innerPadding.calculateBottomPadding() + 96.dp,
        ),
    )
}

/**
 * Der Weg von der Wurzel bis hierher.
 *
 * Jede Station ist antippbar. Waagerecht scrollbar, weil ein tiefer Weg sonst
 * abgeschnitten waere und man dann ausgerechnet die vorderen Stationen nicht
 * mehr traefe.
 */
@Composable
private fun Wegzeile(weg: List<FolderEntity>, imArchiv: Boolean, onOrdner: (String?) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        // Im Archiv fuehrt der Weg zum Anfang des Archivs, nicht zum
        // Hauptordner: zwei Baeume, zwei Wurzeln.
        Icon(
            imageVector = if (imArchiv) Icons.Outlined.Archive else Icons.Outlined.Home,
            contentDescription = if (imArchiv) "Archiv" else "Hauptordner",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOrdner(null) }
                .padding(6.dp)
                .size(18.dp),
        )

        weg.forEachIndexed { stelle, station ->
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
            val letzte = stelle == weg.lastIndex
            Text(
                text = station.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (letzte) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !letzte) { onOrdner(station.id) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Ein Unterordner.
 *
 * Das Menue haengt am Dreipunkt UND am langen Druecken. Der Dreipunkt ist der
 * sichtbare Weg, das lange Druecken der schnelle. Eine Geste, die man nicht
 * sieht, waere als einziger Weg zum Umbenennen zu wenig.
 */
@Composable
private fun Ordnerzeile(
    karte: Ordnerkarte,
    onOeffnen: () -> Unit,
    onUmbenennen: () -> Unit,
    onVerschieben: () -> Unit,
    onFarbe: () -> Unit,
    onNeuAnordnen: (() -> Unit)?,
    onLoeschen: () -> Unit,
) {
    var menue by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(onClick = onOeffnen, onLongClick = { menue = true })
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = karte.ordner.colorArgb?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = karte.ordner.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = untertitel(karte),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menue = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Mehr zu " + karte.ordner.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                    DropdownMenuItem(
                        text = { Text("Umbenennen") },
                        leadingIcon = {
                            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                        },
                        onClick = {
                            menue = false
                            onUmbenennen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Verschieben nach") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
                        },
                        onClick = {
                            menue = false
                            onVerschieben()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Farbe") },
                        leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                        onClick = {
                            menue = false
                            onFarbe()
                        },
                    )
                    // Nur, wenn es etwas zu ordnen gibt: Ein einzelner Ordner
                    // hat keine Reihenfolge.
                    if (onNeuAnordnen != null) {
                        DropdownMenuItem(
                            text = { Text("Neu anordnen") },
                            leadingIcon = { Icon(Icons.Outlined.SwapVert, contentDescription = null) },
                            onClick = {
                                menue = false
                                onNeuAnordnen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menue = false
                            onLoeschen()
                        },
                    )
                }
            }
        }
    }
}

/** Ab wie vielen Unterordnern sie zu einer Zeile zusammenklappen (14e). */
private const val ZUSAMMENKLAPPEN_AB = 10

/** Die Zeile „Ordner (n)" mit Pfeil, die die Unterordner auf- und zuklappt. */
@Composable
private fun Klappzeile(text: String, zugeklappt: Boolean, onUmschalten: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onUmschalten)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (zugeklappt) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = if (zugeklappt) "Ordner aufklappen" else "Ordner zuklappen",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Was neben dem Ordnernamen steht.
 *
 * Die Zahl zaehlt die Unterordner mit. Ein Ordner, der nur volle Unterordner
 * enthaelt, stuende sonst bei null und saehe leer aus.
 */
internal fun untertitel(karte: Ordnerkarte): String {
    val notizen = when (karte.anzahl) {
        0 -> "Noch nichts darin"
        1 -> "1 Notiz"
        else -> karte.anzahl.toString() + " Notizen"
    }
    val ordner = when (karte.unterordner) {
        0 -> ""
        1 -> " · 1 Ordner"
        else -> " · " + karte.unterordner + " Ordner"
    }
    return notizen + ordner
}

@Composable
private fun NeuerOrdnerZeile(onKlick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onKlick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CreateNewFolder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "Neuen Ordner anlegen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun OrdnerLeer(imHauptordner: Boolean, imArchiv: Boolean) {
    val (titel, text) = when {
        imArchiv && imHauptordner -> "Das Archiv ist noch leer" to
            "Halte eine Notiz gedrückt und tipp auf das Archivsymbol. " +
                "Sie kommt hierher, und du holst sie jederzeit zurück."
        imArchiv -> "Dieser Archivordner ist leer" to
            "Leg archivierte Notizen über die Auswahl hinein, oder lösch ihn wieder."
        imHauptordner -> "Noch keine Ordner" to
            "Leg einen Ordner an und deine Notizen hinein. " +
                "Was in keinem Ordner liegt, steht weiterhin hier."
        else -> "Dieser Ordner ist leer" to
            "Tipp auf das Plus für eine neue Notiz. Sie landet gleich hier drin."
    }

    // Hinweisblock und nicht LeererZustand: Ueber diesem Block stehen der Weg
    // und darunter der Anlegeknopf. Eine Fassung, die den ganzen Bildschirm
    // fuellt, schoebe beides weg.
    Hinweisblock(titel = titel, text = text, modifier = Modifier.padding(vertical = 32.dp))
}
