package de.notizen.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.notizen.core.data.model.ArchiveTrigger
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.SyncStatus

/**
 * Erinnerung an einer Notiz. Siehe SYNC.md 14.8.
 *
 * Eine noch nicht ausgeloeste Erinnerung schuetzt die Notiz vor der
 * automatischen Archivierung.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index("triggerAt")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val triggerAt: Long,

    /** AlarmManager-Kennung. REIN LOKAL, jedes Geraet alarmiert fuer sich. */
    val alarmId: Int,

    /** REIN LOKAL. */
    val isFired: Boolean = false,
)

/**
 * Protokoll eines Auto-Archiv-Laufs. REIN LOKAL (SYNC.md 14.9) -- was der Lauf
 * bewirkt hat, synchronisiert ueber die `stage` der Notizen.
 */
@Entity(tableName = "archive_runs", indices = [Index("runAt")])
data class ArchiveRunEntity(
    @PrimaryKey val batchId: String,
    val runAt: Long,
    val noteCount: Int,
    val trigger: ArchiveTrigger,

    /** Gesetzt, wenn der Lauf per Undo zurueckgenommen wurde. */
    val undoneAt: Long? = null,
)

/**
 * Welche Notiz aus WELCHER Stufe ein Lauf archiviert hat.
 *
 * OHNE DIESE TABELLE IST DAS BATCH-UNDO NICHT BAUBAR: `archive_runs` kennt nur
 * Zaehlwerte, und `notes.autoArchivedBatchId` sagt nur DASS eine Notiz
 * betroffen war -- nicht, ob sie nach INBOX oder nach WORKSPACE zurueckgehoert.
 */
@Entity(
    tableName = "archive_run_items",
    primaryKeys = ["batchId", "noteId"],
    foreignKeys = [
        ForeignKey(
            entity = ArchiveRunEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("batchId"), Index("noteId")],
)
data class ArchiveRunItemEntity(
    val batchId: String,
    val noteId: String,

    /** Stufe VOR dem Lauf. Das ist der ganze Zweck dieser Tabelle. */
    val previousStage: Stage,
)

/**
 * Abgleichzustand je Entitaet. REIN LOKAL -- geht nie nach Drive, sonst wuerde
 * jeder Client die Sync-Buchfuehrung des anderen ueberschreiben.
 */
@Entity(tableName = "sync_state", primaryKeys = ["entityType", "entityId"])
data class SyncStateEntity(
    val entityType: EntityType,
    val entityId: String,
    val remoteId: String? = null,
    val remoteRevision: String? = null,

    /** `updatedAt` der Entitaet beim letzten erfolgreichen Abgleich. */
    val localUpdatedAt: Long = 0,
    val lastSyncedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.DIRTY,

    /**
     * Der Zaehlerstand `rev`, mit dem zuletzt erfolgreich abgeglichen wurde
     * (SYNC.md 4). Null heisst: noch nie oben gewesen. Ein Eintrag mit Status
     * DIRTY ist "lokal neuer als baseRev"; beim Hochladen wird
     * `max(baseRev, fern) + 1` geschrieben und hier gemerkt.
     */
    val baseRev: Long = 0,
)

/**
 * Endgueltig geloeschte Entitaet. Siehe SYNC.md 14.13.
 *
 * ZWEI STUFEN VON LOESCHUNG, NICHT VERWECHSELN:
 *  1. `deletedAt` an der Entitaet gesetzt -> Papierkorb, wiederherstellbar,
 *     die Zeile existiert weiter.
 *  2. Eintrag hier -> endgueltig. Die Zeile ist weg, nur die ID ueberlebt,
 *     damit der andere Client sie nicht wiederaufleben laesst.
 */
@Entity(tableName = "tombstones", primaryKeys = ["entityType", "entityId"])
data class TombstoneEntity(
    val entityType: EntityType,
    val entityId: String,
    val deletedAt: Long,

    /**
     * Der Zaehlerstand der DELETED-Fassung in Drive (SYNC.md 3.2 und 6.2):
     * `baseRev + 1` zum Zeitpunkt des Loeschens. Damit gewinnt der Grabstein
     * gegen jede aeltere Fassung, ganz ohne Sonderfall.
     */
    val rev: Long = 1,
)
