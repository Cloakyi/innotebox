package de.notizen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Die Tonspur einer Aufnahme: Abspielknopf, Wellenform, Dauer.
 *
 * Eine Audionotiz zeigt ihre Aufnahme, nicht ihren Text. Auf der Karte in
 * der Übersicht ist die Wellenform das, was die Notiz ausmacht, ein
 * abgeschnittener Transkriptanfang sagt weniger darüber aus, wie lang die
 * Aufnahme ist und wo darin etwas passiert.
 *
 * Der Fortschritt läuft durch die Wellenform: der abgespielte Teil ist voll
 * eingefärbt, der Rest gedämpft. Zwei getrennte Elemente, Wellenform hier,
 * Fortschrittsbalken dort, wären zwei Anzeigen für dieselbe Sache.
 */
@Composable
fun Tonspur(
    balken: FloatArray,
    anteil: Float,
    laeuft: Boolean,
    dauerText: String,
    farbe: Color,
    onAbspielen: () -> Unit,
    modifier: Modifier = Modifier,
    knopfgroesse: Dp = 40.dp,
    hoehe: Dp = 36.dp,
    onSpringen: ((Float) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Abspielknopf(
            laeuft = laeuft,
            farbe = farbe,
            groesse = knopfgroesse,
            onClick = onAbspielen,
        )

        Wellenform(
            balken = balken,
            anteil = anteil,
            farbe = farbe,
            onSpringen = onSpringen,
            modifier = Modifier
                .weight(1f)
                .height(hoehe),
        )

        Text(
            text = dauerText,
            style = MaterialTheme.typography.labelMedium,
            color = farbe.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun Abspielknopf(
    laeuft: Boolean,
    farbe: Color,
    groesse: Dp,
    onClick: () -> Unit,
) {
    Surface(
        // Aus der Notizfarbe abgeleitet, nicht aus dem Theme: Ein blauer Knopf
        // auf einer grünen Karte ist ein Fremdkörper. Die Fläche ist der
        // Textton bei geringer Deckung, das Symbol der volle Textton -- beides
        // also aus dem Paar, dessen Kontrast nachgerechnet ist.
        color = farbe.copy(alpha = 0.18f),
        contentColor = farbe,
        shape = CircleShape,
        modifier = Modifier.size(groesse),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (laeuft) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (laeuft) "Pause" else "Abspielen",
                modifier = Modifier.size(groesse * 0.55f),
            )
        }
    }
}

/**
 * Die Wellenform.
 *
 * Antippen und Ziehen springen an die entsprechende Stelle, dafür ist die
 * Wellenform da: Man sieht, wo etwas gesprochen wurde, also will man auch
 * dorthin. Ohne `onSpringen` ist sie nur ein Bild; so wird sie auf der Karte
 * benutzt, wo ein Tippen die Notiz öffnen soll.
 */
@Composable
private fun Wellenform(
    balken: FloatArray,
    anteil: Float,
    farbe: Color,
    onSpringen: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gespielt = farbe
    val offen = farbe.copy(alpha = 0.30f)

    val gesten = if (onSpringen == null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures { punkt -> onSpringen(punkt.x / size.width) }
        }.pointerInput(Unit) {
            detectHorizontalDragGestures { change, _ ->
                onSpringen(change.position.x / size.width)
            }
        }
    }

    Canvas(modifier = modifier.then(gesten)) {
        if (balken.isEmpty()) return@Canvas

        val breite = 3.dp.toPx()
        val luecke = 2.dp.toPx()
        val proBalken = breite + luecke
        val passen = (size.width / proBalken).roundToInt().coerceAtLeast(1)
        val mitte = size.height / 2

        repeat(passen) { i ->
            // Auf die verfuegbare Breite abgebildet, statt die Zahl der Balken
            // vorzugeben: Dieselbe Tonspur steht auf der Karte schmal und im
            // Editor breit.
            val wert = balken[(i.toLong() * balken.size / passen).toInt().coerceAtMost(balken.lastIndex)]
            val hoehe = (mitte * wert).coerceAtLeast(breite / 2)
            val x = i * proBalken + breite / 2

            drawLine(
                color = if (i.toFloat() / passen <= anteil) gespielt else offen,
                start = Offset(x, mitte - hoehe),
                end = Offset(x, mitte + hoehe),
                strokeWidth = breite,
            )
        }
    }
}

/** Minuten und Sekunden, wie man sie hinschreiben würde. */
fun dauerText(ms: Long): String {
    val sekunden = ms / 1000
    return "%d:%02d".format(sekunden / 60, sekunden % 60)
}
