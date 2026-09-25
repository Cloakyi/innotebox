package de.notizen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.model.WischZiel
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GestenEinstellung(
    val rechts: WischZiel = WischZiel.NAECHSTE_STUFE,
    val links: WischZiel = WischZiel.VORHERIGE_STUFE,
    val papierkorbRechts: WischZiel = WischZiel.ENDGUELTIG,
    val papierkorbLinks: WischZiel = WischZiel.WIEDERHERSTELLEN,
)

@HiltViewModel
class GestenViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
) : ViewModel() {

    val state: StateFlow<GestenEinstellung> = combine(
        einstellungen.wischRechts(),
        einstellungen.wischLinks(),
        einstellungen.wischPapierkorbRechts(),
        einstellungen.wischPapierkorbLinks(),
    ) { rechts, links, pRechts, pLinks -> GestenEinstellung(rechts, links, pRechts, pLinks) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GestenEinstellung())

    fun setPapierkorbRechts(ziel: WischZiel) {
        viewModelScope.launch { einstellungen.setWischPapierkorbRechts(ziel) }
    }

    fun setPapierkorbLinks(ziel: WischZiel) {
        viewModelScope.launch { einstellungen.setWischPapierkorbLinks(ziel) }
    }

    fun setRechts(ziel: WischZiel) {
        viewModelScope.launch { einstellungen.setWischRechts(ziel) }
    }

    fun setLinks(ziel: WischZiel) {
        viewModelScope.launch { einstellungen.setWischLinks(ziel) }
    }
}
