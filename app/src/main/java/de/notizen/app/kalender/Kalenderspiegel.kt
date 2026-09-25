package de.notizen.app.kalender

import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.relation.Kalenderzeile
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält den Kalender des Geräts an den Notizen.
 *
 * **Eine Stelle, nicht viele.** Eine Erinnerung entsteht im Editor, kommt vom
 * anderen Gerät über den Abgleich oder aus einer eingelesenen Sicherung. Wer an
 * jedem dieser Wege einen Aufruf einbaut, vergisst den vierten, der später
 * dazukommt. Hier wird stattdessen die **Datenbank beobachtet**: Wie eine
 * Erinnerung entstanden ist, spielt dann keine Rolle mehr.
 *
 * **Der Fluss trägt eine schmale Projektion** (`Kalenderzeile`) und ein
 * `distinctUntilChanged`. Ohne das meldete er sich bei jeder Änderung an
 * irgendeiner Notiz, und jedes getippte Wort schriebe alle Termine neu.
 *
 * Ohne Erlaubnis für den Kalender tut er nichts und beschwert sich nicht. Die
 * Oberfläche fragt danach, wenn der Nutzer den Schalter umlegt; hier ist das
 * Fehlen der Erlaubnis eine gültige Antwort und kein Fehler.
 */
@Singleton
class Kalenderspiegel @Inject constructor(
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val einstellungen: Einstellungen,
    private val kalender: Kalenderzugang,
) {

    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Welcher Kalender beim letzten Abgleich gewählt war.
     *
     * Wechselt der Nutzer den Kalender, müssen die Termine mit. Ein Termin
     * lässt sich beim Anbieter nicht in einen anderen Kalender verschieben, also
     * werden die alten weggeräumt und neu angelegt. Ohne dieses Gedächtnis
     * blieben sie im alten Kalender liegen und die Notizen bekämen keine neuen,
     * denn ihre Kennung stünde ja noch da.
     */
    private var zuletztGewaehlt: Long? = null

    fun beobachten() {
        bereich.launch {
            combine(
                noteDao.observeKalenderzeilen().distinctUntilChanged(),
                einstellungen.kalenderAn(),
                einstellungen.kalenderId(),
            ) { zeilen, an, id -> Triple(zeilen, an, id) }
                .collect { (zeilen, an, id) ->
                    runCatching { abgleichen(an, id) }
                    runCatching { ausfuehren(zeilen, an, id) }
                }
        }
    }

    /**
     * Der Wechsel des Kalenders.
     *
     * Räumt alle eingetragenen Termine weg und vergisst ihre Kennungen. Der
     * Durchlauf gleich danach legt sie im neuen Kalender wieder an.
     *
     * Beim allerersten Durchlauf nach dem Start passiert das ausdrücklich
     * **nicht**: Da ist `zuletztGewaehlt` noch leer, und das heißt „ich weiß es
     * nicht", nicht „es hat sich geändert".
     */
    private suspend fun abgleichen(an: Boolean, kalenderId: Long) {
        val vorher = zuletztGewaehlt
        zuletztGewaehlt = kalenderId
        if (vorher == null || vorher == kalenderId || !an) return

        for (eintrag in noteDao.mitKalendertermin()) {
            if (kalender.entfernen(eintrag.calendarEventId)) {
                noteDao.setCalendarEventId(eintrag.id, null)
            }
        }
    }

    private suspend fun ausfuehren(
        zeilen: List<Kalenderzeile>,
        an: Boolean,
        kalenderId: Long,
    ) {
        if (!kalender.erlaubt()) return

        // Zuerst die Reste. Ein Termin, dessen Notiz es nicht mehr gibt, ist
        // ueber die Notizen nicht mehr zu finden -- nur ueber seinen Merkzettel.
        verwaisteWegraeumen()

        // Kein Kalender gewaehlt heisst aus. Einen zu raten hiesse, Termine
        // irgendwo abzulegen.
        val wirklichAn = an && kalenderId > 0

        for (tat in taten(zeilen, wirklichAn)) {
            when (tat) {
                is Kalendertat.Anlegen -> {
                    val id = kalender.eintragen(kalenderId, tat.noteId, tat.titel, tat.beginn)
                    if (id != null) noteDao.setCalendarEventId(tat.noteId, id)
                }

                is Kalendertat.Aendern ->
                    kalender.aendern(tat.eventId, tat.titel, tat.beginn)

                is Kalendertat.Entfernen -> {
                    // Die Kennung wird auch dann vergessen, wenn das Loeschen
                    // nicht geklappt hat. Sie zeigt sonst auf einen Termin, den
                    // niemand mehr anfasst, und die Notiz bekaeme nie wieder
                    // einen neuen.
                    kalender.entfernen(tat.eventId)
                    noteDao.setCalendarEventId(tat.noteId, null)
                }
            }
        }
    }

    /** Termine, deren Notiz endgültig gelöscht wurde. Siehe `EntityType.CALENDAR`. */
    private suspend fun verwaisteWegraeumen() {
        for (stein in syncDao.tombstonesOf(EntityType.CALENDAR)) {
            val eventId = stein.entityId.toLongOrNull()
            if (eventId == null || kalender.entfernen(eventId)) {
                syncDao.dropTombstone(EntityType.CALENDAR, stein.entityId)
            }
        }
    }
}
