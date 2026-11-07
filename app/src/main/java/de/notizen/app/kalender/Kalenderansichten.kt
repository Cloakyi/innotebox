package de.notizen.app.kalender

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.notizen.app.ui.components.Hinweisblock
import de.notizen.core.data.model.Terminart
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields

/** Hoehe einer Stunde im Raster. */
val STUNDE: Dp = 52.dp

/** Breite der Spalte mit den Uhrzeiten. */
private val ZEITSPALTE: Dp = 48.dp

/** Kleinste Hoehe eines Blocks, damit ein kurzer Termin lesbar bleibt. */
private const val MINDESTDAUER: Long = 20 * 60_000L

/** Schriftfarbe auf einer Kalenderfarbe. */
fun aufFarbe(farbe: Color): Color = if (farbe.luminance() > 0.5f) Color.Black else Color.White

/** Die Minute des Tages, auf die ein Tippen ins Raster faellt, auf halbe Stunden abgerundet. */
fun tippminute(y: Float, stundePx: Float): Int =
    ((y / stundePx * 60).toInt() / 30 * 30).coerceIn(0, 23 * 60 + 30)

/** Der Block des Termins, der gerade im Editor liegt. Beginn und Ende in Ortszeit. */
data class Entwurfblock(val beginn: LocalDateTime, val ende: LocalDateTime, val farbe: Int, val titel: String)

/** Der zuletzt gezeigte Entwurfsblock, damit er nach dem Schliessen ausblenden kann. */
private class Entwurfmerker {
    var block: Entwurfblock? = null
}

/** Wo ein Block auf dem Bildschirm lag, fuer die Animation ins Vollbild. */
internal class Lage {
    var grenzen: Rect? = null
}

internal fun Modifier.merkeLage(lage: Lage) = onGloballyPositioned { lage.grenzen = it.boundsInRoot() }

// ------------------------------------------------------------- Stundenraster

/**
 * Tag, drei Tage oder Woche: oben die ganzen Tage, darunter die Stunden.
 * [scroll] teilen sich alle Seiten, damit beim Blaettern die Uhrzeit stehen bleibt.
 *
 * [entwurf] ist der Block des offenen Editors mit seinen zwei Griffen;
 * [verborgen] blendet Bloecke aus, die gerade im Editor oder in der
 * Detailansicht stehen.
 */
