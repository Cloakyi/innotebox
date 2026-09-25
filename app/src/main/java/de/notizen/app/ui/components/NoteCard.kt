package de.notizen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.notizen.app.ui.NoteTypeIcons
import de.notizen.app.bild.rememberBild
import de.notizen.app.ui.theme.BILD_ABDUNKLUNG
import de.notizen.app.ui.theme.BILD_FARBE
import de.notizen.app.ui.editor.Auszeichnung
import de.notizen.app.ui.theme.FAVORIT_GOLD
import de.notizen.core.data.model.Herkunft
import de.notizen.app.ui.theme.alsNotizFarbe
import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.db.relation.NoteWithRelations
import java.io.File

/** Maximal sichtbarer Text auf einer Karte. */
private const val MAX_ZEILEN = 7

/**
 * Annahme fuer die Ladebreite des Hintergrundbildes.
 *
 * Anders als bei `Bildvorschau` laesst sich die Breite hier nicht messen: Das
 * Bild liegt in einer Box, deren Groesse erst aus dem Inhalt entsteht, und
 * BoxWithConstraints daraufzusetzen ergaebe eine Messung im Kreis. 200 dp ist
 * die uebliche Spaltenbreite des Rasters auf einem Telefon; abgedunkelt und
 * beschnitten faellt eine Abweichung nach oben nicht auf.
 */
private val KARTENBREITE = 200.dp

/**
 * Der Eckenradius der Karte.
 *
 * Als Konstante, weil die Linie um den Rahmen denselben Wert braucht: Zwei
 * getrennte 16.dp laufen beim naechsten Feinschliff auseinander, und dann
 * schneidet die Linie die Ecken.
 */
private val ECKEN = 16.dp

/** Neben einer Tonspur bleibt weniger Platz -- und weniger ist dort auch richtig. */
private const val ZEILEN_NEBEN_TON = 3

/**
 * Eine Notiz-Bubble im Raster oder in der Liste.
 *
 * FARBGEBUNG: Die Flaeche kommt aus der NoteColor der Notiz. Ohne eigene Farbe
 * (DEFAULT) ist das `surface` -- und damit die Stelle, die aussieht wie ein
 * Fehler und keiner ist: der Hintergrund des Screens liegt auf
 * `surfaceContainerLow`, die Karte also DUNKLER als ihr Hintergrund. Sie sinkt
 * optisch ein, statt zu schweben. Gewollt und am Geraet nachgemessen (siehe
 * docs/ENTSCHEIDUNGEN.md). Nicht auf `surfaceContainer` "korrigieren".
 *
 * Keine Schlagschatten: die Abstufung entsteht ausschliesslich ueber die
 * Flaechenfarbe plus eine Haarlinie in `outlineVariant`.
 */
