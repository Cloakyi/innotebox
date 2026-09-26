package de.notizen.app.archiv

import de.notizen.app.ai.KiZustand
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holt beim Öffnen der App nach, was die KI nachts nicht durfte.
 *
 * Der nächtliche Lauf archiviert im Leerlauf, und dort lässt Google die KI
 * nicht arbeiten, nur im Vordergrund. Er gibt Notizen ohne Titel deshalb den
 * Titel aus dem Text und merkt sie vor. Hier bekommen sie, solange die App
 * vorn ist, einen Titel von der KI und einen passenden Tag aus den vorhandenen.
 *
 * Nur mit eingeschalteter KI. Ist sie aus, oder bietet das Gerät sie gar nicht
 * an, wird nichts nachgeholt und die Liste geleert. Geht die App mittendrin in
 * den Hintergrund, bleibt der Rest für das nächste Öffnen liegen.
 */
@Singleton
class Nachbeschriftung @Inject constructor(
    private val notes: NoteRepository,
    private val tags: TagRepository,
    private val beschriftung: Beschriftung,
    private val einstellungen: Einstellungen,
) {
    private val laeuft = Mutex()

    suspend fun nachholen(imVordergrund: () -> Boolean) {
        if (!laeuft.tryLock()) return
        try {
            val offen = einstellungen.beschriftungOffen().first()
            if (offen.isEmpty()) return
            if (!einstellungen.kiAktiv().first()) {
                einstellungen.beschriftungLeeren()
                return
            }
            when (beschriftung.kiZustand()) {
                KiZustand.BEREIT -> Unit
                KiZustand.NICHT_VERFUEGBAR -> {
                    einstellungen.beschriftungLeeren()
                    return
                }
                // Das Modell fehlt noch oder lädt gerade: beim nächsten Mal.
                else -> return
            }

            val vorhandene = tags.getAll()
            for (id in offen) {
                if (!imVordergrund()) return
                val voll = notes.get(id)
                if (voll != null && voll.note.deletedAt == null) {
                    val ergebnis = beschriftung.nachholen(voll, vorhandene)
                    // Kam die App währenddessen in den Hintergrund, hat Google
                    // die KI gerade abgewiesen. Dann bleibt die Notiz vorgemerkt.
                    if (!imVordergrund()) return
                    ergebnis.titel?.let { notes.setTitle(id, it) }
                    ergebnis.tag?.let { notes.setTags(id, listOf(it.id)) }
                }
                einstellungen.beschriftungErledigt(id)
            }
        } finally {
            laeuft.unlock()
        }
    }
}
