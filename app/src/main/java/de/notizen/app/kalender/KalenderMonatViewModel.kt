package de.notizen.app.kalender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.relation.Notiztermin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Was an einem Tag ansteht. */
data class Tagesinhalt(
    val notizen: List<Notiztermin> = emptyList(),
    val termine: List<Fremdtermin> = emptyList(),
) {
    val leer: Boolean get() = notizen.isEmpty() && termine.isEmpty()
}

/**
 * Was der Kalender gerade zeigt.
 *
 * **Die Notizen kommen aus der Datenbank und melden sich von selbst, die
 * fremden Termine nicht.** Der Kalender-Anbieter kennt keinen Fluss. Sie werden
 * deshalb geholt, wenn sich der Zeitraum ändert und wenn der Bildschirm wieder
 * aufgeht. Ein Termin, den jemand nebenher in der Kalender-App anlegt, erscheint
 * hier also erst beim nächsten Hinsehen. Das ist der ehrliche Preis dafür, nicht
 * im Sekundentakt einen fremden Anbieter zu befragen.
 *
 * **Der Zeitraum hängt an der Ansicht, nicht am Monat.** Er ist eine reine
 * Rechnung aus Ansicht und Anker (`bereich`) und läuft durch
 * `distinctUntilChanged`: Wer im Monatsraster einen anderen Tag antippt, ändert
 * den Zeitraum nicht, und dann wird auch nichts neu geladen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KalenderMonatViewModel @Inject constructor(
    private val reminderDao: ReminderDao,
    private val noteDao: NoteDao,
    private val zugang: Kalenderzugang,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _ansicht = MutableStateFlow(Kalenderansicht.MONAT)
    val ansicht: StateFlow<Kalenderansicht> = _ansicht.asStateFlow()

    /** Der Tag, um den es geht. Im Monat der gewählte, im Tag der gezeigte. */
    private val _anker = MutableStateFlow(LocalDate.now())
    val anker: StateFlow<LocalDate> = _anker.asStateFlow()

    private val _termine = MutableStateFlow<List<Fremdtermin>>(emptyList())
    val termine: StateFlow<List<Fremdtermin>> = _termine.asStateFlow()

    private val zeitraum: StateFlow<Pair<Long, Long>> =
        combine(_ansicht, _anker) { ansicht, anker -> bereich(ansicht, anker, zone) }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                bereich(Kalenderansicht.MONAT, LocalDate.now(), zone),
            )

    val notizen: StateFlow<List<Notiztermin>> = zeitraum
        .flatMapLatest { (von, bis) -> reminderDao.observeImZeitraum(von, bis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Der Zeitraum aendert sich beim Blaettern und beim Wechsel der
        // Ansicht. Beides ist derselbe Anlass, die fremden Termine neu zu
        // holen -- deshalb haengt es hier am Zeitraum und nicht an drei
        // einzelnen Knoepfen.
        viewModelScope.launch {
            zeitraum.map { it }.distinctUntilChanged().collect { (von, bis) ->
                laden(von, bis)
            }
        }
    }

    /** Ob der Kalender des Geräts gelesen werden darf. Bei jedem Aufruf neu gefragt. */
    fun erlaubt(): Boolean = zugang.erlaubt()

    fun setAnsicht(neu: Kalenderansicht) {
        // Der Anker bleibt. Wer im Monat den 14. gewaehlt hat und auf Tag
        // umschaltet, will den 14. sehen und nicht heute.
        _ansicht.value = neu
    }

    fun weiter() {
        _anker.value = verschoben(_ansicht.value, _anker.value, 1)
    }

    fun zurueck() {
        _anker.value = verschoben(_ansicht.value, _anker.value, -1)
    }

    fun heute() {
        _anker.value = LocalDate.now()
    }

    fun waehle(tag: LocalDate) {
        _anker.value = tag
    }

    /** Holt die fremden Termine des sichtbaren Zeitraums erneut. */
    fun termineLaden() {
        val (von, bis) = zeitraum.value
        viewModelScope.launch { laden(von, bis) }
    }

    /**
     * Die eigenen Termine fallen heraus.
     *
     * Sie stehen schon als Notiz in der Liste, und zweimal dasselbe ist keine
     * Auskunft.
     */
    private suspend fun laden(von: Long, bis: Long) {
        val eigene = noteDao.mitKalendertermin().mapTo(HashSet()) { it.calendarEventId }
        _termine.value = zugang.termine(von, bis, eigene)
    }
}

/** Ordnet zu, was an welchem Tag liegt. Rein, damit es sich prüfen lässt. */
fun nachTagen(
    notizen: List<Notiztermin>,
    termine: List<Fremdtermin>,
    zone: ZoneId,
): Map<LocalDate, Tagesinhalt> {
    val tage = sortedMapOf<LocalDate, Tagesinhalt>()

    for (n in notizen) {
        val tag = tagVon(n.triggerAt, zone)
        val bisher = tage[tag] ?: Tagesinhalt()
        tage[tag] = bisher.copy(notizen = bisher.notizen + n)
    }
    for (t in termine) {
        val tag = tagVon(t.beginn, zone)
        val bisher = tage[tag] ?: Tagesinhalt()
        tage[tag] = bisher.copy(termine = bisher.termine + t)
    }
    return tage
}

private fun tagVon(millis: Long, zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
