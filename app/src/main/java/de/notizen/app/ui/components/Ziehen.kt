package de.notizen.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wo eine Zeile gerade liegt: oberer Rand und Hoehe in Pixeln, im Bezugssystem
 * der Liste, ohne den Versatz einer gerade gezogenen Zeile.
 */
data class Zeilenlage(val kennung: String, val oben: Float, val hoehe: Float)

/**
 * Das Ziehen einer Zeile am Griff, einmal gebaut und zweimal benutzt: fuer die
 * Ordner in „Neu anordnen" und fuer die Eintraege einer Liste im
 * Bearbeitungszustand.
 *
 * Selbst gebaut, weil Compose das Umordnen nicht mitbringt, so wie die
 * Wischgeste. Die Mechanik ist klein: Der Griff nimmt die Zeile auf, der
 * Versatz folgt dem Finger, und sobald die Mitte der gezogenen Zeile in eine
 * Nachbarzeile faellt, tauschen beide in der Liste. Die anderen Zeilen gleiten
 * an ihren neuen Platz; die gezogene selbst bewegt sich nur ueber den Versatz,
 * sonst kaempfte die Platzanimation gegen den Finger.
 *
 * Nach dem Tausch wird der Versatz um den Sprung berichtigt. Die Zeile
 * liegt nach dem Tausch an der Stelle der Nachbarzeile; ohne Berichtigung
 * spraenge sie unter dem Finger um eine ganze Zeilenhoehe weg.
 *
 * Beim Loslassen federt die Zeile an ihren Platz (`EINRASTEN`, dieselbe
 * Feder wie bei der Wischgeste), statt dorthin zu springen. Solange sie
 * federt, gilt sie noch als gezogen und liegt ueber den anderen.
 *
 * Woher die Lagen der Zeilen kommen, entscheidet die Liste: aus `layoutInfo`
 * bei einer `LazyColumn`, aus der eigenen Messung bei [ZiehbareSpalte].
 */
@Stable
class Ziehzustand {
    /** Die Kennung der Zeile am Finger, oder `null`. */
    var gezogen by mutableStateOf<String?>(null)
        private set

    /** Versatz der gezogenen Zeile gegenueber ihrem Platz, in Pixeln. */
    var versatz by mutableFloatStateOf(0f)
        private set

    private var einrasten: Job? = null

    internal fun aufnehmen(kennung: String) {
        einrasten?.cancel()
        gezogen = kennung
        versatz = 0f
    }

    internal fun ziehen(
        delta: Float,
        kennung: String,
        lagen: List<Zeilenlage>,
        reihenfolge: List<String>,
        onVerschieben: (von: Int, nach: Int) -> Unit,
    ) {
        versatz += delta

        val ich = lagen.firstOrNull { it.kennung == kennung } ?: return
        val mitte = ich.oben + versatz + ich.hoehe / 2f
        val ziel = lagen.firstOrNull {
            it.kennung != kennung && mitte >= it.oben && mitte < it.oben + it.hoehe
        } ?: return

        val von = reihenfolge.indexOf(kennung)
        val nach = reihenfolge.indexOf(ziel.kennung)
        if (von >= 0 && nach >= 0 && von != nach) {
            onVerschieben(von, nach)
            versatz -= (ziel.oben - ich.oben)
        }
    }

    internal fun loslassen(scope: CoroutineScope) {
        val kennung = gezogen ?: return
        einrasten = scope.launch {
            animate(versatz, 0f, animationSpec = EINRASTEN) { wert, _ -> versatz = wert }
            if (gezogen == kennung) gezogen = null
        }
    }
}

@Composable
fun rememberZiehzustand(): Ziehzustand = remember { Ziehzustand() }

/**
 * Macht ein Element zum Griff einer Zeile.
 *
 * [lagen] und [reihenfolge] werden bei jeder Bewegung neu erfragt, weil sich
 * beides mit jedem Tausch aendert.
 */
fun Modifier.ziehgriff(
    zustand: Ziehzustand,
    kennung: String,
    scope: CoroutineScope,
    lagen: () -> List<Zeilenlage>,
    reihenfolge: () -> List<String>,
    onVerschieben: (von: Int, nach: Int) -> Unit,
): Modifier = pointerInput(kennung) {
    detectDragGestures(
        onDragStart = { zustand.aufnehmen(kennung) },
        onDragEnd = { zustand.loslassen(scope) },
        onDragCancel = { zustand.loslassen(scope) },
        onDrag = { aenderung, delta ->
            aenderung.consume()
            zustand.ziehen(delta.y, kennung, lagen(), reihenfolge(), onVerschieben)
        },
    )
}

/** Straff und ohne Nachfedern: die Zeile faellt an ihren Platz wie an einen Anschlag. */
private val EINRASTEN = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
