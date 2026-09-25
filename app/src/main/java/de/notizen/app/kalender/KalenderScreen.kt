package de.notizen.app.kalender

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.components.LeererZustand
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Der Kalender in der App.
 *
 * Er zeigt beides: Notizen mit Erinnerung und die echten Termine des
 * Geräts. Nur die eigenen Notizen zu zeigen wäre eine zweite Liste derselben
 * Erinnerungen, die es an der Notiz schon gibt; erst zusammen ergibt es einen
 * Tagesblick.
 *
 * Drei Ansichten, und jede beantwortet eine andere Frage. Der Monat zeigt,
 * an welchen Tagen überhaupt etwas steht; die Woche zeigt, was in den nächsten
 * Tagen kommt; der Tag zeigt einen Tag ohne Ablenkung. Die Pfeile blättern
 * entsprechend um einen Monat, eine Woche oder einen Tag.
 *
 * Kein Stundenraster wie in einem Terminplaner. In einer Notizen-App stehen
 * an einem Tag null bis zwei Dinge; ein Raster von 0 bis 24 Uhr wäre zu
 * neunundneunzig Prozent leere Fläche.
 */
@Composable
fun KalenderScreen(
    innerPadding: PaddingValues,
    onNotiz: (String) -> Unit,
    zeigeHinweis: (String) -> Unit,
    viewModel: KalenderMonatViewModel = hiltViewModel(),
) {
    val ansicht by viewModel.ansicht.collectAsStateWithLifecycle()
    val anker by viewModel.anker.collectAsStateWithLifecycle()
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val termine by viewModel.termine.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val zone = remember { ZoneId.systemDefault() }
    val tage = remember(notizen, termine, zone) { nachTagen(notizen, termine, zone) }
    val heute = remember { LocalDate.now() }

    // Beim Aufgehen des Bildschirms einmal holen. Der Kalender-Anbieter kennt
    // keinen Fluss, der sich meldet, wenn nebenher ein Termin entsteht.
    LaunchedEffect(Unit) { viewModel.termineLaden() }

    val oeffneTermin: (Fremdtermin) -> Unit = { termin ->
        // Ein fremder Termin gehoert nicht dieser App. Statt ihn hier
        // notduerftig anzuzeigen, geht es dorthin, wo er hingehoert.
        val ziel = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            termin.eventId,
        )
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, ziel))
        } catch (fehler: ActivityNotFoundException) {
            zeigeHinweis("Auf diesem Gerät ist keine Kalender-App eingerichtet.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Kopf(
            ansicht = ansicht,
            anker = anker,
            heute = heute,
            onZurueck = viewModel::zurueck,
            onWeiter = viewModel::weiter,
            onHeute = viewModel::heute,
        )

        Ansichtswahl(gewaehlt = ansicht, onWahl = viewModel::setAnsicht)

        if (ansicht == Kalenderansicht.MONAT) {
            Wochentagszeile()
            Monatsraster(
                monat = YearMonth.from(anker),
                heute = heute,
                gewaehlt = anker,
                tage = tage,
                onTag = viewModel::waehle,
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (!viewModel.erlaubt()) {
            Text(
                text = "Deine eigenen Termine fehlen hier, weil die App den " +
                    "Kalender nicht lesen darf. In den Einstellungen unter " +
                    "Kalender kannst du das erlauben.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        when (ansicht) {
            Kalenderansicht.WOCHE -> Wochenliste(
                von = wochenstart(anker),
                heute = heute,
                tage = tage,
                zone = zone,
                onNotiz = onNotiz,
                onTermin = oeffneTermin,
            )

            // Tag und Monat zeigen beide genau EINEN Tag. Im Monat ist es der
            // angetippte, im Tag der gezeigte: dieselbe Liste, nur anders
            // ausgewaehlt.
            else -> Tagesliste(
                inhalt = tage[anker] ?: Tagesinhalt(),
                zone = zone,
                onNotiz = onNotiz,
                onTermin = oeffneTermin,
            )
        }
    }
}

@Composable
private fun Kopf(
    ansicht: Kalenderansicht,
    anker: LocalDate,
    heute: LocalDate,
    onZurueck: () -> Unit,
    onWeiter: () -> Unit,
    onHeute: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
    ) {
        Text(
            text = kopftext(ansicht, anker),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        // Nur wenn man woanders ist. Ein Knopf, der zum heutigen Tag fuehrt,
        // waehrend man ihn schon ansieht, tut nichts.
        if (!zeigtHeute(ansicht, anker, heute)) {
            TextButton(onClick = onHeute) { Text("Heute") }
        }
        IconButton(onClick = onZurueck) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Zurück",
            )
        }
        IconButton(onClick = onWeiter) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Weiter",
            )
        }
    }
}

