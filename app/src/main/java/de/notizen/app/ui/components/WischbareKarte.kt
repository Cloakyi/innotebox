package de.notizen.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.notizen.app.ui.StageUi
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.WischZiel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ab welchem sichtbaren Weg die Geste ausloest.
 *
 * Bewusst ein fester Weg und kein Anteil der Kartenbreite: in der Liste ist
 * eine Karte doppelt so breit wie im Raster, und dieselbe Geste duerfte sich
 * dort nicht doppelt so lang anfuehlen.
 */
private val SCHWELLE = 96.dp

/** Auf schmalen Karten darf die Schwelle nicht ueber die Karte hinauslaufen. */
private const val SCHWELLE_MAX_ANTEIL = 0.6f

/**
 * Wie schnell die Karte mindestens losfliegt, wenn das Band reisst.
 *
 * Sie uebernimmt sonst die Geschwindigkeit des Fingers -- aber wer langsam bis
 * ueber die Schwelle zieht, hat trotzdem Spannung aufgebaut, und die muss sich
 * entladen. Ein zaeh davonkriechende Karte waere genau das Gegenteil von
 * "das Band ist gerissen".
 */
private val MINDESTTEMPO = 2200.dp

/**
 * Zurueck in die Ruhelage, wenn die Schwelle nicht erreicht wurde.
 *
 * Untergedaempft (0,55), federt also sichtbar nach -- dasselbe Verhalten wie
 * die Zurueck-Pille von Android. Eine gleichmaessige Zeitkurve kaeme hier
 * mechanisch daher, weil nichts nachschwingt.
 */
private val ZURUECKFEDERN = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessMediumLow,
)

/** Einrasten der Loeschflaeche: straffer, damit es sich nach Anschlag anfuehlt. */
private val EINRASTEN = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Das Wegfliegen nach dem Reissen.
 *
 * Hier ausdruecklich OHNE Nachfedern: das Ziel liegt ausserhalb des Bildes,
 * ein Ueberschwingen waere unsichtbar und wuerde nur die Dauer strecken. Die
 * Bewegung lebt von der uebernommenen Geschwindigkeit, nicht von der Feder.
 */
private val WEGREISSEN = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

/**
 * Mit welchem Tempo die Karte den Finger verlaesst.
 *
 * Gemessen wird die Geschwindigkeit der KARTE, nicht die des Fingers -- die
 * beiden laufen wegen des Widerstands auseinander, und die Feder muss dort
 * anschliessen, wo die Karte gerade ist. Nach unten begrenzt, siehe
 * [MINDESTTEMPO].
 */
internal fun abfluggeschwindigkeit(gemessen: Float, mindest: Float, nachRechts: Boolean): Float {
    val betrag = maxOf(abs(gemessen), abs(mindest))
    return if (nachRechts) betrag else -betrag
}

/**
 * Der Gummiband-Widerstand.
 *
 * Am Anfang folgt die Karte dem Finger eins zu eins, danach immer traeger:
 * beim Erreichen der Schwelle bewegt sie sich nur noch mit gut 40 Prozent der
 * Fingergeschwindigkeit. Der Finger legt also rund das Anderthalbfache der
 * Schwelle zurueck, bis es ausloest -- deutlich mehr als bei einer geraden
 * Uebertragung, und genau das ist der Punkt: aus Versehen passiert das nicht
 * mehr.
 *
 * Die Kurve ist nach oben beschraenkt (Grenzwert: das Dreifache der Schwelle),
 * darum fuehlt sie sich zunehmend nach Spannung an statt nach Bewegung.
 */
internal fun gummi(weg: Float, schwelle: Float): Float =
    if (schwelle <= 0f) 0f else 3f * schwelle * weg / (3f * schwelle + weg)

