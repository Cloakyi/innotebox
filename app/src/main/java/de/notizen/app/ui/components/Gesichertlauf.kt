package de.notizen.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Was eine Karte gerade über ihren Sicherungsstand sagt.
 *
 * Drei verschiedene Aussagen, drei verschiedene Bilder. Sie in einem Zustand
 * zusammenzuziehen wäre einfacher zu programmieren und für den Nutzer eine
 * Anzeige, die mal dies und mal das bedeutet.
 */
enum class Kartensicherung {
    /** Nichts anzuzeigen. */
    RUHIG,

    /** Diese Notiz geht gerade hoch: Die Linie kreist. */
    LAEUFT,

    /** Sie ist eben in Drive angekommen: Der Rahmen blitzt grün. */
    ANGEKOMMEN,

    /**
     * Auf dem Gerät gespeichert, mehr ist nicht vorgesehen.
     *
     * Der Fall bei ausgeschaltetem Abgleich: Die Linie läuft einmal herum und
     * ist fertig. Ohne diesen Zustand bekäme man beim Schließen einer Notiz gar
     * keine Rückmeldung mehr, sobald man den Abgleich abschaltet.
     */
    GESPEICHERT,
}

/** Eine Runde der kreisenden Linie. */
private const val KREIS_MS = 1_100

/** Die einmalige Runde beim lokalen Speichern. */
private const val RUNDE_MS = 620

private const val VERBLASSEN_MS = 320

/** Wie lange das grüne Aufblitzen zu sehen ist. */
private const val BLITZ_MS = 260

/** Und wie lange es braucht, bis es wieder weg ist. */
private const val BLITZ_AUS_MS = 520

private val STRICHSTAERKE = 2.dp

/** Der Anteil des Rahmens, den die kreisende Linie belegt. */
private const val SCHWANZ = 0.28f

/**
 * Das Grün für „ist oben angekommen".
 *
 * Fest und nicht aus dem Theme, dieselbe Ausnahme wie beim Favoritengold: Die
 * Aussage ist auf jeder Karte dieselbe, und ein Signal, das auf jeder Notiz eine
 * andere Farbe hat, ist keines mehr. Bewusst kein grelles Grün, es blitzt auf
 * einer farbigen Karte auf und soll sie nicht überstrahlen.
 */
private val ANGEKOMMEN_GRUEN = Color(0xFF4CAF50)

/**
 * Eine Linie am Rahmen, die sagt, was mit dieser Notiz gerade passiert.
 *
 * Warum am Rahmen und nicht als Häkchen oder Snackbar. Die Rückmeldung
 * gehört an das Ding, um das es geht. Ein Häkchen am Bildschirmrand sagt „etwas
 * wurde gespeichert"; eine Linie um genau diese Karte sagt „diese Notiz". Und
 * sie hält niemanden auf: Sie belegt keinen Platz und verdeckt nichts.
 *
 * Kreisen heißt „läuft", einmal herum heißt „fertig", grün heißt „oben".
 * Ein Aufblitzen allein ließe offen, ob es eine Meldung oder ein Fehler war.
 *
 * [fertig] wird nach einer einmaligen Animation gerufen, damit der Aufrufer
 * seine Marke löschen kann, sonst liefe sie beim nächsten Scrollen erneut,
 * sobald die Karte wieder ins Bild kommt. Beim Kreisen wird sie nie gerufen:
 * Dieser Zustand endet, wenn die Notiz oben ist, nicht wenn eine Animation
 * durch ist.
 */
@Composable
fun Modifier.gesichertLauf(
    stand: Kartensicherung,
    farbe: Color,
    ecken: Dp,
    fertig: () -> Unit,
): Modifier {
    val anfang = remember { Animatable(0f) }
    val laenge = remember { Animatable(0f) }
    val deckung = remember { Animatable(0f) }
    val blitz = remember { Animatable(0f) }

    LaunchedEffect(stand) {
        when (stand) {
            Kartensicherung.RUHIG -> {
                deckung.snapTo(0f)
                blitz.snapTo(0f)
            }

            Kartensicherung.LAEUFT -> {
                blitz.snapTo(0f)
                deckung.snapTo(1f)
                laenge.snapTo(SCHWANZ)
                // Endlos: Dieser Zustand hoert auf, wenn die Notiz oben ist.
                while (true) {
                    anfang.snapTo(0f)
                    anfang.animateTo(1f, tween(KREIS_MS, easing = LinearEasing))
                }
            }

            Kartensicherung.GESPEICHERT -> {
                blitz.snapTo(0f)
                anfang.snapTo(0f)
                deckung.snapTo(1f)
                laenge.snapTo(0f)
                laenge.animateTo(1f, tween(RUNDE_MS, easing = FastOutSlowInEasing))
                deckung.animateTo(0f, tween(VERBLASSEN_MS, easing = LinearEasing))
                fertig()
            }

            Kartensicherung.ANGEKOMMEN -> {
                deckung.snapTo(0f)
                blitz.snapTo(0f)
                blitz.animateTo(1f, tween(BLITZ_MS, easing = LinearEasing))
                blitz.animateTo(0f, tween(BLITZ_AUS_MS, easing = LinearEasing))
                fertig()
            }
        }
    }

    if (stand == Kartensicherung.RUHIG) return this

    return drawWithContent {
        drawContent()

        val staerke = STRICHSTAERKE.toPx()
        val radius = ecken.toPx()

        // Um eine halbe Strichstaerke nach innen: Ein Strich wird mittig auf den
        // Pfad gezeichnet, sonst laege die Haelfte davon ausserhalb der Karte.
        val rahmen = Path().apply {
            addRoundRect(
                RoundRect(
                    left = staerke / 2,
                    top = staerke / 2,
                    right = size.width - staerke / 2,
                    bottom = size.height - staerke / 2,
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }
        val messer = PathMeasure().apply { setPath(rahmen, forceClosed = true) }

        if (blitz.value > 0f) {
            drawPath(
                path = rahmen,
                color = ANGEKOMMEN_GRUEN.copy(alpha = blitz.value),
                style = Stroke(width = staerke),
            )
            return@drawWithContent
        }

        if (deckung.value <= 0f || laenge.value <= 0f) return@drawWithContent

        val gesamt = messer.length
        val von = gesamt * anfang.value
        val bis = von + gesamt * laenge.value

        val stueck = Path()
        messer.getSegment(von, minOf(bis, gesamt), stueck, startWithMoveTo = true)
        // Laeuft das Stueck ueber den Anfang hinaus, fehlt der Rest am Beginn
        // des Pfades. Ohne das haette die kreisende Linie an einer Stelle des
        // Rahmens einen Sprung -- und zwar immer an derselben.
        if (bis > gesamt) {
            messer.getSegment(0f, bis - gesamt, stueck, startWithMoveTo = true)
        }

        drawPath(
            path = stueck,
            color = farbe.copy(alpha = deckung.value),
            style = Stroke(width = staerke, cap = StrokeCap.Round),
        )
    }
}
