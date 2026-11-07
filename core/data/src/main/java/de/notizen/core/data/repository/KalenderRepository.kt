package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.KalenderDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kalender und Termine der App (SYNC.md 16).
 *
 * Jede Aenderung setzt in derselben Transaktion den Eintrag in `sync_state`
 * auf DIRTY, genau wie bei Tags und Ordnern; damit geht sie beim naechsten
 * Abgleich nach Drive. `updatedAt` steigt nur bei echter Aenderung.
 */
@Singleton
class KalenderRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val dao: KalenderDao,
    private val syncDao: SyncDao,
    private val einstellungen: Einstellungen,
    private val clock: Clock,
) {

    fun beobachteKalender(): Flow<List<EventCalendarEntity>> = dao.beobachteKalender()

    fun beobachteTermine(): Flow<List<EventEntity>> = dao.beobachteTermine()

    fun beobachteTermin(id: String): Flow<EventEntity?> = dao.beobachteTermin(id)

    suspend fun kalender(): List<EventCalendarEntity> = dao.kalender()

    suspend fun termine(): List<EventEntity> = dao.termine()

    suspend fun termin(id: String): EventEntity? = dao.terminById(id)

    /**
     * Legt beim allerersten Start die drei Kalender an: den eigenen, Aufgaben
     * und Geburtstage.
     *
     * Die Kennungen sind fest. Richten zwei Geraete den Kalender vor ihrem
     * ersten Abgleich ein, entstehen so keine doppelten Kalender, sondern
     * dieselben. `updatedAt` steht auf 1, damit jede echte Aenderung auf dem
     * anderen Geraet gegen diese Grundfassung gewinnt.
     */
    suspend fun einrichten() {
        if (einstellungen.kalenderEingerichtet()) return
        if (dao.alleKalender().isEmpty()) {
            val grund = listOf(
                EventCalendarEntity(MEIN_KALENDER, "Mein Kalender", BLAU, 0, 1, 1),
                EventCalendarEntity(AUFGABEN, "Aufgaben", LILA, 1, 1, 1),
                EventCalendarEntity(GEBURTSTAGE, "Geburtstage", GRUEN, 2, 1, 1),
            )
            db.withTransaction {
                dao.kalenderSpeichern(grund)
                grund.forEach { syncDao.markDirty(EntityType.EVENT_CALENDAR, it.id, it.updatedAt) }
            }
        }
        einstellungen.setKalenderEingerichtet()
    }

    // ----------------------------------------------------------- Kalender

    suspend fun kalenderAnlegen(name: String, farbe: Int): EventCalendarEntity {
        val jetzt = clock.now()
        val reihe = (dao.kalender().maxOfOrNull { it.sortIndex } ?: -1) + 1
        val neu = EventCalendarEntity(UUID.randomUUID().toString(), name.trim(), farbe, reihe, jetzt, jetzt)
        speichern(neu)
        return neu
    }

    suspend fun kalenderAendern(id: String, name: String, farbe: Int) {
        val alt = dao.kalenderById(id) ?: return
        if (alt.name == name.trim() && alt.colorArgb == farbe) return
        speichern(alt.copy(name = name.trim(), colorArgb = farbe, updatedAt = clock.now()))
    }

    /**
     * Loescht einen Kalender samt seinen Terminen, weich. Gibt zurueck, was
     * mitging, damit „Rückgängig" genau das zurueckholt.
     */
    suspend fun kalenderLoeschen(id: String): List<String> {
        val jetzt = clock.now()
        val kalender = dao.kalenderById(id) ?: return emptyList()
        val termine = dao.termineIn(id)
        db.withTransaction {
            dao.kalenderSpeichern(listOf(kalender.copy(deletedAt = jetzt, updatedAt = jetzt)))
            syncDao.markDirty(EntityType.EVENT_CALENDAR, id, jetzt)
            dao.termineSpeichern(termine.map { it.copy(deletedAt = jetzt, updatedAt = jetzt) })
            termine.forEach { syncDao.markDirty(EntityType.EVENT, it.id, jetzt) }
        }
        return termine.map { it.id }
    }

    suspend fun kalenderWiederherstellen(id: String, termine: List<String>) {
        val jetzt = clock.now()
        val kalender = dao.kalenderById(id) ?: return
        db.withTransaction {
            dao.kalenderSpeichern(listOf(kalender.copy(deletedAt = null, updatedAt = jetzt)))
            syncDao.markDirty(EntityType.EVENT_CALENDAR, id, jetzt)
            for (tid in termine) {
                val t = dao.terminById(tid) ?: continue
                dao.termineSpeichern(listOf(t.copy(deletedAt = null, updatedAt = jetzt)))
                syncDao.markDirty(EntityType.EVENT, tid, jetzt)
            }
        }
    }

    private suspend fun speichern(k: EventCalendarEntity) {
        db.withTransaction {
            dao.kalenderSpeichern(listOf(k))
            syncDao.markDirty(EntityType.EVENT_CALENDAR, k.id, k.updatedAt)
        }
    }

    // ------------------------------------------------------------ Termine

    /**
     * Speichert einen Termin, neu oder geaendert.
     *
     * Ist er unveraendert, wird nichts geschrieben. Sonst steigt `updatedAt`,
     * und er wird fuer den Abgleich vorgemerkt.
     */
    suspend fun terminSpeichern(termin: EventEntity): EventEntity {
        val alt = dao.terminById(termin.id)
        if (alt != null && alt.copy(updatedAt = 0) == termin.copy(updatedAt = 0)) return alt
        val jetzt = clock.now()
        val neu = termin.copy(
            createdAt = alt?.createdAt ?: termin.createdAt,
            updatedAt = jetzt,
        )
        db.withTransaction {
            dao.termineSpeichern(listOf(neu))
            syncDao.markDirty(EntityType.EVENT, neu.id, jetzt)
        }
        return neu
    }

    suspend fun terminLoeschen(id: String) {
        val t = dao.terminById(id) ?: return
        terminSpeichern(t.copy(deletedAt = clock.now()))
    }

    suspend fun terminWiederherstellen(id: String) {
        val t = dao.terminById(id) ?: return
        terminSpeichern(t.copy(deletedAt = null))
    }

    /** Loescht ein einzelnes Vorkommen einer Serie. */
    suspend fun vorkommenLoeschen(id: String, schluessel: Long) {
        val t = dao.terminById(id) ?: return
        if (schluessel in t.exceptions) return
        terminSpeichern(t.copy(exceptions = t.exceptions + schluessel))
    }

    suspend fun vorkommenWiederherstellen(id: String, schluessel: Long) {
        val t = dao.terminById(id) ?: return
        terminSpeichern(t.copy(exceptions = t.exceptions - schluessel))
    }

    suspend fun erledigt(id: String, fertig: Boolean) {
        val t = dao.terminById(id) ?: return
        terminSpeichern(t.copy(done = fertig))
    }

    /** Setzt das Symbol, das die KI ausgesucht hat. Ohne Wirkung, wenn es schon steht. */
    suspend fun symbolSetzen(id: String, symbol: String?) {
        val t = dao.terminById(id) ?: return
        if (t.symbol == symbol) return
        terminSpeichern(t.copy(symbol = symbol))
    }

    /**
     * Merkt ein ersetztes oder entferntes Bild zum Wegraeumen in Drive vor.
     * Die Datei auf dem Geraet loescht der Aufrufer.
     */
    suspend fun bildVergessen(bannerId: String) {
        syncDao.upsertTombstone(TombstoneEntity(EntityType.ATTACHMENT, bannerId, clock.now()))
    }

    companion object {
        const val MEIN_KALENDER = "00000000-0000-4000-8000-00000000c001"
        const val AUFGABEN = "00000000-0000-4000-8000-00000000c002"
        const val GEBURTSTAGE = "00000000-0000-4000-8000-00000000c003"

        private const val BLAU = 0xFF4285F4.toInt()
        private const val LILA = 0xFF7986CB.toInt()
        private const val GRUEN = 0xFF33B679.toInt()
    }
}
