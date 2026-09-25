package de.notizen.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Was der Titeldialog ueber die KI weiss.
 *
 * [zustand] ist `null`, solange die Pruefung laeuft. Der Dialog ist in genau
 * diesem Moment schon vollstaendig bedienbar -- er zeigt den Fallback-Titel.
 */
data class KiTitelState(
    val zustand: KiZustand? = null,
    val vorschlag: String? = null,
    val laeuft: Boolean = false,
)

/**
 * Haelt den KI-Titelvorschlag fuer den Dialog bereit.
 *
 * DIE REGEL: hier wird NIE blockiert. Der Dialog steht sofort mit dem
 * Fallback-Titel, und was die KI beitraegt, kommt nach oder eben gar nicht.
 * Der Zustand `LADBAR` -- Modell noch nicht auf dem Geraet -- fuehrt deshalb
 * nicht zu einem Spinner, sondern zu einem Knopf: laden nur, wenn der Nutzer
 * es will (siehe docs/ENTSCHEIDUNGEN.md).
 */
@HiltViewModel
class KiTitelViewModel @Inject constructor(
    private val ki: TitelKi,
    private val einstellungen: Einstellungen,
) : ViewModel() {

    private val _state = MutableStateFlow(KiTitelState())
    val state: StateFlow<KiTitelState> = _state.asStateFlow()

    private var job: Job? = null
    private var letzteQuelle: String? = null

    /**
     * Startet die Pruefung fuer einen Notizinhalt. Mehrfachaufrufe mit
     * derselben Quelle sind gratis -- der Dialog ruft bei jeder Recomposition.
     */
    fun starte(quelle: String) {
        if (letzteQuelle == quelle) return
        letzteQuelle = quelle
        job?.cancel()
        _state.value = KiTitelState(laeuft = true)
        job = viewModelScope.launch {
            // Ist die KI-Aufbereitung abgeschaltet, wird sie nicht einmal
            // gefragt. Der Dialog bleibt vollstaendig bedienbar -- er zeigt den
            // abgeleiteten Titel, so wie er es auch tut, wenn kein Modell da
            // ist.
            if (!einstellungen.kiAktiv().first()) {
                _state.value = KiTitelState(zustand = KiZustand.NICHT_VERFUEGBAR)
                return@launch
            }
            val z = ki.zustand()
            _state.update { it.copy(zustand = z) }
            if (z == KiZustand.BEREIT) hole(quelle) else _state.update { it.copy(laeuft = false) }
        }
    }

    /** Modell herunterladen -- nur auf Knopfdruck, nie von selbst. */
    fun modellLaden() {
        val quelle = letzteQuelle ?: return
        job?.cancel()
        _state.update { it.copy(zustand = KiZustand.LAEDT, laeuft = true) }
        job = viewModelScope.launch {
            val z = ki.laden()
            _state.update { it.copy(zustand = z) }
            if (z == KiZustand.BEREIT) hole(quelle) else _state.update { it.copy(laeuft = false) }
        }
    }

    private suspend fun hole(quelle: String) {
        val v = ki.vorschlag(quelle)
        _state.update { it.copy(vorschlag = v, laeuft = false) }
    }
}
