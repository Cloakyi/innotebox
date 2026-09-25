package de.notizen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Der Text, der steht, wenn nichts da ist.
 *
 * Eine einzige Fassung fuer die ganze App, weil es vorher fuenf leicht
 * verschiedene gab: mal linksbuendig, mal mittig, mal eine Ueberschrift, mal
 * ein Absatz. Wer zwischen Eingang, Tags und Papierkorb wechselt, sieht so
 * dieselbe Form -- und liest den Unterschied im Text statt im Layout.
 *
 * Aufbau: eine kurze Feststellung, darunter ein Satz, der weiterhilft. Der
 * Zeilenumbruch wird nicht von Hand gesetzt, sondern durch die Breite
 * begrenzt; so bleibt der Block auf jedem Geraet ausgewogen statt an einer
 * einmal passenden Stelle zu brechen.
 */
@Composable
fun LeererZustand(
    titel: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Hinweisblock(titel = titel, text = text)
    }
}

/**
 * Derselbe Block ohne eigene Flaeche -- fuer Listen, in denen oben schon etwas
 * steht und der Hinweis nur dazwischen liegt.
 */
@Composable
fun Hinweisblock(
    titel: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = titel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}
