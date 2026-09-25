package de.notizen.app.ui.editor

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.app.ai.Aufbereitung
import de.notizen.app.ai.Aufraeumen
import de.notizen.app.ai.fallbackTitel
import de.notizen.app.audio.Abspielstatus
import de.notizen.app.audio.Pausenschnitt
import de.notizen.app.audio.Sprechabschnitt
import de.notizen.app.audio.dauerVon
import de.notizen.app.audio.pcmPegel
import de.notizen.app.audio.AufnahmeSitzung
import de.notizen.app.audio.Wellenformdaten
import de.notizen.app.audio.aufnahmeDatei
import de.notizen.app.audio.Wiedergabe
import de.notizen.app.audio.Modellzustand
import de.notizen.app.audio.Transkription
import de.notizen.app.erinnerung.ErinnerungPlaner
import de.notizen.app.sync.Bearbeitung
import de.notizen.app.sync.Speichermarke
import de.notizen.app.sync.Syncwaechter
import de.notizen.app.ui.ordner.RueckholAnfrage
import de.notizen.app.ui.ordner.rueckholAnfrageFuer
import de.notizen.app.uebersetzung.Uebersetzung
import de.notizen.app.uebersetzung.Uebersetzungsergebnis
import de.notizen.app.ui.util.teileNotizen
import de.notizen.app.bild.Bildablage
import de.notizen.app.bild.Bildspeicher
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.AudioRepository
import de.notizen.core.data.repository.BildRepository
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.ReminderRepository
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditorState(
    val geladen: Boolean = false,
    val type: NoteType = NoteType.TEXT,
    val stage: Stage = Stage.INBOX,
    val colorId: NoteColor = NoteColor.DEFAULT,
    val title: String = "",
    val body: String = "",
    val items: List<NoteItemEntity> = emptyList(),
    val istFavorit: Boolean = false,
    /** Zeitpunkt der Erinnerung, oder `null`. Eine Notiz trägt höchstens eine. */
    val erinnerungAn: Long? = null,
    /**
     * Bilder der Notiz, in Anlagereihenfolge.
     *
     * Nur solche mit der Rolle `INHALT` — ein eigens gewähltes Hintergrundbild
     * steht hier ausdrücklich **nicht** drin, sonst wäre es doch wieder ein
     * Bild der Notiz. Audioanhänge fehlen ebenfalls.
     */
    val bilder: List<AttachmentEntity> = emptyList(),
    val hintergrundId: String? = null,
    /**
     * Das Bild, das als Fläche dient — oder `null`.
     *
     * Eigenes Feld und nicht aus [bilder] abgeleitet: Die Fläche kann ein Bild
     * der Notiz sein **oder** ein eigens gewähltes, das im Raster gar nicht
     * auftaucht. Zeigt der Verweis auf einen Anhang, den es nicht mehr gibt,
     * bleibt das Feld `null` und die Notiz fällt still auf ihre Farbe zurück —
     * genau so steht es in SYNC.md 14.15.
     */
    val hintergrund: AttachmentEntity? = null,
    /**
     * Ob diese Notiz in die Cloud darf.
     *
     * Rein lokal. Steht hier im Zustand, weil das Menü den Schalter zeigt und
     * ein Schalter, der seinen eigenen Stand nicht kennt, geraten ist.
     */
    val abgleichAn: Boolean = true,
    val kalenderAn: Boolean = true,

    /**
     * Der Ordner, in dem diese Notiz liegt, oder `null` fuer den Hauptordner.
     *
     * Anders als [abgleichAn] und [kalenderAn] geht dieses Feld mit nach Drive.
     * Es steht hier trotzdem aus demselben Grund: Das Menue zeigt den Ordner
     * an, und eine Anzeige, die ihren eigenen Stand nicht kennt, ist geraten.
     */
    val ordnerId: String? = null,

    /** Ob die Notiz im Archiv des Ordnermodus liegt (Phase 14b). Steuert das Menue. */
    val imOrdnerArchiv: Boolean = false,
) {
    /**
     * Der Titel fehlt, obwohl die Stufe ihn verlangt.
     *
     * Das ist ein HINWEIS, keine Sperre: geschrieben werden darf trotzdem, und
     * beim Verlassen traegt der Editor selbst einen abgeleiteten Titel nach.
     */
    val titelFehlt: Boolean get() = geladen && stage.requiresTitle() && title.isBlank()

    /** Was ein Titelvorschlag zu lesen bekommt. */
    val titelQuelle: String
        get() = items.filter { it.text.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.text }
            ?: body
}

