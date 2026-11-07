package de.notizen.app.kalender

import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Räumt die Termine weg, die frühere Fassungen in den Kalender des Geräts
 * geschrieben haben.
 *
 * Die App schreibt dort nichts mehr hinein und liest nichts mehr heraus
 * (docs/ENTSCHEIDUNGEN.md 15). Was noch dasteht, wird beim Start entfernt,
 * genau so, wie es der frühere Ausschalter getan hat ([taten] mit „aus“).
 * Ohne Erlaubnis für den Kalender bleibt es liegen; dann hat die App auch nie
 * etwas eingetragen, oder der Nutzer hat ihr das Recht selbst entzogen.
 */
@Singleton
class Kalenderspiegel @Inject constructor(
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val einstellungen: Einstellungen,
    private val kalender: Kalenderzugang,
) {

    private val bereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun aufraeumen() {
        bereich.launch {
            runCatching { einstellungen.setKalenderAn(false) }
            if (!kalender.erlaubt()) return@launch
            runCatching {
                for (tat in taten(noteDao.observeKalenderzeilen().first(), hauptschalterAn = false)) {
                    if (tat is Kalendertat.Entfernen) {
                        kalender.entfernen(tat.eventId)
                        noteDao.setCalendarEventId(tat.noteId, null)
                    }
                }
            }
            runCatching {
                for (stein in syncDao.tombstonesOf(EntityType.CALENDAR)) {
                    val eventId = stein.entityId.toLongOrNull()
                    if (eventId == null || kalender.entfernen(eventId)) {
                        syncDao.dropTombstone(EntityType.CALENDAR, stein.entityId)
                    }
                }
            }
        }
    }
}
