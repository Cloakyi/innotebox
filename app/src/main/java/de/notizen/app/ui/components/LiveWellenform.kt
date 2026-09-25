package de.notizen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import de.notizen.app.audio.WELLE_WERTE_JE_SEKUNDE
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Die mitlaufende Welle während der Aufnahme.
 *
 * Sie läuft von rechts nach links. Der neueste Wert steht am rechten Rand,
 * ältere wandern nach links aus dem Bild. Das ist der Unterschied zu einem
 * bloßen Ausschlag: Man sieht nicht nur, dass gerade etwas ankommt, sondern
 * auch, was in den letzten Sekunden war, ob man zu leise wurde, wo eine Pause
 * lag.
 *
 * Stille wird zum Punkt. Ein Balken der Mindesthöhe sähe aus wie ein sehr
 * leiser Ton; ein Punkt sagt „hier war nichts". Genau diese Unterscheidung
 * macht die Linie lesbar, wenn man später zurückschaut.
 *
 * Sie läuft mit der Bildwiederholrate, nicht mit den Schüben.
 * Die Werte kommen in Schüben, einer je Aufnahmeblock. Vorher wanderte die
 * Welle bei jedem Schub um einen ganzen Balken weiter, und die Animation, die
 * das glätten sollte, hing am Wachsen der Liste; sobald die Liste ihre feste
 * Länge erreicht hatte, wuchs sie nicht mehr, und die Welle ruckte im Takt
 * der Blöcke. Jetzt zählt der Zustand jeden je gemessenen Wert mit
 * ([gesamt]), und hier läuft ein Kopf in Werten je Sekunde über die Zeit
 * ([WELLE_WERTE_JE_SEKUNDE], ein Wert je fünfzig Millisekunden), Bild für
 * Bild über `withFrameNanos`. Gezeichnet wird, was unter dem Kopf liegt, mit
 * dem Bruchteil als Versatz; die Balken gleiten.
 *
 * Der Kopf läuft dem Ton knapp hinterher ([POLSTER] Werte) und regelt sein
 * Tempo leicht nach, damit er weder vorausläuft (dann fehlte rechts ein
 * Balken) noch abreißt (dann stünde die Welle). Die Nachregelung ist so
 * schwach, dass man sie nicht sieht, und stark genug, dass die Uhren nicht
 * auseinanderlaufen.
 */
@Composable
fun LiveWellenform(
    werte: List<Float>,
    /** Wie viele Werte seit Beginn der Aufnahme gemessen wurden, auch die schon vergessenen. */
    gesamt: Long,
    farbe: Color,
    modifier: Modifier = Modifier,
) {
    val stand by rememberUpdatedState(gesamt)
    var kopf by remember { mutableFloatStateOf((gesamt - POLSTER).coerceAtLeast(0f)) }

    LaunchedEffect(Unit) {
        var vorher = 0L
        while (true) {
            withFrameNanos { jetzt ->
                if (vorher != 0L) {
                    val sekunden = (jetzt - vorher) / 1_000_000_000f
                    val ziel = stand - POLSTER
                    // Eine neue Aufnahme faengt vorn an: dann nicht zurueckrollen,
                    // sondern hinspringen.
                    if (ziel < kopf - RUECKSPRUNG) {
                        kopf = ziel.coerceAtLeast(0f)
                    } else {
                        val abweichung = ziel - kopf
                        val tempo = WELLE_WERTE_JE_SEKUNDE * (1f + abweichung * NACHREGELN)
                        val begrenzt = tempo.coerceIn(
                            WELLE_WERTE_JE_SEKUNDE * 0.5f,
                            WELLE_WERTE_JE_SEKUNDE * 1.5f,
                        )
                        kopf = (kopf + begrenzt * sekunden).coerceAtMost(stand.toFloat())
                    }
                }
                vorher = jetzt
            }
        }
    }

    Canvas(modifier = modifier) {
        val breite = 4.dp.toPx()
        val luecke = 3.dp.toPx()
        val proBalken = breite + luecke
        val platz = (size.width / proBalken).roundToInt().coerceAtLeast(1)
        val mitte = size.height / 2
        val hoechste = mitte - breite / 2

        // Der aelteste noch bekannte Wert hat diese laufende Nummer.
        val aeltester = gesamt - werte.size
        val neuester = gesamt - 1
        val ganz = floor(kopf).toLong()
        val bruch = kopf - ganz

        // Vom rechten Rand nach links, einen Balken mehr als hineinpasst, weil
        // der aeusserste gerade hereingleitet.
        for (i in -1..platz + 1) {
            val nummer = ganz - i
            if (nummer < aeltester || nummer > neuester) continue
            val wert = werte[(nummer - aeltester).toInt()]
            val x = size.width - breite / 2 - (i + bruch) * proBalken

            if (wert < PUNKT_UNTER) {
                drawCircle(
                    color = farbe.copy(alpha = 0.45f),
                    radius = breite / 2,
                    center = Offset(x, mitte),
                )
            } else {
                val hoehe = (hoechste * wert).coerceAtLeast(breite / 2)
                drawLine(
                    color = farbe,
                    start = Offset(x, mitte - hoehe),
                    end = Offset(x, mitte + hoehe),
                    strokeWidth = breite,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Darunter wird ein Punkt gezeichnet statt eines Balkens. */
private const val PUNKT_UNTER = 0.08f

/** So viele Werte laeuft der Kopf dem Ton hinterher, damit ihm nie einer fehlt. */
private const val POLSTER = 1.5f

/** Wie stark das Tempo je Wert Abweichung nachgeregelt wird. */
private const val NACHREGELN = 0.15f

/** Ab so vielen Werten rueckwaerts gilt: neue Aufnahme, nicht Nachregeln. */
private const val RUECKSPRUNG = 5f
