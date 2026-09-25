package de.notizen.core.data.repository

import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erinnerungen an Notizen.
 *
 * Hier steht nur der Datenteil. Das Stellen und Abbestellen des eigentlichen
 * Weckers liegt in `:app`, weil der `AlarmManager` Android ist und `:core:data`
 * ohne Emulator testbar bleiben soll. Die Trennung hat einen praktischen
 * Nutzen: die Regeln, wann eine Erinnerung existiert, lassen sich prüfen, ohne
 * je einen Alarm auszulösen.
 *
 * `triggerAt` wird synchronisiert, `alarmId` und `isFired` nicht (SYNC.md 14.8):
 * jedes Gerät weckt für sich. Wer das zusammenzieht, bekommt eine
 * Erinnerung, die auf dem einen Gerät als erledigt gilt und auf dem anderen nie
 * klingelt.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    private val syncDao: SyncDao,
    private val clock: Clock,
) {

    fun beobachteOffene(): Flow<List<ReminderEntity>> = reminderDao.observePending()

    suspend fun offene(): List<ReminderEntity> = reminderDao.pending()

    suspend fun zuNotiz(noteId: String): List<ReminderEntity> = reminderDao.of(noteId)

    fun beobachteZuNotiz(noteId: String): Flow<List<ReminderEntity>> =
        reminderDao.observeOf(noteId)

    /**
     * Setzt die Erinnerung einer Notiz.
     *
     * Eine Notiz trägt höchstens eine: mehrere Wecker für denselben Zettel
     * wären eine Aufgabenverwaltung, und die ist diese App nicht. Ein neuer
     * Termin ersetzt deshalb den alten, statt sich danebenzustellen.
     *
     * Gibt die gespeicherte Erinnerung zurück, damit der Aufrufer den Wecker
     * mit derselben `alarmId` stellen kann.
     */
    suspend fun setzen(noteId: String, triggerAt: Long): ReminderEntity {
        val bestehend = reminderDao.of(noteId)
        bestehend.forEach { reminderDao.delete(it.id) }

        val neu = ReminderEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            triggerAt = triggerAt,
            // Fortlaufend statt aus dem Hash der UUID: zwei GLEICHZEITIG
            // bestehende Erinnerungen mit derselben Kennung wuerden sich
            // gegenseitig abbestellen, und das faende man nie.
            //
            // Nach dem Loeschen aller Erinnerungen faengt die Zaehlung wieder
            // bei eins an. Das ist Absicht und ungefaehrlich: der alte Wecker
            // ist zu dem Zeitpunkt schon abbestellt, und selbst wenn nicht,
            // ueberschreibt FLAG_UPDATE_CURRENT ihn durch den richtigen.
            alarmId = (reminderDao.maxAlarmId() ?: 0) + 1,
            isFired = false,
        )
        reminderDao.upsert(neu)
        syncDao.markDirty(EntityType.REMINDER, neu.id, clock.now())
        return neu
    }

    /** Entfernt die Erinnerungen einer Notiz und gibt zurück, was abzubestellen ist. */
    suspend fun entfernen(noteId: String): List<ReminderEntity> {
        val bestehend = reminderDao.of(noteId)
        bestehend.forEach { reminderDao.delete(it.id) }
        return bestehend
    }

    /**
     * Hakt eine ausgelöste Erinnerung ab.
     *
     * Gelöscht wird sie ausdrücklich nicht: `triggerAt` wird synchronisiert,
     * und ein Löschen hier käme auf dem anderen Gerät als „gibt es nicht mehr"
     * an, bevor es dort überhaupt geklingelt hat.
     */
    suspend fun abhaken(id: String) = reminderDao.markFired(id)
}
