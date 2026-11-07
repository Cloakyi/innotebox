package de.notizen.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung

/**
 * Ein Kalender der App, siehe SYNC.md 16.1.
 *
 * Nicht zu verwechseln mit dem Kalender des Geraets, in den der
 * `Kalenderspiegel` Erinnerungen schreibt. Dieser hier lebt nur in der App
 * und geht ueber Drive, wie Tags und Ordner.
 *
 * Geloescht wird weich, aus demselben Grund wie bei Ordnern: Die Zeile mit
 * [deletedAt] ist die Nachricht an das andere Geraet.
 */
@Entity(
    tableName = "event_calendars",
    indices = [Index("deletedAt")],
)
data class EventCalendarEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Echter ARGB-Rohwert wie bei Tags. */
    val colorArgb: Int,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Ein Termin, eine Aufgabe oder ein Geburtstag, siehe SYNC.md 16.2.
 *
 * Kein Fremdschluessel auf den Kalender: Beim Abgleich kann ein Termin vor
 * seinem Kalender ankommen, und ein Fremdschluessel liesse den Schreibvorgang
 * dann scheitern. Ein Termin ohne auffindbaren Kalender erscheint im ersten.
 *
 * Ganztaegige Termine tragen in [startAt] und [endAt] Mitternacht UTC ihres
 * ersten und letzten Tages. Sie haengen am Datum, nicht an einem Zeitpunkt.
 */
@Entity(
    tableName = "events",
    indices = [Index("calendarId"), Index("deletedAt"), Index("startAt")],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val art: Terminart = Terminart.TERMIN,
    val title: String,
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startAt: Long,
    val endAt: Long,
    val timeZone: String,
    val recurrence: Wiederholung = Wiederholung.KEINE,
    /** Abstand der Serie in Einheiten von [recurrence]. */
    val repeatInterval: Int = 1,
    /** Nur woechentlich: Wochentage nach ISO, 1 Montag. Leer heisst der Tag des Beginns. */
    val repeatDays: List<Int> = emptyList(),
    /** Letzter Tag der Serie in der Form eines ganztaegigen `startAt`, oder `null`. */
    val repeatUntil: Long? = null,
    /** Anzahl der Vorkommen, oder `null`. Gilt nur ohne [repeatUntil]. */
    val repeatCount: Int? = null,
    /** Beginn ausgelassener Vorkommen einer Serie. Als JSON-Text gespeichert. */
    val exceptions: List<Long> = emptyList(),
    /** Minuten vor dem Beginn. Als JSON-Text gespeichert. */
    val reminders: List<Int> = emptyList(),
    /** Wichtig: klingelt als Wecker statt als Benachrichtigung. */
    val important: Boolean = false,
    /** `null` heisst die Farbe des Kalenders. */
    val colorArgb: Int? = null,
    /** Ein Symbolname aus `Terminsymbole`, oder `null`. */
    val symbol: String? = null,
    /** Eigenes Bild ueber dem Termin, als `<bannerId>.jpg` neben den Anhaengen. */
    val bannerId: String? = null,
    val done: Boolean = false,
    /**
     * Nur bei Aufgaben: ohne Datum. Sie steht dann nur in der Liste der
     * Aufgaben; [startAt] und [endAt] tragen den Tag des Anlegens und
     * bedeuten nichts.
     */
    val undated: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
