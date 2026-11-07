package de.notizen.app.kalender

import de.notizen.core.data.kalender.serie
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import de.notizen.app.bild.rememberBild
import de.notizen.core.data.db.entity.EventEntity
import java.io.File
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Ein Eintrag im Vollbild. Er waechst aus dem Block, auf den getippt wurde,
 * und schrumpft beim Schliessen dorthin zurueck:
 * Lage, Ecken und Farbe gehen fliessend vom Block in die Seite ueber, der
 * Inhalt blendet erst in der zweiten Haelfte ein.
 *
 * Ohne [von] (etwa aus der Suche) waechst die Seite aus der Mitte.
 */
@Composable
fun Detailansicht(
    e: Eintrag,
    von: Rect?,
    /** Der Termin, wie er jetzt in der Datenbank steht; das Symbol der KI kommt oft erst danach. */
    termin: EventEntity?,
    bilddatei: (String) -> File,
    kalender: List<EventCalendarEntity>,
    zone: ZoneId,
    onZu: () -> Unit,
    onBearbeiten: (String) -> Unit,
    onNotiz: (String) -> Unit,
    onErledigt: (String, Boolean) -> Unit,
    onLoeschen: (String) -> Unit,
    onVorkommenLoeschen: (String, Long) -> Unit,
) {
    val fortschritt = remember { Animatable(0f) }
    var schliessen by remember { mutableStateOf(false) }
    var eigene by remember { mutableStateOf(Offset.Zero) }
    val zu by rememberUpdatedState(onZu)

    LaunchedEffect(Unit) { fortschritt.animateTo(1f, spring(dampingRatio = 0.92f, stiffness = 420f)) }
    LaunchedEffect(schliessen) {
        if (schliessen) {
            fortschritt.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            zu()
        }
    }
    BackHandler(enabled = !schliessen) { schliessen = true }

    val farbe = Color(e.farbe)
    val flaeche = MaterialTheme.colorScheme.surfaceContainerLow
    val dichte = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { eigene = it.boundsInRoot().topLeft },
    ) {
        val seitenBreite = maxWidth
        val seitenHoehe = maxHeight
        val breite = with(dichte) { maxWidth.toPx() }
        val hoehe = with(dichte) { maxHeight.toPx() }
        val ziel = Rect(0f, 0f, breite, hoehe)
        val start = von?.translate(-eigene) ?: Rect(breite * 0.1f, hoehe * 0.3f, breite * 0.9f, hoehe * 0.5f)
        val p = fortschritt.value
        val r = lerp(start, ziel, p.coerceIn(0f, 1.05f))

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * p.coerceIn(0f, 1f))),
        )
        Box(
            Modifier
                .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size(with(dichte) { r.width.toDp() }, with(dichte) { r.height.toDp() })
                .clip(RoundedCornerShape(lerp(8.dp, 0.dp, p.coerceIn(0f, 1f))))
                .background(lerp(farbe, flaeche, (p * 1.6f).coerceIn(0f, 1f))),
        ) {
            // Der Block, aus dem es kommt: blendet zuerst aus.
            Text(
                text = e.titel,
                style = MaterialTheme.typography.labelMedium,
                color = aufFarbe(farbe),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .graphicsLayer { alpha = (1f - p * 3f).coerceIn(0f, 1f) },
            )
            // Die Seite in voller Groesse, damit sie beim Wachsen nicht umbricht.
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(seitenBreite, seitenHoehe)
                    .graphicsLayer { alpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) },
            ) {
                // Die Seite liegt auf keiner Surface; ohne das waeren Schrift und
                // Zeichen schwarz, auch auf dunklem Grund.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    Detailinhalt(
                        e = e,
                        termin = termin,
                        bilddatei = bilddatei,
                        kalender = kalender,
                        zone = zone,
                        onSchliessen = { schliessen = true },
                        onBearbeiten = onBearbeiten,
                        onNotiz = onNotiz,
                        onErledigt = onErledigt,
                        // Nach dem Loeschen schrumpft die Seite sichtbar zurueck in den Kalender.
                        onLoeschen = { id ->
                            schliessen = true
                            onLoeschen(id)
                        },
                        onVorkommenLoeschen = { id, schluessel ->
                            schliessen = true
                            onVorkommenLoeschen(id, schluessel)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Detailinhalt(
    e: Eintrag,
    termin: EventEntity?,
    bilddatei: (String) -> File,
    kalender: List<EventCalendarEntity>,
    zone: ZoneId,
    onSchliessen: () -> Unit,
    onBearbeiten: (String) -> Unit,
    onNotiz: (String) -> Unit,
    onErledigt: (String, Boolean) -> Unit,
    onLoeschen: (String) -> Unit,
    onVorkommenLoeschen: (String, Long) -> Unit,
) {
    var loeschenFragen by remember { mutableStateOf(false) }
    val vorkommen = (e.herkunft as? Herkunft.Termin)?.vorkommen
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSchliessen) { Icon(Icons.Outlined.Close, contentDescription = "Schließen") }
            Spacer(Modifier.weight(1f))
            if (vorkommen != null) {
                IconButton(onClick = { onBearbeiten(vorkommen.termin.id) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten")
                }
                IconButton(onClick = {
                    if (vorkommen.termin.recurrence == Wiederholung.KEINE) onLoeschen(vorkommen.termin.id) else loeschenFragen = true
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            (termin ?: vorkommen?.termin)?.let { t -> Banner(t, Color(e.farbe), bilddatei) }
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(e.farbe)),
                )
                Column(Modifier.padding(start = 20.dp)) {
                    Text(e.titel, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (vorkommen?.termin?.undated == true) "Ohne Datum" else Kalenderformat.zeitraum(e, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (vorkommen != null && vorkommen.termin.recurrence != Wiederholung.KEINE) {
                        Text(
                            wiederholungstext(vorkommen.termin.serie),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            when (val h = e.herkunft) {
                is Herkunft.Termin -> {
                    val t = h.vorkommen.termin
                    val k = kalender.firstOrNull { it.id == t.calendarId }
                    Detailzeile(Terminsymbole.zeichen(t.symbol, t.art), artText(t.art) + (k?.let { " · " + it.name } ?: ""))
                    if (t.important) Detailzeile(Icons.Outlined.Alarm, "Wichtig. Klingelt wie ein Wecker.")
                    t.reminders.sorted().forEach { m ->
                        Detailzeile(Icons.Outlined.Notifications, erinnerungstext(m, t.allDay))
                    }
                    if (t.location.isNotBlank()) Detailzeile(Icons.Outlined.LocationOn, t.location)
                    if (t.description.isNotBlank()) Detailzeile(Icons.AutoMirrored.Outlined.Notes, t.description)
                    if (t.art == Terminart.AUFGABE) {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { onErledigt(t.id, !t.done) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (t.done) "Als nicht erledigt markieren" else "Als erledigt markieren")
                        }
                    }
                }
                is Herkunft.Notiz -> {
                    Detailzeile(Icons.AutoMirrored.Outlined.Notes, "Erinnerung aus einer Notiz")
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = { onNotiz(h.noteId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Notiz öffnen")
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (loeschenFragen && vorkommen != null) {
        var nurDieser by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { loeschenFragen = false },
            title = { Text("Wiederkehrenden Termin löschen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { nurDieser = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = nurDieser, onClick = { nurDieser = true })
                        Text("Dieser Termin")
                    }
                    Row(Modifier.fillMaxWidth().clickable { nurDieser = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !nurDieser, onClick = { nurDieser = false })
                        Text("Alle Termine")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    loeschenFragen = false
                    if (nurDieser) onVorkommenLoeschen(vorkommen.termin.id, vorkommen.schluessel) else onLoeschen(vorkommen.termin.id)
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { loeschenFragen = false }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Oben ueber dem Termin: das eigene Bild oder, ohne Bild, das Symbol, das
 * die KI oder die Woerter ausgesucht haben, gross auf der Farbe des Termins.
 */
@Composable
private fun Banner(t: EventEntity, farbe: Color, bilddatei: (String) -> File) {
    val bild = rememberBild(t.bannerId?.let(bilddatei), 600.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 20.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(farbe.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bild != null) {
            Image(bild, contentDescription = "Bild des Termins", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Terminsymbole.zeichen(t.symbol, t.art), contentDescription = null, tint = farbe, modifier = Modifier.size(72.dp))
        }
    }
}

@Composable
private fun Detailzeile(zeichen: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(zeichen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp))
    }
}
