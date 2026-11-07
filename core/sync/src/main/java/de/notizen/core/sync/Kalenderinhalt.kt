package de.notizen.core.sync

import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import kotlinx.serialization.Serializable

/**
 * Die Dokument-Schema-Version seit dem Kalender (SYNC.md 16). Additiv: Ein
 * Leser auf Schema 5 sieht die Ordner `calendars/` und `events/` nicht an.
 */
const val SCHEMA_VERSION_6 = 6

/** `payload` eines Kalenders, SYNC.md 16.1. */
@Serializable
data class Kalenderinhalt(
    val name: String,
    val colorArgb: Int,
    val sortIndex: Int = 0,
    val createdAt: Long,
)

/** `payload` eines Termins, SYNC.md 16.2. */
@Serializable
data class Termininhalt(
    val calendarId: String,
    val art: Terminart = Terminart.TERMIN,
    val title: String,
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startAt: Long,
    val endAt: Long,
    val timeZone: String = "UTC",
    val recurrence: Wiederholung = Wiederholung.KEINE,
    val repeatInterval: Int = 1,
    val repeatDays: List<Int> = emptyList(),
    val repeatUntil: Long? = null,
    val repeatCount: Int? = null,
    val exceptions: List<Long> = emptyList(),
    val reminders: List<Int> = emptyList(),
    val important: Boolean = false,
    val colorArgb: Int? = null,
    val symbol: String? = null,
    val bannerId: String? = null,
    val done: Boolean = false,
    val undated: Boolean = false,
    val createdAt: Long,
)

/** Ein Kalender in der Sicherung: der Inhalt plus die Felder der Huelle. */
@Serializable
data class Kalenderdokument(
    val id: String,
    val inhalt: Kalenderinhalt,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Ein Termin in der Sicherung. */
@Serializable
data class Termindokument(
    val id: String,
    val inhalt: Termininhalt,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

fun EventCalendarEntity.alsInhalt() = Kalenderinhalt(name, colorArgb, sortIndex, createdAt)

fun EventCalendarEntity.alsDokument() = Kalenderdokument(id, alsInhalt(), updatedAt, deletedAt)

fun Kalenderdokument.alsKalender() = EventCalendarEntity(
    id = id,
    name = inhalt.name,
    colorArgb = inhalt.colorArgb,
    sortIndex = inhalt.sortIndex,
    createdAt = inhalt.createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun EventEntity.alsInhalt() = Termininhalt(
    calendarId = calendarId,
    art = art,
    title = title,
    description = description,
    location = location,
    allDay = allDay,
    startAt = startAt,
    endAt = endAt,
    timeZone = timeZone,
    recurrence = recurrence,
    repeatInterval = repeatInterval,
    repeatDays = repeatDays,
    repeatUntil = repeatUntil,
    repeatCount = repeatCount,
    exceptions = exceptions,
    reminders = reminders,
    important = important,
    colorArgb = colorArgb,
    symbol = symbol,
    bannerId = bannerId,
    done = done,
    undated = undated,
    createdAt = createdAt,
)

fun EventEntity.alsDokument() = Termindokument(id, alsInhalt(), updatedAt, deletedAt)

fun Termindokument.alsTermin() = EventEntity(
    id = id,
    calendarId = inhalt.calendarId,
    art = inhalt.art,
    title = inhalt.title,
    description = inhalt.description,
    location = inhalt.location,
    allDay = inhalt.allDay,
    startAt = inhalt.startAt,
    endAt = maxOf(inhalt.startAt, inhalt.endAt),
    timeZone = inhalt.timeZone,
    recurrence = inhalt.recurrence,
    repeatInterval = maxOf(1, inhalt.repeatInterval),
    repeatDays = inhalt.repeatDays.filter { it in 1..7 }.distinct().sorted(),
    repeatUntil = inhalt.repeatUntil,
    repeatCount = inhalt.repeatCount?.takeIf { it > 0 },
    exceptions = inhalt.exceptions,
    reminders = inhalt.reminders,
    important = inhalt.important,
    colorArgb = inhalt.colorArgb,
    symbol = inhalt.symbol,
    // Nur eine Kennung, die als Dateiname taugt (SYNC.md 16.2).
    bannerId = inhalt.bannerId?.takeIf { id -> id.isNotEmpty() && id.length <= 64 && id.all { it.isLetterOrDigit() || it == '-' } },
    done = inhalt.done,
    undated = inhalt.undated,
    createdAt = inhalt.createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/** Aus einer Huelle das Dokument; `null` bei DELETED. */
fun Huelle<Kalenderinhalt>.alsKalenderdokument(): Kalenderdokument? = payload?.let {
    Kalenderdokument(id, it, updatedAt, if (state == Zustand.TRASHED) stateChangedAt else null)
}

fun Huelle<Termininhalt>.alsTermindokument(): Termindokument? = payload?.let {
    Termindokument(id, it, updatedAt, if (state == Zustand.TRASHED) stateChangedAt else null)
}
