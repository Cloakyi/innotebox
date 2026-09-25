package de.notizen.app.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import kotlinx.coroutines.delay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.audio.aufnahmeDatei
import de.notizen.app.audio.dauerVon
import de.notizen.app.ui.components.LeererZustand
import de.notizen.app.ui.components.Kartensicherung
import de.notizen.app.ui.components.WischbareKarte
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.Darstellung
import de.notizen.core.data.model.Stage

/**
 * Der Inhalt einer Stufen-Ansicht. Top Bar, Drawer und FAB liegen eine Ebene
 * hoeher in NotizenApp, damit sie beim Wechsel zwischen den Stufen stehen
 * bleiben.
 */
@Composable
fun StageScreen(
    viewModel: StageViewModel,
    innerPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
) {
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()

    if (notizen.isEmpty()) {
        StufeLeer(viewModel.stage, Modifier.padding(innerPadding))
        return
    }

    NotizenRaster(
        viewModel = viewModel,
        onOpenNote = onOpenNote,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            // Grosszuegig, damit der FAB die letzte Karte nicht verdeckt.
            bottom = innerPadding.calculateBottomPadding() + 96.dp,
        ),
    )
}

/**
 * Raster bzw. Liste der Notizen. Ausgelagert, weil der Archiv-Screen dasselbe
 * Gitter unter seinem Suchfeld zeigt.
 *
 * Die aufgezogene Loeschflaeche ist hier gemerkt und nicht in der Karte:
 * hoechstens eine Karte darf gleichzeitig offen stehen. Zwei offene rote
 * Flaechen waeren zwei scharfe Knoepfe auf einmal.
 */
@Composable
internal fun NotizenRaster(
    viewModel: StageViewModel,
    onOpenNote: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    kopfzeilen: List<Kopfzeile> = emptyList(),
) {
    val notizen by viewModel.notizen.collectAsStateWithLifecycle()
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val darstellung by viewModel.darstellung.collectAsStateWithLifecycle()
    val rechts by viewModel.wischRechts.collectAsStateWithLifecycle()
    val links by viewModel.wischLinks.collectAsStateWithLifecycle()

    var offen by remember { mutableStateOf<String?>(null) }

    val abspielen by viewModel.abspielstatus.collectAsStateWithLifecycle()
    val kartenstand by viewModel.kartenstand.collectAsStateWithLifecycle()
    val wellenformen by viewModel.wellenformen.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val abstand = Arrangement.spacedBy(10.dp)

    // Die Datenbank sortiert Favoriten bereits nach vorne, hier wird nur
    // getrennt. Die Ueberschriften stehen deshalb genau an der Nahtstelle und
    // koennen nicht in Widerspruch zur Sortierung geraten.
    val favoriten = notizen.filter { it.note.isFavorite }
    val uebrige = notizen.filterNot { it.note.isFavorite }
    // Ueberschriften nur, wenn es wirklich zwei Gruppen gibt. Ein einzelnes
    // "Uebrige" ueber allem waere eine Einteilung ohne Einteilung.
    val getrennt = favoriten.isNotEmpty() && uebrige.isNotEmpty()

    // DAS AUFBLITZEN NACH EINEM SPRUNG (14c). Erst zur Karte scrollen, dann
    // den Rahmen eine Sekunde lang ausblenden. Der Wert lebt hier und nicht in
    // der Karte: Eine Karte im Raster wird beim Scrollen neu gebaut und
    // verloere dabei ihren eigenen Stand. Danach wird die Kennung geloescht,
    // sonst blitzte sie bei jeder Drehung wieder auf.
    val hervorheben by viewModel.hervorheben.collectAsStateWithLifecycle()
    val rasterZustand = rememberLazyStaggeredGridState()
    val listenZustand = rememberLazyListState()
    val hervorhebung = remember { Animatable(0f) }
    LaunchedEffect(hervorheben, notizen.isEmpty()) {
        val ziel = hervorheben ?: return@LaunchedEffect
        if (notizen.isEmpty()) return@LaunchedEffect
        val stelle = stelleVon(ziel, kopfzeilen.size, getrennt, favoriten, uebrige)
        if (stelle != null) {
            if (darstellung == Darstellung.RASTER) {
                rasterZustand.animateScrollToItem(stelle)
            } else {
                listenZustand.animateScrollToItem(stelle)
            }
            hervorhebung.snapTo(1f)
            delay(AUFBLITZEN_HALTEN_MS)
            hervorhebung.animateTo(0f, tween(AUFBLITZEN_AUSBLENDEN_MS))
        }
        viewModel.hervorhebungGezeigt()
    }

    val karte: @Composable (NoteWithRelations) -> Unit = { n ->
        val hatTon = n.attachments.any { it.mimeType.startsWith("audio/") }
        val datei = remember(n.note.id) { aufnahmeDatei(context, n.note.id) }

        // Erst wenn die Karte wirklich sichtbar wird -- eine Stufe mit
        // hundert Aufnahmen soll nicht beim Oeffnen hundert Dateien lesen.
        if (hatTon) {
            LaunchedEffect(n.note.id) { viewModel.wellenformLaden(n.note.id, datei) }
        }

        WischbareKarte(
            note = n,
            stufe = viewModel.stage,
            selected = n.note.id in auswahl,
            auswahlAktiv = auswahl.isNotEmpty(),
            rechts = rechts,
            links = links,
            eingerastet = offen == n.note.id,
            onEinrasten = { offen = n.note.id },
            onAusrasten = { offen = null },
            onClick = {
                if (auswahl.isEmpty()) onOpenNote(n.note.id) else viewModel.toggleAuswahl(n.note.id)
            },
            // Im Papierkorb gibt es keine Auswahl (die Leiste dafuer fehlt
            // dort), langes Druecken tut dasselbe wie Tippen.
            onLongClick = {
                if (viewModel.imPapierkorb) onOpenNote(n.note.id) else viewModel.toggleAuswahl(n.note.id)
            },
            onWisch = { ziel -> viewModel.wischAktion(n.note.id, ziel) },
            wellenform = wellenformen[n.note.id] ?: FloatArray(0),
            spielt = abspielen.laeuftFuer(n.note.id),
            abspielAnteil = if (abspielen.notizId == n.note.id) abspielen.anteil else 0f,
            abspielDauerMs = if (abspielen.notizId == n.note.id) {
                abspielen.dauerMs.toLong()
            } else {
                datei.takeIf { hatTon }?.let { dauerVon(it) } ?: 0L
            },
            onAbspielen = { viewModel.abspielen(n.note.id, datei) },
            sicherung = kartenstand[n.note.id] ?: Kartensicherung.RUHIG,
            onGesichertGezeigt = { viewModel.gesichertGezeigt(n.note.id) },
            hervorhebung = if (hervorheben == n.note.id) hervorhebung.value else 0f,
        )
    }

    when (darstellung) {
        Darstellung.RASTER ->
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = rasterZustand,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalItemSpacing = 10.dp,
                horizontalArrangement = abstand,
            ) {
                items(
                    items = kopfzeilen,
                    key = { it.schluessel },
                    span = { StaggeredGridItemSpan.FullLine },
                ) { zeile -> zeile.inhalt() }

                if (getrennt) {
                    item(span = StaggeredGridItemSpan.FullLine) { Abschnitt("Favoriten") }
                }
                items(favoriten, key = { it.note.id }) { n -> karte(n) }

                if (getrennt) {
                    item(span = StaggeredGridItemSpan.FullLine) { Abschnitt("Übrige") }
                }
                items(uebrige, key = { it.note.id }) { n -> karte(n) }
            }

        Darstellung.LISTE ->
            LazyColumn(
                state = listenZustand,
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = abstand,
            ) {
                items(kopfzeilen, key = { it.schluessel }) { zeile -> zeile.inhalt() }

                if (getrennt) item { Abschnitt("Favoriten") }
                items(favoriten, key = { it.note.id }) { n -> karte(n) }

                if (getrennt) item { Abschnitt("Übrige") }
                items(uebrige, key = { it.note.id }) { n -> karte(n) }
            }
    }
}

