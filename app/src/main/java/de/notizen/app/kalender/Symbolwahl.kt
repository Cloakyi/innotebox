package de.notizen.app.kalender

import de.notizen.app.ai.TitelKi
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gibt einem gespeicherten Termin sein Symbol.
 *
 * Erst sofort aus den Woertern, damit der Termin nie ohne Zeichen dasteht,
 * dann, wenn die KI an ist, eine Antwort des Modells auf dem Geraet. Das
 * laeuft nach dem Speichern und in einem eigenen Bereich, nicht im
 * Bildschirm: Der Editor ist dann meist schon zu.
 *
 * Neu gewaehlt wird nur, wenn sich Titel, Ort, Beschreibung oder Art geaendert
 * haben oder noch gar kein Symbol da ist. Wer nur die Uhrzeit verschiebt, fragt
 * das Modell nicht erneut.
 */
@Singleton
class Symbolwahl @Inject constructor(
    private val repo: KalenderRepository,
    private val ki: TitelKi,
    private val einstellungen: Einstellungen,
) {
    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun nachSpeichern(vorher: EventEntity?, termin: EventEntity) {
        val inhaltNeu = vorher == null || vorher.title != termin.title ||
            vorher.location != termin.location || vorher.description != termin.description ||
            vorher.art != termin.art
        if (!inhaltNeu && termin.symbol != null) return
        bereich.launch {
            val rest = termin.location + " " + termin.description
            val ausWoertern = Terminsymbole.ausWoertern(termin.title, rest, termin.art)
            repo.symbolSetzen(termin.id, ausWoertern)

            if (termin.art == Terminart.GEBURTSTAG) return@launch
            if (!einstellungen.kiAktiv().first() || !einstellungen.terminsymbolKi().first()) return@launch
            val text = buildString {
                append(termin.title)
                if (termin.location.isNotBlank()) append("\nOrt: ").append(termin.location)
                if (termin.description.isNotBlank()) append("\n").append(termin.description)
            }
            val antwort = ki.symbol(text, Terminsymbole.alle.map { it.schluessel to it.beschreibung })
            val gewaehlt = Terminsymbole.ausAntwort(antwort) ?: return@launch
            // Hat jemand den Termin in der Zwischenzeit geaendert, gilt seine Fassung.
            val jetzt = repo.termin(termin.id) ?: return@launch
            if (jetzt.title != termin.title) return@launch
            repo.symbolSetzen(termin.id, gewaehlt)
        }
    }
}
