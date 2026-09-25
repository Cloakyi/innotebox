package de.notizen.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.notizen.app.bild.rememberBild
import de.notizen.core.data.db.entity.AttachmentEntity
import java.io.File

/**
 * Ein Bild formatfuellend, mit den Aktionen, die dazugehoeren.
 *
 * Loeschen sitzt hier und nicht im Raster: Ein Muelleimer auf jeder Kachel
 * waere ein Fehlgriff, der jederzeit einen Anhang kostet. Wer loeschen will,
 * hat das Bild vorher geoeffnet und sieht, was er loescht.
 *
 * Bewusst OHNE Ruecknahme -- und deshalb mit einer klaren Beschriftung statt
 * eines blossen Symbols. Ein Anhang ist eine Datei, keine Zeile Text; ihn
 * "vielleicht" zu loeschen und drei Sekunden auf einen Widerruf zu warten,
 * hiesse, ihn drei Sekunden lang doppelt zu fuehren.
 */
@Composable
fun Bildbetrachter(
    anhang: AttachmentEntity,
    istHintergrund: Boolean,
    onHintergrund: () -> Unit,
    onLoeschen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    Dialog(
        onDismissRequest = onSchliessen,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Undurchsichtig und nicht halbtransparent: Ein Foto vor dem
                // durchscheinenden Editor waere schwer zu beurteilen, und genau
                // das will man hier tun.
                .background(Color(0xFF101010)),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val bild = rememberBild(File(anhang.localPath), maxWidth)
                bild?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Bild",
                        // Fit und nicht Crop: Hier soll das ganze Bild zu sehen
                        // sein, nicht ein huebscher Ausschnitt davon.
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            IconButton(
                onClick = onSchliessen,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Schließen", tint = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Aktion(
                    text = if (istHintergrund) "Kein Hintergrund" else "Als Hintergrund",
                    onKlick = onHintergrund,
                ) {
                    Icon(
                        imageVector = if (istHintergrund) {
                            Icons.Filled.Wallpaper
                        } else {
                            Icons.Outlined.Wallpaper
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Aktion(text = "Bild löschen", onKlick = onLoeschen) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Aktion(text: String, onKlick: () -> Unit, symbol: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onKlick) {
        symbol()
        Text(text, color = Color.White, modifier = Modifier.padding(start = 6.dp))
    }
}