/** Wie lange der Rahmen voll steht, bevor er verblasst, und wie lange das Verblassen dauert. */
private const val AUFBLITZEN_HALTEN_MS = 400L
private const val AUFBLITZEN_AUSBLENDEN_MS = 700

/**
 * An welcher Stelle der Liste eine Notiz steht, Kopfzeilen und Ueberschriften
 * mitgezaehlt. `null`, wenn sie nicht in der Liste ist.
 *
 * Rechnet dieselbe Reihenfolge nach, in der die Eintraege unten gebaut werden:
 * erst die Kopfzeilen, dann (mit Ueberschrift) die Favoriten, dann (mit
 * Ueberschrift) die uebrigen. Wer die Reihenfolge dort aendert, aendert sie
 * hier mit.
 */
internal fun stelleVon(
    notizId: String,
    kopfzeilen: Int,
    getrennt: Boolean,
    favoriten: List<NoteWithRelations>,
    uebrige: List<NoteWithRelations>,
): Int? {
    val ueberschrift = if (getrennt) 1 else 0
    val inFavoriten = favoriten.indexOfFirst { it.note.id == notizId }
    if (inFavoriten >= 0) return kopfzeilen + ueberschrift + inFavoriten
    val inUebrigen = uebrige.indexOfFirst { it.note.id == notizId }
    if (inUebrigen >= 0) return kopfzeilen + ueberschrift * 2 + favoriten.size + inUebrigen
    return null
}

/**
 * Etwas, das ueber den Karten steht und ueber die volle Breite geht.
 *
 * Im selben Scrollbereich und nicht darueber. Die Ordneransicht setzt ihre
 * Unterordner hierhin. Ein eigener Block ueber dem Raster waere bei drei
 * Ordnern huebsch und bei dreissig eine Wand, hinter der die Notizen nicht mehr
 * zu sehen sind. So scrollt alles zusammen weg.
 */
data class Kopfzeile(
    val schluessel: String,
    val inhalt: @Composable () -> Unit,
)

/** Ueberschrift ueber einer Gruppe von Karten. */
@Composable
private fun Abschnitt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

/**
 * Was statt der Karten steht, wenn eine Stufe leer ist.
 *
 * Bewusst kein blosses "Keine Notizen": der Text sagt, wofuer die Stufe da
 * ist. Gerade am Anfang ist das die einzige Stelle, an der die Idee des
 * Flusses ueberhaupt auftaucht.
 */
@Composable
internal fun StufeLeer(stage: Stage, modifier: Modifier = Modifier) {
    val (titel, text) = when (stage) {
        Stage.INBOX -> "Hier ist noch nichts" to
            "Der Eingang nimmt alles auf, was dir zwischendurch einfällt. " +
                "Tipp auf das Plus und schreib einfach los."
        Stage.WORKSPACE -> "Der Schreibtisch ist frei" to
            "Hierher wandert, woran du gerade wirklich arbeitest. " +
                "Alles andere darf im Eingang liegen bleiben."
        Stage.ARCHIVE -> "Das Archiv ist noch leer" to
            "Was fertig ist, sammelt sich hier von selbst. " +
                "Wiederfinden wirst du es über die Suche."
    }

    LeererZustand(titel = titel, text = text, modifier = modifier)
}