/**
 * Tag, Woche, Monat.
 *
 * Drei `FilterChip` und keine Reihe aus `SegmentedButton`: Die ist in dieser
 * Material-Fassung noch als experimentell angemeldet, und die Konvention dieses
 * Projekts ist, das zu meiden, wo es eine stabile Entsprechung gibt.
 */
@Composable
private fun Ansichtswahl(gewaehlt: Kalenderansicht, onWahl: (Kalenderansicht) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        for (ansicht in Kalenderansicht.entries) {
            FilterChip(
                selected = ansicht == gewaehlt,
                onClick = { onWahl(ansicht) },
                label = { Text(ansichtsname(ansicht)) },
            )
        }
    }
}

@Composable
private fun Wochentagszeile() {
    Row(modifier = Modifier.padding(horizontal = 12.dp)) {
        // Montag zuerst, wie im deutschsprachigen Raum ueblich.
        for (tag in DayOfWeek.entries) {
            Text(
                text = tag.getDisplayName(TextStyle.SHORT, Locale.GERMANY),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Das Monatsraster.
 *
 * Immer sechs Zeilen, auch wenn der Monat mit fünf auskäme. Sonst sprängen
 * beim Blättern die Liste darunter und der ganze Bildschirm.
 */
@Composable
private fun Monatsraster(
    monat: YearMonth,
    heute: LocalDate,
    gewaehlt: LocalDate,
    tage: Map<LocalDate, Tagesinhalt>,
    onTag: (LocalDate) -> Unit,
) {
    val start = rasterstart(monat)

    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        for (woche in 0 until RASTERTAGE / 7) {
            Row {
                for (wochentag in 0 until 7) {
                    val tag = start.plusDays((woche * 7 + wochentag).toLong())
                    Tageszelle(
                        tag = tag,
                        imMonat = YearMonth.from(tag) == monat,
                        istHeute = tag == heute,
                        istGewaehlt = tag == gewaehlt,
                        inhalt = tage[tag],
                        onKlick = { onTag(tag) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Tageszelle(
    tag: LocalDate,
    imMonat: Boolean,
    istHeute: Boolean,
    istGewaehlt: Boolean,
    inhalt: Tagesinhalt?,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val schrift = when {
        istGewaehlt -> MaterialTheme.colorScheme.onPrimary
        !imMonat -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        istHeute -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                if (istGewaehlt) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onKlick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tag.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (istHeute) FontWeight.Bold else FontWeight.Normal,
                color = schrift,
            )
            Punkte(inhalt, istGewaehlt)
        }
    }
}

/**
 * Zwei Punkte, weil es zwei Sorten gibt.
 *
 * Eigene Notiz und fremder Termin. Ein gemeinsamer Punkt ließe offen, was einen
 * dort erwartet.
 */
@Composable
private fun Punkte(inhalt: Tagesinhalt?, aufFlaeche: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(6.dp),
    ) {
        if (inhalt?.notizen?.isNotEmpty() == true) {
            Punkt(
                if (aufFlaeche) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        if (inhalt?.termine?.isNotEmpty() == true) {
            Punkt(
                if (aufFlaeche) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Punkt(farbe: Color) {
    Box(
        Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(farbe),
    )
}

/**
 * Die ganze Woche, nach Tagen gegliedert.
 *
 * Alle sieben Tage stehen da, auch die leeren. Eine Woche, aus der die
 * leeren Tage herausfallen, ist eine Liste und keine Woche: Man sähe nicht mehr,
 * dass am Mittwoch nichts ansteht, sondern nur, dass der Mittwoch fehlt.
 */
@Composable
private fun Wochenliste(
    von: LocalDate,
    heute: LocalDate,
    tage: Map<LocalDate, Tagesinhalt>,
    zone: ZoneId,
    onNotiz: (String) -> Unit,
    onTermin: (Fremdtermin) -> Unit,
) {
    val woche = (0 until 7).map { von.plusDays(it.toLong()) }

    LazyColumn(Modifier.fillMaxSize()) {
        for (tag in woche) {
            val inhalt = tage[tag] ?: Tagesinhalt()

            item(key = "kopf-$tag") { Tageskopf(tag = tag, istHeute = tag == heute) }

            if (inhalt.leer) {
                item(key = "leer-$tag") {
                    Text(
                        text = "Nichts vorgemerkt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                    )
                }
            } else {
                eintraege(inhalt, zone, onNotiz, onTermin, schluessel = tag.toString())
            }
        }
    }
}

@Composable
private fun Tageskopf(tag: LocalDate, istHeute: Boolean) {
    Text(
        text = "${tag.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMANY)}, " +
            "${tag.dayOfMonth}.",
        style = MaterialTheme.typography.labelLarge,
        color = if (istHeute) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Tagesliste(
    inhalt: Tagesinhalt,
    zone: ZoneId,
    onNotiz: (String) -> Unit,
    onTermin: (Fremdtermin) -> Unit,
) {
    if (inhalt.leer) {
        LeererZustand(
            titel = "Nichts an diesem Tag",
            text = "Notizen mit einer Erinnerung erscheinen hier, und deine " +
                "eigenen Termine daneben.",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        eintraege(inhalt, zone, onNotiz, onTermin, schluessel = "tag")
    }
}

/**
 * Die Einträge eines Tages.
 *
 * Als Erweiterung auf `LazyListScope`, damit Tages- und Wochenansicht dieselben
 * Zeilen benutzen. Zwei Fassungen davon liefen mit der Zeit auseinander, und
 * dann sähe derselbe Termin in zwei Ansichten verschieden aus.
 *
 * [schluessel] hält die Schlüssel eindeutig. In der Wochenansicht stehen sieben
 * Tage in einer Liste, und zwei Tage dürfen nicht denselben Schlüssel
 * vergeben.
 */
private fun LazyListScope.eintraege(
    inhalt: Tagesinhalt,
    zone: ZoneId,
    onNotiz: (String) -> Unit,
    onTermin: (Fremdtermin) -> Unit,
    schluessel: String,
) {
    items(
        count = inhalt.notizen.size,
        key = { i ->
            val n = inhalt.notizen[i]
            "n-$schluessel-${n.noteId}-${n.triggerAt}"
        },
    ) { i ->
        val notiz = inhalt.notizen[i]
        Zeile(
            zeit = uhrzeit(notiz.triggerAt, zone),
            titel = notiz.title.ifBlank { "Notiz ohne Titel" },
            unterzeile = if (notiz.isFired) "Erinnerung, war fällig" else "Erinnerung",
            akzent = true,
            onKlick = { onNotiz(notiz.noteId) },
        )
    }

    items(
        count = inhalt.termine.size,
        key = { i ->
            val t = inhalt.termine[i]
            "t-$schluessel-${t.eventId}-${t.beginn}"
        },
    ) { i ->
        val termin = inhalt.termine[i]
        Zeile(
            zeit = if (termin.ganztaegig) "ganztägig" else uhrzeit(termin.beginn, zone),
            titel = termin.titel,
            unterzeile = "Termin im Kalender",
            akzent = false,
            onKlick = { onTermin(termin) },
        )
    }
}

@Composable
private fun Zeile(
    zeit: String,
    titel: String,
    unterzeile: String,
    akzent: Boolean,
    onKlick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onKlick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = zeit,
            style = MaterialTheme.typography.labelLarge,
            color = if (akzent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(width = 68.dp, height = 20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = titel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = unterzeile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Die Uhrzeit eines Zeitpunkts in der Zone des Geräts. */
private fun uhrzeit(millis: Long, zone: ZoneId): String {
    val zeit = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return "%02d:%02d".format(zeit.hour, zeit.minute)
}