@Composable
fun NoteCard(
    note: NoteWithRelations,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    wellenform: FloatArray = FloatArray(0),
    spielt: Boolean = false,
    abspielAnteil: Float = 0f,
    abspielDauerMs: Long = 0,
    onAbspielen: () -> Unit = {},
    /** Was die Karte über ihren Sicherungsstand sagt. */
    sicherung: Kartensicherung = Kartensicherung.RUHIG,
    onGesichertGezeigt: () -> Unit = {},
    /**
     * Das Aufblitzen nach einem Sprung: 0 = nichts, 1 = voller
     * Rahmen in `primary`. Der Wert kommt animiert von aussen, damit die Karte
     * beim Neubauen im Raster nicht ihren eigenen Stand verliert.
     */
    hervorhebung: Float = 0f,
) {
    val hatTitel = note.note.title.isNotBlank()

    // Bilder der Notiz, und darunter das eine, das als Flaeche dient. Der
    // Verweis darf ins Leere zeigen (SYNC.md 14.15) -- dann bleibt es bei der
    // Palettenfarbe, ohne Aufhebens.
    // Nur INHALT: ein eigens gewaehltes Hintergrundbild ist kein Bild der
    // Notiz und bekommt deshalb auch kein Vorschaubild.
    val bilder = note.attachments.filter {
        it.mimeType.startsWith("image/") && it.role == Anhangsrolle.INHALT
    }
    // Die Flaeche wird unter ALLEN Anhaengen gesucht, nicht nur unter den
    // angezeigten -- sie kann ja gerade das eigens gewaehlte Bild sein.
    val hintergrund = note.note.backgroundAttachmentId
        ?.let { id -> note.attachments.firstOrNull { it.id == id } }

    val farbe = if (hintergrund != null) BILD_FARBE else note.note.colorId.alsNotizFarbe()
    // Gedaempfte Variante fuer Beiwerk. Abgeleitet aus der Textfarbe der Karte,
    // NICHT aus dem Theme -- auf einer eingefaerbten Karte waere
    // onSurfaceVariant schlicht die falsche Farbe.
    val gedaempft = farbe.onContainer.copy(alpha = 0.72f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .gesichertLauf(
                stand = sicherung,
                // Die Textfarbe der Karte, nicht die des Themes: Auf einer
                // eingefaerbten Karte ist `primary` schlicht die falsche Farbe
                // (siehe docs/ENTSCHEIDUNGEN.md, Farbregeln).
                farbe = farbe.onContainer,
                ecken = ECKEN,
                fertig = onGesichertGezeigt,
            ),
        shape = RoundedCornerShape(ECKEN),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = farbe.container,
            contentColor = farbe.onContainer,
        ),
        // Reihenfolge mit Absicht: Die Auswahl schlaegt alles, denn sie ist
        // fluechtig und muss auf ALLEN Karten gleich aussehen. Danach der
        // Favorit in Gold -- er ist schon von weitem als solcher zu erkennen,
        // ohne dass man den Stern suchen muss.
        border = when {
            // Das Aufblitzen schlaegt sogar die Auswahl: Es dauert eine
            // Sekunde und sagt „hier", und genau das soll man sehen.
            hervorhebung > 0f -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = hervorhebung))
            selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            note.note.isFavorite -> BorderStroke(2.dp, FAVORIT_GOLD)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box {
            hintergrund?.let { anhang ->
                val bild = rememberBild(File(anhang.localPath), KARTENBREITE)
                bild?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // matchParentSize und nicht fillMaxSize: Das Bild soll
                        // sich nach der Karte richten, nicht die Karte nach dem
                        // Bild. Mit fillMaxSize zoege es die Karte auf die volle
                        // Rasterhoehe auf.
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = BILD_ABDUNKLUNG)),
                )
            }

        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NoteTypeIcons.of(note.note.type),
                    contentDescription = NoteTypeIcons.label(note.note.type),
                    tint = gedaempft,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                if (hatTitel) {
                    Text(
                        text = note.note.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = farbe.onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                // Eine gestellte Erinnerung ist etwas, das spaeter von selbst
                // passiert. So etwas darf man einer Karte ansehen, ohne sie zu
                // oeffnen.
                if (note.offeneErinnerung != null) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Erinnerung gestellt",
                        tint = gedaempft,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                }
                // Eine ausgenommene Notiz sieht man ihr an. Sonst muesste man
                // jede einzeln oeffnen, um zu wissen, was in der Cloud liegt --
                // und genau diese Frage stellt sich, wenn man die Ausnahme
                // ueberhaupt benutzt.
                if (!note.note.syncEnabled) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = "Wird nicht synchronisiert",
                        tint = gedaempft,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                }
                // Die letzte Fassung kam von aussen, ueber Drive (SYNC.md 8).
                // Ein kleines Zeichen, damit nachvollziehbar bleibt, was ein
                // Assistent geschrieben hat; beim naechsten eigenen Schreiben
                // ist es wieder weg.
                if (note.note.origin == Herkunft.EXTERNAL) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = "Zuletzt von außen geändert",
                        tint = gedaempft,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                }
                if (note.note.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorit",
                        // Gold und nicht die Kartenfarbe: "Favorit" ist überall
                        // dieselbe Aussage, und ein Stern, der auf jeder Notiz
                        // anders aussieht, ist kein Erkennungszeichen mehr.
                        tint = FAVORIT_GOLD,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Ein Vorschaubild -- aber nur, wenn KEIN Hintergrundbild gesetzt
            // ist: Dann saehe man dasselbe Foto zweimal, einmal abgedunkelt
            // dahinter und einmal scharf davor.
            val hatBild = hintergrund == null && bilder.isNotEmpty()
            if (hatBild) {
                if (hatTitel) Spacer(Modifier.height(8.dp))
                Bildvorschau(bilder.first())
                if (bilder.size > 1) {
                    Text(
                        text = "+" + (bilder.size - 1) + " weitere",
                        style = MaterialTheme.typography.labelSmall,
                        color = gedaempft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Eine Audionotiz zeigt ihre AUFNAHME, nicht ihren Text. Ein
            // abgeschnittener Transkriptanfang sagt weniger darueber aus, wie
            // lang die Aufnahme ist und wo darin etwas passiert.
            val aufnahme = note.attachments.firstOrNull { it.mimeType.startsWith("audio/") }
            if (aufnahme != null) {
                if (hatTitel || hatBild) Spacer(Modifier.height(8.dp))
                Tonspur(
                    balken = wellenform,
                    anteil = if (spielt) abspielAnteil else 0f,
                    laeuft = spielt,
                    dauerText = dauerText(abspielDauerMs),
                    farbe = farbe.onContainer,
                    onAbspielen = onAbspielen,
                    knopfgroesse = 36.dp,
                    hoehe = 30.dp,
                )
            }

            val vorschau = vorschautext(note)
            if (vorschau.isNotBlank()) {
                if (hatTitel || aufnahme != null || hatBild) Spacer(Modifier.height(8.dp))
                Text(
                    // Gestaltet und nicht roh: Sonst staenden die
                    // Markierungszeichen auf der Karte, waehrend sie im Editor
                    // unsichtbar sind -- und die Karte saehe kaputt aus.
                    text = Auszeichnung.alsAnnotiert(vorschau),
                    style = MaterialTheme.typography.bodyMedium,
                    color = farbe.onContainer,
                    // Neben einer Tonspur reichen drei Zeilen: Die Karte soll
                    // andeuten, worum es ging, nicht das Transkript ersetzen.
                    maxLines = if (aufnahme != null) ZEILEN_NEBEN_TON else MAX_ZEILEN,
                    overflow = TextOverflow.Ellipsis,
                )
                if (istGekuerzt(vorschau, aufnahme != null)) {
                    Text(
                        text = "Mehr anzeigen",
                        style = MaterialTheme.typography.labelSmall,
                        color = gedaempft,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .align(Alignment.End),
                    )
                }
            }

            if (note.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.tags.take(3).forEach {
                        TagChip(it.name, Color(it.colorArgb), farbe.onContainer)
                    }
                }
            }
        }
        }
    }
}

