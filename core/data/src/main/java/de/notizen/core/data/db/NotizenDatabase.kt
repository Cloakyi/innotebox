package de.notizen.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import de.notizen.core.data.db.dao.ArchiveDao
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.KalenderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.dao.SearchDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TagDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.db.entity.ArchiveRunEntity
import de.notizen.core.data.db.entity.ArchiveRunItemEntity
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteFtsEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.db.entity.SyncStateEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.db.entity.TranscriptEntity

/**
 * Die lokale Datenbank. Auf jedem Client die Quelle der Wahrheit (offline-first).
 *
 * MIGRATIONSSTRATEGIE
 * -------------------
 * `exportSchema = true` schreibt das Schema nach core/data/schemas/. Diese
 * Dateien gehoeren in die Versionskontrolle -- ohne sie kann Room Migrationen
 * weder erzeugen noch verifizieren.
 *
 * Regeln fuer jede kuenftige Schema-Aenderung:
 *
 *  1. ZUERST SYNC.md aendern, dann den Code. SYNC.md ist der Vertrag mit dem
 *     Web-Client, und der liest dieselben Daten.
 *  2. `version` hochzaehlen und eine explizite Migration in [MIGRATIONS]
 *     ergaenzen. `fallbackToDestructiveMigration` wird NICHT verwendet -- das
 *     wuerde beim ersten Fehler die lokale Quelle der Wahrheit loeschen.
 *  3. Additiv bleiben, wo es geht: neue Spalte mit Defaultwert statt
 *     Umbenennen. Umbenennen oder Loeschen einer Spalte, die in SYNC.md als
 *     sync-relevant steht, ist ein Bruch fuer den anderen Client und braucht
 *     eine Anhebung der Schema-Version in SYNC.md.
 *  4. Zu jeder Migration gehoert ein Test.
 */