/**
 * Eine Notizkarte, die sich zur Seite ziehen laesst.
 *
 * RICHTUNG: Der Fluss laeuft Eingang -> Workspace -> Archiv. Was die beiden
 * Richtungen tun, steht in den Einstellungen; eine Richtung, die in dieser
 * Stufe ins Leere liefe, laesst sich gar nicht erst bewegen.
 *
 * ZWEI AUSGAENGE, und der Unterschied ist Absicht:
 *
 *  - Verschieben reisst ab. Ueber der Schwelle schnellt die Karte beim
 *    LOSLASSEN zur Seite weg, als waere das Gummiband gerissen, und die Notiz
 *    wandert. Verschieben ist harmlos und ueber die Snackbar zurueckzunehmen.
 *  - Loeschen rastet ein. Die Karte bleibt an der Schwelle stehen und legt die
 *    rote Flaeche frei; erst ein Tippen darauf loescht wirklich. Kein Dialog,
 *    keine Rueckfrage -- die zweite Handlung IST die Bestaetigung, und sie
 *    kostet einen Fingertipp statt einer Entscheidung.
 *
 * AUSGELOEST WIRD BEIM LOSLASSEN, nicht beim Ueberschreiten der Schwelle. Man
 * kann also bis zuletzt umkehren. Der Preis dafuer: waehrend der Finger liegt,
 * sieht man dem Bildschirm nicht an, ob es reicht. Deshalb meldet sich die
 * Schwelle beim Ueberschreiten mit einem Haptik-Tick und die freigelegte
 * Flaeche wird von halb auf voll deckend -- ohne diese beiden Rueckmeldungen
 * waere die Geste ein Ratespiel.
 *
 * Ein schneller Wisch unterhalb der Schwelle loest NICHT aus, obwohl das
 * ueblich waere. Genau dieser Kurzschluss war der Grund fuer versehentliches
 * Verschieben; die Strecke entscheidet, nicht das Tempo.
 *
 * Die freigelegte Flaeche ist immer so breit, dass ihre Beschriftung
 * vollstaendig lesbar ist -- sie wird gemessen, nicht geschaetzt.
 */
@Composable
fun WischbareKarte(
    note: NoteWithRelations,
    stufe: Stage,
    selected: Boolean,
    auswahlAktiv: Boolean,
    rechts: WischZiel,
    links: WischZiel,
    eingerastet: Boolean,
    onEinrasten: () -> Unit,
    onAusrasten: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onWisch: (WischZiel) -> Unit,
    wellenform: FloatArray = FloatArray(0),
    spielt: Boolean = false,
    abspielAnteil: Float = 0f,
    abspielDauerMs: Long = 0,
    onAbspielen: () -> Unit = {},
    sicherung: Kartensicherung = Kartensicherung.RUHIG,
    onGesichertGezeigt: () -> Unit = {},
    hervorhebung: Float = 0f,
) {
    WischbarerRahmen(
        stufe = stufe,
        auswahlAktiv = auswahlAktiv,
        rechts = rechts,
        links = links,
        eingerastet = eingerastet,
        onEinrasten = onEinrasten,
        onAusrasten = onAusrasten,
        onWisch = onWisch,
    ) { modifier ->
        NoteCard(
            note = note,
            selected = selected,
            // Ist die Loeschflaeche offen, schliesst ein Tippen auf die Karte
            // sie wieder. Die Notiz zu oeffnen waere hier die falsche Antwort.
            onClick = { if (eingerastet) onAusrasten() else onClick() },
            onLongClick = { if (eingerastet) onAusrasten() else onLongClick() },
            wellenform = wellenform,
            spielt = spielt,
            abspielAnteil = abspielAnteil,
            abspielDauerMs = abspielDauerMs,
            // Waehrend die Loeschflaeche offen steht, schliesst ein Tippen sie
            // -- auch das auf dem Abspielknopf.
            onAbspielen = { if (eingerastet) onAusrasten() else onAbspielen() },
            sicherung = sicherung,
            onGesichertGezeigt = onGesichertGezeigt,
            hervorhebung = hervorhebung,
            modifier = modifier,
        )
    }
}

/**
 * Die Wischmechanik, losgeloest von der Notizkarte.
 *
 * Seit dem 2026-09-15 getrennt, weil im Papierkorb auch ORDNER wischbar sein
 * sollen. Der [inhalt] bekommt den Modifier mit Versatz und Gesten und muss
 * ihn an sein aeusserstes Element haengen; was er bei einem Tippen tut, wenn
 * die Flaeche eingerastet ist, entscheidet er selbst (die Karte schliesst
 * sie dann, statt sich zu oeffnen).
 */
