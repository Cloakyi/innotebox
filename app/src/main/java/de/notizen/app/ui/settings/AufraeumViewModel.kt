package de.notizen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.db.entity.ArchiveRunEntity
import de.notizen.core.data.model.Stage
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.ArchiveRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Eine Stufe mit ihren beiden Grenzen. `0` heißt jeweils **aus**.
 */
data class Stufengrenzen(
    val tage: Int = 0,
    val menge: Int = 0,
) {
    /** Was in den Einstellungen unter der Stufe steht. */
    val beschreibung: String
        get() = when {
            tage == 0 && menge == 0 -> "Aus"
            menge == 0 -> "nach $tage Tagen"
            tage == 0 -> "ab $menge Notizen"
            else -> "nach $tage Tagen, ab $menge Notizen"
        }
}

data class Aufraeumzustand(
    val an: Boolean = false,
    val eingang: Stufengrenzen = Stufengrenzen(),
    val workspace: Stufengrenzen = Stufengrenzen(),
    val papierkorbTage: Int = 0,
    val laeufe: List<ArchiveRunEntity> = emptyList(),
)

/**
 * Einstellungen und Protokoll des automatischen Aufräumens.
 *
 * Die Auswahlwerte sind bewusst grob gestuft. Ob nach 30 oder nach 31 Tagen
 * archiviert wird, ist keine Frage, die jemand beantworten kann — ein freies
 * Zahlenfeld würde nur so tun, als gäbe es darauf eine richtige Antwort.
 */
@HiltViewModel
class AufraeumViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
    private val archiv: ArchiveRepository,
) : ViewModel() {

    val state: StateFlow<Aufraeumzustand> = combine(
        einstellungen.autoArchivAn(),
        einstellungen.altersgrenze(Stage.INBOX),
        einstellungen.mengengrenze(Stage.INBOX),
        einstellungen.altersgrenze(Stage.WORKSPACE),
        einstellungen.mengengrenze(Stage.WORKSPACE),
        einstellungen.papierkorbFrist(),
        archiv.observeRuns(),
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        Aufraeumzustand(
            an = werte[0] as Boolean,
            eingang = Stufengrenzen(werte[1] as Int, werte[2] as Int),
            workspace = Stufengrenzen(werte[3] as Int, werte[4] as Int),
            papierkorbTage = werte[5] as Int,
            laeufe = werte[6] as List<ArchiveRunEntity>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Aufraeumzustand())

    fun setAn(an: Boolean) = viewModelScope.launch { einstellungen.setAutoArchivAn(an) }

    fun setAlter(stage: Stage, tage: Int) =
        viewModelScope.launch { einstellungen.setAltersgrenze(stage, tage) }

    fun setMenge(stage: Stage, anzahl: Int) =
        viewModelScope.launch { einstellungen.setMengengrenze(stage, anzahl) }

    fun setPapierkorbFrist(tage: Int) =
        viewModelScope.launch { einstellungen.setPapierkorbFrist(tage) }

    /**
     * Nimmt einen protokollierten Lauf zurück.
     *
     * Auch Wochen später noch möglich, solange der Lauf im Protokoll steht —
     * die Benachrichtigung ist nur der schnelle Weg, nicht der einzige.
     */
    fun zuruecknehmen(batchId: String) = viewModelScope.launch { archiv.undo(batchId) }

    companion object {
        /** `0` heißt aus und steht deshalb an erster Stelle: Es ist der Rückweg. */
        val TAGE = listOf(0, 7, 14, 30, 60, 90, 180)
        val MENGEN = listOf(0, 20, 50, 100, 200)
        val PAPIERKORB_TAGE = listOf(0, 7, 14, 30, 60)

        fun tageText(tage: Int) = if (tage == 0) "Aus" else "$tage Tage"

        fun mengeText(menge: Int) = if (menge == 0) "Aus" else "$menge Notizen"
    }
}
