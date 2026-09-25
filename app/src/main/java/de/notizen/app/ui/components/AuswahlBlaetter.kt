package de.notizen.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import de.notizen.app.bild.rememberBild
import de.notizen.app.ui.theme.alsNotizFarbe
import de.notizen.app.ui.theme.anzeigename
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.model.NoteColor
import java.io.File

/**
 * Farbauswahl fuer eine oder mehrere Notizen.
 *
 * ZWEI GETRENNTE SYSTEME, nicht verwechseln: Das hier faerbt die KARTE. Die
 * Farbe eines Tags faerbt nur dessen Chip und den Punkt im Drawer. Eine Notiz
 * behaelt ihren Tag, wenn man sie umfaerbt, und umgekehrt.
 *
 * Standard steht bewusst an erster Stelle: es ist der Weg zurueck, und den
 * sucht man haeufiger als eine bestimmte Farbe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarbAuswahlBlatt(
    aktuell: NoteColor?,
    anzahl: Int,
    onWahl: (NoteColor) -> Unit,
    onSchliessen: () -> Unit,
    /**
     * Das gerade gesetzte Hintergrundbild, falls es eines gibt.
     *
     * Die drei Hintergrund-Parameter sind `null`, wenn das Blatt aus der
     * Uebersicht heraus fuer MEHRERE Notizen geoeffnet wird: Ein Bild fuer
     * sieben Notizen auf einmal zu waehlen waere keine sinnvolle Handlung.
     */
    hintergrund: AttachmentEntity? = null,
    onHintergrundWaehlen: (() -> Unit)? = null,
    onHintergrundEntfernen: (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onSchliessen,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = if (anzahl > 1) "Farbe für $anzahl Notizen" else "Farbe",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (aktuell == null && anzahl > 1) {
                Text(
                    text = "Die Auswahl hat gerade verschiedene Farben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            NoteColor.entries.chunked(5).forEach { reihe ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    reihe.forEach { farbe ->
                        Farbkreis(
                            farbe = farbe,
                            gewaehlt = farbe == aktuell,
                            onClick = { onWahl(farbe) },
                        )
                    }
                }
            }

            if (onHintergrundWaehlen != null) {
                HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 12.dp))

                Text(
                    text = "Hintergrundbild",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Ein Bild als Fläche der Notiz. Es zählt nicht zu den Bildern " +
                        "der Notiz und erscheint nicht in ihrem Raster. Solange eines " +
                        "gesetzt ist, hat die Farbe oben keine Wirkung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (hintergrund != null) {
                        val vorschau = rememberBild(File(hintergrund.localPath), 64.dp)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            vorschau?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                    }

                    TextButton(onClick = onHintergrundWaehlen) {
                        Icon(
                            Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (hintergrund == null) "Bild wählen" else "Bild ändern",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }

                    if (hintergrund != null && onHintergrundEntfernen != null) {
                        TextButton(onClick = onHintergrundEntfernen) {
                            Text("Entfernen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Farbkreis(farbe: NoteColor, gewaehlt: Boolean, onClick: () -> Unit) {
    val toene = farbe.alsNotizFarbe()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.size(56.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(toene.container)
                .border(
                    width = if (gewaehlt) 2.dp else 1.dp,
                    color = if (gewaehlt) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        ) {
            if (gewaehlt) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = toene.onContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = farbe.anzeigename(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Tags an eine oder mehrere Notizen haengen.
 *
 * Bei Mehrfachauswahl steht der Haken, wenn ALLE gewaehlten Notizen den Tag
 * tragen. Antippen setzt ihn dann fuer alle oder nimmt ihn allen weg -- ein
 * dritter Zwischenzustand waere zwar ehrlicher, aber kaum bedienbar.
 */
@Composable
fun TagAuswahlDialog(
    tags: List<TagEntity>,
    zugewiesen: Set<String>,
    anzahl: Int,
    onUmschalten: (String) -> Unit,
    onNeuerTag: () -> Unit,
    onFertig: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFertig,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = { Text(if (anzahl > 1) "Tags für $anzahl Notizen" else "Tags") },
        text = {
            if (tags.isEmpty()) {
                Text(
                    text = "Noch keine Tags angelegt. Tags sind hier das, was anderswo " +
                        "Ordner sind, nur kann eine Notiz mehrere davon tragen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUmschalten(tag.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = tag.id in zugewiesen,
                                onCheckedChange = { onUmschalten(tag.id) },
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.colorArgb)),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onNeuerTag) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Neuer Tag")
            }
        },
        confirmButton = {
            TextButton(onClick = onFertig) { Text("Fertig") }
        },
    )
}