@Composable
fun WischbarerRahmen(
    stufe: Stage,
    auswahlAktiv: Boolean,
    rechts: WischZiel,
    links: WischZiel,
    eingerastet: Boolean,
    onEinrasten: () -> Unit,
    onAusrasten: () -> Unit,
    onWisch: (WischZiel) -> Unit,
    inhalt: @Composable (modifier: Modifier) -> Unit,
) {
    val dichte = LocalDensity.current
    val haptik = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val versatz = remember { Animatable(0f) }

    // Ob ein Loslassen jetzt etwas ausloesen wuerde.
    var scharf by remember { mutableStateOf(false) }

    var kartenBreite by remember { mutableIntStateOf(0) }
    var hinweisBreite by remember { mutableIntStateOf(0) }
    // Welche Seite gerade freigelegt ist -- nur fuers Zeichnen.
    var nachRechts by remember { mutableStateOf(true) }
    // Wie schnell die Karte war, als der Finger sie uebergeben hat. Das
    // Einrasten laeuft ueber den Zustand, nicht ueber den Finger, und wuerde
    // die Geschwindigkeit sonst unterwegs verlieren.
    var uebergabetempo by remember { mutableFloatStateOf(0f) }

    val mindesttempo = with(dichte) { MINDESTTEMPO.toPx() }

    val schwelle = with(dichte) {
        val voll = SCHWELLE.toPx()
        if (kartenBreite == 0) voll else minOf(voll, kartenBreite * SCHWELLE_MAX_ANTEIL)
    }

    // So weit wird beim Loeschen aufgezogen: die Beschriftung plus Luft.
    val rastweite = with(dichte) { maxOf(hinweisBreite + 24.dp.toPx(), schwelle) }

    val ziel = if (nachRechts) rechts else links

    // Das Ein- und Ausrasten haengt am Zustand, nicht am Finger -- sonst laesst
    // es sich von aussen nicht schliessen, wenn eine andere Karte aufgeht.
    LaunchedEffect(eingerastet) {
        if (eingerastet) {
            versatz.animateTo(
                targetValue = if (nachRechts) rastweite else -rastweite,
                animationSpec = EINRASTEN,
                initialVelocity = uebergabetempo,
            )
        } else if (versatz.value != 0f) {
            versatz.animateTo(
                targetValue = 0f,
                animationSpec = ZURUECKFEDERN,
                initialVelocity = uebergabetempo,
            )
        }
        uebergabetempo = 0f
    }

    val gesten = if (auswahlAktiv || eingerastet) {
        Modifier
    } else {
        Modifier.pointerInput(rechts, links, stufe, kartenBreite) {
            var weg = 0f
            // Verfolgt die Bahn der KARTE, nicht die des Fingers: gefuettert
            // wird der gedaempfte Versatz. Damit ist die berechnete
            // Geschwindigkeit genau die, mit der die Feder weiterrechnen muss.
            val bahn = VelocityTracker()

            val zurueckfedern: () -> Unit = {
                val tempo = bahn.calculateVelocity().x
                scharf = false
                scope.launch {
                    versatz.animateTo(
                        targetValue = 0f,
                        animationSpec = ZURUECKFEDERN,
                        initialVelocity = tempo,
                    )
                }
            }

            detectHorizontalDragGestures(
                onDragStart = {
                    weg = 0f
                    scharf = false
                    bahn.resetTracking()
                },
                onDragCancel = zurueckfedern,
                onDragEnd = {
                    val aktuell = if (nachRechts) rechts else links
                    if (!scharf || !aktuell.wirktIn(stufe)) {
                        zurueckfedern()
                    } else {
                        val tempo = abfluggeschwindigkeit(
                            gemessen = bahn.calculateVelocity().x,
                            mindest = mindesttempo,
                            nachRechts = nachRechts,
                        )
                        if (aktuell.rastetEin()) {
                            scharf = false
                            uebergabetempo = tempo
                            onEinrasten()
                        } else {
                            scope.launch {
                                // Das Reissen. Die Feder uebernimmt die Bewegung
                                // genau dort, wo der Finger sie gelassen hat --
                                // eine Zeitkurve wuerde bei null anfangen und die
                                // Karte fuer einen Moment stehen lassen.
                                versatz.animateTo(
                                    targetValue = kartenBreite * 1.5f *
                                        if (nachRechts) 1f else -1f,
                                    animationSpec = WEGREISSEN,
                                    initialVelocity = tempo,
                                )
                                onWisch(aktuell)
                                // Die Notiz verlaesst die Liste meistens, aber
                                // nicht immer: steht der Titeldialog an und wird
                                // abgebrochen, bliebe die Karte sonst weggewischt
                                // haengen.
                                versatz.snapTo(0f)
                                // Erst jetzt: sonst verblasst die freigelegte
                                // Flaeche, waehrend die Karte noch ueber ihr
                                // hinwegfliegt.
                                scharf = false
                            }
                        }
                    }
                },
            ) { change, delta ->
                weg += delta
                val zurRechten = weg > 0f
                val aktuell = if (zurRechten) rechts else links

                if (!aktuell.wirktIn(stufe)) {
                    // Tote Richtung. `weg` wird zurueckgesetzt, damit die Karte
                    // sofort wieder anspricht, sobald man umkehrt -- sonst
                    // muesste man den ganzen Leerweg erst zurueckziehen.
                    weg = 0f
                    scharf = false
                    if (versatz.value != 0f) scope.launch { versatz.snapTo(0f) }
                    return@detectHorizontalDragGestures
                }

                change.consume()
                nachRechts = zurRechten

                val sichtbar = gummi(abs(weg), schwelle) * if (zurRechten) 1f else -1f
                bahn.addPosition(change.uptimeMillis, Offset(sichtbar, 0f))
                scope.launch { versatz.snapTo(sichtbar) }

                val jetztScharf = abs(sichtbar) >= schwelle
                if (jetztScharf && !scharf) {
                    // Der einzige Hinweis, dass die Schwelle erreicht ist,
                    // solange der Finger die Sicht auf die Karte verdeckt.
                    haptik.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
                scharf = jetztScharf
            }
        }
    }

    Box(modifier = Modifier.onSizeChanged { kartenBreite = it.width }) {
        WischHintergrund(
            ziel = ziel,
            stufe = stufe,
            nachRechts = nachRechts,
            eingerastet = eingerastet,
            scharf = scharf,
            onBreite = { hinweisBreite = it },
            onLoeschen = {
                onAusrasten()
                onWisch(ziel)
            },
            modifier = Modifier.matchParentSize(),
        )

        inhalt(
            Modifier
                .offset { IntOffset(versatz.value.roundToInt(), 0) }
                .then(gesten),
        )
    }
}

/**
 * Was unter der Karte zum Vorschein kommt.
 *
 * Die Ausrichtung folgt der Wischrichtung, nicht dem Ziel: freigelegt wird
 * immer die Seite, von der die Karte weggezogen wird.
 *
 * Der Papierkorb erscheint in `errorContainer` statt in `secondaryContainer`.
 * Ein Loeschen soll sich schon anders anfuehlen, bevor es passiert.
 */
@Composable
private fun WischHintergrund(
    ziel: WischZiel,
    stufe: Stage,
    nachRechts: Boolean,
    eingerastet: Boolean,
    scharf: Boolean,
    onBreite: (Int) -> Unit,
    onLoeschen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zielStufe = when (ziel) {
        WischZiel.NAECHSTE_STUFE -> stufe.next()
        WischZiel.VORHERIGE_STUFE -> stufe.previous()
        else -> null
    }
    // Rot und einrastend: der Papierkorb und das endgueltige Loeschen.
    val loeschen = ziel.rastetEin()
    val zurueck = ziel == WischZiel.WIEDERHERSTELLEN
    if (!loeschen && !zurueck && zielStufe == null) return

    val flaeche = if (loeschen) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val darauf = if (loeschen) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(flaeche)
            .then(
                if (loeschen && eingerastet) {
                    Modifier.clickable(onClick = onLoeschen)
                } else {
                    Modifier
                },
            ),
        contentAlignment = if (nachRechts) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        // Halb deckend, solange ein Loslassen nichts ausloesen wuerde. Das ist
        // die sichtbare Haelfte der Rueckmeldung, der Haptik-Tick die fuehlbare.
        val staerke by animateFloatAsState(
            targetValue = if (scharf || eingerastet) 1f else 0.5f,
            label = "wischhinweis",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Gemessen wird die fertige Zeile samt Rand: die Karte gibt genau
            // so viel frei, dass hier nichts abgeschnitten ist.
            modifier = Modifier
                .onSizeChanged { onBreite(it.width) }
                .alpha(staerke)
                .padding(horizontal = 18.dp),
        ) {
            Icon(
                imageVector = when {
                    ziel == WischZiel.ENDGUELTIG -> Icons.Outlined.DeleteForever
                    loeschen -> Icons.Outlined.Delete
                    zurueck -> Icons.Outlined.RestoreFromTrash
                    else -> StageUi.icon(zielStufe!!)
                },
                contentDescription = null,
                tint = darauf,
            )
            Text(
                // Vor dem Einrasten benennt die Flaeche das Ziel, danach die
                // Handlung: ab da ist sie ein Knopf.
                text = when {
                    ziel == WischZiel.ENDGUELTIG && eingerastet -> "Endgültig löschen"
                    ziel == WischZiel.ENDGUELTIG -> "Endgültig"
                    ziel == WischZiel.PAPIERKORB && eingerastet -> "Löschen"
                    ziel == WischZiel.PAPIERKORB -> "Papierkorb"
                    zurueck -> "Zurückholen"
                    else -> StageUi.label(zielStufe!!)
                },
                style = MaterialTheme.typography.labelLarge,
                color = darauf,
                // Hoechstens zwei Zeilen, nie breiter als 80 dp: "Endgueltig
                // loeschen" passt so untereinander, und die freigelegte
                // Flaeche bleibt auch auf einer Rasterkarte schmaler als die
                // Karte. Eine Zeile wuerde die halbe Karte wegschieben.
                maxLines = 2,
                modifier = Modifier.widthIn(max = 80.dp),
            )
        }
    }
}
