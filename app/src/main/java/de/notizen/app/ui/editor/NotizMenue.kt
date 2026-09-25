package de.notizen.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Was man mit der ganzen Notiz tun kann.
 *
 * **Warum ein Blatt und keine weiteren Knöpfe in der Leiste:** Die untere
 * Leiste trägt, was man beim Schreiben ständig braucht — Anhang, Farbe,
 * Erinnerung. Löschen, Teilen und Kopieren braucht man einmal, dafür dann
 * bewusst. Sie in dieselbe Reihe zu stellen hieße, den seltenen Griff genauso
 * leicht zu machen wie den häufigen — und Löschen liegt dann einen Fingerbreit
 * neben Einfärben.
 *
 * Das Blatt liegt auf der Theme-Fläche, nicht auf der Notiz. Deshalb trägt es
 * hier ausnahmsweise Theme-Farben und nicht die Notizfarbe: Es schwebt darüber,
 * es sitzt nicht darauf (siehe docs/ENTSCHEIDUNGEN.md, Farbregeln).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotizMenueBlatt(
    anzahlTags: Int,
    abgleichAn: Boolean,
    kalenderAn: Boolean,
    hatErinnerung: Boolean,
    zeigeOrdner: Boolean,
    ordnername: String?,
    imArchiv: Boolean,
    onOrdner: () -> Unit,
    onArchivieren: () -> Unit,
    onZurueckholen: () -> Unit,
    onAbgleich: () -> Unit,
    onKalender: () -> Unit,
    onJetztSichern: () -> Unit,
    onTags: () -> Unit,
    onKopie: () -> Unit,
    onTeilen: () -> Unit,
    onLoeschen: () -> Unit,
    onSchliessen: () -> Unit,
    /** Nur, wenn es einen Weg zum Uebersetzen gibt (Phase 16). */
    zeigeUebersetzen: Boolean = false,
    onUebersetzen: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onSchliessen,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Eintrag(
                symbol = Icons.AutoMirrored.Outlined.Label,
                text = if (anzahlTags == 0) {
                    "Tag hinzufügen"
                } else {
                    "Tags ändern ($anzahlTags)"
                },
                onKlick = onTags,
            )
            // NUR im Ordnermodus. Solange der Fluss laeuft, gibt es keine
            // Ordneransicht, in der das Ergebnis zu sehen waere -- der Eintrag
            // verspraeche dann eine Ablage, die niemand aufmachen kann.
            //
            // Im Archiv des Ordnermodus (14b) steht statt der Ordnerwahl das
            // Zurueckholen: Es fragt, wohin, und deckt die Ordnerwahl damit ab.
            // Eine Ordnerwahl daneben, die den normalen Baum anboete, legte
            // eine archivierte Notiz in einen Ordner, in dem sie nicht zu
            // sehen waere.
            if (zeigeOrdner && imArchiv) {
                Eintrag(
                    symbol = Icons.Outlined.Unarchive,
                    text = "Aus dem Archiv zurückholen",
                    unterzeile = "Liegt im Archiv. Du wählst, wohin sie zurückkommt",
                    onKlick = onZurueckholen,
                )
            } else if (zeigeOrdner) {
                Eintrag(
                    symbol = Icons.AutoMirrored.Outlined.DriveFileMove,
                    text = if (ordnername == null) "In einen Ordner legen" else "Ordner wechseln",
                    unterzeile = ordnername ?: "Liegt in keinem Ordner",
                    onKlick = onOrdner,
                )
                Eintrag(
                    symbol = Icons.Outlined.Archive,
                    text = "Archivieren",
                    unterzeile = "Kommt ins Archiv der Ordner. Die Stufe bleibt",
                    onKlick = onArchivieren,
                )
            }
            Eintrag(Icons.Outlined.ContentCopy, "Kopie erstellen", onKopie)
            Eintrag(Icons.AutoMirrored.Outlined.Send, "Teilen", onTeilen)
            // Erst fragen, dann anbieten: Der Eintrag fehlt, wenn dieses
            // Geraet keinen Weg zum Uebersetzen hat.
            if (zeigeUebersetzen) {
                Eintrag(
                    symbol = Icons.Outlined.Translate,
                    text = "Übersetzen",
                    unterzeile = "Den ganzen Text oder alle Einträge. Du wählst danach, wohin",
                    onKlick = onUebersetzen,
                )
            }

            // Nur wenn die Notiz ueberhaupt in die Cloud darf. Ein Knopf
            // "Jetzt sichern" an einer ausgenommenen Notiz waere ein
            // Versprechen, das die Einstellung darunter gerade widerruft.
            if (abgleichAn) {
                Eintrag(
                    symbol = Icons.Outlined.CloudUpload,
                    text = "Jetzt sichern",
                    unterzeile = "Gleicht sofort ab, statt auf den Takt zu warten",
                    onKlick = onJetztSichern,
                )
            }

            // Der Schalter sagt, was NACH dem Antippen gilt, und die Zeile
            // darunter, was JETZT gilt. Ein Menueeintrag, der nur "Abgleich"
            // heisst, laesst beides offen.
            Eintrag(
                symbol = if (abgleichAn) Icons.Outlined.CloudOff else Icons.Outlined.CloudQueue,
                text = if (abgleichAn) {
                    "Nicht mehr synchronisieren"
                } else {
                    "Wieder synchronisieren"
                },
                unterzeile = if (abgleichAn) {
                    "Liegt in Google Drive"
                } else {
                    "Bleibt auf diesem Gerät"
                },
                onKlick = onAbgleich,
            )
            // Nur wenn es ueberhaupt eine Erinnerung gibt. Ohne Termin gibt es
            // nichts einzutragen, und ein Schalter fuer etwas, das gar nicht
            // stattfindet, laesst einen raten, was er tut.
            if (hatErinnerung) {
                Eintrag(
                    symbol = if (kalenderAn) {
                        Icons.Outlined.EventBusy
                    } else {
                        Icons.Outlined.EventAvailable
                    },
                    text = if (kalenderAn) {
                        "Nicht im Kalender zeigen"
                    } else {
                        "Wieder im Kalender zeigen"
                    },
                    unterzeile = if (kalenderAn) {
                        "Steht als Termin im Kalender"
                    } else {
                        "Klingelt, steht aber in keinem Kalender"
                    },
                    onKlick = onKalender,
                )
            }

            Eintrag(
                symbol = Icons.Outlined.Delete,
                text = "In den Papierkorb",
                onKlick = onLoeschen,
                farbe = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun Eintrag(
    symbol: ImageVector,
    text: String,
    onKlick: () -> Unit,
    farbe: Color = MaterialTheme.colorScheme.onSurface,
    unterzeile: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onKlick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = farbe, modifier = Modifier.size(22.dp))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = farbe)
            if (unterzeile != null) {
                Text(
                    text = unterzeile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
