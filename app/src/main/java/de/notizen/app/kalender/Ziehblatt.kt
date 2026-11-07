package de.notizen.app.kalender

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.notizen.app.ui.components.tastaturOffen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wie weit das Blatt offen steht. */
enum class Blattstufe { VOLL, HALB, ZU }

/** Ab diesem Tempo (px/s) springt das Blatt eine Stufe weiter, egal wo es steht. */
private const val SCHWUNG = 1400f

/**
 * Ein Blatt, das von unten kommt und sich am Kopf ziehen laesst:
 * ganz offen, halb offen mit dem Kalender dahinter,
 * oder weggezogen. Halb offen gibt es keine Abdunkelung, der Kalender
 * dahinter bleibt bedienbar.
 *
 * [stufe] gibt die Lage vor; wohin der Finger es zieht, meldet [onStufe].
 * Zum Wegziehen fragt das Blatt erst [darfZu]; sagt das nein, federt es
 * zurueck. Erst wenn es unten angekommen ist, ruft es [onZu].
 */
@Composable
fun Ziehblatt(
    stufe: Blattstufe,
    onStufe: (Blattstufe) -> Unit,
    darfZu: () -> Boolean,
    onZu: () -> Unit,
    kopf: @Composable (Modifier) -> Unit,
    inhalt: @Composable (Modifier) -> Unit,
) {
    val dichte = LocalDensity.current
    val fokus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val oben = WindowInsets.statusBars.getTop(dichte) + with(dichte) { 8.dp.toPx() }
    val aktuelleStufe by rememberUpdatedState(stufe)
    val stufeMelden by rememberUpdatedState(onStufe)
    val zuFragen by rememberUpdatedState(darfZu)
    val zuMelden by rememberUpdatedState(onZu)
    val tastatur = tastaturOffen()

    // Mit offener Tastatur gibt es nur ganz offen, sonst laege das Feld unter ihr.
    LaunchedEffect(tastatur) { if (tastatur && aktuelleStufe == Blattstufe.HALB) onStufe(Blattstufe.VOLL) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hoehe = with(dichte) { maxHeight.toPx() }
        val halb = hoehe - maxOf(hoehe * 0.46f, with(dichte) { 300.dp.toPx() })
        fun anker(s: Blattstufe) = when (s) {
            Blattstufe.VOLL -> oben
            Blattstufe.HALB -> halb
            Blattstufe.ZU -> hoehe
        }

        var versatz by remember { mutableFloatStateOf(hoehe) }
        var fahrt by remember { mutableStateOf<Job?>(null) }
        var faehrtZu by remember { mutableStateOf(false) }

        fun fahren(ziel: Float, tempo: Float = 0f, danach: () -> Unit = {}) {
            fahrt?.cancel()
            faehrtZu = ziel >= hoehe
            fahrt = scope.launch {
                animate(
                    initialValue = versatz,
                    targetValue = ziel,
                    initialVelocity = tempo,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
                ) { wert, _ -> versatz = wert }
                danach()
            }
        }

        LaunchedEffect(stufe, hoehe) {
            // Faehrt das Blatt schon nach unten, weil der Finger es weggezogen hat,
            // laeuft diese Fahrt mit ihrem Schwung weiter.
            if (stufe == Blattstufe.ZU) {
                if (!(faehrtZu && fahrt?.isActive == true)) fahren(hoehe) { zuMelden() }
            } else {
                fahren(anker(stufe))
            }
        }

        fun ziehen(betrag: Float): Float {
            if (aktuelleStufe == Blattstufe.ZU) return 0f
            fahrt?.cancel()
            val neu = (versatz + betrag).coerceIn(oben, hoehe)
            val verbraucht = neu - versatz
            versatz = neu
            return verbraucht
        }

        fun loslassen(tempo: Float) {
            val stufen = listOf(Blattstufe.VOLL, Blattstufe.HALB, Blattstufe.ZU)
            val naechste = stufen.minBy { abs(anker(it) - versatz) }
            val ziel = when {
                tempo > SCHWUNG -> stufen.firstOrNull { anker(it) > versatz + 1 } ?: Blattstufe.ZU
                tempo < -SCHWUNG -> stufen.lastOrNull { anker(it) < versatz - 1 } ?: Blattstufe.VOLL
                else -> naechste
            }
            if (ziel == Blattstufe.ZU) {
                if (zuFragen()) {
                    // Gleich melden, nicht erst unten: Wer hinter dem Blatt etwas
                    // ausblendet, soll damit nicht warten, bis die Feder ruht.
                    fahren(hoehe, tempo) { zuMelden() }
                    stufeMelden(Blattstufe.ZU)
                } else {
                    fahren(anker(aktuelleStufe), tempo)
                }
                return
            }
            if (ziel != aktuelleStufe) stufeMelden(ziel)
            fahren(anker(ziel), tempo)
        }

        // Im Inhalt zieht ein Wisch nach oben erst das Blatt hoch; ein Wisch nach
        // unten, wenn der Inhalt schon oben steht, zieht es herunter. Waehrend
        // das Blatt zugeht, zieht nichts mehr daran: Ein Feld mit Fokus scrollt
        // sich beim Schrumpfen selbst ins Bild und hielte das Blatt sonst auf.
        val verbindung = remember(hoehe, oben) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (aktuelleStufe != Blattstufe.ZU && available.y < 0 && versatz > oben && source == NestedScrollSource.UserInput) {
                        Offset(0f, ziehen(available.y))
                    } else {
                        Offset.Zero
                    }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (aktuelleStufe != Blattstufe.ZU && available.y > 0 && source == NestedScrollSource.UserInput) {
                        fokus.clearFocus()
                        Offset(0f, ziehen(available.y))
                    } else {
                        Offset.Zero
                    }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (aktuelleStufe != Blattstufe.ZU && versatz > oben + 1f) {
                        loslassen(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // Abgedunkelt wird nur zwischen halb und ganz offen.
        val dunkel = ((halb - versatz) / (halb - oben)).coerceIn(0f, 1f)
        if (dunkel > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(dunkel)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onStufe(Blattstufe.HALB)
                    },
            )
        }

        val ziehzustand = rememberDraggableState { betrag -> ziehen(betrag) }
        Surface(
            modifier = Modifier
                .offset { IntOffset(0, versatz.roundToInt()) }
                .fillMaxWidth()
                .height(maxHeight - with(dichte) { oben.toDp() }),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                kopf(
                    Modifier.draggable(
                        state = ziehzustand,
                        orientation = Orientation.Vertical,
                        onDragStarted = { fokus.clearFocus() },
                        onDragStopped = { tempo -> loslassen(tempo) },
                    ),
                )
                // Der Inhalt bekommt nur die Hoehe, die zu sehen ist, sonst liesse
                // er sich halb offen nicht bis zum Ende scrollen.
                val sichtbar = with(dichte) { (hoehe - maxOf(versatz, oben)).toDp() }
                inhalt(
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(sichtbar - BLATTKOPF, 0.dp))
                        .nestedScroll(verbindung),
                )
            }
        }
    }
}
