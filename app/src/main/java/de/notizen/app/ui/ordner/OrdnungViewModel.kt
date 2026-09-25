package de.notizen.app.ui.ordner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Der Schalter zwischen Fluss und Ordnern, fuer die Einstellungen.
 *
 * Eigenes ViewModel und nicht das der Ordneransicht: Dieses hier lebt am
 * Einstellungsbildschirm, wo es gar keinen offenen Ordner gibt.
 */
@HiltViewModel
class OrdnungViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
) : ViewModel() {

    val ordnermodus: StateFlow<Boolean> = einstellungen.ordnermodus()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setOrdnermodus(an: Boolean) {
        viewModelScope.launch { einstellungen.setOrdnermodus(an) }
    }
}
