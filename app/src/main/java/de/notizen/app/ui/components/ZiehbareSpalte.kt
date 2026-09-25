package de.notizen.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Eine Spalte, deren Zeilen sich am Griff umordnen lassen, fuer Listen, die
 * in einem gewoehnlichen Scrollbereich stehen und keine `LazyColumn` sein
 * koennen (die Eintraege einer Listennotiz im Editor).
 *
 * Die Geste ist [Ziehzustand]; hier steht nur, was eine Spalte dazu beitragen
 * muss: Sie misst ihre Zeilen selbst und weiss deshalb ohne Umweg, wo jede
 * liegt. Bewusst keine `onGloballyPositioned`-Abfrage je Zeile: Die meldet die
 * Lage samt Versatz der gezogenen Zeile, und genau der darf hier nicht
 * hineinrechnen.
 *
 * Die Nachbarn gleiten. Tauscht eine Zeile beim Ziehen den Platz, springt
 * sie nicht dorthin, sondern faehrt vom alten Platz hin; das ist die
 * Platzanimation, die `LazyColumn` mit `animateItem` mitbringt und eine
 * Spalte nicht. Nur waehrend gezogen wird: Aenderungen an der Liste selbst
 * (ein Eintrag kommt dazu, einer geht) bleiben ohne Gleiten, sonst rutschte
 * bei jedem neuen Eintrag die ganze Liste.
 *
 * [zeile] bekommt den Modifier fuer den Griff und ob die Zeile gerade am
 * Finger haengt.
 */
@Composable
fun <T> ZiehbareSpalte(
    eintraege: List<T>,
    kennung: (T) -> String,
    onVerschieben: (von: Int, nach: Int) -> Unit,
    modifier: Modifier = Modifier,
    zeile: @Composable (eintrag: T, griff: Modifier, gezogen: Boolean) -> Unit,
) {
    val zustand = rememberZiehzustand()
    val scope = rememberCoroutineScope()
    // Wo jede Zeile beim letzten Layout lag. Kein Compose-Zustand: Das liest
    // nur die Geste, und die fragt bei jeder Bewegung frisch nach.
    val lagen = remember { LinkedHashMap<String, Zeilenlage>() }
    val gleiten = remember { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    // Ueber `rememberUpdatedState`, weil `pointerInput` seinen Block nur beim
    // Wechsel der Kennung neu startet: Eine direkt eingefangene Reihenfolge
    // bliebe nach dem ersten Tausch die alte.
    val kennungen by rememberUpdatedState(eintraege.map(kennung))
    val verschieben by rememberUpdatedState(onVerschieben)

    Layout(
        modifier = modifier,
        content = {
            eintraege.forEach { eintrag ->
                val k = kennung(eintrag)
                key(k) {
                    val gleitet = remember { Animatable(0f) }.also { gleiten[k] = it }
                    val istGezogen = zustand.gezogen == k
                    Box(
                        Modifier
                            .layoutId(k)
                            .zIndex(if (istGezogen) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (istGezogen) zustand.versatz else gleitet.value
                            },
                    ) {
                        zeile(
                            eintrag,
                            Modifier.ziehgriff(
                                zustand = zustand,
                                kennung = k,
                                scope = scope,
                                lagen = { lagen.values.toList() },
                                reihenfolge = { kennungen },
                                onVerschieben = { von, nach -> verschieben(von, nach) },
                            ),
                            istGezogen,
                        )
                    }
                }
            }
        },
    ) { messbare, constraints ->
        val platz = messbare.map { it.measure(constraints.copy(minHeight = 0)) }
        val breite = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            platz.maxOfOrNull { it.width } ?: constraints.minWidth
        }

        var y = 0
        val neu = ArrayList<Zeilenlage>(platz.size)
        messbare.forEachIndexed { i, m ->
            neu += Zeilenlage(m.layoutId as String, y.toFloat(), platz[i].height.toFloat())
            y += platz[i].height
        }

        val gezogen = zustand.gezogen
        if (gezogen != null) {
            neu.forEach { jetzt ->
                val vorher = lagen[jetzt.kennung] ?: return@forEach
                if (jetzt.kennung == gezogen || vorher.oben == jetzt.oben) return@forEach
                gleiten[jetzt.kennung]?.let { anim ->
                    scope.launch {
                        anim.snapTo(vorher.oben - jetzt.oben)
                        anim.animateTo(0f, GLEITEN)
                    }
                }
            }
        }
        lagen.clear()
        neu.forEach { lagen[it.kennung] = it }

        layout(breite, y) {
            platz.forEachIndexed { i, p -> p.placeRelative(0, neu[i].oben.roundToInt()) }
        }
    }
}

/** Die Platzanimation der Nachbarn: kurz und ohne Nachfedern. */
private val GLEITEN = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)