/**
 * Tag-Pille. Ihre Farbe ist UNABHAENGIG von der Kartenfarbe -- das sind zwei
 * getrennte Systeme.
 */
@Composable
private fun TagChip(name: String, farbe: Color, textfarbe: Color) {
    Surface(
        color = farbe.copy(alpha = 0.22f),
        // Textfarbe kommt von der KARTE, nicht vom Theme: auf einer
        // eingefaerbten Karte waere onSurface unlesbar.
        contentColor = textfarbe,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, farbe.copy(alpha = 0.5f)),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Listen zeigen ihre Eintraege, Textnotizen ihren Body. */
private fun vorschautext(note: NoteWithRelations): String {
    val eintraege = note.orderedItems
    return if (eintraege.isNotEmpty()) {
        eintraege.joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text }
    } else {
        note.note.body
    }
}

private fun istGekuerzt(text: String, nebenTonspur: Boolean): Boolean {
    val zeilen = if (nebenTonspur) ZEILEN_NEBEN_TON else MAX_ZEILEN
    // Die Zeichengrenze skaliert mit: neben einer Tonspur passen weniger
    // Zeilen, also ist auch frueher etwas abgeschnitten.
    val zeichen = if (nebenTonspur) 140 else 280
    return text.lines().size > zeilen || text.length > zeichen
}
