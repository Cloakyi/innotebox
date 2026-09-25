package de.notizen.app.ui.stage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.core.data.model.Stage

/**
 * Das Archiv wird DURCHSUCHT, nicht durchblättert (Spezifikation Abschnitt 4).
 *
 * Deshalb sitzt oben ein Sucheinstieg statt einer Sortierleiste. Er ist
 * bewusst ein Knopf und kein Textfeld: getippt wird auf dem Such-Screen, und
 * die Suche geht dort über alle Stufen -- was man im Archiv sucht, liegt
 * manchmal doch noch im Workspace. Das Archiv ist nur die Voreinstellung des
 * Filters, kein Käfig.
 *
 * Darunter bleibt das Blättern erreichbar, für die Fälle, in denen man nicht
 * weiß, wonach man sucht.
 */
@Composable
fun ArchivScreen(
    viewModel: StageViewModel,
    innerPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
    onSuche: () -> Unit,
) {
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        Sucheinstieg(
            onClick = onSuche,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (notizen.isEmpty()) {
            StufeLeer(
                stage = Stage.ARCHIVE,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            )
            return@Column
        }

        Abschnittsmarke(
            if (notizen.size == 1) "Eine archivierte Notiz" else "${notizen.size} archivierte Notizen",
        )
        NotizenRaster(
            viewModel = viewModel,
            onOpenNote = onOpenNote,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
        )
    }
}

/**
 * Der Einstieg in die Suche. Sieht aus wie ein Suchfeld, ist aber eines, das
 * beim Antippen dorthin führt, wo wirklich gesucht wird.
 *
 * Vorher stand hier ein echtes Textfeld, das Eingaben annahm und dann sagte,
 * dass es noch nicht sucht. Zwei Felder für dieselbe Sache wären eines zu viel.
 */
@Composable
private fun Sucheinstieg(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Im Archiv suchen",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun Abschnittsmarke(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
    )
}
