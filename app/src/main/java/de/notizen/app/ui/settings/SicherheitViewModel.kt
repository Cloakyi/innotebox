package de.notizen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.sicherheit.Sperre
import de.notizen.core.data.model.Sperrverzoegerung
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Sicherheit(
    val sperreAn: Boolean = false,
    val verzoegerung: Sperrverzoegerung = Sperrverzoegerung.STANDARD,
    val aufnahmeschutzAn: Boolean = false,
)

/** Der Abschnitt „Sicherheit" in den Einstellungen (Phase 19). */
@HiltViewModel
class SicherheitViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
    private val sperre: Sperre,
) : ViewModel() {

    val state: StateFlow<Sicherheit> = combine(
        einstellungen.sperreAn(),
        einstellungen.sperrverzoegerung(),
        einstellungen.aufnahmeschutzAn(),
    ) { an, verzoegerung, schutz -> Sicherheit(an, verzoegerung, schutz) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Sicherheit())

    /** Nur nach erfolgreicher Entsperrung aufrufen: Wer sich nicht ausweisen kann, sperrt sich sonst aus. */
    fun setSperreAn(an: Boolean) {
        viewModelScope.launch {
            einstellungen.setSperreAn(an)
            if (an) sperre.eingeschaltet()
        }
    }

    fun setVerzoegerung(wert: Sperrverzoegerung) {
        viewModelScope.launch { einstellungen.setSperrverzoegerung(wert) }
    }

    fun setAufnahmeschutz(an: Boolean) {
        viewModelScope.launch { einstellungen.setAufnahmeschutzAn(an) }
    }
}
