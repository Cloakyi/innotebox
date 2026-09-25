package de.notizen.app.ui.suche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.notizen.app.ui.NoteTypeIcons
import de.notizen.app.ui.StageUi
import de.notizen.app.ui.theme.alsNotizFarbe
import de.notizen.core.data.repository.Suchtreffer

/**
 * Ein Suchtreffer.
 *
 * Sieht bewusst anders aus als eine Notizkarte, weil es etwas anderes ist: eine
 * Karte zeigt eine Notiz, ein Treffer zeigt warum sie gefunden wurde. Der
 * Ausschnitt kommt deshalb aus `snippet()` und nicht vom Anfang der Notiz -- er
 * springt an die Fundstelle, auch wenn die auf Seite drei eines Transkripts
 * liegt.
 *
 * Dazu die Stufe, denn die Suche geht über alle drei: ohne diese Angabe wüsste
 * man nach dem Finden nicht, wo die Notiz eigentlich liegt.
 */
@Composable
fun Trefferkarte(treffer: Suchtreffer, onClick: () -> Unit) {
    val notiz = treffer.notiz.note
    val farbe = notiz.colorId.alsNotizFarbe()
    val gedaempft = farbe.onContainer.copy(alpha = 0.72f)

    Surface(
        color = farbe.container,
        contentColor = farbe.onContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NoteTypeIcons.of(notiz.type),
                    contentDescription = null,
                    tint = gedaempft,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notiz.title.ifBlank { "Ohne Titel" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (notiz.title.isBlank()) gedaempft else farbe.onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (notiz.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            treffer.ausschnitt?.let { ausschnitt ->
                Text(
                    text = hervorgehoben(
                        ausschnitt = ausschnitt,
                        stil = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = gedaempft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                // Aus dem Papierkorb (14d): erkennbar markiert, sonst hielte
                // man eine weggeworfene Notiz fuer eine lebende.
                if (treffer.imPapierkorb) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Im Papierkorb",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = StageUi.label(notiz.stage),
                    style = MaterialTheme.typography.labelSmall,
                    color = gedaempft,
                )
                treffer.notiz.tags.take(3).forEach { tag ->
                    Text(
                        text = "#${tag.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = gedaempft,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