@Composable
fun Zeitraster(
    tage: List<LocalDate>,
    kalenderwoche: Boolean,
    eintraege: List<Eintrag>,
    heute: LocalDate,
    zone: ZoneId,
    scroll: ScrollState,
    entwurf: Entwurfblock?,
    verborgen: (Eintrag) -> Boolean,
    onTag: (LocalDate) -> Unit,
    onLeer: (LocalDate, Int) -> Unit,
    onEintrag: (Eintrag, Rect?) -> Unit,
    onEntwurf: (LocalDateTime, LocalDateTime) -> Unit,
    onEntwurfTipp: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Kopf: Wochentag und Datum je Spalte, darunter die ganzen Tage.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(Modifier.width(ZEITSPALTE).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                if (kalenderwoche) {
                    Text(
                        text = "KW ${kalenderwochennummer(tage.first())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            tage.forEach { tag ->
                Column(
                    modifier = Modifier.weight(1f).clickable { onTag(tag) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val istHeute = tag == heute
                    Text(
                        text = Kalenderformat.wochentagKurz(tag),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (istHeute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Tageszahl(tag, istHeute, gross = tage.size <= 3)
                }
            }
        }
        val oben = remember(eintraege, tage) { tage.map { tag -> amTag(eintraege, tag).filter { it.oben } } }
        AnimatedVisibility(
            visible = oben.any { liste -> liste.any { !verborgen(it) } },
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Spacer(Modifier.width(ZEITSPALTE))
                oben.forEach { liste ->
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val sichtbar = liste.filter { !verborgen(it) }
                        sichtbar.take(3).forEach { e -> Leiste(e, onKlick = { onEintrag(e, it) }) }
                        if (sichtbar.size > 3) {
                            Text(
                                "+${sichtbar.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val linie = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(ZEITSPALTE)) {
                    for (h in 0 until 24) {
                        Box(Modifier.height(STUNDE).fillMaxWidth()) {
                            if (h > 0) {
                                Text(
                                    text = "%d:00".format(h),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.TopEnd).offset(y = (-7).dp).padding(end = 6.dp),
                                )
                            }
                        }
                    }
                }
                tage.forEach { tag ->
                    Tagesspalte(
                        tag = tag,
                        eintraege = remember(eintraege, tag) { amTag(eintraege, tag).filter { !it.oben } },
                        istHeute = tag == heute,
                        zone = zone,
                        linie = linie,
                        verborgen = verborgen,
                        onLeer = onLeer,
                        onEintrag = onEintrag,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Geht der Editor zu, blendet der Block kurz aus, statt zu verschwinden.
            // Dafuer merkt sich das Raster den letzten Stand; waehrend des Ausblendens
            // nimmt der Block keine Gesten mehr an.
            val gemerkt = remember { Entwurfmerker() }
            if (entwurf != null) gemerkt.block = entwurf
            val deckung by animateFloatAsState(
                targetValue = if (entwurf != null) 1f else 0f,
                animationSpec = tween(if (entwurf != null) 120 else 180),
                label = "Entwurf",
            )
            val zeigen = entwurf ?: gemerkt.block?.takeIf { deckung > 0f }
            if (zeigen != null) {
                Entwurfsebene(
                    d = zeigen,
                    tage = tage,
                    zone = zone,
                    onAendern = { b, e -> if (entwurf != null) onEntwurf(b, e) },
                    onTipp = { if (entwurf != null) onEntwurfTipp() },
                    modifier = Modifier
                        .padding(start = ZEITSPALTE)
                        .fillMaxWidth()
                        .height(STUNDE * 24)
                        .graphicsLayer { alpha = deckung },
                )
            }
        }
    }
}

@Composable
private fun Tageszahl(tag: LocalDate, istHeute: Boolean, gross: Boolean) {
    val groesse = if (gross) 36.dp else 30.dp
    val flaeche by animateColorAsState(
        if (istHeute) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tageszahl",
    )
    Box(
        modifier = Modifier
            .size(groesse)
            .clip(CircleShape)
            .background(flaeche),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tag.dayOfMonth.toString(),
            style = if (gross) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (istHeute) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Tagesspalte(
    tag: LocalDate,
    eintraege: List<Eintrag>,
    istHeute: Boolean,
    zone: ZoneId,
    linie: Color,
    verborgen: (Eintrag) -> Boolean,
    onLeer: (LocalDate, Int) -> Unit,
    onEintrag: (Eintrag, Rect?) -> Unit,
    modifier: Modifier,
) {
    val dichte = LocalDensity.current
    val stundePx = with(dichte) { STUNDE.toPx() }
    val jetztFarbe = MaterialTheme.colorScheme.error
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val beginn = remember(tag) { tag.atStartOfDay(zone).toInstant().toEpochMilli() }
    val ende = remember(tag) { tag.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val plaetze = remember(eintraege, beginn) { kaskade(eintraege, beginn, ende, MINDESTDAUER) }
    val leer by rememberUpdatedState(onLeer)

    // Die Linie fuer jetzt wandert minuetlich weiter.
    val jetzt by produceState(System.currentTimeMillis(), istHeute) {
        while (istHeute) {
            value = System.currentTimeMillis()
            delay(60_000L)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(STUNDE * 24)
            .drawBehind {
                for (h in 1 until 24) {
                    val y = h * stundePx
                    drawLine(linie, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawLine(linie, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            }
            .pointerInput(tag) {
                detectTapGestures { leer(tag, tippminute(it.y, stundePx)) }
            },
    ) {
        val breite = maxWidth
        plaetze.forEach { p ->
            if (verborgen(p.eintrag)) return@forEach
            val anfang = maxOf(p.eintrag.beginn, beginn)
            val schluss = maxOf(minOf(p.eintrag.ende, ende), anfang + MINDESTDAUER)
            val y = with(dichte) { ((anfang - beginn) / 3_600_000f * stundePx).toDp() }
            val h = with(dichte) { ((schluss - anfang) / 3_600_000f * stundePx).toDp() }
            // Wer ueber oder unter einem anderen liegt, bekommt einen Rand in der Farbe des Hintergrunds.
            val gestapelt = p.links > 0f || p.breite < 1f
            key(p.eintrag.schluessel) {
                Terminblock(
                    e = p.eintrag,
                    teil = teil(p.eintrag, tag),
                    zone = zone,
                    hoch = h >= 36.dp,
                    ring = if (gestapelt) ring else null,
                    onKlick = { onEintrag(p.eintrag, it) },
                    modifier = Modifier
                        .offset(x = breite * p.links, y = y)
                        .width(breite * p.breite)
                        .height(h)
                        .padding(start = 1.dp, end = 1.dp, bottom = 1.dp),
                )
            }
        }
        if (istHeute && jetzt in beginn until ende) {
            val y = with(dichte) { ((jetzt - beginn) / 3_600_000f * stundePx).toDp() }
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(jetztFarbe))
            Box(Modifier.offset(x = (-5).dp, y = y - 5.dp).size(10.dp).clip(CircleShape).background(jetztFarbe))
        }
    }
}

/** Ein Termin im Stundenraster. [teil] ist „(1/2)“ bei einem Termin ueber Mitternacht. */
@Composable
private fun Terminblock(
    e: Eintrag,
    teil: String?,
    zone: ZoneId,
    hoch: Boolean,
    ring: Color?,
    onKlick: (Rect?) -> Unit,
    modifier: Modifier,
) {
    val farbe = Color(e.farbe)
    val schrift = aufFarbe(farbe)
    val notiz = e.herkunft is Herkunft.Notiz
    val lage = remember { Lage() }
    val form = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .merkeLage(lage)
            .then(if (ring != null) Modifier.border(1.dp, ring, form) else Modifier)
            .clip(form)
            .then(
                if (notiz) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, farbe, form)
                } else {
                    Modifier.background(if (e.erledigt) farbe.copy(alpha = 0.45f) else farbe)
                },
            )
            .clickable { onKlick(lage.grenzen) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Zeichen(e, if (notiz) MaterialTheme.colorScheme.onSurface else schrift, 12.dp)
                Text(
                    text = if (teil == null) e.titel else "${e.titel} $teil",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (notiz) MaterialTheme.colorScheme.onSurface else schrift,
                    maxLines = if (hoch) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (e.erledigt && e.art == Terminart.AUFGABE) TextDecoration.LineThrough else null,
                )
            }
            if (hoch) {
                Text(
                    text = Kalenderformat.uhrzeit(e.beginn, zone) +
                        if (notiz) "" else " bis " + Kalenderformat.uhrzeit(e.ende, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (notiz) MaterialTheme.colorScheme.onSurfaceVariant else schrift.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Der Block des offenen Editors ueber den Tagesspalten. Er liegt auf einer
 * eigenen Ebene ueber allen Spalten, damit er beim Ziehen in einen anderen Tag
 * derselbe bleibt und die Geste nicht abreisst. Ziehen am Block verschiebt
 * ihn, die Punkte oben und unten aendern Beginn und Ende, beides in
 * Viertelstunden.
 */
@Composable
private fun Entwurfsebene(
    d: Entwurfblock,
    tage: List<LocalDate>,
    zone: ZoneId,
    onAendern: (LocalDateTime, LocalDateTime) -> Unit,
    onTipp: () -> Unit,
    modifier: Modifier,
) {
    val dichte = LocalDensity.current
    val stundePx = with(dichte) { STUNDE.toPx() }
    val aktuell by rememberUpdatedState(d)
    val aendern by rememberUpdatedState(onAendern)
    val haptik = LocalHapticFeedback.current
    val tipp by rememberUpdatedState(onTipp)
    val farbe = Color(d.farbe)
    val schrift = aufFarbe(farbe)
    val ring = MaterialTheme.colorScheme.surfaceContainerLow

    BoxWithConstraints(modifier) {
        val spalte = maxWidth / tage.size
        val spaltePx = with(dichte) { spalte.toPx() }
        val erster = tage.first()
        val letzter = tage.last()

        // Der Block und seine Griffe wandern mit dem Finger. Compose rechnet die
        // alte und die neue Fingerlage aber mit derselben, aktuellen Lage des
        // Knotens um; der Weg je Ereignis ist also schon der Weg auf dem
        // Bildschirm. Wer den Weg des Blocks noch einmal dazurechnet, zaehlt ihn
        // doppelt, und das Ende laeuft dem Finger davon.
        fun viertel(px: Float): Long = (px / stundePx * 4).roundToInt().toLong()

        // Die Stuecke je Tag. Der Schluessel ist der Abstand zum ersten Tag des
        // Blocks, damit ein Stueck beim Verschieben dasselbe bleibt.
        tage.forEachIndexed { i, tag ->
            val tagBeginn = tag.atStartOfDay()
            val tagEnde = tag.plusDays(1).atStartOfDay()
            val von = maxOf(d.beginn, tagBeginn)
            val bis = minOf(d.ende, tagEnde)
            if (!von.isBefore(bis)) return@forEachIndexed
            val y = with(dichte) { (java.time.Duration.between(tagBeginn, von).toMinutes() / 60f * stundePx).toDp() }
            val h = with(dichte) {
                maxOf(java.time.Duration.between(von, bis).toMinutes() / 60f * stundePx, stundePx / 4).toDp()
            }
            key(java.time.temporal.ChronoUnit.DAYS.between(d.beginn.toLocalDate(), tag)) {
                Box(
                    Modifier
                        .offset(x = spalte * i, y = y)
                        .width(spalte)
                        .height(h)
                        .padding(start = 1.dp, end = 1.dp, bottom = 1.dp)
                        .shadow(6.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(farbe)
                        .border(1.dp, ring, RoundedCornerShape(6.dp))
                        .pointerInput(Unit) { detectTapGestures { tipp() } }
                        .pointerInput(stundePx, spaltePx) {
                            var start: Entwurfblock? = null
                            var finger = Offset.Zero
                            var zuletzt = 0L to 0L
                            detectDragGestures(
                                onDragStart = {
                                    start = aktuell
                                    finger = Offset.Zero
                                    zuletzt = 0L to 0L
                                },
                                onDrag = { change, betrag ->
                                    change.consume()
                                    val s = start ?: return@detectDragGestures
                                    finger += betrag
                                    val ganz = finger
                                    val schritte = viertel(ganz.y)
                                    // Nicht aus den sichtbaren Tagen hinaus, sonst verschwaende der Block unter dem Finger.
                                    val tagSchritte = (ganz.x / spaltePx).roundToInt().toLong().coerceIn(
                                        java.time.temporal.ChronoUnit.DAYS.between(s.beginn.toLocalDate(), erster),
                                        java.time.temporal.ChronoUnit.DAYS.between(s.beginn.toLocalDate(), letzter),
                                    )
                                    if ((schritte to tagSchritte) != zuletzt) {
                                        zuletzt = schritte to tagSchritte
                                        haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        aendern(
                                            s.beginn.plusMinutes(15 * schritte).plusDays(tagSchritte),
                                            s.ende.plusMinutes(15 * schritte).plusDays(tagSchritte),
                                        )
                                    }
                                },
                            )
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Column {
                        Text(
                            text = d.titel.ifBlank { "(Ohne Titel)" },
                            style = MaterialTheme.typography.labelMedium,
                            color = schrift,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "%d:%02d bis %d:%02d".format(d.beginn.hour, d.beginn.minute, d.ende.hour, d.ende.minute),
                            style = MaterialTheme.typography.labelSmall,
                            color = schrift.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Die beiden Griffe sind eigene Knoten, damit sie beim Wandern ueber
        // Mitternacht nicht mit ihrem Stueck verschwinden.
        listOf(true, false).forEach { oben ->
            val zeit = if (oben) d.beginn else d.ende
            val tag = if (!oben && zeit.toLocalTime() == LocalTime.MIDNIGHT) zeit.toLocalDate().minusDays(1) else zeit.toLocalDate()
            val i = tage.indexOf(tag)
            if (i < 0) return@forEach
            val minuten = java.time.Duration.between(tag.atStartOfDay(), zeit).toMinutes()
            val y = with(dichte) { (minuten / 60f * stundePx).toDp() }
            val x = if (oben) spalte * i + 14.dp else spalte * (i + 1) - 14.dp
            key(if (oben) "oben" else "unten") {
                Box(
                    Modifier
                        .offset(x = x - 18.dp, y = y - 18.dp)
                        .size(36.dp)
                        .pointerInput(stundePx) {
                            var start: Entwurfblock? = null
                            var finger = 0f
                            var zuletzt = 0L
                            detectDragGestures(
                                onDragStart = {
                                    start = aktuell
                                    finger = 0f
                                    zuletzt = 0L
                                },
                                onDrag = { change, betrag ->
                                    change.consume()
                                    val s = start ?: return@detectDragGestures
                                    finger += betrag.y
                                    val schritte = viertel(finger)
                                    if (schritte == zuletzt) return@detectDragGestures
                                    if (oben) {
                                        val neu = s.beginn.plusMinutes(15 * schritte)
                                        if (neu.isAfter(s.ende.minusMinutes(15))) return@detectDragGestures
                                        zuletzt = schritte
                                        haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        aendern(neu, s.ende)
                                    } else {
                                        val neu = s.ende.plusMinutes(15 * schritte)
                                        if (neu.isBefore(s.beginn.plusMinutes(15))) return@detectDragGestures
                                        zuletzt = schritte
                                        haptik.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        aendern(s.beginn, neu)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(ring)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(farbe),
                    )
                }
            }
        }
    }
}

/** Kleines Zeichen vor dem Titel: Wecker, Aufgabe oder Notiz. */
@Composable
private fun Zeichen(e: Eintrag, farbe: Color, groesse: Dp) {
    val zeichen = when {
        e.wichtig -> Icons.Outlined.Alarm
        e.art == Terminart.AUFGABE -> Icons.Outlined.CheckCircle
        e.herkunft is Herkunft.Notiz -> Icons.AutoMirrored.Outlined.Notes
        else -> null
    } ?: return
    Icon(zeichen, contentDescription = null, tint = farbe, modifier = Modifier.padding(end = 3.dp).size(groesse))
}

/** Ein ganztaegiger Eintrag als schmale Leiste. */
@Composable
fun Leiste(e: Eintrag, onKlick: (Rect?) -> Unit, modifier: Modifier = Modifier) {
    val farbe = Color(e.farbe)
    val schrift = aufFarbe(farbe)
    val lage = remember { Lage() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .merkeLage(lage)
            .clip(RoundedCornerShape(4.dp))
            .background(if (e.erledigt) farbe.copy(alpha = 0.45f) else farbe)
            .clickable { onKlick(lage.grenzen) }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Zeichen(e, schrift, 11.dp)
        Text(
            text = e.titel,
            style = MaterialTheme.typography.labelSmall,
            color = schrift,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (e.erledigt && e.art == Terminart.AUFGABE) TextDecoration.LineThrough else null,
        )
    }
}

// ------------------------------------------------------------------- Monat

/** Die Nummer der Woche nach ISO 8601, wie sie in Deutschland gilt. */
fun kalenderwochennummer(tag: LocalDate): Int = tag.get(WeekFields.ISO.weekOfWeekBasedYear())

/** Die schmale Spalte fuer die Nummer der Woche vor dem Monat. */
private val KWSPALTE = 22.dp

@Composable
fun Wochentagskopf(modifier: Modifier = Modifier, kalenderwoche: Boolean = false) {
    val montag = wochenstart(LocalDate.now())
    Row(modifier.fillMaxWidth()) {
        if (kalenderwoche) Spacer(Modifier.width(KWSPALTE))
        for (i in 0 until 7) {
            Text(
                text = Kalenderformat.wochentagKurz(montag.plusDays(i.toLong())).take(2),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

/** Ein Monat als sechs Wochen, in jedem Tag die Eintraege als schmale Leisten. */
@Composable
fun Monatsseite(
    monat: YearMonth,
    eintraege: List<Eintrag>,
    heute: LocalDate,
    kalenderwoche: Boolean,
    onTag: (LocalDate) -> Unit,
) {
    val start = rasterstart(monat)
    val linie = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Wochentagskopf(kalenderwoche = kalenderwoche)
        HorizontalDivider(color = linie)
        for (woche in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                if (kalenderwoche) {
                    Text(
                        text = kalenderwochennummer(start.plusDays(woche * 7L)).toString(),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(KWSPALTE)
                            .fillMaxHeight()
                            .drawBehind { drawLine(linie, Offset(0f, size.height), Offset(size.width, size.height), 1f) }
                            .padding(top = 6.dp),
                    )
                }
                for (t in 0 until 7) {
                    val tag = start.plusDays((woche * 7 + t).toLong())
                    val drin = tag.month == monat.month
                    val liste = remember(eintraege, tag) { amTag(eintraege, tag) }
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .drawBehind {
                                drawLine(linie, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                                if (t > 0 || kalenderwoche) drawLine(linie, Offset(0f, 0f), Offset(0f, size.height), 1f)
                            }
                            .clickable { onTag(tag) },
                    ) {
                        // Wie viele Leisten passen, haengt von der Hoehe der Zeile ab.
                        val platz = ((maxHeight - 26.dp) / 17.dp).toInt().coerceAtLeast(0)
                        Column(Modifier.fillMaxSize().padding(1.dp)) {
                            val istHeute = tag == heute
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 2.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (istHeute) MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = tag.dayOfMonth.toString(),
                                    fontSize = 12.sp,
                                    color = when {
                                        istHeute -> MaterialTheme.colorScheme.onPrimary
                                        drin -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    },
                                )
                            }
                            val zeigen = if (liste.size > platz) (platz - 1).coerceAtLeast(0) else liste.size
                            liste.take(zeigen).forEach { e ->
                                Leiste(e, { onTag(tag) }, Modifier.padding(top = 1.dp).heightIn(min = 16.dp))
                            }
                            if (liste.size > zeigen) {
                                Text(
                                    text = "+${liste.size - zeigen}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------- Terminuebersicht

/** Was die Terminuebersicht zeigt: Monatskopf oder ein Tag mit seinen Eintraegen. */
sealed interface Zeile {
    data class Monat(val monat: YearMonth) : Zeile
    data class Tag(val tag: LocalDate, val eintraege: List<Eintrag>) : Zeile
}

/**
 * Baut die Zeilen der Terminuebersicht: nur Tage mit Eintraegen, dazu immer
 * heute, und vor jedem neuen Monat ein Kopf.
 */
fun uebersichtszeilen(eintraege: List<Eintrag>, von: LocalDate, bis: LocalDate, heute: LocalDate): List<Zeile> {
    val tage = sortedMapOf<LocalDate, MutableList<Eintrag>>()
    for (e in eintraege) {
        var tag = maxOf(e.ersterTag, von)
        val letzter = minOf(e.letzterTag, bis.minusDays(1))
        while (!tag.isAfter(letzter)) {
            tage.getOrPut(tag) { ArrayList() } += e
            tag = tag.plusDays(1)
        }
    }
    if (!heute.isBefore(von) && heute.isBefore(bis)) tage.getOrPut(heute) { ArrayList() }
    val zeilen = ArrayList<Zeile>()
    var monat: YearMonth? = null
    for ((tag, liste) in tage) {
        val m = YearMonth.from(tag)
        if (m != monat) {
            zeilen += Zeile.Monat(m)
            monat = m
        }
        zeilen += Zeile.Tag(tag, liste.sortedWith(compareBy({ !it.oben }, { it.beginn })))
    }
    return zeilen
}

@Composable
fun Terminuebersicht(
    zeilen: List<Zeile>,
    anker: LocalDate,
    heute: LocalDate,
    zone: ZoneId,
    onTag: (LocalDate) -> Unit,
    onEintrag: (Eintrag, Rect?) -> Unit,
) {
    val liste = rememberLazyListState()
    var gesprungen by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(anker, zeilen.isNotEmpty()) {
        if (zeilen.isEmpty() || gesprungen == anker) return@LaunchedEffect
        val stelle = zeilen.indexOfFirst { it is Zeile.Tag && !it.tag.isBefore(anker) }
        if (stelle >= 0) liste.scrollToItem(maxOf(0, stelle - 1))
        gesprungen = anker
    }
    LazyColumn(
        state = liste,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(zeilen, key = {
            when (it) {
                is Zeile.Monat -> "m" + it.monat
                is Zeile.Tag -> "t" + it.tag
            }
        }) { zeile ->
            when (zeile) {
                is Zeile.Monat -> Text(
                    text = Kalenderformat.monat(zeile.monat, heute),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
                is Zeile.Tag -> Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.width(56.dp).clickable { onTag(zeile.tag) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val istHeute = zeile.tag == heute
                        Text(
                            text = Kalenderformat.wochentagKurz(zeile.tag),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (istHeute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Tageszahl(zeile.tag, istHeute, gross = false)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (zeile.eintraege.isEmpty()) {
                            Text(
                                text = "Nichts geplant",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        zeile.eintraege.forEach { e -> Uebersichtskarte(e, teil(e, zeile.tag), zone, onEintrag) }
                    }
                }
            }
        }
        if (zeilen.none { it is Zeile.Tag && it.eintraege.isNotEmpty() }) {
            item {
                Hinweisblock(
                    titel = "Noch keine Termine",
                    text = "Tippe unten rechts auf das Plus, um einen Termin, eine Aufgabe oder einen Geburtstag anzulegen.",
                    modifier = Modifier.padding(top = 48.dp, start = 32.dp, end = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun Uebersichtskarte(e: Eintrag, teil: String?, zone: ZoneId, onEintrag: (Eintrag, Rect?) -> Unit) {
    val farbe = Color(e.farbe)
    val schrift = aufFarbe(farbe)
    val notiz = e.herkunft is Herkunft.Notiz
    val lage = remember { Lage() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .merkeLage(lage)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (notiz) {
                    Modifier.background(MaterialTheme.colorScheme.surface).border(1.5.dp, farbe, RoundedCornerShape(8.dp))
                } else {
                    Modifier.background(if (e.erledigt) farbe.copy(alpha = 0.45f) else farbe)
                },
            )
            .clickable { onEintrag(e, lage.grenzen) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val textfarbe = if (notiz) MaterialTheme.colorScheme.onSurface else schrift
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (val h = e.herkunft) {
                    is Herkunft.Notiz -> Icons.AutoMirrored.Outlined.Notes
                    is Herkunft.Termin -> Terminsymbole.zeichen(h.vorkommen.termin.symbol, e.art)
                },
                contentDescription = null,
                tint = textfarbe,
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                text = if (teil == null) e.titel else "${e.titel} $teil",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = textfarbe,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (e.erledigt && e.art == Terminart.AUFGABE) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (e.wichtig) Icon(Icons.Outlined.Alarm, contentDescription = "Wichtig", tint = textfarbe, modifier = Modifier.size(16.dp))
        }
        val zeit = when {
            e.ganztaegig -> "Ganztägig"
            notiz -> Kalenderformat.uhrzeit(e.beginn, zone)
            else -> Kalenderformat.uhrzeit(e.beginn, zone) + " bis " + Kalenderformat.uhrzeit(e.ende, zone)
        }
        Text(
            text = if (e.ort.isBlank()) zeit else "$zeit · ${e.ort}",
            style = MaterialTheme.typography.bodySmall,
            color = if (notiz) MaterialTheme.colorScheme.onSurfaceVariant else schrift.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 26.dp),
        )
    }
}

// ---------------------------------------------------------------- Mini-Monat

/** Der kleine Monat, der unter dem Monatsnamen aufklappt. Punkte zeigen Tage mit Eintraegen. */
@Composable
fun MiniMonat(
    gewaehlt: LocalDate,
    heute: LocalDate,
    mitEintrag: (LocalDate) -> Boolean,
    onTag: (LocalDate) -> Unit,
) {
    var monat by remember(gewaehlt) { mutableStateOf(YearMonth.from(gewaehlt)) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Kalenderformat.monat(monat, heute),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { monat = monat.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Voriger Monat")
            }
            IconButton(onClick = { monat = monat.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Nächster Monat")
            }
        }
        Wochentagskopf()
        val start = rasterstart(monat)
        for (woche in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (t in 0 until 7) {
                    val tag = start.plusDays((woche * 7 + t).toLong())
                    val drin = tag.month == monat.month
                    Column(
                        modifier = Modifier.weight(1f).clickable { onTag(tag) }.padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (tag) {
                                        heute -> MaterialTheme.colorScheme.primary
                                        gewaehlt -> MaterialTheme.colorScheme.primaryContainer
                                        else -> Color.Transparent
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tag.dayOfMonth.toString(),
                                fontSize = 13.sp,
                                color = when {
                                    tag == heute -> MaterialTheme.colorScheme.onPrimary
                                    drin -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                            )
                        }
                        Box(
                            Modifier
                                .padding(top = 1.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (mitEintrag(tag)) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
