package de.notizen.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.notizen.app.ai.Textvergleich

/** Welche Fassung des Transkripts gezeigt wird. */
enum class Fassung(val beschriftung: String) {
    BEARBEITET("Bearbeitet"),
    ORIGINAL("Original"),
}

/**
 * Das Transkript, lesbar, nicht versehentlich änderbar.
 *
 * Das Rohtranskript ist ein Beleg und kein Entwurf. Es hält fest, was
 * tatsächlich gesagt wurde; wer es überschreiben kann, verliert genau dann
 * etwas, wenn er später wissen will, ob ein Satz wirklich so fiel. Deshalb ist
 * es hier nur zu lesen, und Änderungen leben in einer zweiten Fassung daneben.
 *
 * Gezeigt wird standardmäßig die bearbeitete Fassung, weil sie die ist, mit
 * der man weiterarbeitet. Ein Umschalter führt zum Original, und was gegenüber
 * dem Original hinzugekommen ist, wird eingefärbt, wer sieht, was die
 * Aufbereitung geändert hat, kann ihr überhaupt erst misstrauen.
 */
@Composable
fun Transkriptansicht(
    original: String,
    bearbeitet: String,
    fassung: Fassung,
    bearbeitungsmodus: Boolean,
    farbe: Color,
    markierer: Color,
    onFassung: (Fassung) -> Unit,
    onBearbeiten: () -> Unit,
    onFertig: () -> Unit,
    onZuruecksetzen: () -> Unit,
    bearbeitungsfeld: @Composable (VisualTransformation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Fassung.entries.forEach { wert ->
                FilterChip(
                    selected = wert == fassung && !bearbeitungsmodus,
                    onClick = { onFassung(wert) },
                    enabled = !bearbeitungsmodus,
                    label = { Text(wert.beschriftung) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = farbe.copy(alpha = 0.75f),
                        selectedLabelColor = farbe,
                        selectedContainerColor = farbe.copy(alpha = 0.18f),
                    ),
                    // Auch der Rand des NICHT gewaehlten Chips sitzt auf der
                    // Notiz und darf nicht im Themeblau stehen bleiben.
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = !bearbeitungsmodus,
                        selected = wert == fassung && !bearbeitungsmodus,
                        borderColor = farbe.copy(alpha = 0.4f),
                        selectedBorderColor = farbe.copy(alpha = 0.6f),
                    ),
                )
            }

            Spacer(Modifier.weight(1f))

            if (bearbeitungsmodus) {
                TextButton(onClick = onFertig) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = farbe,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Fertig", color = farbe, modifier = Modifier.padding(start = 4.dp))
                }
            } else if (fassung == Fassung.BEARBEITET) {
                // Nur an der bearbeiteten Fassung: Das Original bleibt, wie es
                // ist, und ein Stift daneben waere eine Zusage, die nicht gilt.
                TextButton(onClick = onBearbeiten) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = farbe,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Bearbeiten", color = farbe, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        when {
            // Auch beim Bearbeiten bleibt markiert, was hinzukam. Vorher war
            // der Text dort schlagartig einfarbig -- und man musste die eigene
            // Änderung suchen, statt sie zu sehen.
            bearbeitungsmodus -> bearbeitungsfeld(
                Markierungsanzeige(original = original, markierer = markierer),
            )

            fassung == Fassung.ORIGINAL -> Text(
                text = original.ifBlank { "Es gibt noch kein Transkript." },
                style = MaterialTheme.typography.bodyLarge,
                color = farbe.copy(alpha = if (original.isBlank()) 0.6f else 1f),
            )

            else -> Text(
                text = markiert(original, bearbeitet, markierer),
                style = MaterialTheme.typography.bodyLarge,
                color = farbe,
            )
        }

        if (fassung == Fassung.BEARBEITET && original.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    text = "Hervorgehoben ist, was gegenüber der Aufnahme hinzukam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = farbe.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                // Der Rückweg zum Original. Ohne ihn wäre jede Aufbereitung
                // endgültig -- und man traut sich nicht, sie auszuprobieren.
                if (bearbeitet != original) {
                    TextButton(onClick = onZuruecksetzen) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = null,
                            tint = farbe,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Zurücksetzen",
                            color = farbe,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Hinterlegt das, was gegenüber dem Original hinzukam. */
private fun markiert(original: String, bearbeitet: String, markierer: Color) =
    buildAnnotatedString {
        Textvergleich.vergleiche(original, bearbeitet).forEach { teil ->
            if (teil.neu) {
                withStyle(SpanStyle(background = markierer)) { append(teil.text) }
            } else {
                append(teil.text)
            }
        }
    }

/**
 * Zeigt die Markierung auch im Textfeld.
 *
 * Ein `TextField` stellt seinen Inhalt normalerweise einfarbig dar. Über eine
 * [VisualTransformation] lässt sich derselbe Text mit Auszeichnungen zeigen,
 * ohne den gespeicherten Inhalt anzufassen, die Zeichen bleiben Zeichen für
 * Zeichen dieselben, deshalb genügt [OffsetMapping.Identity] und Cursor und
 * Auswahl sitzen weiter richtig.
 */
private class Markierungsanzeige(
    private val original: String,
    private val markierer: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            markiert(original, text.text, markierer),
            OffsetMapping.Identity,
        )
}
