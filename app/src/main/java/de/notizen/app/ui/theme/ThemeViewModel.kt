package de.notizen.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.model.ThemeWahl
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeEinstellung(
    val wahl: ThemeWahl = ThemeWahl.SYSTEM,
    val dynamicColor: Boolean = true,
)

/**
 * Die Theme-Einstellung, gelesen an der Wurzel der App.
 *
 * Liegt hier und nicht in den Einstellungen selbst, weil `NotizenTheme` sie
 * braucht, bevor irgendein Screen existiert. `SharingStarted.Eagerly` sorgt
 * dafuer, dass der gespeicherte Wert schon beim ersten Zeichnen anliegt --
 * sonst blitzte beim Start kurz das Standard-Theme auf.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
) : ViewModel() {

    val state: StateFlow<ThemeEinstellung> = combine(
        einstellungen.themeWahl(),
        einstellungen.dynamicColor(),
    ) { wahl, dynamic -> ThemeEinstellung(wahl, dynamic) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeEinstellung())

    fun setWahl(wert: ThemeWahl) {
        viewModelScope.launch { einstellungen.setThemeWahl(wert) }
    }

    fun setDynamicColor(an: Boolean) {
        viewModelScope.launch { einstellungen.setDynamicColor(an) }
    }
}
