package de.notizen.app.ui.ordner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.Ordnersortierung
import de.notizen.core.data.ordner.Ordnerzeile
import de.notizen.core.data.ordner.baum
import de.notizen.core.data.ordner.gesamtzahlen
import de.notizen.core.data.ordner.kinder
import de.notizen.core.data.ordner.moeglicheZiele
import de.notizen.core.data.ordner.pfad
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.app.ui.UndoRequest
import de.notizen.core.data.repository.FolderRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ein Ordner in der Liste, mit allem, was daneben steht. */
data class Ordnerkarte(
    val ordner: FolderEntity,
    /** Notizen darin, einschliesslich aller Unterordner. */
    val anzahl: Int,
    val unterordner: Int,
)

/** Welcher Dialog gerade offen ist. */
sealed interface Ordnerdialog {
    /** Neuer Ordner unterhalb von [elternId]. */
    data class Neu(val elternId: String?) : Ordnerdialog
    data class Umbenennen(val ordner: FolderEntity) : Ordnerdialog
    data class Verschieben(val ordner: FolderEntity) : Ordnerdialog
    data class Loeschen(val ordner: FolderEntity) : Ordnerdialog

    /** Die Farbe des Ordners, aus der Tagpalette (Phase 14e). */
    data class Farbe(val ordner: FolderEntity) : Ordnerdialog

    /**
     * Wohin die Notizen sollen, wenn nur der Ordner geloescht wird.
     *
     * Zweiter Schritt und kein Dialog im Dialog: Die Ordnerauswahl ist eine
     * Liste, die scrollt, und die passt nicht in eine Frage mit zwei Antworten.
     */
    data class Loeschziel(val ordner: FolderEntity) : Ordnerdialog
}

/**
 * Die Ordner einer Ansicht.
 *
 * **Nur die Ordner, nicht die Notizen.** Die stehen im StageViewModel, das
 * schon alles kann, was an einer Notizkarte haengt. Zwei ViewModels
 * nebeneinander auf einem Bildschirm sind hier die einfachere Loesung als
 * eines, das beides tut: Ein Ordner und eine Notiz haben ausser ihrem Platz auf
 * dem Bildschirm nichts gemeinsam.
 */
