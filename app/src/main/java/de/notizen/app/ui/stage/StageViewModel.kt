package de.notizen.app.ui.stage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ai.fallbackTitel
import de.notizen.app.ui.UndoRequest
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.app.ui.ordner.RueckholAnfrage
import de.notizen.app.ui.ordner.rueckholAnfrageFuer
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.Darstellung
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.app.sync.Speichermarke
import de.notizen.app.ui.components.Kartensicherung
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.sync.Abgleich
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.WischZiel
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.NoteRepository
import de.notizen.app.audio.Abspielstatus
import de.notizen.app.audio.Wellenformdaten
import de.notizen.app.audio.Wiedergabe
import de.notizen.core.data.repository.NoteSort
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Eine Notiz braucht einen Titel, bevor sie weiterziehen darf.
 *
 * [alleIds] ist der GANZE Verschiebe-Auftrag, nicht nur die titellose Notiz --
 * sonst bliebe der Rest der Auswahl liegen, waehrend der Dialog offen ist.
 */
data class TitelAnfrage(
    val noteId: String,
    val alleIds: List<String>,
    val ziel: Stage,
    val fallback: String,
    val quelle: String,
)

@HiltViewModel
class StageViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val ordner: FolderRepository,
    private val einstellungen: Einstellungen,
    private val wiedergabe: Wiedergabe,
    private val wellenformdaten: Wellenformdaten,
    savedState: SavedStateHandle,
    private val speichermarke: Speichermarke,
    private val abgleich: Abgleich,
    private val syncDao: SyncDao,
) : ViewModel() {

    /**
     * Was jede Karte über ihren Sicherungsstand sagt.
     *
     * **Drei Quellen, eine Aussage je Karte.** Welche Notizen noch anstehen,
     * weiß die Datenbank; ob gerade ein Lauf läuft, weiß der Abgleich; und
     * welche Notiz eben verlassen wurde, die Marke. Erst zusammen ergibt sich
     * eine Anzeige, die nicht lügt: Eine kreisende Linie ohne laufenden Abgleich
     * wäre eine Behauptung, dass etwas passiert, während nichts passiert.
     *
     * **Der Übergang zu „angekommen" wird hier gemerkt, nicht in der Karte.**
     * Eine Karte im Raster wird beim Scrollen neu gebaut und verliert dabei, was
     * vorher war. Der Vergleich mit dem letzten Stand gehört deshalb hierher.
     */
    private val geradeAngekommen = MutableStateFlow<Set<String>>(emptySet())

    val kartenstand: StateFlow<Map<String, Kartensicherung>> = combine(
        syncDao.observeOffeneNotizIds(),
        abgleich.laeuft,
        speichermarke.notiz,
        geradeAngekommen,
        einstellungen.syncIntervall(),
    ) { offen, laeuft, marke, angekommen, takt ->
        buildMap {
            // Nur waehrend wirklich ein Lauf laeuft. Eine kreisende Linie ohne
            // laufenden Abgleich waere die Behauptung, dass gerade etwas
            // passiert, waehrend nichts passiert.
            if (laeuft) offen.forEach { put(it, Kartensicherung.LAEUFT) }

            // Schlaegt das Kreisen: Angekommen ist das Ende, nicht der Verlauf.
            angekommen.forEach { put(it, Kartensicherung.ANGEKOMMEN) }

            // Ohne Abgleich gibt es kein "oben". Dann ist die einmalige Runde
            // die ganze Rueckmeldung, und sie bedeutet schlicht "gespeichert".
            if (takt <= 0 && marke != null) put(marke, Kartensicherung.GESPEICHERT)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // Wer eben noch anstand und jetzt nicht mehr, ist angekommen. Das laesst
        // sich nur aus dem VERGLEICH zweier Staende ablesen, nicht aus einem.
        viewModelScope.launch {
            var vorher = emptySet<String>()
            syncDao.observeOffeneNotizIds().collect { jetzt ->
                val fertig = vorher - jetzt.toSet()
                if (fertig.isNotEmpty()) geradeAngekommen.value = fertig
                vorher = jetzt.toSet()
            }
        }
    }

    /**
     * Nach der Animation EINER Karte. Sonst liefe sie beim naechsten Scrollen
     * erneut, sobald die Karte wieder ins Bild kommt.
     *
     * Nur die eigene Kennung, nicht alles: Sind zwei Notizen gleichzeitig oben
     * angekommen, wuerde die erste fertige Karte der zweiten sonst mitten in der
     * Animation den Zustand wegnehmen.
     */
    fun gesichertGezeigt(noteId: String) {
        if (speichermarke.notiz.value == noteId) speichermarke.quittieren()
        geradeAngekommen.update { it - noteId }
    }

    /**
     * Dasselbe ViewModel dient zwei Ansichten: einer Stufe und einem Ordner.
     *
     * **Woran es sie unterscheidet:** Die Stufenroute traegt `stage` als festen
     * Teil des Pfades, die Ordnerroute nicht. Fehlt das Argument, ist dies eine
     * Ordneransicht. Kein Merkwert, kein Schalter von aussen, nichts, was
     * auseinanderlaufen koennte.
     *
     * **Warum nicht ein zweites ViewModel:** An einer Notizkarte haengt mehr,
     * als man ihr ansieht: Auswahl, Wischgesten, Farben, Tags, Papierkorb mit
     * Rueckweg, Wellenformen, Abspielen und die drei Sicherungsanzeigen am
     * Rahmen. Das alles ein zweites Mal zu bauen hiesse, es ein zweites Mal
     * falsch zu machen. Verschieden ist nur, WELCHE Notizen in der Liste
     * stehen.
     */
    private val stufenArgument: String? = savedState["stage"]

    /**
     * Die dritte Ansicht: der Papierkorb (seit 2026-09-15).
     *
     * Die Papierkorbroute traegt ein festes Argument `papierkorb = true`. Dort
     * soll dieselbe Oberflaeche stehen wie im Eingang, mit Wischgesten,
     * statt einer eigenen Liste; also bedient dieses ViewModel auch den
     * Papierkorb, mit festen Gesten und ohne Sortierung.
     */
    val imPapierkorb: Boolean = savedState.get<Boolean>("papierkorb") == true

    /** Ob diese Ansicht einen Ordner zeigt statt einer Stufe. */
    val imOrdner: Boolean = stufenArgument == null && !imPapierkorb

    /**
     * Die vierte Ansicht: das Archiv des Ordnermodus (Phase 14b).
     *
     * Eine Ordneransicht wie jede andere, nur ueber dem Archivbaum. Die
     * Archivroute traegt das feste Argument `bereich = ARCHIV`; [imOrdner]
     * gilt hier weiter, denn an einer Karte haengt dort dasselbe.
     */
    val imArchiv: Boolean = imOrdner && savedState.get<String>("bereich") == Bereich.ARCHIV.name

    /** Der gezeigte Ordner. `null` ist der Hauptordner. */
    val ordnerId: String? = savedState["ordner"]

    /**
     * Eine Notiz, zu der die Liste scrollt und die kurz aufblitzt (Phase 14c).
     *
     * Kommt als Routenparameter `hervorheben` herein, etwa vom Sprung aus dem
     * Papierkorb. Nach dem Aufblitzen wird sie geloescht, sonst blitzte sie bei
     * jeder Drehung des Geraets wieder auf.
     */
    private val _hervorheben = MutableStateFlow<String?>(savedState["hervorheben"])
    val hervorheben: StateFlow<String?> = _hervorheben.asStateFlow()

    fun hervorhebungGezeigt() {
        _hervorheben.value = null
    }

    /**
     * Die Stufe der Ansicht.
     *
     * Im Ordnermodus steht hier der Eingang, und er wird nirgends gezeigt. Er
     * ist trotzdem nicht bedeutungslos: Neue Notizen bekommen ihn, damit sie
     * beim Zurueckschalten in den Fluss dort liegen, wo neue Notizen hingehoeren.
     */
    val stage: Stage = stufenArgument?.let { Stage.valueOf(it) } ?: Stage.INBOX

    /**
     * Sortierung und Darstellung kommen aus dem DataStore und werden PRO STUFE
     * gefuehrt. Sie duerfen nicht im ViewModel leben: beim Stufenwechsel
     * entsteht ein neues, und die Einstellung waere jedes Mal wieder auf
     * Standard -- genau der Fehler, der am Geraet aufgefallen ist.
     *
     * Die Ordneransicht fuehrt ihr eigenes Paar. Alle Ordner teilen es sich,
     * anders als die Stufen: Ein Ordner ist eine Sammlung wie die naechste, und
     * je Ordner gemerkte Ansichten waeren eine Einstellung, die man dreissigmal
     * setzt.
     */
    val sort: StateFlow<NoteSort> = sortierungsfluss()
        .stateIn(viewModelScope, SharingStarted.Eagerly, NoteSort.CREATED)

    val darstellung: StateFlow<Darstellung> = darstellungsfluss()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Darstellung.RASTER)

    private fun sortierungsfluss(): Flow<NoteSort> = when {
        // Der Papierkorb sortiert nach dem Wegwerfen, fest. Der Wert hier ist
        // nur ein Platzhalter fuer den Fluss.
        imPapierkorb -> flowOf(NoteSort.CREATED)
        imOrdner -> einstellungen.ordnerSortierung()
        else -> einstellungen.sortierung(stage)
    }

    // Der Papierkorb uebernimmt die Darstellung des Eingangs: Er soll aussehen
    // wie der Eingang, das war die Bestellung.
    private fun darstellungsfluss(): Flow<Darstellung> = when {
        imPapierkorb -> einstellungen.darstellung(Stage.INBOX)
        imOrdner -> einstellungen.ordnerDarstellung()
        else -> einstellungen.darstellung(stage)
    }

    /** Auswahl per Long-Press. Leer = keine Auswahl aktiv. */
    private val _auswahl = MutableStateFlow<Set<String>>(emptySet())
    val auswahl: StateFlow<Set<String>> = _auswahl.asStateFlow()

    /** Offener Titeldialog, oder null. */
    private val _titelAnfrage = MutableStateFlow<TitelAnfrage?>(null)
    val titelAnfrage: StateFlow<TitelAnfrage?> = _titelAnfrage.asStateFlow()

    private val undoKanal = Channel<UndoRequest>(Channel.BUFFERED)
    val undoEvents: Flow<UndoRequest> = undoKanal.receiveAsFlow()

    /** Ein Satz ohne Rueckweg, fuer die Snackbar: etwa die Ablehnung beim Verschieben ohne Titel. */
    private val hinweisKanal = Channel<String>(Channel.BUFFERED)
    val hinweisEvents: Flow<String> = hinweisKanal.receiveAsFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notizen: StateFlow<List<NoteWithRelations>> =
        sortierungsfluss()
            .flatMapLatest {
                when {
                    imPapierkorb -> notes.observeTrash()
                    imOrdner -> notes.observeImOrdner(ordnerId, it, archiviert = imArchiv)
                    else -> notes.observe(stage, it)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(neu: NoteSort) {
        viewModelScope.launch {
            if (imOrdner) {
                einstellungen.setOrdnerSortierung(neu)
            } else {
                einstellungen.setSortierung(stage, neu)
            }
        }
    }

    fun toggleDarstellung() {
        viewModelScope.launch {
            val jetzt = darstellungsfluss().first().umgeschaltet()
            if (imOrdner) {
                einstellungen.setOrdnerDarstellung(jetzt)
            } else {
                einstellungen.setDarstellung(stage, jetzt)
            }
        }
    }

    // ----------------------------------------------------------------- Auswahl

    fun toggleAuswahl(id: String) = _auswahl.update {
        if (id in it) it - id else it + id
    }

    fun auswahlAufheben() = _auswahl.update { emptySet() }

    /**
     * Im Papierkorb gelten eigene Gesten aus den Einstellungen: endgueltig
     * loeschen oder zurueckholen, beides rastet ein und verlangt den zweiten
     * Tipp. Die Gesten des Flusses gelten dort nicht; eine Notiz im Papierkorb
     * hat keine naechste Stufe.
     */
    val wischRechts: StateFlow<WischZiel> =
        (if (imPapierkorb) einstellungen.wischPapierkorbRechts() else einstellungen.wischRechts())
            .map { imOrdnerBrauchbar(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                if (imPapierkorb) WischZiel.ENDGUELTIG else WischZiel.NAECHSTE_STUFE,
            )

    val wischLinks: StateFlow<WischZiel> =
        (if (imPapierkorb) einstellungen.wischPapierkorbLinks() else einstellungen.wischLinks())
            .map { imOrdnerBrauchbar(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                if (imPapierkorb) WischZiel.WIEDERHERSTELLEN else WischZiel.VORHERIGE_STUFE,
            )

    /**
     * Im Ordner gibt es keine naechste Stufe.
     *
     * Die eingestellte Geste bleibt stehen, sie wirkt hier nur nicht. Eine
     * Karte, die sich wegziehen laesst und dann zurueckschnappt, saehe nach
     * einem Fehler aus, und ein Stufenwechsel in einer Ansicht ohne Stufen
     * waere eine Wirkung, die niemand sehen kann.
     */
    private fun imOrdnerBrauchbar(ziel: WischZiel): WischZiel = when {
        !imOrdner -> ziel
        ziel == WischZiel.PAPIERKORB -> ziel
        else -> WischZiel.NICHTS
    }

    /** Fuehrt aus, was fuer diese Wischrichtung eingestellt ist. */
    fun wischAktion(noteId: String, ziel: WischZiel) {
        when (ziel) {
            WischZiel.NAECHSTE_STUFE -> stage.next()?.let { verschiebeNach(setOf(noteId), it) }
            WischZiel.VORHERIGE_STUFE -> stage.previous()?.let { verschiebeNach(setOf(noteId), it) }
            WischZiel.PAPIERKORB -> inDenPapierkorb(setOf(noteId))
            WischZiel.NICHTS -> Unit
            WischZiel.WIEDERHERSTELLEN -> zurueckholen(noteId)
            WischZiel.ENDGUELTIG -> endgueltigLoeschen(noteId)
        }
    }

    /** Aus dem Papierkorb zurueck, mit Undo. */
    fun zurueckholen(noteId: String) {
        viewModelScope.launch {
            notes.restore(listOf(noteId))
            undoKanal.send(
                UndoRequest("Notiz wiederhergestellt") { notes.trash(listOf(noteId)) },
            )
        }
    }

    /**
     * Endgueltig. Bewusst KEIN Undo: Ab hier ist die Notiz weg, und ein
     * Grabstein sorgt dafuer, dass das andere Geraet sie nicht wiederbelebt.
     * Die zweite Handlung (Wischen, dann Tippen) ist die Bestaetigung.
     */
    fun endgueltigLoeschen(noteId: String) {
        viewModelScope.launch { notes.purge(listOf(noteId)) }
    }

    /**
     * Setzt oder loescht den Stern fuer die Auswahl.
     *
     * Gemischte Auswahl wird zu ALLEN Favoriten, nicht umgekehrt: wer mehrere
     * Karten anfasst und den Stern drueckt, will sie merken. Aus Versehen
     * bestehende Favoriten zu verlieren waere der teurere Fehler.
     */
    fun toggleFavorit(ids: Set<String>) {
        if (ids.isEmpty()) return
        val betroffen = notizen.value.filter { it.note.id in ids }
        val alleSchon = betroffen.isNotEmpty() && betroffen.all { it.note.isFavorite }
        viewModelScope.launch {
            notes.setFavorite(ids.toList(), !alleSchon)
            _auswahl.value = emptySet()
        }
    }

    /** Ob JEDE Notiz der Auswahl schon ein Favorit ist. Steuert nur das Symbol. */
    fun auswahlIstFavorit(ids: Set<String>): Boolean {
        val betroffen = notizen.value.filter { it.note.id in ids }
        return betroffen.isNotEmpty() && betroffen.all { it.note.isFavorite }
    }

    // --------------------------------------------------------------- Anhoeren

    val abspielstatus: StateFlow<Abspielstatus> = wiedergabe.status

    /**
     * Die Wellenformen der sichtbaren Audionotizen.
     *
     * Berechnet wird jede genau einmal und dann gemerkt -- das Lesen der Datei
     * darf nicht bei jedem Neuzeichnen passieren, und schon gar nicht auf dem
     * Bildschirm-Thread.
     */
    private val _wellenformen = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val wellenformen: StateFlow<Map<String, FloatArray>> = _wellenformen.asStateFlow()

    fun wellenformLaden(notizId: String, datei: java.io.File) {
        if (_wellenformen.value.containsKey(notizId)) return
        viewModelScope.launch {
            val werte = wellenformdaten.fuer(datei)
            if (werte.isNotEmpty()) {
                _wellenformen.update { it + (notizId to werte) }
            }
        }
    }

    fun abspielen(notizId: String, datei: java.io.File) = wiedergabe.umschalten(notizId, datei)

    /** Die gerade ausgewaehlten Notizen -- fuer das Teilen. */
    fun gewaehlteNotizen(): List<NoteWithRelations> =
        notizen.value.filter { it.note.id in _auswahl.value }

    // ------------------------------------------------- Farbe und Tags

    /** Welches Blatt gerade offen ist. */
    private val _farbBlatt = MutableStateFlow(false)
    val farbBlattOffen: StateFlow<Boolean> = _farbBlatt.asStateFlow()

    private val _tagDialog = MutableStateFlow(false)
    val tagDialogOffen: StateFlow<Boolean> = _tagDialog.asStateFlow()

    fun oeffneFarbAuswahl() = _farbBlatt.update { true }

    fun schliesseFarbAuswahl() = _farbBlatt.update { false }

    fun oeffneTagAuswahl() = _tagDialog.update { true }

    fun schliesseTagAuswahl() = _tagDialog.update { false }

    /**
     * Die gemeinsame Farbe der Auswahl, oder null bei gemischter Auswahl.
     * Null heisst fuer die Oberflaeche: kein Haken setzen.
     */
    fun gemeinsameFarbe(): NoteColor? {
        val gewaehlt = notizen.value.filter { it.note.id in _auswahl.value }
        val farben = gewaehlt.map { it.note.colorId }.distinct()
        return farben.singleOrNull()
    }

    /** Tags, die ALLE gewaehlten Notizen tragen. */
    fun gemeinsameTags(): Set<String> {
        val gewaehlt = notizen.value.filter { it.note.id in _auswahl.value }
        if (gewaehlt.isEmpty()) return emptySet()
        return gewaehlt
            .map { n -> n.tags.map { it.id }.toSet() }
            .reduce { a, b -> a intersect b }
    }

    /**
     * Faerbt die Auswahl um. Ein Farbwechsel ist eine normale Aenderung:
     * Autosave, Sync, Undo-Snackbar (Spezifikation Abschnitt 5a).
     */
    fun setFarbe(farbe: NoteColor) {
        val ids = _auswahl.value
        if (ids.isEmpty()) return
        val liste = ids.toList()

        viewModelScope.launch {
            // Vorher merken, damit das Undo JEDE Notiz auf ihre eigene alte
            // Farbe zuruecksetzt -- nicht pauschal auf Standard.
            val vorher = liste.mapNotNull { id -> notes.get(id)?.let { id to it.note.colorId } }

            notes.setColor(liste, farbe)
            _farbBlatt.value = false
            auswahlAufheben()

            undoKanal.send(
                UndoRequest(
                    message = if (liste.size == 1) {
                        "Farbe geändert"
                    } else {
                        "${liste.size} Notizen eingefärbt"
                    },
                    undo = {
                        vorher.groupBy({ it.second }, { it.first })
                            .forEach { (alteFarbe, betroffene) ->
                                notes.setColor(betroffene, alteFarbe)
                            }
                    },
                ),
            )
        }
    }

    /**
     * Haengt einen Tag an alle gewaehlten Notizen oder nimmt ihn allen weg --
     * je nachdem, ob ihn schon alle tragen.
     *
     * Das Blatt bleibt dabei offen: man vergibt selten genau einen Tag.
     */
    fun tagUmschalten(tagId: String) {
        val ids = _auswahl.value
        if (ids.isEmpty()) return
        val entfernen = tagId in gemeinsameTags()

        viewModelScope.launch {
            ids.forEach { id ->
                val n = notes.get(id) ?: return@forEach
                val jetzt = n.tags.sortedBy { t -> n.tags.indexOf(t) }.map { it.id }
                val neu = if (entfernen) jetzt - tagId else if (tagId in jetzt) jetzt else jetzt + tagId
                if (neu != jetzt) notes.setTags(id, neu)
            }
        }
    }

    // ------------------------------------------------------------- Ordnerwahl

    /** Ob die Ordnerauswahl fuer die Mehrfachauswahl offen ist. */
    private val _ordnerBlatt = MutableStateFlow(false)
    val ordnerBlattOffen: StateFlow<Boolean> = _ordnerBlatt.asStateFlow()

    fun oeffneOrdnerAuswahl() = _ordnerBlatt.update { true }

    fun schliesseOrdnerAuswahl() = _ordnerBlatt.update { false }

    /**
     * Legt die Auswahl in einen Ordner. `null` nimmt sie aus jedem heraus.
     *
     * Das Undo setzt JEDE Notiz auf ihren eigenen alten Ordner zurueck, nicht
     * alle pauschal in den Hauptordner. Sonst waere das Zuruecknehmen einer
     * Verschiebung selbst eine Verschiebung, und man haette hinterher mehr
     * aufzuraeumen als vorher. Dieselbe Regel wie beim Umfaerben.
     */
    fun setOrdner(zielId: String?, zielname: String) {
        val ids = _auswahl.value
        if (ids.isEmpty()) return
        val liste = ids.toList()

        viewModelScope.launch {
            val vorher = liste.mapNotNull { id -> notes.get(id)?.let { id to it.note.folderId } }

            notes.setOrdner(liste, zielId)
            _ordnerBlatt.value = false
            auswahlAufheben()

            undoKanal.send(
                UndoRequest(
                    message = if (liste.size == 1) {
                        "Notiz nach $zielname gelegt"
                    } else {
                        "${liste.size} Notizen nach $zielname gelegt"
                    },
                    undo = {
                        vorher.groupBy({ it.second }, { it.first })
                            .forEach { (alterOrdner, betroffene) ->
                                notes.setOrdner(betroffene, alterOrdner)
                            }
                    },
                ),
            )
        }
    }

    // -------------------------------------------- Archiv im Ordnermodus (14b)

    /**
     * Archiviert die Auswahl im Ordnersystem. Die Notizen landen oben im
     * Archiv und merken sich ihre Herkunft. Die Stufe bleibt unberuehrt.
     *
     * Das Undo bringt jede Notiz dorthin zurueck, woher sie kam; das ist das
     * Zuruecknehmen der Handlung, nicht das Zurueckholen von Hand, das immer
     * eine Wahl ist.
     */
    fun imOrdnerArchivieren(ids: Set<String>) {
        if (ids.isEmpty()) return
        val liste = ids.toList()
        viewModelScope.launch {
            notes.imOrdnerArchivieren(liste)
            auswahlAufheben()
            undoKanal.send(
                UndoRequest(
                    message = if (liste.size == 1) "Notiz archiviert" else "${liste.size} Notizen archiviert",
                    undo = { notes.archivierungZuruecknehmen(liste) },
                ),
            )
        }
    }

    /** Die offene Frage beim Zurueckholen, oder null. */
    private val _rueckholAnfrage = MutableStateFlow<RueckholAnfrage?>(null)
    val rueckholAnfrage: StateFlow<RueckholAnfrage?> = _rueckholAnfrage.asStateFlow()

    /**
     * Beginnt das Zurueckholen: rechnet den Vorschlag aus und oeffnet die Frage.
     *
     * Einen Vorschlag gibt es nur, wenn alle gewaehlten Notizen aus demselben
     * Ordner kamen. Bei gemischter Herkunft bleibt die freie Wahl; drei
     * verschiedene „Zurueck nach" auf einmal waeren keine Frage mehr.
     */
    fun zurueckholenAnfragen(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { _rueckholAnfrage.value = rueckholAnfrageFuer(ids.toList(), notes, ordner) }
    }

    fun zurueckholenAbgebrochen() {
        _rueckholAnfrage.value = null
    }

    /**
     * Holt die angefragten Notizen nach [zielId] zurueck (`null` = Hauptordner).
     *
     * Das Undo stellt je Notiz den Archivstand von vorher wieder her: den
     * Archivordner, in dem sie lag, und ihre Herkunft. Ein Undo, das sie bloss
     * wieder archivierte, wuerfe beides weg.
     */
    fun zurueckholenNach(zielId: String?, zielname: String) {
        val anfrage = _rueckholAnfrage.value ?: return
        _rueckholAnfrage.value = null
        viewModelScope.launch {
            val vorher = anfrage.ids.mapNotNull { id -> notes.get(id)?.note }
            notes.ausOrdnerArchivZurueck(anfrage.ids, zielId)
            auswahlAufheben()
            undoKanal.send(
                UndoRequest(
                    message = if (anfrage.ids.size == 1) {
                        "Notiz nach $zielname zurückgeholt"
                    } else {
                        "${anfrage.ids.size} Notizen nach $zielname zurückgeholt"
                    },
                    undo = {
                        vorher.forEach {
                            notes.archivstandSetzen(
                                it.id, it.folderId, it.herkunftOrdnerId, it.ordnerArchiviertAt ?: 0L,
                            )
                        }
                    },
                ),
            )
        }
    }

    /**
     * Der dritte Weg der Dreifach-Frage: die geloeschte Ordnerkette zurueck
     * aus dem Papierkorb holen (ohne ihre Notizen) und dann dorthin.
     */
    fun herkunftsordnerNeuAnlegen(name: String) {
        val anfrage = _rueckholAnfrage.value ?: return
        val herkunft = anfrage.herkunftId ?: return
        viewModelScope.launch {
            ordner.wiederherstellen(herkunft, mitNotizen = false)
            zurueckholenNach(herkunft, name)
        }
    }

    /** Der normale Ordnerbaum, fuer die freie Wahl beim Zurueckholen. */
    suspend fun normalerBaum(): List<Ordnerzeile> =
        baum(ordner.getAll(Bereich.ORDNER), einstellungen.ordnerReihenfolge().first())

    // ----------------------------------------------------------------- Aktionen

    /**
     * Legt eine Notiz an und gibt ihre ID an [dann] weiter, damit der Aufrufer
     * direkt in den Editor springen kann. Neue Notizen landen immer in INBOX --
     * auch wenn man sie vom Workspace aus anlegt (Spezifikation Abschnitt 14).
     */
    fun neueNotiz(type: NoteType, dann: (String) -> Unit) {
        // Im Ordner entsteht sie GLEICH IM OFFENEN ORDNER. Alles andere waere
        // die falsche Antwort auf "neue Notiz, waehrend ich hier stehe": Man
        // muesste sie hinterher von Hand dorthin legen, wo man ohnehin war.
        //
        // Im Archiv dagegen im Hauptordner: Eine neue Notiz ist Arbeit, kein
        // Abgelegtes, und ein Archivordner als Ziel ergaebe eine Notiz, die
        // in keinem der beiden Baeume zu sehen waere. Dieselbe Regel wie im
        // Archiv des Flusses, wo Neues im Eingang landet.
        viewModelScope.launch { dann(notes.create(type, ordnerId.takeIf { imOrdner && !imArchiv })) }
    }

    /**
     * In den Papierkorb. Loeschabfragen gibt es nicht -- der Rueckweg ist die
     * Snackbar.
     */
    fun inDenPapierkorb(ids: Set<String>) {
        if (ids.isEmpty()) return
        val liste = ids.toList()
        viewModelScope.launch {
            notes.trash(liste)
            auswahlAufheben()
            undoKanal.send(
                UndoRequest(
                    message = if (liste.size == 1) {
                        "Notiz in den Papierkorb verschoben"
                    } else {
                        "${liste.size} Notizen in den Papierkorb verschoben"
                    },
                    undo = { notes.restore(liste) },
                ),
            )
        }
    }

    /**
     * Stufenwechsel, inklusive Titelpflicht.
     *
     * Ab WORKSPACE braucht eine Notiz einen Titel (Spezifikation Abschnitt 4).
     * **Seit Phase 15 strenger** (Nutzer, 2026-08-21 und 2026-09-14): Ohne
     * Titel kein Verschieben, Punkt. Kein stilles Auffuellen mehr.
     *
     *  - keine ohne Titel  -> direkt verschieben, auch ueber eine Stufe hinweg
     *  - genau eine        -> Dialog als Angebot, einen Titel zu vergeben; wer
     *                         abbricht, verschiebt nicht
     *  - mehrere           -> abgelehnt, mit einem Satz. N Dialoge hintereinander
     *                         waeren keine Bedienung, und stille Titel wollte der
     *                         Nutzer ausdruecklich nicht
     *
     * Zurueck nach INBOX verlangt nie einen Titel. Ist die Titelpflicht in den
     * Einstellungen abgeschaltet, wird immer direkt verschoben.
     */
    fun verschiebeNach(ids: Set<String>, ziel: Stage) {
        if (ids.isEmpty()) return
        val liste = ids.toList()

        viewModelScope.launch {
            if (!ziel.requiresTitle() || !einstellungen.titelpflicht().first()) {
                fuehreAus(liste, ziel, emptyMap())
                return@launch
            }

            val ohneTitel = liste.mapNotNull { notes.get(it) }.filter { it.note.title.isBlank() }

            when (ohneTitel.size) {
                0 -> fuehreAus(liste, ziel, emptyMap())

                1 -> {
                    val n = ohneTitel.single()
                    val quelle = quelltext(n)
                    _titelAnfrage.value = TitelAnfrage(
                        noteId = n.note.id,
                        alleIds = liste,
                        ziel = ziel,
                        fallback = fallbackTitel(n.note.body, n.orderedItems.map { it.text })
                            .ifBlank { "Ohne Titel" },
                        quelle = quelle,
                    )
                }

                else -> hinweisKanal.send(
                    "${ohneTitel.size} der gewählten Notizen haben keinen Titel. " +
                        "Gib ihnen erst einen, dann lassen sie sich verschieben.",
                )
            }
        }
    }

    /** Der Nutzer hat im Titeldialog bestaetigt. */
    fun titelBestaetigt(titel: String) {
        val anfrage = _titelAnfrage.value ?: return
        _titelAnfrage.value = null
        viewModelScope.launch {
            fuehreAus(anfrage.alleIds, anfrage.ziel, mapOf(anfrage.noteId to titel))
        }
    }

    /** Abgebrochen: nichts wird verschoben, die Auswahl bleibt bestehen. */
    fun titelAbgebrochen() {
        _titelAnfrage.value = null
    }

    /**
     * Verschiebt und traegt dabei die noetigen Titel nach.
     *
     * Das Undo nimmt BEIDES zurueck. Ein Undo, das die Notiz zurueckschiebt,
     * ihr aber den unterwegs erfundenen Titel laesst, waere nur ein halbes
     * Undo -- und der Nutzer haette keinen Weg mehr zum leeren Titel zurueck.
     */
    private suspend fun fuehreAus(ids: List<String>, ziel: Stage, neueTitel: Map<String, String>) {
        val herkunft = stage
        neueTitel.forEach { (id, titel) -> notes.setTitle(id, titel) }
        notes.moveTo(ids, ziel)
        auswahlAufheben()

        undoKanal.send(
            UndoRequest(
                message = meldung(ids.size, ziel, neueTitel.size),
                undo = {
                    notes.moveTo(ids, herkunft)
                    neueTitel.keys.forEach { notes.setTitle(it, "") }
                },
            ),
        )
    }

    private fun meldung(anzahl: Int, ziel: Stage, ergaenzteTitel: Int): String {
        val kern = if (anzahl == 1) {
            "Verschoben nach ${zielName(ziel)}"
        } else {
            "$anzahl Notizen nach ${zielName(ziel)} verschoben"
        }
        return when {
            ergaenzteTitel > 1 -> "$kern, $ergaenzteTitel Titel ergänzt"
            else -> kern
        }
    }

    /** Was die KI zu sehen bekommt: Body oder, bei Listen, die Eintraege. */
    private fun quelltext(n: NoteWithRelations): String {
        val eintraege = n.orderedItems.filter { it.text.isNotBlank() }
        return if (eintraege.isNotEmpty()) {
            eintraege.joinToString("\n") { it.text }
        } else {
            n.note.body
        }
    }

    private fun zielName(stage: Stage) = when (stage) {
        Stage.INBOX -> "Eingang"
        Stage.WORKSPACE -> "Workspace"
        Stage.ARCHIVE -> "Archiv"
    }
}
