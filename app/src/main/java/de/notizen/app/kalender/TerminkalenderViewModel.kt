package de.notizen.app.kalender

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.sync.Syncwaechter
import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.db.relation.Notiztermin
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Die Ansichten des Kalenders. */
enum class Kalendermodus(val text: String, val tage: Int) {
    UEBERSICHT("Terminübersicht", 0),
    TAG("Tag", 1),
    DREI_TAGE("3 Tage", 3),
    WOCHE("Woche", 7),
    MONAT("Monat", 0),
}

/** Der erste Tag, den eine Seite einer Ansicht zeigt. */
fun seitenstart(modus: Kalendermodus, anker: LocalDate): LocalDate = when (modus) {
    Kalendermodus.WOCHE -> wochenstart(anker)
    Kalendermodus.MONAT -> anker.withDayOfMonth(1)
    else -> anker
}

/** Der Anker um [schritte] Seiten verschoben. */
fun blaettern(modus: Kalendermodus, anker: LocalDate, schritte: Int): LocalDate = when (modus) {
    Kalendermodus.MONAT -> anker.withDayOfMonth(1).plusMonths(schritte.toLong())
    Kalendermodus.WOCHE -> wochenstart(anker).plusWeeks(schritte.toLong())
    Kalendermodus.DREI_TAGE -> anker.plusDays(3L * schritte)
    else -> anker.plusDays(schritte.toLong())
}

/**
 * Der Kalender der App: Termine, Aufgaben, Geburtstage, dazu die Notizen mit
 * Erinnerung. Mit dem Kalender des Geraets ist er nicht verbunden
 * (docs/ENTSCHEIDUNGEN.md 15).
 *
 * Die Termine kommen vollstaendig aus der Datenbank, Serien rechnet die
 * Oberflaeche fuer die sichtbaren Tage aus. Die Notizen werden fuer ein
 * Fenster von einigen Monaten um den gezeigten Tag geholt; das Fenster
 * springt in ganzen Monaten, damit Blaettern nicht jedes Mal neu laedt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TerminkalenderViewModel @Inject constructor(
    private val anwendung: Application,
    private val repo: KalenderRepository,
    private val einstellungen: Einstellungen,
    private val reminderDao: ReminderDao,
    private val syncwaechter: Syncwaechter,
    private val wecker: Terminwecker,
) : ViewModel() {

    val zone: ZoneId = ZoneId.systemDefault()

    private val _modus = MutableStateFlow<Kalendermodus?>(null)
    val modus: StateFlow<Kalendermodus?> = _modus.asStateFlow()

    private val _anker = MutableStateFlow(LocalDate.now())
    val anker: StateFlow<LocalDate> = _anker.asStateFlow()

    val kalender: StateFlow<List<EventCalendarEntity>> = repo.beobachteKalender()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val termine: StateFlow<List<EventEntity>> = repo.beobachteTermine()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val notizenAn = einstellungen.erinnerungsnotizenImKalender()

    /** Ausgeblendete Kalender und Ebenen; die Notizen auch, wenn sie in den Einstellungen aus sind. */
    val ausgeblendet: StateFlow<Set<String>> =
        combine(einstellungen.kalenderAusgeblendet(), notizenAn) { aus, notizen ->
            if (notizen) aus else aus + EBENE_NOTIZEN
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val notizenImKalender: StateFlow<Boolean> = notizenAn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val terminDauer: StateFlow<Int> = einstellungen.terminDauer()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val kalenderwochen: StateFlow<Boolean> = einstellungen.kalenderwochen()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val wochenendeAus: StateFlow<Boolean> = einstellungen.wochenendeAus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val tippenLegtAn: StateFlow<Boolean> = einstellungen.tippenLegtAn()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val fenster: StateFlow<Pair<LocalDate, LocalDate>> = _anker
        .map { fensterUm(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, fensterUm(LocalDate.now()))

    val notizen: StateFlow<List<Notiztermin>> = fenster
        .flatMapLatest { (von, bis) ->
            reminderDao.observeImZeitraum(
                von.atStartOfDay(zone).toInstant().toEpochMilli(),
                bis.atStartOfDay(zone).toInstant().toEpochMilli(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _modus.value = Kalendermodus.entries.firstOrNull { it.name == einstellungen.kalenderAnsicht().first() }
                ?: Kalendermodus.MONAT
        }
    }

    private fun fensterUm(tag: LocalDate): Pair<LocalDate, LocalDate> {
        val monat = YearMonth.from(tag)
        return monat.minusMonths(2).atDay(1) to monat.plusMonths(3).atDay(1)
    }

    fun setModus(neu: Kalendermodus) {
        _modus.value = neu
        viewModelScope.launch { einstellungen.setKalenderAnsicht(neu.name) }
    }

    fun setAnker(tag: LocalDate) {
        _anker.value = tag
    }

    fun sichtbar(id: String, sichtbar: Boolean) {
        viewModelScope.launch { einstellungen.setKalenderSichtbar(id, sichtbar) }
    }

    fun aktualisieren() {
        syncwaechter.jetzt(anwendung)
    }

    fun kalenderAnlegen(name: String, farbe: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.kalenderAnlegen(name, farbe) }
    }

    fun erledigt(id: String, fertig: Boolean) {
        viewModelScope.launch { repo.erledigt(id, fertig) }
    }

    /** Loescht den ganzen Termin, weich. */
    fun loeschen(id: String) {
        viewModelScope.launch { repo.terminLoeschen(id) }
    }

    suspend fun wiederherstellen(id: String) = repo.terminWiederherstellen(id)

    /** Loescht nur dieses Vorkommen einer Serie. */
    fun vorkommenLoeschen(id: String, schluessel: Long) {
        viewModelScope.launch { repo.vorkommenLoeschen(id, schluessel) }
    }

    suspend fun vorkommenWiederherstellen(id: String, schluessel: Long) =
        repo.vorkommenWiederherstellen(id, schluessel)

    /** Wecker-Erlaubnisse, fuer den Hinweis im Kalender. */
    fun weckerBereit(): Boolean = wecker.darfExaktWecken() && wecker.darfVollbild()
}
