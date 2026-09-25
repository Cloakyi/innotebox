package de.notizen.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Der Abstand zur Tastatur, der sich nicht aufhaengt.
 *
 * Der Fehler: Nach dem Schliessen der Tastatur blieb der Editor manchmal
 * hochgeschoben, die untere Leiste stand mitten im Bild, bis zum Neustart der
 * App. Meist nach einer Auswahl im Text.
 *
 * Die Ursache liegt in Compose, nicht in unserem Code (foundation-layout
 * 1.12.0, `WindowInsets.android.kt`, Klasse `InsetsListener`; am 2026-09-19
 * in der Quelle nachgelesen). Der Listener merkt sich mit `onPrepare`, dass
 * eine Tastaturanimation beginnt, und ignoriert ab da jedes
 * `onApplyWindowInsets`, bis `onStart` oder `onEnd` kommt. Bricht das System
 * die Animation zwischen `onPrepare` und `onStart` ab, kommen beide nie. Das
 * passiert, wenn beim Ausblenden ein zweites Fenster im Spiel ist, etwa die
 * schwebende Auswahlleiste oder die Lupe der Textauswahl. Compose hat dafuer
 * nur auf Android 11 einen Rueckfall (`view.post`); auf allen neueren
 * Versionen bleibt `WindowInsets.ime` auf der Hoehe der Tastatur stehen, und
 * damit haengt jeder `imePadding()` im Fenster, bis die Activity neu entsteht.
 * Zurueckgesetzt wird der Zustand nur, wenn kein Composable mehr Insets liest;
 * das ist in dieser App nie der Fall, weil jedes Scaffold sie liest.
 *
 * Was trotzdem stimmt: `WindowInsets.imeAnimationTarget` setzt derselbe
 * Listener VOR der Sperre, bei jedem `onApplyWindowInsets`. Es sagt immer, wo
 * die Tastatur wirklich ist oder gleich sein wird.
 *
 * Deshalb: Solange die Animation laeuft, folgt der Abstand wie bisher dem
 * animierten Wert, Bild fuer Bild mit der Tastatur. Bewegt sich der Wert nach
 * einem neuen Ziel aber gar nicht ([STILLSTAND_MS]) oder kommt er nie an
 * ([HOECHSTENS_MS]), gilt das Ziel, und der Abstand faehrt in einer kurzen
 * Animation dorthin. Sobald der animierte Wert wieder stimmt, uebernimmt er.
 *
 * Als `windowInsetsPadding` und nicht als eigenes Padding, damit die
 * Verbrauchsrechnung der Insets erhalten bleibt: Die untere Leiste zieht
 * ihren Abstand zur Navigationsleiste nur ab, wenn die Tastatur ihn nicht
 * schon abgedeckt hat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.tastaturAbstand(): Modifier {
    val dichte = LocalDensity.current
    val animiert = WindowInsets.ime
    val ziel = WindowInsets.imeAnimationTarget
    val zielUnten = ziel.getBottom(dichte)
    val heilung = remember { Animatable(0f) }
    var geheilt by remember { mutableStateOf(false) }

    LaunchedEffect(zielUnten) {
        val start = animiert.getBottom(dichte)
        if (start == zielUnten) {
            geheilt = false
            return@LaunchedEffect
        }
        val bewegtSich = withTimeoutOrNull(STILLSTAND_MS) {
            snapshotFlow { animiert.getBottom(dichte) }.first { it != start }
        } != null
        val angekommen = bewegtSich && withTimeoutOrNull(HOECHSTENS_MS) {
            snapshotFlow { animiert.getBottom(dichte) }.first { it == zielUnten }
        } != null
        if (angekommen) {
            geheilt = false
            return@LaunchedEffect
        }
        // Compose haengt. Ab hier gilt das Ziel, mit einer kurzen Fahrt dorthin.
        heilung.snapTo(animiert.getBottom(dichte).toFloat())
        geheilt = true
        heilung.animateTo(zielUnten.toFloat(), tween(HEILUNG_MS))
    }

    val abstand = remember(animiert) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getTop(density: Density) = 0
            override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getBottom(density: Density): Int =
                if (geheilt) heilung.value.roundToInt() else animiert.getBottom(density)
        }
    }
    return windowInsetsPadding(abstand)
}

/**
 * Ob die Tastatur offen ist oder gerade aufgeht.
 *
 * Aus dem Ziel der Animation, nicht aus dem animierten Wert: Das Ziel steht
 * fest, sobald das System die Tastatur anfordert, und es haengt nie (siehe
 * [tastaturAbstand]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun tastaturOffen(): Boolean = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0

/** Laenger als der Anlauf jeder echten Tastaturanimation. */
private const val STILLSTAND_MS = 400L

/** Laenger als jede echte Tastaturanimation. */
private const val HOECHSTENS_MS = 1_500L

/** Die Fahrt zum Ziel, wenn Compose haengen geblieben ist. */
private const val HEILUNG_MS = 220
