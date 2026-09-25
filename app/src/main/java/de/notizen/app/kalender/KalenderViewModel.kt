package de.notizen.app.kalender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Kalenderlage(
    val an: Boolean = false,
    val gewaehlt: Long = -1L,
)

/**
 * Die Einstellungen für den Kalender.
 *
 * Die Liste der Kalender wird **auf Zuruf geholt, nicht beobachtet.** Der
 * Anbieter kennt keinen Fluss, der sich meldet, wenn ein Konto dazukommt, und
 * ihn regelmäßig abzufragen wäre Arbeit für eine Liste, die sich im Jahr
 * vielleicht einmal ändert. Sie wird geladen, wenn der Bildschirm aufgeht und
 * wenn die Erlaubnis erteilt wurde.
 */
@HiltViewModel
class KalenderViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
    private val zugang: Kalenderzugang,
) : ViewModel() {

    val lage: StateFlow<Kalenderlage> = combine(
        einstellungen.kalenderAn(),
        einstellungen.kalenderId(),
    ) { an, id -> Kalenderlage(an, id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Kalenderlage())

    private val _auswahl = MutableStateFlow<List<Kalenderwahl>>(emptyList())
    val auswahl: StateFlow<List<Kalenderwahl>> = _auswahl.asStateFlow()

    /** Ob das System den Zugriff gerade erlaubt. Bei jedem Aufruf neu gefragt. */
    fun erlaubt(): Boolean = zugang.erlaubt()

    fun kalenderLaden() {
        viewModelScope.launch { _auswahl.value = zugang.kalender() }
    }

    fun setAn(an: Boolean) {
        viewModelScope.launch {
            einstellungen.setKalenderAn(an)

            // Beim Einschalten gleich einen Kalender vorschlagen, wenn es nur
            // einen gibt oder noch keiner gewaehlt ist. Ein Schalter, der an
            // steht und trotzdem nichts tut, weil eine zweite Einstellung
            // fehlt, ist der haeufigste Weg, eine Funktion fuer kaputt zu
            // halten.
            if (!an) return@launch
            val liste = zugang.kalender()
            _auswahl.value = liste
            if (einstellungen.kalenderId().first() <= 0) {
                vorschlag(liste)?.let { einstellungen.setKalenderId(it.id) }
            }
        }
    }

    fun setKalender(id: Long) {
        viewModelScope.launch { einstellungen.setKalenderId(id) }
    }

    /** Nach der erteilten Erlaubnis: einschalten, Liste holen, Kalender vorschlagen. */
    fun erlaubnisErteilt() = setAn(true)
}
