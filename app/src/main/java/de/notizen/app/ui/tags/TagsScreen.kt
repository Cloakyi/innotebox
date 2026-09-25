package de.notizen.app.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auswahlpalette fuer Tag-Farben.
 *
 * ACHTUNG, ZWEI GETRENNTE SYSTEME: Das hier sind TAG-Farben. Sie faerben nur
 * den Chip und den Punkt im Drawer. Die Farbe der KARTE waehlt man getrennt
 * ueber das Palettensymbol -- eine Notiz behaelt ihren Tag, wenn man sie
 * umfaerbt, und umgekehrt (Spezifikation Abschnitt 5 und 5a).
 */
/** Die Farben, die ein Tag-Chip tragen kann. Auch vom Editor benutzt. */
val TAG_FARBEN = listOf(
    0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679,
    0xFF0B8043, 0xFF00897B, 0xFF039BE5, 0xFF3F51B5, 0xFF7986CB,
    0xFF8E24AA, 0xFF795548, 0xFF616161,
).map { it.toInt() }

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tags: TagRepository,
) : ViewModel() {

    val alle: StateFlow<List<TagEntity>> = tags.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Wie oft jeder Tag verwendet wird -- fuer die Anzeige im Verwaltungsscreen. */
    val nutzung: StateFlow<Map<String, Int>> = tags.observeAll()
        .map { liste -> liste.associate { it.id to tags.usageCount(it.id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun anlegen(name: String, farbe: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { tags.create(name, farbe) }
    }

    fun umbenennen(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { tags.rename(id, name) }
    }

    fun umfaerben(id: String, farbe: Int) {
        viewModelScope.launch { tags.recolor(id, farbe) }
    }

    fun zusammenfuehren(vonId: String, aufId: String) {
        viewModelScope.launch { tags.merge(vonId, aufId) }
    }

    fun loeschen(id: String) {
        viewModelScope.launch { tags.delete(id) }
    }
}

/**
 * Tag-Verwaltung: anlegen, umbenennen, umfaerben, zusammenfuehren, loeschen.
 *
 * Loeschen und Zusammenfuehren sind ENDGUELTIG -- der Tag verschwindet samt
 * Zuordnungen, und ein Tombstone sorgt dafuer, dass ihn der andere Client
 * nicht wiederbelebt. Die Notizen selbst bleiben unberuehrt und verlieren nur
 * ihren Chip. Deshalb steht die Zahl der betroffenen Notizen im Dialog.
 */
@Composable
fun TagsScreen(
    innerPadding: PaddingValues,
    zeigeHinweis: (String) -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val tags by viewModel.alle.collectAsStateWithLifecycle()
    val nutzung by viewModel.nutzung.collectAsStateWithLifecycle()

    var neuOffen by remember { mutableStateOf(false) }
    var bearbeitet by remember { mutableStateOf<TagEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 48.dp,
        ),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { neuOffen = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(14.dp))
                Text(
                    text = "Neuer Tag",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (tags.isEmpty()) {
            item {
                Hinweisblock(
                    titel = "Noch keine Tags",
                    text = "Tags sind hier das, was anderswo Ordner sind, nur beweglicher: " +
                        "eine Notiz kann mehrere tragen und taucht dann unter jedem auf.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }

        items(tags, key = { it.id }) { tag ->
            val anzahl = nutzung[tag.id] ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bearbeitet = tag }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(tag.colorArgb)),
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (anzahl) {
                            0 -> "Noch nicht verwendet"
                            1 -> "1 Notiz"
                            else -> "$anzahl Notizen"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (neuOffen) {
        TagDialog(
            ueberschrift = "Neuer Tag",
            startName = "",
            startFarbe = TAG_FARBEN.first(),
            andereTags = emptyList(),
            verwendungen = 0,
            onAbbrechen = { neuOffen = false },
            onSpeichern = { name, farbe ->
                viewModel.anlegen(name, farbe)
                neuOffen = false
            },
            onZusammenfuehren = null,
            onLoeschen = null,
        )
    }

    bearbeitet?.let { tag ->
        TagDialog(
            ueberschrift = "Tag bearbeiten",
            startName = tag.name,
            startFarbe = tag.colorArgb,
            andereTags = tags.filter { it.id != tag.id },
            verwendungen = nutzung[tag.id] ?: 0,
            onAbbrechen = { bearbeitet = null },
            onSpeichern = { name, farbe ->
                if (name != tag.name) viewModel.umbenennen(tag.id, name)
                if (farbe != tag.colorArgb) viewModel.umfaerben(tag.id, farbe)
                bearbeitet = null
            },
            onZusammenfuehren = { zielId ->
                viewModel.zusammenfuehren(tag.id, zielId)
                bearbeitet = null
                zeigeHinweis("Zusammengeführt. Der alte Tag ist endgültig weg")
            },
            onLoeschen = {
                viewModel.loeschen(tag.id)
                bearbeitet = null
                zeigeHinweis("Tag gelöscht. Die Notizen selbst bleiben erhalten")
            },
        )
    }
}

/**
 * Anlegen und Bearbeiten eines Tags.
 *
 * Nicht mehr privat, seit der Editor ihn ebenfalls braucht: Wer dort „Neuer
 * Tag" antippt, soll denselben Dialog sehen wie in der Tag-Verwaltung --
 * dieselben Farben, dieselbe Beschriftung. Eine zweite, leicht abweichende
 * Fassung waere genau die Art Unterschied, die man erst nach Monaten bemerkt
 * und dann nicht mehr erklaeren kann.
 *
 * `onZusammenfuehren` und `onLoeschen` duerfen `null` sein -- beim Anlegen gibt
 * es weder etwas zusammenzufuehren noch zu loeschen.
 */
@Composable
fun TagDialog(
    ueberschrift: String,
    startName: String,
    startFarbe: Int,
    andereTags: List<TagEntity>,
    verwendungen: Int,
    onAbbrechen: () -> Unit,
    onSpeichern: (String, Int) -> Unit,
    onZusammenfuehren: ((String) -> Unit)?,
    onLoeschen: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(startName) }
    var farbe by remember { mutableStateOf(startFarbe) }
    var zusammenfuehrenOffen by remember { mutableStateOf(false) }

    if (zusammenfuehrenOffen && onZusammenfuehren != null) {
        ZusammenfuehrenDialog(
            quelle = startName,
            ziele = andereTags,
            onAbbrechen = { zusammenfuehrenOffen = false },
            onWahl = onZusammenfuehren,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = { Text(ueberschrift) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Farbe des Chips",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TAG_FARBEN.chunked(7).forEach { reihe ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    ) {
                        reihe.forEach { wert ->
                            Farbpunkt(wert, wert == farbe) { farbe = wert }
                        }
                    }
                }

                if (onZusammenfuehren != null || onLoeschen != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = if (verwendungen == 0) {
                            "Dieser Tag wird von keiner Notiz verwendet."
                        } else {
                            "$verwendungen Notizen tragen diesen Tag. Sie bleiben " +
                                "erhalten und verlieren nur den Chip."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onZusammenfuehren != null && andereTags.isNotEmpty()) {
                            TextButton(onClick = { zusammenfuehrenOffen = true }) {
                                Icon(Icons.Outlined.Merge, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Zusammenführen")
                            }
                        }
                        if (onLoeschen != null) {
                            TextButton(onClick = onLoeschen) {
                                Text("Löschen", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSpeichern(name.trim(), farbe) },
                enabled = name.isNotBlank(),
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun ZusammenfuehrenDialog(
    quelle: String,
    ziele: List<TagEntity>,
    onAbbrechen: () -> Unit,
    onWahl: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        title = { Text("$quelle zusammenführen mit") },
        text = {
            Column {
                Text(
                    text = "Alle Notizen wechseln auf den gewählten Tag. " +
                        "$quelle verschwindet dabei endgültig.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(ziele, key = { it.id }) { ziel ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onWahl(ziel.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(ziel.colorArgb)),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                text = ziel.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
        },
    )
}

@Composable
internal fun Farbpunkt(wert: Int, gewaehlt: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(wert))
            .border(
                width = if (gewaehlt) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
