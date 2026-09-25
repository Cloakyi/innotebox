package de.notizen.app.ui.suche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.ordner.mitKindern
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.FolderRepository
import de.notizen.core.data.repository.SucheRepository
import de.notizen.core.data.repository.Suchtreffer
import de.notizen.core.data.repository.TagRepository
import de.notizen.core.data.search.Suchanfrage
import de.notizen.core.data.search.Zeitraum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Der Ordnerbaum fuer den Filter: beide Baeume, Archiv hinter dem normalen. */
data class Ordnerfilterbaum(
    val ordner: List<Ordnerzeile>,
    val archiv: List<Ordnerzeile>,
    val mitKindern: Set<String>,
) {
    val alle: List<FolderEntity> get() = (ordner + archiv).map { it.ordner }
}

/**
 * Die Suche.
 *
 * Kein Entprellen beim Tippen: der Index liegt lokal, die Abfrage ist eine
 * Sache von Millisekunden, und eine künstliche Verzögerung wäre am Gerät als
 * Ruckeln zu spüren. Sollte sich das je ändern, gehört das Entprellen hierher
 * und nirgendwo sonst.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SucheViewModel @Inject constructor(
    private val suche: SucheRepository,
    tagRepository: TagRepository,
    ordnerRepository: FolderRepository,
    einstellungen: Einstellungen,
) : ViewModel() {

    private val _anfrage = MutableStateFlow(Suchanfrage())
    val anfrage: StateFlow<Suchanfrage> = _anfrage.asStateFlow()

    val treffer: StateFlow<List<Suchtreffer>> = _anfrage
        .flatMapLatest { suche.suche(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Für die Tag-Chips in der Filterleiste. */
    val tags: StateFlow<List<TagEntity>> = tagRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Der Ordnerfilter gibt es nur im Ordnermodus (14d). Im Fluss sieht man
     * keine Ordner, und ein Filter nach etwas, das nirgends zu sehen ist,
     * waere ein Raetsel.
     */
    val ordnermodus: StateFlow<Boolean> = einstellungen.ordnermodus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Beide Baeume fuer den Filter, eingerueckt wie in der Seitenspalte. */
    val ordnerbaum: StateFlow<Ordnerfilterbaum> = combine(
        ordnerRepository.observeAll(Bereich.ORDNER),
        ordnerRepository.observeAll(Bereich.ARCHIV),
        einstellungen.ordnerReihenfolge(),
    ) { normal, archiv, sort ->
        Ordnerfilterbaum(baum(normal, sort), baum(archiv, sort), mitKindern(normal) + mitKindern(archiv))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ordnerfilterbaum(emptyList(), emptyList(), emptySet()))

    /** Die Namen der gewaehlten Ordner, fuer die Beschriftung des Chips. */
    val gewaehlteOrdnernamen: StateFlow<List<String>> = combine(_anfrage, ordnerbaum) { a, b ->
        b.alle.filter { it.id in a.ordnerIds }.map { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Voreinstellung beim Öffnen.
     *
     * Wer aus dem Archiv heraus sucht, meint meistens zuerst das Archiv -- aber
     * eben nur meistens. Deshalb wird die Stufe als **abwählbarer Chip**
     * gesetzt und nicht fest verdrahtet: die Suche geht über alles, sie fängt
     * nur woanders an. Dasselbe gilt fuer den Ordner, aus dem heraus gesucht
     * wird (14d).
     *
     * Wird beim erneuten Betreten NICHT wiederholt, sonst käme jede Rückkehr
     * aus einer geöffneten Notiz mit zurückgesetzten Filtern.
     */
    fun voreinstellen(stufe: Stage?, tagId: String?, ordnerId: String? = null) {
        if (_anfrage.value != Suchanfrage()) return
        _anfrage.update {
            it.copy(
                stufen = setOfNotNull(stufe),
                tagIds = setOfNotNull(tagId),
                ordnerIds = setOfNotNull(ordnerId),
            )
        }
    }

    fun setText(text: String) = _anfrage.update { it.copy(text = text) }

    fun toggleStufe(stufe: Stage) = _anfrage.update { it.copy(stufen = it.stufen umschalten stufe) }

    fun toggleTag(tagId: String) = _anfrage.update { it.copy(tagIds = it.tagIds umschalten tagId) }

    fun toggleFarbe(farbe: NoteColor) =
        _anfrage.update { it.copy(farben = it.farben umschalten farbe) }

    fun toggleTyp(typ: NoteType) = _anfrage.update { it.copy(typen = it.typen umschalten typ) }

    fun toggleFavoriten() = _anfrage.update { it.copy(nurFavoriten = !it.nurFavoriten) }

    fun setZeitraum(zeitraum: Zeitraum) = _anfrage.update { it.copy(zeitraum = zeitraum) }

    fun toggleOrdner(ordnerId: String) =
        _anfrage.update { it.copy(ordnerIds = it.ordnerIds umschalten ordnerId) }

    fun togglePapierkorb() = _anfrage.update { it.copy(mitPapierkorb = !it.mitPapierkorb) }

    /** Setzt die Filter zurück, lässt den Suchtext aber stehen. */
    fun filterZuruecksetzen() = _anfrage.update { Suchanfrage(text = it.text) }
}

private infix fun <T> Set<T>.umschalten(wert: T): Set<T> =
    if (wert in this) this - wert else this + wert