@HiltViewModel
class OrdnerViewModel @Inject constructor(
    private val ordner: FolderRepository,
    private val einstellungen: Einstellungen,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Der offene Ordner. `null` ist der Hauptordner. */
    val ordnerId: String? = savedState["ordner"]

    /**
     * Welcher Baum: der normale oder das Archiv des Ordnermodus (Phase 14b).
     *
     * Kommt als festes Argument der Archivroute herein, so wie `papierkorb`
     * bei der Papierkorbroute. Alles darunter rechnet nur ueber diesen Baum:
     * die Liste, die Ziele, das Anlegen.
     */
    val bereich: Bereich = savedState.get<String>("bereich")?.let { Bereich.valueOf(it) } ?: Bereich.ORDNER

    val imArchiv: Boolean get() = bereich == Bereich.ARCHIV

    /**
     * Alle Ordner. EAGERLY und nicht WhileSubscribed.
     *
     * `baumzeilen` und `zieleFuer` lesen den aktuellen Wert, ohne dass jemand
     * den Fluss einsammelt -- ein Dialog fragt einmal und zeichnet dann. Bei
     * WhileSubscribed staende dort eine leere Liste, solange niemand sonst
     * mitliest, und die Ordnerauswahl waere leer. Es sind ein paar Dutzend
     * kurze Zeilen; das darf laufen, solange dieser Bildschirm offen ist.
     */
    private val alle: StateFlow<List<FolderEntity>> = ordner.observeAll(bereich)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Wonach die Ordner geordnet stehen (Phase 14e). EAGERLY aus demselben
     * Grund wie [alle]: `baumzeilen` und `zieleFuer` lesen den Wert direkt.
     */
    val sortierung: StateFlow<Ordnersortierung> = einstellungen.ordnerReihenfolge()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ordnersortierung.EIGENE)

    fun setSortierung(neu: Ordnersortierung) {
        viewModelScope.launch { einstellungen.setOrdnerReihenfolge(neu) }
    }

    /**
     * Die Unterordner des offenen Ordners, mit ihren Zahlen.
     *
     * Die Zahl zaehlt die Unterordner mit. Ein Ordner, der selbst nichts
     * enthaelt, aber drei volle Unterordner hat, stuende sonst mit einer Null
     * da und saehe leer aus, obwohl er es nicht ist.
     */
    val inhalt: StateFlow<List<Ordnerkarte>> =
        combine(alle, ordner.observeZaehlung(), sortierung) { ordnerliste, zaehlung, sort ->
            val direkt = zaehlung.associate { it.ordnerId to it.anzahl }
            val gesamt = gesamtzahlen(ordnerliste, direkt)

            kinder(ordnerliste, ordnerId, sort).map { eintrag ->
                Ordnerkarte(
                    ordner = eintrag,
                    anzahl = gesamt[eintrag.id] ?: 0,
                    unterordner = kinder(ordnerliste, eintrag.id).size,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------ Neu anordnen (14e)

    /**
     * Die Geschwisterreihe waehrend des Neuordnens, oder `null`, wenn nicht
     * neu geordnet wird. Eine Kopie, die erst der Haken speichert: Wer
     * abbricht, hat nichts veraendert.
     */
    private val _neuAnordnen = MutableStateFlow<List<FolderEntity>?>(null)
    val neuAnordnen: StateFlow<List<FolderEntity>?> = _neuAnordnen.asStateFlow()

    /**
     * Beginnt das Neuordnen mit der Reihenfolge, wie sie gerade zu sehen ist.
     * Andere Sortierungen (Name, Datum) schreiben nie in `sortIndex`; wer aus
     * ihnen heraus neu ordnet, macht die sichtbare Reihenfolge zur eigenen und
     * die Einstellung springt auf „Eigene Reihenfolge", sonst saehe man das
     * Ergebnis nicht.
     */
    fun neuAnordnenStarten() {
        _neuAnordnen.value = inhalt.value.map { it.ordner }
    }

    fun neuAnordnenVerschieben(von: Int, nach: Int) {
        _neuAnordnen.update { liste ->
            if (liste == null || von == nach || von !in liste.indices || nach !in liste.indices) return@update liste
            liste.toMutableList().apply { add(nach, removeAt(von)) }
        }
    }

    fun neuAnordnenAbbrechen() {
        _neuAnordnen.value = null
    }

    /** Der Haken: schreibt `sortIndex` und schaltet auf die eigene Reihenfolge. */
    fun neuAnordnenSpeichern() {
        val liste = _neuAnordnen.value ?: return
        viewModelScope.launch {
            ordner.reihenfolgeSetzen(liste.map { it.id })
            einstellungen.setOrdnerReihenfolge(Ordnersortierung.EIGENE)
            _neuAnordnen.value = null
        }
    }

    /** Der Weg von der Wurzel bis hierher, fuer die Zeile ueber dem Inhalt. */
    val weg: StateFlow<List<FolderEntity>> = alle
        .map { pfad(it, ordnerId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow<Ordnerdialog?>(null)
    val dialog: StateFlow<Ordnerdialog?> = _dialog.asStateFlow()

    fun oeffne(dialog: Ordnerdialog) = _dialog.update { dialog }

    fun schliesse() = _dialog.update { null }

    /** Der ganze Baum, eingerueckt. Fuer die Ordnerauswahl. */
    fun baumzeilen(): List<Ordnerzeile> = baum(alle.value, sortierung.value)

    /**
     * Die moeglichen Ziele fuer einen Ordner.
     *
     * Was nicht gehen kann, steht gar nicht erst zur Wahl. Ein ausgegrauter
     * Eintrag muesste erklaeren, warum ein Ordner nicht in sich selbst passt.
     */
    fun zieleFuer(id: String): List<Ordnerzeile> = moeglicheZiele(alle.value, id, sortierung.value)

    fun anlegen(name: String, elternId: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            ordner.anlegen(name, elternId, bereich)
            schliesse()
        }
    }

    fun umbenennen(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            ordner.umbenennen(id, name)
            schliesse()
        }
    }

    /** Die Farbe des Ordners, `null` nimmt sie weg (Phase 14e). */
    fun umfaerben(id: String, colorArgb: Int?) {
        viewModelScope.launch {
            ordner.umfaerben(id, colorArgb)
            schliesse()
        }
    }

    /** Gibt ueber [dann] zurueck, ob es geklappt hat. */
    fun verschieben(id: String, zielId: String?, dann: (Boolean) -> Unit) {
        viewModelScope.launch {
            val geklappt = ordner.verschieben(id, zielId)
            schliesse()
            dann(geklappt)
        }
    }

    private val undoKanal = Channel<UndoRequest>(Channel.BUFFERED)
    val undoEvents: Flow<UndoRequest> = undoKanal.receiveAsFlow()

    /**
     * Der Einstieg ins Loeschen.
     *
     * **Ein leerer Ordner wird ohne Rueckfrage geloescht.** Die Frage "nur den
     * Ordner oder samt Inhalt" hat bei einem Ordner ohne Inhalt keine zwei
     * Antworten; sie zu stellen waere eine Huerde ohne Sinn (seit
     * 2026-09-14). Die Undo-Leiste bleibt, wie bei jedem Loeschen.
     */
    fun loeschenAnfragen(karte: Ordnerkarte) {
        if (karte.anzahl == 0 && karte.unterordner == 0) {
            viewModelScope.launch {
                ordner.loeschen(karte.ordner.id)
                undoKanal.send(
                    UndoRequest("Ordner „" + karte.ordner.name + "\" gelöscht") {
                        ordner.wiederherstellen(karte.ordner.id, mitNotizen = false)
                    },
                )
            }
        } else {
            oeffne(Ordnerdialog.Loeschen(karte.ordner))
        }
    }

    /**
     * Nur den Ordner. Die Notizen wandern nach [zielId], sonst eine Ebene hoeher.
     *
     * Kein Undo: Die Notizen sind danach woanders, und wohin, hat man eben
     * selbst gewaehlt. Ein Undo, das nur den Ordner zurueckbraechte, die
     * Notizen aber nicht, waere ein halbes Versprechen.
     */
    fun loeschen(id: String, zielId: String? = null) {
        viewModelScope.launch {
            ordner.loeschen(id, zielId)
            schliesse()
        }
    }

    /** Der Ordner samt allem, was darin liegt. Alles landet im Papierkorb. */
    fun loeschenMitInhalt(ordnerZeile: FolderEntity) {
        viewModelScope.launch {
            ordner.loeschenMitInhalt(ordnerZeile.id)
            schliesse()
            undoKanal.send(
                UndoRequest("„" + ordnerZeile.name + "\" samt Inhalt gelöscht") {
                    ordner.wiederherstellenSamtInhalt(ordnerZeile.id)
                },
            )
        }
    }

    /** Zurueck in den Fluss. Der Schalter steht auch in den Einstellungen. */
    fun zurueckZumFluss() {
        viewModelScope.launch { einstellungen.setOrdnermodus(false) }
    }
}
