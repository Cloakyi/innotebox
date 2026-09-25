package de.notizen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.notizen.app.audio.Sprechabschnitt
import kotlin.math.roundToInt

/**
 * Die Abspielsteuerung einer Aufnahme.
 *
 * Aufbau von oben nach unten: Tonspur, Zeiten, Knöpfe. Die Zeiten stehen
 * **links verstrichen, rechts verbleibend** — verbleibend mit Minuszeichen, wie
 * man es aus jedem Abspieler kennt. Die Gesamtdauer wäre die schlechtere
 * Angabe: Wie lange es noch dauert, ist die Frage, die man beim Hören
 * tatsächlich hat.
 */
@Composable
fun Abspielleiste(
    balken: FloatArray,
    abschnitte: List<Sprechabschnitt> = emptyList(),
    positionMs: Long,
    dauerMs: Long,
    laeuft: Boolean,
    farbe: Color,
    onAbspielen: () -> Unit,
    onSpringen: (Float) -> Unit,
    onZurueck: () -> Unit,
    onVor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anteil = if (dauerMs <= 0) 0f else (positionMs.toFloat() / dauerMs).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Spurleiste(
            balken = balken,
            abschnitte = abschnitte,
            dauerMs = dauerMs,
            anteil = anteil,
            farbe = farbe,
            onSpringen = onSpringen,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dauerText(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = farbe.copy(alpha = 0.85f),
            )
            Text(
                text = "-${dauerText((dauerMs - positionMs).coerceAtLeast(0))}",
                style = MaterialTheme.typography.labelLarge,
                color = farbe.copy(alpha = 0.6f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sprungknopf(Icons.Outlined.Replay5, "Fünf Sekunden zurück", farbe, onZurueck)

            // Der breite Knopf in der Mitte: Wer eine Audionotiz oeffnet, will
            // sie meistens hoeren. Der Rest ist seltener und darf kleiner sein.
            Surface(
                // Siehe Tonspur: aus der Notizfarbe, nicht aus dem Theme.
                color = farbe.copy(alpha = 0.18f),
                contentColor = farbe,
                shape = RoundedCornerShape(28.dp),
                onClick = onAbspielen,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (laeuft) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = if (laeuft) "Pause" else "Abspielen",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Sprungknopf(Icons.Outlined.Forward10, "Zehn Sekunden vor", farbe, onVor)
        }
    }
}

@Composable
private fun Sprungknopf(
    symbol: androidx.compose.ui.graphics.vector.ImageVector,
    beschreibung: String,
    farbe: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = farbe.copy(alpha = 0.10f),
        contentColor = farbe.copy(alpha = 0.85f),
        shape = CircleShape,
        onClick = onClick,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(symbol, contentDescription = beschreibung, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Die Tonspur als flache Leiste.
 *
 * Anders als die mitlaufende Welle beim Aufnehmen ist sie **gestaucht**: Die
 * ganze Aufnahme passt auf einmal ins Bild, weil man beim Abspielen nicht den
 * Moment sucht, sondern die Stelle. Der abgespielte Teil ist voll eingefärbt,
 * der Rest gedämpft, und ein Strich markiert, wo man gerade ist.
 */
@Composable
private fun Spurleiste(
    balken: FloatArray,
    abschnitte: List<Sprechabschnitt>,
    dauerMs: Long,
    anteil: Float,
    farbe: Color,
    onSpringen: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { punkt -> onSpringen(punkt.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onSpringen(change.position.x / size.width)
                }
            },
    ) {
        if (balken.isEmpty()) return@Canvas

        val breite = 4.dp.toPx()
        val luecke = 2.dp.toPx()
        val proBalken = breite + luecke
        val platz = (size.width / proBalken).roundToInt().coerceAtLeast(1)
        val mitte = size.height / 2
        val hoechste = mitte - breite / 2

        repeat(platz) { i ->
            val wert = balken[(i.toLong() * balken.size / platz).toInt().coerceAtMost(balken.lastIndex)]
            val x = i * proBalken + breite / 2
            val gehoert = i.toFloat() / platz <= anteil

            // Wo NICHT gesprochen wurde, steht ein Punkt statt eines Balkens --
            // dieselbe Sprache wie bei der mitlaufenden Welle. So sieht man auf
            // einen Blick, wohin sich das Vorspulen lohnt.
            val gesprochen = abschnitte.isEmpty() || dauerMs <= 0 || abschnitte.any { a ->
                val zeit = (i.toFloat() / platz * dauerMs).toLong()
                zeit >= a.startMs && zeit < a.endeMs
            }

            if (!gesprochen) {
                drawCircle(
                    color = farbe.copy(alpha = if (gehoert) 0.5f else 0.2f),
                    radius = breite / 2,
                    center = Offset(x, mitte),
                )
            } else {
                val hoehe = (hoechste * wert).coerceAtLeast(breite / 2)
                drawLine(
                    color = if (gehoert) farbe else farbe.copy(alpha = 0.28f),
                    start = Offset(x, mitte - hoehe),
                    end = Offset(x, mitte + hoehe),
                    strokeWidth = breite,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Der Strich sitzt ueber der Spur und nicht darunter: Er beantwortet die
        // Frage "wo bin ich gerade" und muss deshalb auch ueber lauten Stellen
        // sichtbar bleiben.
        val x = (size.width * anteil).coerceIn(0f, size.width)
        drawLine(
            color = farbe,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