/**
 * Editor ohne Speichern-Knopf (Spezifikation Abschnitt 9).
 *
 * DREI REGELN, die zusammengehoeren:
 *
 *  1. Getippt wird in den Entwurf. Nach [AUTOSAVE_DEBOUNCE] ohne weitere
 *     Eingabe wandert er nach Room.
 *  2. Beim Verlassen, bei onPause und beim Backgrounding wird HART gespeichert
 *     -- ohne auf den Debounce zu warten. Sonst verliert ein Wegwischen der
 *     App die letzten Sekunden.
 *  3. Wer eine leere Notiz verlaesst, wollte sie nicht: sie wird geloescht,
 *     nicht gespeichert.
 *
 * Dass wiederholtes Speichern desselben Inhalts `updatedAt` NICHT hochzaehlt,
 * stellt das Repository sicher -- der Autosave feuert oft, auch wenn sich
 * nichts geaendert hat.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val erinnerungen: ReminderRepository,
    private val planer: ErinnerungPlaner,
    private val transkription: Transkription,
    private val aufraeumen: Aufraeumen,
    private val wiedergabe: Wiedergabe,
    private val wellenformdaten: Wellenformdaten,
    private val einstellungen: Einstellungen,
    private val audio: AudioRepository,
    private val bilder: BildRepository,
    private val bildspeicher: Bildspeicher,
    private val tags: TagRepository,
    private val ordner: FolderRepository,
    private val speichermarke: Speichermarke,
    private val syncwaechter: Syncwaechter,
    private val bearbeitung: Bearbeitung,
    @param:ApplicationContext private val anwendung: Context,
    val aufnahme: AufnahmeSitzung,
    private val uebersetzung: Uebersetzung,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Oeffentlich, weil der Aufnahmedienst von aussen gestartet wird. */
    val noteId: String = requireNotNull(savedState.get<String>("noteId"))

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var speicherJob: Job? = null

    init {
        // Ab jetzt geht nichts von dieser Notiz nach Drive, bis der Editor zu
        // ist. Sonst laedt jede Denkpause beim Tippen die ganze Notiz hoch.
        bearbeitung.begonnen()

        viewModelScope.launch {
            val n = notes.get(noteId)
            if (n != null) {
                _state.value = EditorState(
                    geladen = true,
                    type = n.note.type,
                    stage = n.note.stage,
                    colorId = n.note.colorId,
                    title = n.note.title,
                    body = n.note.body,
                    items = n.orderedItems,
                    istFavorit = n.note.isFavorite,
                    erinnerungAn = erinnerungen.zuNotiz(noteId)
                        .firstOrNull { !it.isFired }?.triggerAt,
                    bilder = nurBilder(n),
                    hintergrundId = n.note.backgroundAttachmentId,
                    hintergrund = hintergrundVon(n),
                    abgleichAn = n.note.syncEnabled,
                    kalenderAn = n.note.calendarEnabled,
                    ordnerId = n.note.folderId,
                    imOrdnerArchiv = n.note.ordnerArchiviertAt != null,
                )
                _meineTags.value = n.tags.map { it.id }
                // Oeffnen setzt lastOpenedAt -- und NUR das.
                notes.open(noteId)
            }
        }
    }

    /**
     * Meldet, dass das System exakte Alarme verweigert.
     *
     * Ein eigener Zustand und keine Snackbar: die Erinnerung ist gespeichert,
     * klingelt aber nicht, und das ist zu wichtig, um nach drei Sekunden
     * wegzurutschen. Die Oberfläche zeigt dafür einen Dialog mit dem Weg in die
     * Systemeinstellung.
     */
    private val _erlaubnisFehlt = MutableStateFlow(false)
    val erlaubnisFehlt: StateFlow<Boolean> = _erlaubnisFehlt.asStateFlow()

    fun erlaubnisHinweisSchliessen() {
        _erlaubnisFehlt.value = false
    }

    fun einstellungenFuerAlarme(): Intent = planer.einstellungen()

    // -------------------------------------------------------------- Aufnahme

    private val _modell = MutableStateFlow<Modellzustand>(Modellzustand.Ladbar)
    val modell: StateFlow<Modellzustand> = _modell.asStateFlow()

    /**
     * Ob die App gerade im Ordnermodus laeuft.
     *
     * Steuert nur, ob das Menue den Ordnereintrag zeigt. Im Fluss gibt es keine
     * Ordner zu sehen, und eine Notiz dort einzusortieren waere eine Wirkung
     * ohne sichtbares Ergebnis.
     */
    val ordnermodus: StateFlow<Boolean> = einstellungen.ordnermodus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Der Schalter aus den Einstellungen, ohne das Geraet zu fragen.
     *
     * **Er deckt seit dem 2026-08-25 auch die Umwandlung in Text ab.** Vorher
     * galt: Spracherkennung ist keine Textgenerierung, also laeuft sie weiter,
     * wenn jemand die KI abschaltet. Der bessere Grund spricht dagegen: Wer
     * die KI ausschaltet, will nichts von ihr, und dass die Erkennung
     * technisch etwas anderes ist als ein Sprachmodell, ist nicht seine Sache.
     *
     * Die AUFNAHME bleibt davon unberuehrt. Sie haengt an nichts und ist der
     * Schritt, der sich nicht wiederholen laesst.
     */
    val kiSchalter: StateFlow<Boolean> = einstellungen.kiAktiv()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _kiVerfuegbar = MutableStateFlow(false)
    val kiVerfuegbar: StateFlow<Boolean> = _kiVerfuegbar.asStateFlow()

    private val _bereitetAuf = MutableStateFlow(false)
    val bereitetAuf: StateFlow<Boolean> = _bereitetAuf.asStateFlow()

    /**
     * Prüft das Sprachmodell, BEVOR die Aufnahme-UI erscheint (Abschnitt 12).
     *
     * Ein Aufnahmeknopf, der erst nach dem Drücken zugibt, dass das Modell
     * fehlt, ist schlechter als einer, der von vornherein sagt, was zu tun ist.
     */
    fun modellPruefen() {
        viewModelScope.launch {
            // Der Schalter aus den Einstellungen zaehlt ZUERST: Ist die KI aus
            // (oder fehlt die Zustimmung, seit Alpha 9), wird ML Kit gar nicht
            // erst gefragt, auch nicht nach dem Sprachmodell. Die Knoepfe
            // erscheinen dann nicht, statt dazustehen und nichts zu tun.
            if (!einstellungen.kiAktiv().first()) {
                _kiVerfuegbar.value = false
                return@launch
            }
            _modell.value = transkription.zustand().first
            _kiVerfuegbar.value = aufraeumen.verfuegbar()
        }
    }

    fun modellLaden() {
        viewModelScope.launch {
            val modus = transkription.zustand().second
            transkription.laden(modus).collect { _modell.value = it }
        }
    }

    /**
     * Übernimmt das fertige Transkript in den Text der Notiz.
     *
     * Angehängt statt ersetzt: Wer vor der Aufnahme schon etwas getippt hat,
     * hat es nicht umsonst getan.
     */
    fun transkriptUebernehmen() {
        val neu = aufnahme.transkript.value.text
        if (neu.isBlank()) return

        _state.update { alt ->
            alt.copy(body = if (alt.body.isBlank()) neu else alt.body.trimEnd() + "\n\n" + neu)
        }
        speichereJetzt()
        transkriptLaden()
    }

    /** Ob zu dieser Notiz eine Aufnahme auf dem Gerät liegt. */
    private val _hatAufnahme = MutableStateFlow(false)
    val hatAufnahme: StateFlow<Boolean> = _hatAufnahme.asStateFlow()

    /**
     * Die Dauer der Aufnahme — aus der Datei, nicht vom Abspieler.
     *
     * Der Abspieler kennt sie erst, wenn er läuft. Alles, was auf der Dauer
     * aufbaut (welche Stelle in eine Pause fällt, wie viel das Überspringen
     * spart), rechnete vorher bis zum ersten Abspielen mit null.
     */
    private val _aufnahmedauer = MutableStateFlow(0L)
    val aufnahmedauer: StateFlow<Long> = _aufnahmedauer.asStateFlow()

    /** Die Wellenform der Aufnahme, für die Tonspur. */
    private val _wellenform = MutableStateFlow(FloatArray(0))
    val wellenform: StateFlow<FloatArray> = _wellenform.asStateFlow()

    val abspielstatus: StateFlow<Abspielstatus> = wiedergabe.status

    /**
     * Sieht nach, ob eine Aufnahme da ist, und holt ihre Wellenform.
     *
     * Nach einer neuen Aufnahme wird die alte Wellenform ausdrücklich vergessen
     * -- sonst zeigte die Spur weiter das Bild der vorherigen Aufnahme, und man
     * käme nie darauf, warum sie nicht zum Ton passt.
     */
    fun aufnahmePruefen(datei: java.io.File, neuAufgenommen: Boolean = false) {
        viewModelScope.launch {
            if (neuAufgenommen) wellenformdaten.vergessen(datei)
            _hatAufnahme.value = datei.exists() && datei.length() > 0
            _aufnahmedauer.value = dauerVon(datei)
            _wellenform.value = wellenformdaten.fuer(datei)
            // Dieselben Abschnitte, aus denen auch das Transkript entsteht --
            // was man hoert, ist genau das, was im Text steht.
            _sprechabschnitte.value = if (_hatAufnahme.value) {
                withContext(Dispatchers.IO) {
                    runCatching { Pausenschnitt.zerlegen(pcmPegel(datei)) }.getOrDefault(emptyList())
                }
            } else {
                emptyList()
            }
            if (_stilleUeberspringen.value) {
                wiedergabe.setzeUeberspringen(_sprechabschnitte.value)
            }
            transkriptLaden()
        }
    }

    fun abspielen(datei: java.io.File) = wiedergabe.umschalten(noteId, datei)

    fun springeZu(anteil: Float) = wiedergabe.springeZu(anteil)

    fun springenUm(sekunden: Int) = wiedergabe.springenUm(sekunden)

    /**
     * Ein Titelvorschlag aus dem Transkript.
     *
     * Der Titel ist überall derselbe -- auf der Karte, in der Suche, im
     * Editor. Eine Audionotiz ohne Titel ist in einer Liste nicht von der
     * nächsten zu unterscheiden, deshalb ist das Vorschlagen nach dem
     * Transkribieren der naheliegende nächste Schritt.
     */
    val titelQuelleAusTranskript: String
        get() = aufnahme.transkript.value.text.ifBlank { _state.value.body }

    /**
     * Das Rohtranskript, so wie es aus der Aufnahme kam.
     *
     * Aus der Datenbank und nicht aus der Sitzung: Nach dem Neustart der App
     * ist die Sitzung leer, das Transkript aber noch da. Es ist ein Beleg und
     * kein Zwischenstand.
     */
    private val _rohtranskript = MutableStateFlow("")
    val rohtranskript: StateFlow<String> = _rohtranskript.asStateFlow()

    /** Die Sprechabschnitte der Aufnahme — für das Überspringen der Stille. */
    private val _sprechabschnitte = MutableStateFlow<List<Sprechabschnitt>>(emptyList())
    val sprechabschnitte: StateFlow<List<Sprechabschnitt>> = _sprechabschnitte.asStateFlow()

    private val _stilleUeberspringen = MutableStateFlow(false)
    val stilleUeberspringen: StateFlow<Boolean> = _stilleUeberspringen.asStateFlow()

    fun stilleUeberspringenUmschalten() {
        val neu = !_stilleUeberspringen.value
        _stilleUeberspringen.value = neu
        wiedergabe.setzeUeberspringen(if (neu) _sprechabschnitte.value else emptyList())
    }

    /**
     * Verwirft die Bearbeitung und stellt das Rohtranskript wieder her.
     *
     * Ohne diesen Rückweg wäre jede Aufbereitung endgültig — und dann traut
     * man sich nicht, sie überhaupt auszuprobieren.
     */
    fun aufOriginalZuruecksetzen() {
        val roh = _rohtranskript.value
        if (roh.isBlank()) return
        _state.update { it.copy(body = roh) }
        speichereJetzt()
    }

    private fun transkriptLaden() {
        viewModelScope.launch {
            _rohtranskript.value = audio.transkript(noteId)
                .sortedBy { it.startMs }
                .joinToString(" ") { it.text }
                .trim()
        }
    }

    /**
     * Lässt die KI über das Rohtranskript gehen.
     *
     * Das Ergebnis ersetzt den Fließtext, **nicht** das Transkript: das Original
     * bleibt in `transcripts` stehen. Misslingt es, bleibt alles, wie es war,
     * und die Oberfläche sagt es.
     */
    fun aufbereiten(art: Aufbereitung) {
        val roh = _state.value.body.ifBlank { aufnahme.transkript.value.text }
        if (roh.isBlank()) return

        _bereitetAuf.value = true
        viewModelScope.launch {
            val ergebnis = aufraeumen.aufbereiten(roh, art)
            _bereitetAuf.value = false
            if (ergebnis == null) {
                _hinweis.value = "Das Aufbereiten hat nicht geklappt. Der Text bleibt, wie er ist."
                return@launch
            }
            _state.update { it.copy(body = ergebnis) }
            speichereJetzt()
        }
    }

    private val _hinweis = MutableStateFlow<String?>(null)
    val hinweis: StateFlow<String?> = _hinweis.asStateFlow()

    fun hinweisGelesen() {
        _hinweis.value = null
    }

    // ------------------------------------------------------ Stern und Wecker

    fun toggleFavorit() {
        val neu = !_state.value.istFavorit
        _state.update { it.copy(istFavorit = neu) }
        viewModelScope.launch { notes.setFavorite(listOf(noteId), neu) }
    }

    /**
     * Setzt die Erinnerung.
     *
     * Vorher wird HART gespeichert: der Wecker bekommt den Titel mit, und ohne
     * das stünde in der Benachrichtigung der Titel von vor dem letzten Tippen.
     */
    fun setzeErinnerung(zeitpunkt: Long) {
        speichereJetzt()
        viewModelScope.launch {
            val erinnerung = erinnerungen.setzen(noteId, zeitpunkt)
            _state.update { it.copy(erinnerungAn = zeitpunkt) }
            if (!planer.stellen(erinnerung, _state.value.title)) {
                _erlaubnisFehlt.value = true
            }
        }
    }

    fun entferneErinnerung() {
        viewModelScope.launch {
            erinnerungen.entfernen(noteId).forEach { planer.stornieren(it) }
            _state.update { it.copy(erinnerungAn = null) }
        }
    }

    /**
     * Stellt einen Wecker nach, für den beim Setzen die Erlaubnis fehlte.
     *
     * Wird beim Zurückkehren aus den Systemeinstellungen gerufen. Ohne das
     * bliebe die Erinnerung gespeichert und stumm, obwohl der Nutzer gerade
     * genau das erlaubt hat -- der ärgerlichste denkbare Ausgang.
     */
    fun weckerNachziehen() {
        viewModelScope.launch {
            if (!planer.darfExaktWecken()) return@launch
            erinnerungen.zuNotiz(noteId)
                .filterNot { it.isFired }
                .forEach { planer.stellen(it, _state.value.title) }
            _erlaubnisFehlt.value = false
        }
    }

    // ------------------------------------------------------------- Bearbeiten

    fun onTitleChanged(neu: String) {
        _state.update { it.copy(title = neu) }
        planeSpeichern()
    }

    fun onBodyChanged(neu: String) {
        _state.update { it.copy(body = neu) }
        planeSpeichern()
    }

    fun onItemTextChanged(id: String, text: String) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(text = text) else it }) }
        planeSpeichern()
    }

    fun onItemCheckedChanged(id: String, checked: Boolean) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == id) it.copy(isChecked = checked) else it })
        }
        planeSpeichern()
    }

    /**
     * Haengt einen Eintrag mit Text an (Phase 18).
     *
     * Der Text kommt aus dem Feld „Eintrag hinzufuegen" unter der Liste, das in
     * beiden Zustaenden der Liste da ist. Leeres wird nicht angehaengt: Ein
     * leerer Eintrag fiele beim Speichern ohnehin weg und stuende bis dahin
     * als Luecke in der Liste.
     */
    fun eintragHinzufuegen(text: String) {
        val sauber = text.trim()
        if (sauber.isEmpty()) return
        _state.update { s ->
            s.copy(
                items = s.items + NoteItemEntity(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    text = sauber,
                    isChecked = false,
                    position = s.items.size,
                ),
            )
        }
        planeSpeichern()
    }

    /** Entfernt einen Eintrag und gibt ihn samt Stelle zurueck, fuer das Undo. */
    fun removeItem(id: String): Pair<NoteItemEntity, Int>? {
        val vorher = _state.value.items
        val stelle = vorher.indexOfFirst { it.id == id }
        if (stelle < 0) return null
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
        planeSpeichern()
        return vorher[stelle] to stelle
    }

    /** Das Undo zu [removeItem]: der Eintrag kommt an seine alte Stelle zurueck. */
    fun eintragZurueck(eintrag: NoteItemEntity, stelle: Int) {
        _state.update { s ->
            if (s.items.any { it.id == eintrag.id }) return@update s
            val neu = s.items.toMutableList()
            neu.add(stelle.coerceIn(0, neu.size), eintrag)
            s.copy(items = neu)
        }
        planeSpeichern()
    }

    /**
     * Verschiebt einen Eintrag im Bearbeitungszustand der Liste (Phase 18).
     *
     * Die Stelle in der Liste ist die Reihenfolge; `position` wird beim
     * Speichern aus ihr neu vergeben (`NoteRepository.updateContent`).
     */
    fun eintragVerschieben(von: Int, nach: Int) {
        _state.update { s ->
            if (von !in s.items.indices || nach !in s.items.indices || von == nach) return@update s
            val neu = s.items.toMutableList()
            neu.add(nach, neu.removeAt(von))
            s.copy(items = neu)
        }
        planeSpeichern()
    }

    // -------------------------------------------------------------- Speichern

    private fun planeSpeichern() {
        speicherJob?.cancel()
        speicherJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE)
            speichern()
        }
    }

    /** Hart speichern: bei onPause, beim Backgrounding und vor dem Verlassen. */
    /**
     * Der Editor ist weg.
     *
     * **An `onCleared` und nicht am Zurück-Knopf.** Aus einem Bildschirm führen
     * mehr Wege heraus als der eine, an den man denkt — und bliebe die Sperre
     * hängen, ginge nie wieder etwas nach Drive.
     */
    override fun onCleared() {
        bearbeitung.beendet()
        super.onCleared()
    }

    fun speichereJetzt() {
        speicherJob?.cancel()
        viewModelScope.launch { speichern() }
    }

    private suspend fun speichern() {
        val s = _state.value
        if (!s.geladen) return
        notes.updateContent(
            noteId,
            NoteContent(
                title = s.title,
                body = s.body,
                items = s.items.filter { it.text.isNotBlank() },
            ),
        )
    }

    /**
     * Beim Verlassen des Editors. Speichert hart und verwirft die Notiz, wenn
     * sie weder Titel noch Inhalt hat.
     *
     * [dann] wird erst aufgerufen, wenn beides durch ist -- sonst zeigte die
     * Liste kurz eine leere Notiz, die gleich darauf verschwindet.
     */
    fun verlassen(dann: () -> Unit) {
        speicherJob?.cancel()
        // Ein MediaPlayer, den niemand freigibt, haelt eine Audiospur des
        // Systems offen -- und davon gibt es nur eine begrenzte Zahl fuer das
        // ganze Geraet.
        wiedergabe.loslassen()
        viewModelScope.launch {
            erzwingeTitel()
            speichern()
            // Eine laufende Aufnahme ist noch nirgends eingetragen -- der Anhang
            // entsteht erst, wenn sie endet. Ohne diese Ausnahme haelt
            // `discardIfEmpty` die Notiz fuer leer und loescht sie, WAEHREND
            // aufgenommen wird. Am Geraet gefunden am 2026-08-21.
            val nimmtGeradeAuf = aufnahme.aufnahme.value.let {
                it.laeuft && it.notizId == noteId
            }
            val verworfen = !nimmtGeradeAuf && notes.discardIfEmpty(noteId)

            // Nur wenn es die Notiz noch gibt. Eine Linie um eine Karte, die
            // gerade verworfen wurde, waere eine Meldung ueber nichts.
            if (!verworfen) speichermarke.melde(noteId)
            dann()
        }
    }

    /**
     * Titelpflicht ab WORKSPACE (Spezifikation Abschnitt 4), durchgesetzt beim
     * Verlassen -- nicht beim Tippen.
     *
     * Ein Editor, der den Rueckweg blockiert, bis ein Feld gefuellt ist, waere
     * in einer App ohne Speichern-Knopf ein Fremdkoerper. Stattdessen wird der
     * Titel aus dem Inhalt abgeleitet. Wer einen besseren will, hat ihn im
     * Editor jederzeit vorschlagen lassen koennen.
     *
     * Notizen ganz ohne Inhalt bekommen bewusst KEINEN Titel: sie sollen von
     * `discardIfEmpty` verworfen werden, und ein erfundener Titel wuerde genau
     * das verhindern.
     */
    private fun erzwingeTitel() {
        val s = _state.value
        if (!s.titelFehlt) return
        // Klartext, nicht Rohtext: Ein abgeleiteter Titel "**Einkauf**" waere
        // in jeder Liste und jeder Benachrichtigung falsch -- dort gibt es
        // keine Auszeichnung, die die Zeichen wieder verschwinden liesse.
        val abgeleitet = fallbackTitel(
            Auszeichnung.klartext(s.body),
            s.items.map { it.text },
        )
        if (abgeleitet.isBlank()) return
        _state.update { it.copy(title = abgeleitet) }
    }

    // -------------------------------------------------------------------- Tags

    /** Alle vorhandenen Tags, für das Auswahlblatt im Editor. */
    val alleTags: StateFlow<List<TagEntity>> = tags.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _meineTags = MutableStateFlow<List<String>>(emptyList())
    val meineTags: StateFlow<List<String>> = _meineTags.asStateFlow()

    private suspend fun tagsNachladen() {
        _meineTags.value = notes.get(noteId)?.tags?.map { it.id } ?: emptyList()
    }

    /**
     * Hängt einen Tag an die Notiz oder nimmt ihn weg.
     *
     * Das Blatt bleibt dabei offen — man vergibt selten genau einen Tag. Dieselbe
     * Entscheidung wie bei der Mehrfachauswahl in der Übersicht.
     */
    fun tagUmschalten(tagId: String) {
        viewModelScope.launch {
            val jetzt = _meineTags.value
            notes.setTags(noteId, if (tagId in jetzt) jetzt - tagId else jetzt + tagId)
            tagsNachladen()
        }
    }

    fun neuerTag(name: String, farbe: Int) {
        viewModelScope.launch {
            val tag = tags.create(name, farbe)
            notes.setTags(noteId, _meineTags.value + tag.id)
            tagsNachladen()
        }
    }

    // ------------------------------------------------------------------ Kopie

    /**
     * Legt eine Kopie der Notiz im Eingang an.
     *
     * Vorher wird hart gespeichert: Der Entwurf im Editor ist noch nicht in der
     * Datenbank, und eine Kopie ohne die letzten Sätze wäre eine Kopie von
     * gestern.
     *
     * Die Notiz bleibt geöffnet. Man ist gerade beim Schreiben — in die Kopie
     * geschoben zu werden wäre ein Ortswechsel, den niemand verlangt hat.
     */
    /**
     * Nimmt die Notiz vom Abgleich aus oder wieder hinein.
     *
     * Das Wegräumen in Drive passiert **nicht** hier: Es braucht Netz, und Netz
     * gehört nicht hinter eine Schaltfläche. Der nächste Abgleich erledigt es.
     */
    /**
     * Nimmt diese Notiz vom Kalender aus oder wieder hinein.
     *
     * Der Termin selbst wird hier nicht angefasst. Das macht der
     * `Kalenderspiegel`, der auf die Datenbank sieht: Zwei Stellen, die Termine
     * schreiben, waeren eine zu viel.
     */
    fun kalenderUmschalten() {
        val neu = !_state.value.kalenderAn
        _state.update { it.copy(kalenderAn = neu) }
        viewModelScope.launch {
            notes.setKalender(noteId, neu)
            _hinweis.value = if (neu) {
                "Diese Notiz erscheint wieder im Kalender."
            } else {
                "Diese Notiz erscheint nicht mehr im Kalender. Die Erinnerung " +
                    "klingelt trotzdem."
            }
        }
    }

    /**
     * Legt diese Notiz in einen Ordner. `null` nimmt sie aus jedem heraus.
     *
     * Kein Rueckweg ueber eine Snackbar, anders als bei der Mehrfachauswahl im
     * Raster: Hier sieht man die Notiz vor sich, und das Zuruecklegen ist
     * derselbe Griff noch einmal. Eine Snackbar ueber dem offenen Editor waere
     * ein Rueckweg fuer etwas, das man ohnehin gerade in der Hand hat.
     */
    fun setOrdner(ordnerId: String?, name: String) {
        _state.update { it.copy(ordnerId = ordnerId) }
        viewModelScope.launch {
            notes.setOrdner(listOf(noteId), ordnerId)
            _hinweis.value = "Diese Notiz liegt jetzt in $name."
        }
    }

    // -------------------------------------------- Archiv im Ordnermodus (14b)

    /** Ins Archiv des Ordnersystems, die Stufe bleibt. Kein Undo, wie beim Ordnerwechsel hier. */
    fun imOrdnerArchivieren() {
        _state.update { it.copy(imOrdnerArchiv = true, ordnerId = null) }
        viewModelScope.launch {
            notes.imOrdnerArchivieren(listOf(noteId))
            _hinweis.value = "Diese Notiz liegt jetzt im Archiv."
        }
    }

    private val _rueckholAnfrage = MutableStateFlow<RueckholAnfrage?>(null)
    val rueckholAnfrage: StateFlow<RueckholAnfrage?> = _rueckholAnfrage.asStateFlow()

    fun zurueckholenAnfragen() {
        viewModelScope.launch { _rueckholAnfrage.value = rueckholAnfrageFuer(listOf(noteId), notes, ordner) }
    }

    fun zurueckholenAbgebrochen() {
        _rueckholAnfrage.value = null
    }

    fun zurueckholenNach(zielId: String?, name: String) {
        _rueckholAnfrage.value = null
        _state.update { it.copy(imOrdnerArchiv = false, ordnerId = zielId) }
        viewModelScope.launch {
            notes.ausOrdnerArchivZurueck(listOf(noteId), zielId)
            _hinweis.value = "Diese Notiz liegt jetzt wieder in $name."
        }
    }

    fun herkunftsordnerNeuAnlegen(name: String) {
        val herkunft = _rueckholAnfrage.value?.herkunftId ?: return
        viewModelScope.launch {
            ordner.wiederherstellen(herkunft, mitNotizen = false)
            zurueckholenNach(herkunft, name)
        }
    }

    suspend fun normalerBaum(): List<Ordnerzeile> =
        baum(ordner.getAll(Bereich.ORDNER), einstellungen.ordnerReihenfolge().first())

    fun abgleichUmschalten() {
        val neu = !_state.value.abgleichAn
        _state.update { it.copy(abgleichAn = neu) }
        viewModelScope.launch {
            notes.setAbgleich(noteId, neu)
            _hinweis.value = if (neu) {
                "Diese Notiz wird wieder abgeglichen."
            } else {
                "Diese Notiz bleibt auf diesem Gerät. Beim nächsten Abgleich " +
                    "verschwindet sie aus Google Drive."
            }
        }
    }

    /**
     * Sichert diese Notiz sofort, statt auf den Takt zu warten.
     *
     * Erst hart speichern, dann anmelden, dann den Lauf ohne Verzögerung
     * anstoßen. Ohne das Speichern fehlte das zuletzt Getippte — der Autosave
     * wartet ja noch.
     *
     * Der Lauf selbst gleicht **alles** ab, nicht nur diese Notiz. Ein zweiter
     * Abgleichweg nur für eine einzelne Notiz wäre eine zweite Fassung derselben
     * Logik, und die beiden liefen irgendwann auseinander.
     */
    fun jetztSichern() {
        viewModelScope.launch {
            speichereJetzt()
            notes.nochmalSichern(noteId)

            if (einstellungen.syncGetrennt().first()) {
                _hinweis.value = "Der Abgleich ist getrennt. Verbinde ihn in den Einstellungen."
                return@launch
            }

            // Ueber den Waechter und nicht ueber WorkManager: Solange die App
            // im Vordergrund ist, laeuft der Abgleich direkt. Der Planer haette
            // ihn gebuendelt, und "jetzt" haette Minuten heissen koennen.
            syncwaechter.jetzt(anwendung)
            _hinweis.value = "Wird gesichert."
        }
    }

    fun kopieErstellen() {
        viewModelScope.launch {
            speichern()
            val kopie = notes.duplizieren(noteId) { anhang, neueAnhangId, neueNotizId ->
                when {
                    // Der Aufnahmepfad wird aus der NOTIZ-Kennung gebildet, nicht
                    // aus der des Anhangs -- die Oberfläche sucht die Datei genau
                    // dort. Ein Name aus der Anhangskennung wäre eine Datei, die
                    // niemand findet.
                    anhang.mimeType.startsWith("audio/") -> aufnahmeDatei(anwendung, neueNotizId)
                    anhang.mimeType.startsWith("image/") -> bildspeicher.datei(neueAnhangId)
                    else -> null
                }
            }
            _hinweis.value = if (kopie != null) {
                "Kopie im Eingang erstellt"
            } else {
                "Die Kopie ließ sich nicht anlegen."
            }
        }
    }

    // ------------------------------------------------------------------ Bilder

    /**
     * Wohin die Kamera-App ihr Foto legen soll, oder `null`, wenn keine da ist.
     *
     * Der Pfad wird gemerkt, weil `TakePicture` nur `true`/`false` zurückgibt —
     * die Datei kennt danach sonst niemand mehr.
     */
    fun kameraZiel(): android.net.Uri? {
        if (!bildspeicher.kameraVorhanden()) {
            _hinweis.value = "Auf diesem Gerät ist keine Kamera-App eingerichtet."
            return null
        }
        val (datei, adresse) = bildspeicher.kameraZiel()
        kameraDatei = datei
        return adresse
    }

    private var kameraDatei: java.io.File? = null

    /**
     * Ob eine Kamera-App bereitsteht.
     *
     * Einmal beim Aufbau gelesen: Ein Geraet bekommt waehrend eines
     * Editorbesuchs keine Kamera. Der Eintrag im Blatt wird dann gar nicht
     * erst gezeigt, statt zu erscheinen und ins Leere zu fuehren.
     */
    val kameraVorhanden: Boolean = bildspeicher.kameraVorhanden()

    fun kameraFertig(erfolg: Boolean) {
        val datei = kameraDatei
        kameraDatei = null
        if (!erfolg || datei == null) {
            // Abgebrochen. Die leere Zwischendatei wegräumen, sonst sammelt
            // sich im Cache pro Abbruch eine Leiche an.
            bildspeicher.kameraAufraeumen()
            return
        }
        viewModelScope.launch {
            uebernehmen { id -> bildspeicher.uebernehmen(datei, id) }
            bildspeicher.kameraAufraeumen()
        }
    }

    fun bilderHinzufuegen(adressen: List<android.net.Uri>) {
        if (adressen.isEmpty()) return
        viewModelScope.launch {
            adressen.forEach { adresse ->
                uebernehmen { id -> bildspeicher.uebernehmen(adresse, id) }
            }
        }
    }

    /**
     * Legt ein Bild ab und trägt es als Anhang ein.
     *
     * Die Kennung entsteht VOR dem Ablegen und ist zugleich der Dateiname —
     * damit gehören Datei und Datensatz von Anfang an zusammen und können nicht
     * auseinanderlaufen.
     */
    private suspend fun uebernehmen(ablegen: suspend (String) -> Bildablage?) {
        val id = UUID.randomUUID().toString()
        val ablage = ablegen(id)
        if (ablage == null) {
            _hinweis.value = "Das Bild ließ sich nicht lesen."
            return
        }
        bilder.hinzufuegen(noteId, id, ablage.datei) ?: return
        bilderNachladen()
    }

    fun bildEntfernen(anhangId: String) {
        viewModelScope.launch {
            bilder.entfernen(anhangId)
            bilderNachladen()
        }
    }

    /**
     * Wählt ein Bild aus der Galerie **nur** als Fläche.
     *
     * Es zählt nicht zu den Bildern der Notiz und erscheint nicht im Raster;
     * genau dafür gibt es diesen Weg. Ein vorher eigens gewähltes
     * Hintergrundbild räumt das Repository dabei weg.
     */
    fun hintergrundWaehlen(adresse: android.net.Uri) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val ablage = bildspeicher.uebernehmen(adresse, id)
            if (ablage == null) {
                _hinweis.value = "Das Bild ließ sich nicht lesen."
                return@launch
            }
            bilder.hintergrundAusDatei(noteId, id, ablage.datei) ?: return@launch
            bilderNachladen()
        }
    }

    /** Setzt das Hintergrundbild, oder hebt es mit `null` auf. */
    fun setzeHintergrund(anhangId: String?) {
        viewModelScope.launch {
            bilder.setzeHintergrund(noteId, anhangId)
            bilderNachladen()
        }
    }

    /**
     * Liest Anhänge und Hintergrundverweis neu.
     *
     * Der Editor beobachtet die Notiz bewusst NICHT — er führt einen eigenen
     * Entwurf, sonst überschriebe jeder Autosave-Rücklauf den Text unter dem
     * Cursor. Bilder ändern sich aber nicht durchs Tippen, sondern nur durch
     * genau die drei Funktionen hier; deshalb wird an dieser Stelle gezielt
     * nachgeladen statt dauerhaft beobachtet.
     */
    private suspend fun bilderNachladen() {
        val n = notes.get(noteId) ?: return
        _state.update {
            it.copy(
                bilder = nurBilder(n),
                hintergrundId = n.note.backgroundAttachmentId,
                hintergrund = hintergrundVon(n),
            )
        }
    }

    private fun nurBilder(n: NoteWithRelations): List<AttachmentEntity> =
        n.attachments.filter {
            it.mimeType.startsWith("image/") && it.role == Anhangsrolle.INHALT
        }

    private fun hintergrundVon(n: NoteWithRelations): AttachmentEntity? =
        n.note.backgroundAttachmentId?.let { id -> n.attachments.firstOrNull { it.id == id } }

    // ------------------------------------------------------------------ Farbe

    private val _farbBlatt = MutableStateFlow(false)
    val farbBlattOffen: StateFlow<Boolean> = _farbBlatt.asStateFlow()

    fun oeffneFarbAuswahl() = _farbBlatt.update { true }

    fun schliesseFarbAuswahl() = _farbBlatt.update { false }

    /**
     * Faerbt die offene Notiz um.
     *
     * Geht direkt ans Repository statt ueber den Autosave: die Farbe ist kein
     * Text, den man noch weitertippt -- sie ist mit dem Antippen entschieden.
     * Der lokale Zustand wird sofort mitgezogen, damit der Editor nicht erst
     * auf die Datenbank warten muss.
     */
    fun setFarbe(farbe: NoteColor) {
        _state.update { it.copy(colorId = farbe) }
        _farbBlatt.value = false
        viewModelScope.launch { notes.setColor(listOf(noteId), farbe) }
    }

    /** Titel aus dem Vorschlagsdialog uebernehmen. */
    fun onTitleChosen(titel: String) {
        _state.update { it.copy(title = titel) }
        speichereJetzt()
    }

    /**
     * Teilt die offene Notiz. Liest sie vorher frisch aus der Datenbank,
     * damit der eben gespeicherte Stand mitgeht und nicht der vom Oeffnen.
     */
    fun teile(context: Context) {
        viewModelScope.launch {
            notes.get(noteId)?.let { teileNotizen(context, listOf(it)) }
        }
    }

    /** Aus dem Editor heraus loeschen. Der Rueckweg ist die Snackbar. */
    fun inDenPapierkorb(dann: () -> Unit) {
        speicherJob?.cancel()
        viewModelScope.launch {
            notes.trash(listOf(noteId))
            dann()
        }
    }

    suspend fun wiederherstellen() = notes.restore(listOf(noteId))

    // ------------------------------------------------------- Uebersetzung

    /**
     * Ob es „Uebersetzen" ueberhaupt gibt (Phase 16): erst fragen, dann
     * anbieten. Gemessen beim Oeffnen, wie das Sprachmodell.
     */
    private val _uebersetzungVerfuegbar = MutableStateFlow(false)
    val uebersetzungVerfuegbar: StateFlow<Boolean> = _uebersetzungVerfuegbar.asStateFlow()

    private val _uebersetzungsanfrage = MutableStateFlow<Uebersetzungsanfrage?>(null)
    val uebersetzungsanfrage: StateFlow<Uebersetzungsanfrage?> = _uebersetzungsanfrage.asStateFlow()

    fun uebersetzungPruefen() {
        viewModelScope.launch {
            _uebersetzungVerfuegbar.value = runCatching { uebersetzung.irgendeinWeg() }.getOrDefault(false)
        }
    }

    /**
     * Oeffnet den Dialog. [auswahl] ist die Auswahl im Text (Anfang, Ende im
     * Quelltext) oder `null` fuer die ganze Notiz. Bei einer Liste sind es
     * immer die Eintraege.
     */
    fun uebersetzenAnfragen(auswahl: Pair<Int, Int>? = null) {
        val s = _state.value
        viewModelScope.launch {
            val (von, nach) = einstellungen.uebersetzungSprachen().first()
            val weg = uebersetzung.weg()
            val anfrage = when {
                s.type == NoteType.LIST -> Uebersetzungsanfrage(
                    bereich = Uebersetzungsanfrage.Bereich.Eintraege(s.items.map { it.id }),
                    texte = s.items.map { it.text },
                    von = von,
                    nach = nach,
                    weg = weg,
                )
                auswahl != null && auswahl.first < auswahl.second -> {
                    val anfang = auswahl.first.coerceIn(0, s.body.length)
                    val ende = auswahl.second.coerceIn(anfang, s.body.length)
                    Uebersetzungsanfrage(
                        bereich = Uebersetzungsanfrage.Bereich.Auswahl(anfang, ende),
                        texte = listOf(s.body.substring(anfang, ende)),
                        von = von,
                        nach = nach,
                        weg = weg,
                    )
                }
                else -> Uebersetzungsanfrage(
                    bereich = Uebersetzungsanfrage.Bereich.GanzerText,
                    texte = listOf(s.body),
                    von = von,
                    nach = nach,
                    weg = weg,
                )
            }
            if (anfrage.texte.all { it.isBlank() }) {
                _hinweis.value = "Hier steht noch nichts, was sich übersetzen ließe."
                return@launch
            }
            _uebersetzungsanfrage.value = anfrage
        }
    }

    fun uebersetzungSprachen(von: String, nach: String) {
        _uebersetzungsanfrage.update { it?.copy(von = von, nach = nach, ergebnis = null) }
    }

    /** Startet die Uebersetzung; [laden] holt vorher ein fehlendes Sprachpaket (ML Kit). */
    fun uebersetzungStarten(laden: Boolean = false) {
        val anfrage = _uebersetzungsanfrage.value ?: return
        if (anfrage.laeuft) return
        _uebersetzungsanfrage.value = anfrage.copy(laeuft = true, ergebnis = null)
        viewModelScope.launch {
            val ergebnis = runCatching {
                uebersetzung.uebersetzen(anfrage.texte, anfrage.von, anfrage.nach, laden)
            }.getOrElse { Uebersetzungsergebnis.Fehler(it.message ?: "Unbekannter Fehler") }
            _uebersetzungsanfrage.update { it?.copy(laeuft = false, ergebnis = ergebnis) }
        }
    }

    fun uebersetzungAbbrechen() {
        _uebersetzungsanfrage.value = null
    }

    /**
     * Setzt die Uebersetzung an die Stelle des Originals und gibt das Undo
     * zurueck, das den alten Stand wiederherstellt. Das Original geht also
     * nicht verloren: Es steht in der Undo-Leiste.
     */
    fun uebersetzungErsetzen(): (suspend () -> Unit)? {
        val anfrage = _uebersetzungsanfrage.value ?: return null
        val fertig = anfrage.fertig ?: return null
        val vorher = _state.value
        when (val bereich = anfrage.bereich) {
            is Uebersetzungsanfrage.Bereich.Auswahl -> {
                val body = vorher.body
                if (bereich.ende > body.length) return null
                val neu = body.substring(0, bereich.anfang) + fertig.first() + body.substring(bereich.ende)
                _state.update { it.copy(body = neu) }
            }
            Uebersetzungsanfrage.Bereich.GanzerText -> _state.update { it.copy(body = fertig.first()) }
            is Uebersetzungsanfrage.Bereich.Eintraege -> {
                val texte = bereich.kennungen.zip(fertig).toMap()
                _state.update { s ->
                    s.copy(items = s.items.map { e -> texte[e.id]?.let { t -> e.copy(text = t) } ?: e })
                }
            }
        }
        _uebersetzungsanfrage.value = null
        planeSpeichern()
        return {
            _state.update { it.copy(body = vorher.body, items = vorher.items) }
            planeSpeichern()
        }
    }

    /** Haengt die Uebersetzung unter das Original: unter die Auswahl, unter den Text, unter die Liste. */
    fun uebersetzungAnhaengen() {
        val anfrage = _uebersetzungsanfrage.value ?: return
        val fertig = anfrage.fertig ?: return
        when (val bereich = anfrage.bereich) {
            is Uebersetzungsanfrage.Bereich.Auswahl -> _state.update { s ->
                val body = s.body
                if (bereich.ende > body.length) return@update s
                s.copy(body = body.substring(0, bereich.ende) + "\n" + fertig.first() + body.substring(bereich.ende))
            }
            Uebersetzungsanfrage.Bereich.GanzerText -> _state.update { s ->
                s.copy(body = s.body.trimEnd() + "\n\n" + fertig.first())
            }
            is Uebersetzungsanfrage.Bereich.Eintraege -> _state.update { s ->
                val neue = fertig.filter { it.isNotBlank() }.mapIndexed { i, t ->
                    NoteItemEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = noteId,
                        text = t,
                        isChecked = false,
                        position = s.items.size + i,
                    )
                }
                s.copy(items = s.items + neue)
            }
        }
        _uebersetzungsanfrage.value = null
        planeSpeichern()
    }

    /** Der Weg in die Systemeinstellung fuer Sprachpakete, wenn es ihn gibt. */
    fun sprachpaketEinstellung() = uebersetzung.systemeinstellung()

    companion object {
        /** Spezifikation Abschnitt 9. */
        const val AUTOSAVE_DEBOUNCE = 800L
    }
}
