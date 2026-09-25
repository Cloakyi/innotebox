package de.notizen.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ob gerade jemand an einer Notiz schreibt.
 *
 * **Der Abgleich wartet darauf, und das ist der Kern.** Der Autosave feuert
 * beim Tippen alle paar hundert Millisekunden; hinge der Abgleich nur an einer
 * Entprellung, ginge bei jeder Denkpause die ganze Notizdatei nach Drive. Zwanzig
 * Uploads für einen Absatz, und neunzehn davon sind Zwischenstände, die niemand
 * je sehen wird.
 *
 * **Der ehrlichste „ich bin fertig"-Moment ist das Schließen des Editors.** Nicht
 * eine Pause beim Tippen — die kann auch bedeuten, dass jemand nachdenkt. Erst
 * wenn der Editor zu ist, steht fest, dass an dieser Notiz nichts mehr kommt.
 *
 * **Ein Zähler und kein Schalter.** Beim Wechsel von einer Notiz zur nächsten
 * überlappen sich die beiden Editoren einen Augenblick; ein Schalter stünde in
 * diesem Moment auf „niemand schreibt", und der Abgleich liefe mitten hinein.
 *
 * Das Beenden hängt an `onCleared` des Editors, nicht am Zurück-Knopf: Es gibt
 * mehr Wege aus einem Bildschirm heraus als den einen, an den man denkt.
 */
@Singleton
class Bearbeitung @Inject constructor() {

    private val _offen = MutableStateFlow(0)

    /** Wie viele Editoren gerade offen sind. `0` heißt: freie Bahn. */
    val offen: StateFlow<Int> = _offen.asStateFlow()

    fun begonnen() = _offen.update { it + 1 }

    fun beendet() = _offen.update { (it - 1).coerceAtLeast(0) }
}
