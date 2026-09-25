package de.notizen.app.ui.ordner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.app.ui.components.Zeilenlage
import de.notizen.app.ui.components.rememberZiehzustand
import de.notizen.app.ui.components.ziehgriff
import de.notizen.core.data.db.entity.FolderEntity

/**
 * Die Geschwisterreihe zum Neuordnen per Ziehen.
 *
 * Die Geste selbst steht in `ui/components/Ziehen.kt` und ist dieselbe wie bei
 * den Eintraegen einer Liste im Editor. Hier kommt nur dazu, was
 * eine `LazyColumn` beitraegt: die Lagen der Zeilen aus `layoutInfo`, und
 * `animateItem` fuer die Nachbarn, die an ihren neuen Platz gleiten. Die
 * gezogene Zeile selbst bekommt kein `animateItem`, sonst kaempfte die
 * Platzanimation gegen den Finger.
 *
 * Gespeichert wird hier nichts. Der Haken unten rechts tut das; wer mit
 * Zurueck abbricht, hat nichts veraendert.
 */
@Composable
fun NeuAnordnenListe(
    ordner: List<FolderEntity>,
    onVerschieben: (von: Int, nach: Int) -> Unit,
    innerPadding: PaddingValues,
) {
    val listenZustand = rememberLazyListState()
    val zustand = rememberZiehzustand()
    val scope = rememberCoroutineScope()
    // Die Geste liest die Reihenfolge bei jeder Bewegung neu. Ueber
    // `rememberUpdatedState`, weil `pointerInput` seinen Block nur beim Wechsel
    // der Kennung neu startet: Eine direkt eingefangene Liste bliebe nach dem
    // ersten Tausch die alte, und der zweite Tausch traefe die falschen Zeilen.
    val aktuell by rememberUpdatedState(ordner)
    val bekannt = remember(ordner) { ordner.mapTo(HashSet()) { it.id } }

    LazyColumn(
        state = listenZustand,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hinweis") {
            Hinweisblock(
                titel = "Ordner neu anordnen",
                text = "Zieh die Ordner am Griff in die Reihenfolge, die du willst. " +
                    "Der Haken unten rechts speichert sie, Zurück verwirft.",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        itemsIndexed(ordner, key = { _, o -> o.id }) { _, o ->
            val istGezogen = zustand.gezogen == o.id
            Surface(
                color = if (istGezogen) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (istGezogen) Modifier else Modifier.animateItem())
                    .zIndex(if (istGezogen) 1f else 0f)
                    .graphicsLayer { translationY = if (istGezogen) zustand.versatz else 0f },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = o.name + " verschieben",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .ziehgriff(
                                zustand = zustand,
                                kennung = o.id,
                                scope = scope,
                                lagen = {
                                    listenZustand.layoutInfo.visibleItemsInfo
                                        .filter { it.key in bekannt }
                                        .map { Zeilenlage(it.key as String, it.offset.toFloat(), it.size.toFloat()) }
                                },
                                reihenfolge = { aktuell.map { it.id } },
                                onVerschieben = onVerschieben,
                            ),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = o.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(text = o.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}
