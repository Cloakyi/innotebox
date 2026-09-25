package de.notizen.app.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Was das Symbol in der Kopfzeile sagt. */
enum class Sicherung {
    /** Der Abgleich ist aus oder getrennt. Es soll gar nichts hochgehen. */
    AUS,

    /** Es liegt noch etwas an. */
    OFFEN,

    /** Alles, was hochgehört, ist oben. */
    GESICHERT,
}

data class Sicherungszustand(
    val stand: Sicherung = Sicherung.GESICHERT,
    val offen: Int = 0,
) {
    /** Der Satz hinter dem Symbol. Wird beim Antippen gezeigt. */
    val text: String
        get() = when (stand) {
            Sicherung.AUS -> "Der Abgleich ist aus. Deine Notizen gehen nicht zu Google."
            Sicherung.GESICHERT -> "Alle Notizen sind gesichert."
            Sicherung.OFFEN -> if (offen == 1) {
                "Eine Notiz ist noch nicht gesichert."
            } else {
                "$offen Notizen sind noch nicht gesichert."
            }
        }
}

/**
 * Der Sicherungsstand für die Kopfzeile der Übersicht.
 *
 * Eigenes ViewModel und nicht [SyncViewModel]. Jenes fragt beim Erzeugen
 * bei Google nach, ob der Zugriff steht, richtig für den Sync-Bildschirm, den
 * man selten öffnet, und falsch für die Übersicht, die man ständig sieht. Hier
 * wird nur die Datenbank gelesen; das kostet nichts und sagt genau das, worum
 * es geht: ob noch etwas ansteht.
 *
 * „Getrennt" und „Takt aus" sind derselbe Zustand für die Anzeige. In beiden
 * Fällen geht nichts von selbst nach oben, und das Symbol darf dann nicht
 * behaupten, alles sei gesichert.
 */
@HiltViewModel
class SicherungViewModel @Inject constructor(
    syncDao: SyncDao,
    einstellungen: Einstellungen,
) : ViewModel() {

    val state: StateFlow<Sicherungszustand> = combine(
        syncDao.observeOffeneNotizen(),
        einstellungen.syncGetrennt(),
        einstellungen.syncIntervall(),
    ) { offen, getrennt, takt ->
        when {
            getrennt || takt <= 0 -> Sicherungszustand(Sicherung.AUS, offen)
            offen > 0 -> Sicherungszustand(Sicherung.OFFEN, offen)
            else -> Sicherungszustand(Sicherung.GESICHERT, 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sicherungszustand())
}
