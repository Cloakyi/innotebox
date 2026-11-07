package de.notizen.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/** Kalender und Termine der App (SYNC.md 16). */
@Dao
interface KalenderDao {

    // ----------------------------------------------------------- Kalender

    @Query("SELECT * FROM event_calendars WHERE deletedAt IS NULL ORDER BY sortIndex, createdAt")
    fun beobachteKalender(): Flow<List<EventCalendarEntity>>

    @Query("SELECT * FROM event_calendars WHERE deletedAt IS NULL ORDER BY sortIndex, createdAt")
    suspend fun kalender(): List<EventCalendarEntity>

    @Query("SELECT * FROM event_calendars")
    suspend fun alleKalender(): List<EventCalendarEntity>

    @Query("SELECT * FROM event_calendars WHERE id = :id")
    suspend fun kalenderById(id: String): EventCalendarEntity?

    @Upsert
    suspend fun kalenderSpeichern(kalender: List<EventCalendarEntity>)

    @Query("DELETE FROM event_calendars WHERE id = :id")
    suspend fun kalenderEntfernen(id: String)

    // ------------------------------------------------------------ Termine

    /**
     * Alle lebenden Termine. Serien lassen sich nicht ueber einen Zeitraum
     * abfragen, ohne sie auszurechnen; das macht `Terminregeln` in Kotlin. Ein
     * Kalender traegt Hunderte, nicht Hunderttausende Eintraege.
     */
    @Query("SELECT * FROM events WHERE deletedAt IS NULL")
    fun beobachteTermine(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deletedAt IS NULL")
    suspend fun termine(): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun alleTermine(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun terminById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun beobachteTermin(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE calendarId = :kalenderId AND deletedAt IS NULL")
    suspend fun termineIn(kalenderId: String): List<EventEntity>

    @Upsert
    suspend fun termineSpeichern(termine: List<EventEntity>)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun terminEntfernen(id: String)
}