@Database(
    version = NotizenDatabase.VERSION,
    exportSchema = true,
    entities = [
        NoteEntity::class,
        NoteItemEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
        TagEntity::class,
        FolderEntity::class,
        AttachmentEntity::class,
        TranscriptEntity::class,
        ReminderEntity::class,
        ArchiveRunEntity::class,
        ArchiveRunItemEntity::class,
        SyncStateEntity::class,
        TombstoneEntity::class,
        EventCalendarEntity::class,
        EventEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class NotizenDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun tagDao(): TagDao

    abstract fun searchDao(): SearchDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun reminderDao(): ReminderDao

    abstract fun archiveDao(): ArchiveDao

    abstract fun folderDao(): FolderDao

    abstract fun syncDao(): SyncDao

    abstract fun kalenderDao(): KalenderDao

    companion object {
        const val VERSION = 11
        const val NAME = "notizen.db"

        /**
         * Schema 1 -> 2: `notes.backgroundAttachmentId` (Bildnotizen).
         *
         * Rein additiv, nullbar, ohne Default -- eine bestehende Datenbank
         * bekommt in jeder Zeile NULL, und das ist genau der richtige Wert:
         * keine Notiz hatte vorher ein Hintergrundbild.
         *
         * Bewusst KEIN fallbackToDestructiveMigration: die lokale Datenbank ist
         * die Quelle der Wahrheit, und solange kein Sync laeuft, ist sie die
         * EINZIGE Kopie. Ein fehlgeschlagener Aufstieg darf sie nicht raeumen.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE notes ADD COLUMN backgroundAttachmentId TEXT DEFAULT NULL",
                )
            }
        }

        /**
         * Schema 2 -> 3: `attachments.role` (Hintergrundbild ohne Notizbild).
         *
         * NOT NULL mit Default, weil jede bestehende Zeile ein Bild oder eine
         * Aufnahme der Notiz ist -- also INHALT. Der Default steht bewusst auch
         * in der Spaltendefinition und nicht nur im UPDATE: Room vergleicht das
         * erzeugte Schema mit dem exportierten, und ein fehlender Default macht
         * die Validierung rot.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE attachments ADD COLUMN role TEXT NOT NULL DEFAULT 'INHALT'",
                )
            }
        }

        /**
         * Schema 3 -> 4: `notes.syncEnabled` (Notiz vom Abgleich ausnehmen).
         *
         * NOT NULL mit Default 1: Jede bestehende Notiz wurde bisher
         * abgeglichen, und eine Migration, die das stillschweigend abstellte,
         * waere die schlechteste Ueberraschung von allen.
         *
         * Die Spalte ist REIN LOKAL und steht nicht im Drive-Dokument. Die
         * Dokument-Version bleibt deshalb bei 3 -- siehe SYNC.md 14.2a.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE notes ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        /**
         * Schema 4 -> 5: die beiden Kalenderfelder an `notes`.
         *
         * Beide REIN LOKAL, wie `syncEnabled`. `calendarEnabled` steht auf 1,
         * damit eine bestehende Notiz sich verhaelt wie eine neue: Der
         * Hauptschalter entscheidet, nicht ein Feld, das nie jemand gesetzt hat.
         * `calendarEventId` bleibt NULL, denn es gab noch keine Termine.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE notes ADD COLUMN calendarEnabled INTEGER NOT NULL DEFAULT 1",
                )
                connection.execSQL("ALTER TABLE notes ADD COLUMN calendarEventId INTEGER")
            }
        }

        /**
         * Schema 5 -> 6: der Abgleich nach Schema 4 (SYNC.md).
         *
         * Drei neue Spalten und ein Neuanfang der Buchfuehrung:
         *  - `notes.origin`: ob die letzte Fassung von aussen kam. Alles
         *    Bestehende ist von der App, also APP.
         *  - `sync_state.baseRev`: der Zaehlerstand des letzten Abgleichs.
         *  - `tombstones.rev`: der Zaehlerstand der DELETED-Fassung.
         *
         * Der Neuanfang (SYNC.md 11): Der lokale Stand ist die Wahrheit.
         * Jeder Eintrag wird DIRTY, verliert seine Drive-Kennung und faengt bei
         * `baseRev` 0 an; jede Anhangsdatei gilt als noch nicht oben. Damit geht
         * beim naechsten Lauf alles als Schema 4 in den neuen Ordner
         * `InNoteBox`. Der alte Ordner bleibt liegen. Am 2026-09-15
         * so entschieden, Verluste eingeschlossen.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE notes ADD COLUMN origin TEXT NOT NULL DEFAULT 'APP'",
                )
                connection.execSQL(
                    "ALTER TABLE sync_state ADD COLUMN baseRev INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    "ALTER TABLE tombstones ADD COLUMN rev INTEGER NOT NULL DEFAULT 1",
                )
                connection.execSQL(
                    "UPDATE sync_state SET syncStatus = 'DIRTY', remoteId = NULL, " +
                        "remoteRevision = NULL, baseRev = 0",
                )
                connection.execSQL("UPDATE attachments SET remoteId = NULL")
            }
        }

        /**
         * Schema 6 -> 7: die Felder fuer das Archiv im Ordnermodus und den
         * Papierkorb (SYNC.md 13, Dokument-Schema 5).
         *
         * Eine Migration, nicht fuenf: Alles, was 14b bis 14e brauchen,
         * kommt hier auf einmal. Alle Spalten additiv:
         *  - `folders.bereich`, NOT NULL mit Default 'ORDNER': Jeder
         *    bestehende Ordner gehoert zum normalen Baum, ein Archiv gab es
         *    noch nicht.
         *  - `folders.ehemaligerElternId`, `notes.ehemaligerOrdnerId`: NULL,
         *    denn kein Ordner ist bisher „nur der Ordner" geloescht worden,
         *    ohne dass die Herkunft gemerkt wurde -- fuer die alten Faelle
         *    laesst sich das nicht nachtragen, und raten waere falsch.
         *  - `notes.ordnerArchiviertAt`, `notes.herkunftOrdnerId`: NULL, im
         *    Ordnermodus war noch nichts archiviert.
         *
         * Der Index auf `ordnerArchiviertAt` steht auch in der Entity; Room
         * vergleicht das erzeugte Schema mit dem exportierten, ein fehlender
         * Index macht die Validierung rot.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE folders ADD COLUMN bereich TEXT NOT NULL DEFAULT 'ORDNER'",
                )
                connection.execSQL("ALTER TABLE folders ADD COLUMN ehemaligerElternId TEXT DEFAULT NULL")
                connection.execSQL("ALTER TABLE notes ADD COLUMN ordnerArchiviertAt INTEGER DEFAULT NULL")
                connection.execSQL("ALTER TABLE notes ADD COLUMN herkunftOrdnerId TEXT DEFAULT NULL")
                connection.execSQL("ALTER TABLE notes ADD COLUMN ehemaligerOrdnerId TEXT DEFAULT NULL")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_ordnerArchiviertAt` " +
                        "ON `notes` (`ordnerArchiviertAt`)",
                )
            }
        }

        /**
         * Schema 7 -> 8: der Kalender der App (SYNC.md 16, Dokument-Schema 6).
         *
         * Zwei neue Tabellen, keine bestehende aendert sich. Die Anweisungen
         * sind aus dem exportierten Schema 8 abgeschrieben; Room vergleicht
         * beim Oeffnen Zeichen fuer Zeichen.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(SQL_KALENDER)
                connection.execSQL(SQL_TERMINE)
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_event_calendars_deletedAt` " +
                        "ON `event_calendars` (`deletedAt`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_events_calendarId` ON `events` (`calendarId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_events_deletedAt` ON `events` (`deletedAt`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_events_startAt` ON `events` (`startAt`)",
                )
            }
        }

        /**
         * Schema 8 -> 9: Aufgaben ohne Datum (SYNC.md 16.2, `undated`).
         * Eine Spalte, Vorgabe nein; bestehende Termine bleiben, wie sie sind.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE events ADD COLUMN undated INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Schema 9 -> 10: benutzerdefinierte Serien (SYNC.md 16.2). Vier Spalten,
         * die Vorgaben ergeben genau die bisherigen Serien.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE events ADD COLUMN repeatInterval INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE events ADD COLUMN repeatDays TEXT NOT NULL DEFAULT '[]'")
                connection.execSQL("ALTER TABLE events ADD COLUMN repeatUntil INTEGER")
                connection.execSQL("ALTER TABLE events ADD COLUMN repeatCount INTEGER")
            }
        }

        /** Schema 10 -> 11: ein eigenes Bild ueber dem Termin (SYNC.md 16.2, `bannerId`). */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE events ADD COLUMN bannerId TEXT")
            }
        }

        private const val SQL_KALENDER =
            "CREATE TABLE IF NOT EXISTS `event_calendars` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorArgb` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))"

        private const val SQL_TERMINE =
            "CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `calendarId` TEXT NOT NULL, " +
                "`art` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`location` TEXT NOT NULL, `allDay` INTEGER NOT NULL, `startAt` INTEGER NOT NULL, " +
                "`endAt` INTEGER NOT NULL, `timeZone` TEXT NOT NULL, `recurrence` TEXT NOT NULL, " +
                "`exceptions` TEXT NOT NULL, `reminders` TEXT NOT NULL, `important` INTEGER NOT NULL, " +
                "`colorArgb` INTEGER, `symbol` TEXT, `done` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))"

        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        )
    }
}
