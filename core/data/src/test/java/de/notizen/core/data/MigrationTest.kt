package de.notizen.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import de.notizen.core.data.db.NotizenDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migrationen der lokalen Datenbank.
 *
 * **Der Grund, warum es diesen Test überhaupt gibt:** Die App benutzt bewusst
 * kein `fallbackToDestructiveMigration`. Die lokale Datenbank ist die Quelle
 * der Wahrheit, und solange kein Sync läuft, ist sie die einzige Kopie — eine
 * fehlgeschlagene Migration ist damit kein Schönheitsfehler, sondern der
 * Verlust aller Notizen des Nutzers.
 *
 * `runMigrationsAndValidate` prüft zweierlei: dass die Migration ohne Fehler
 * läuft **und** dass das Ergebnis exakt dem exportierten Schema entspricht.
 * Der zweite Teil ist der wertvollere — er fängt genau den Fall ab, dass jemand
 * eine Spalte in der Entity ergänzt und die Migration dazu vergisst.
 *
 * Benutzt wird der **treiberbasierte** Konstruktor des Helfers. Die ältere
 * Fassung mit `FrameworkSQLiteOpenHelperFactory` reicht dem Treiber einen
 * blossen Dateinamen, während Room ihn zum absoluten Pfad auflöst — das
 * scheitert unter Robolectric mit „This driver is configured to open a database
 * named …". Hier wird die Datei direkt benannt, und die Frage stellt sich nicht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val datei = File(
        RuntimeEnvironment.getApplication().cacheDir,
        "migrationstest.db",
    ).also { it.delete() }

    @get:Rule
    val helfer = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = datei,
        driver = AndroidSQLiteDriver(),
        databaseClass = NotizenDatabase::class,
    )

    private fun SQLiteConnection.einWert(sql: String, spalte: Int): String? =
        prepare(sql).use { satz ->
            assertTrue("Keine Zeile für: $sql", satz.step())
            if (satz.isNull(spalte)) null else satz.getText(spalte)
        }

    @Test
    fun `von Schema 1 auf 2 -- backgroundAttachmentId kommt dazu`() {
        helfer.createDatabase(1).use { alt ->
            // Eine Notiz aus der Zeit VOR der Spalte. Genau die muss den
            // Aufstieg überstehen — eine leere Datenbank zu migrieren beweist
            // nichts.
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt
                ) VALUES (
                    'alt-1', 'INBOX', 'TEXT', 'Vor der Migration', 'Text bleibt',
                    'GREEN', 0, NULL, NULL, 0, 100, 200, 300, 100, NULL, NULL
                )
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(2, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            val sql = "SELECT title, body, colorId, backgroundAttachmentId " +
                "FROM notes WHERE id = 'alt-1'"
            assertEquals("Vor der Migration", neu.einWert(sql, 0))
            assertEquals("Text bleibt", neu.einWert(sql, 1))
            assertEquals("GREEN", neu.einWert(sql, 2))
            // Der richtige Wert für eine Notiz, die nie ein Hintergrundbild
            // hatte. Ein Default wie "" wäre ein Verweis auf einen Anhang
            // namens Leerstring.
            assertEquals(null, neu.einWert(sql, 3))
        }
    }

    @Test
    fun `von Schema 2 auf 3 -- attachments bekommen eine Rolle`() {
        helfer.createDatabase(2).use { alt ->
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt,
                    backgroundAttachmentId
                ) VALUES (
                    'n-1', 'INBOX', 'IMAGE', 'Mit Bild', '', 'DEFAULT', 0, NULL,
                    NULL, 0, 100, 200, 300, 100, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
            alt.execSQL(
                """
                INSERT INTO attachments (id, noteId, localPath, mimeType, sizeBytes, hash, remoteId)
                VALUES ('a-1', 'n-1', '/pfad/bild.jpg', 'image/jpeg', 42, 'abc', NULL)
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(3, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            // Bestehende Anhaenge sind Bilder oder Aufnahmen DER NOTIZ, also
            // INHALT. Ein Default von HINTERGRUND liesse sie alle aus dem
            // Raster verschwinden -- und das saehe aus wie Datenverlust.
            assertEquals(
                "INHALT",
                neu.einWert("SELECT role FROM attachments WHERE id = 'a-1'", 0),
            )
            assertEquals(
                "/pfad/bild.jpg",
                neu.einWert("SELECT localPath FROM attachments WHERE id = 'a-1'", 0),
            )
        }
    }

    @Test
    fun `von Schema 3 auf 4 -- bestehende Notizen werden weiter abgeglichen`() {
        helfer.createDatabase(3).use { alt ->
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt,
                    backgroundAttachmentId
                ) VALUES (
                    'n-3', 'INBOX', 'TEXT', 'Vor dem Schalter', 'Text', 'DEFAULT', 0,
                    NULL, NULL, 0, 100, 200, 300, 100, NULL, NULL, NULL
                )
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(4, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            // Der einzig vertretbare Default. Jede bestehende Notiz wurde
            // bisher abgeglichen; eine Migration, die das stillschweigend
            // abstellt, nimmt dem Nutzer sein Backup, ohne es zu sagen.
            assertEquals(
                "1",
                neu.einWert("SELECT syncEnabled FROM notes WHERE id = 'n-3'", 0),
            )
        }
    }

    @Test
    fun `von Schema 4 auf 5 -- die Kalenderfelder kommen dazu`() {
        helfer.createDatabase(4).use { alt ->
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt,
                    backgroundAttachmentId, syncEnabled
                ) VALUES (
                    'n-4', 'INBOX', 'TEXT', 'Vor dem Kalender', 'Text', 'DEFAULT', 0,
                    NULL, NULL, 0, 100, 200, 300, 100, NULL, NULL, NULL, 1
                )
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(5, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            // Bestehende Notizen verhalten sich wie neue: Der Hauptschalter
            // entscheidet, nicht ein Feld, das nie jemand gesetzt hat.
            assertEquals(
                "1",
                neu.einWert("SELECT calendarEnabled FROM notes WHERE id = 'n-4'", 0),
            )
            // Es gab noch keine Termine, also darf hier auch keiner stehen.
            assertNull(neu.einWert("SELECT calendarEventId FROM notes WHERE id = 'n-4'", 0))
        }
    }

    /**
     * Der Neuanfang fuer Schema 4 (SYNC.md 11): Buchfuehrung auf DIRTY und
     * ohne Drive-Kennung, Anhaenge gelten als noch nicht oben, Grabsteine
     * bekommen einen Zaehlerstand, Notizen eine Herkunft.
     */
    @Test
    fun `von Schema 5 auf 6 -- die Buchfuehrung faengt von vorn an`() {
        helfer.createDatabase(5).use { alt ->
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt,
                    backgroundAttachmentId, syncEnabled, calendarEnabled, calendarEventId
                ) VALUES (
                    'n-5', 'INBOX', 'TEXT', 'Vor Schema 4', 'Text', 'DEFAULT', 0,
                    NULL, NULL, 0, 100, 200, 300, 100, NULL, NULL, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            alt.execSQL(
                """
                INSERT INTO sync_state (entityType, entityId, remoteId, remoteRevision,
                    localUpdatedAt, lastSyncedAt, syncStatus)
                VALUES ('NOTE', 'n-5', 'drive-1', 'rev-1', 200, 250, 'SYNCED')
                """.trimIndent(),
            )
            alt.execSQL(
                """
                INSERT INTO attachments (id, noteId, localPath, mimeType, sizeBytes, hash,
                    remoteId, role)
                VALUES ('a-5', 'n-5', '/tmp/a', 'image/jpeg', 1, 'h', 'drive-2', 'INHALT')
                """.trimIndent(),
            )
            alt.execSQL(
                "INSERT INTO tombstones (entityType, entityId, deletedAt) VALUES ('NOTE', 'weg', 50)",
            )
        }

        helfer.runMigrationsAndValidate(6, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            assertEquals("APP", neu.einWert("SELECT origin FROM notes WHERE id = 'n-5'", 0))
            assertEquals(
                "DIRTY",
                neu.einWert("SELECT syncStatus FROM sync_state WHERE entityId = 'n-5'", 0),
            )
            assertNull(neu.einWert("SELECT remoteId FROM sync_state WHERE entityId = 'n-5'", 0))
            assertEquals("0", neu.einWert("SELECT baseRev FROM sync_state WHERE entityId = 'n-5'", 0))
            assertNull(neu.einWert("SELECT remoteId FROM attachments WHERE id = 'a-5'", 0))
            assertEquals("1", neu.einWert("SELECT rev FROM tombstones WHERE entityId = 'weg'", 0))
        }
    }

    /**
     * Phase 14a (SYNC.md 13): die Felder fuer das Archiv im Ordnermodus und
     * den Papierkorb. Mit einer befuellten Datenbank, weil genau die den
     * Aufstieg ueberstehen muss: ein Ordner mit Unterordner, eine Notiz darin,
     * eine im Papierkorb.
     */
    @Test
    fun `von Schema 6 auf 7 -- Ordner gehoeren zum normalen Baum, nichts ist archiviert`() {
        helfer.createDatabase(6).use { alt ->
            alt.execSQL(
                """
                INSERT INTO folders (id, parentId, name, iconOrEmoji, colorArgb, sortIndex,
                    createdAt, updatedAt, deletedAt)
                VALUES ('o-1', NULL, 'Rechnungen', NULL, NULL, 2, 100, 100, NULL),
                       ('o-2', 'o-1', '2026', NULL, 4278190335, 0, 110, 110, NULL),
                       ('o-weg', NULL, 'Alt', NULL, NULL, 0, 120, 130, 130)
                """.trimIndent(),
            )
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt,
                    backgroundAttachmentId, syncEnabled, calendarEnabled, calendarEventId, origin
                ) VALUES
                    ('n-6', 'WORKSPACE', 'TEXT', 'Im Ordner', 'Text', 'DEFAULT', 0,
                     NULL, 'o-2', 0, 100, 200, 300, 100, NULL, NULL, NULL, 1, 1, NULL, 'APP'),
                    ('n-7', 'INBOX', 'TEXT', 'Weggeworfen', '', 'DEFAULT', 0,
                     NULL, 'o-weg', 0, 100, 200, 300, 100, NULL, 250, NULL, 1, 1, NULL, 'EXTERNAL')
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(7, NotizenDatabase.MIGRATIONS.toList()).use { neu ->
            // Jeder bestehende Ordner gehoert zum normalen Baum. Ein Archiv gab
            // es noch nicht, und ein Ordner mit leerem Bereich waere in keinem
            // der beiden Baeume zu sehen.
            assertEquals("ORDNER", neu.einWert("SELECT bereich FROM folders WHERE id = 'o-1'", 0))
            assertEquals("ORDNER", neu.einWert("SELECT bereich FROM folders WHERE id = 'o-2'", 0))
            assertEquals("ORDNER", neu.einWert("SELECT bereich FROM folders WHERE id = 'o-weg'", 0))
            assertNull(neu.einWert("SELECT ehemaligerElternId FROM folders WHERE id = 'o-2'", 0))
            // Der Baum, die Farbe und die Reihenfolge bleiben, wie sie waren.
            assertEquals("o-1", neu.einWert("SELECT parentId FROM folders WHERE id = 'o-2'", 0))
            assertEquals("4278190335", neu.einWert("SELECT colorArgb FROM folders WHERE id = 'o-2'", 0))
            assertEquals("2", neu.einWert("SELECT sortIndex FROM folders WHERE id = 'o-1'", 0))
            assertEquals("130", neu.einWert("SELECT deletedAt FROM folders WHERE id = 'o-weg'", 0))

            // Keine Notiz ist im Ordnermodus archiviert, keine kam aus einem
            // geloeschten Ordner: Das laesst sich fuer die Vergangenheit nicht
            // nachtragen, und raten waere falsch.
            for (id in listOf("n-6", "n-7")) {
                assertNull(neu.einWert("SELECT ordnerArchiviertAt FROM notes WHERE id = '$id'", 0))
                assertNull(neu.einWert("SELECT herkunftOrdnerId FROM notes WHERE id = '$id'", 0))
                assertNull(neu.einWert("SELECT ehemaligerOrdnerId FROM notes WHERE id = '$id'", 0))
            }
            assertEquals("o-2", neu.einWert("SELECT folderId FROM notes WHERE id = 'n-6'", 0))
            assertEquals("250", neu.einWert("SELECT deletedAt FROM notes WHERE id = 'n-7'", 0))
            assertEquals("EXTERNAL", neu.einWert("SELECT origin FROM notes WHERE id = 'n-7'", 0))
        }
    }

    @Test
    fun `der Weg von Schema 1 bis zur aktuellen Version laeuft am Stueck`() {
        // Jemand, der die App seit der ersten Fassung benutzt, springt nicht
        // von Version zu Version -- er macht einen Sprung ueber alle. Die
        // Einzeltests oben pruefen jede Stufe, dieser den ganzen Weg.
        helfer.createDatabase(1).use { alt ->
            alt.execSQL(
                """
                INSERT INTO notes (
                    id, stage, type, title, body, colorId, isFavorite, favoritedAt,
                    folderId, sortIndex, createdAt, updatedAt, lastOpenedAt,
                    stageChangedAt, autoArchivedBatchId, deletedAt
                ) VALUES (
                    'uralt', 'WORKSPACE', 'TEXT', 'Von Anfang an', 'bleibt',
                    'BLUE', 1, 50, NULL, 0, 10, 20, 30, 10, NULL, NULL
                )
                """.trimIndent(),
            )
        }

        helfer.runMigrationsAndValidate(
            NotizenDatabase.VERSION,
            NotizenDatabase.MIGRATIONS.toList(),
        ).use { neu ->
            assertEquals(
                "Von Anfang an",
                neu.einWert("SELECT title FROM notes WHERE id = 'uralt'", 0),
            )
            assertEquals("bleibt", neu.einWert("SELECT body FROM notes WHERE id = 'uralt'", 0))
            assertEquals(
                "1",
                neu.einWert("SELECT syncEnabled FROM notes WHERE id = 'uralt'", 0),
            )
            assertEquals(
                "1",
                neu.einWert("SELECT calendarEnabled FROM notes WHERE id = 'uralt'", 0),
            )
            assertNull(neu.einWert("SELECT ordnerArchiviertAt FROM notes WHERE id = 'uralt'", 0))
        }
    }

    @Test
    fun `alle Migrationen lueckenlos bis zur aktuellen Version`() {
        // Fängt den Fall ab, dass jemand VERSION hochzählt und die Migration
        // vergisst — dann klafft eine Lücke, und Room wirft am Gerät beim
        // Öffnen. Hier fällt es beim Bauen auf.
        val stufen = NotizenDatabase.MIGRATIONS
            .sortedBy { it.startVersion }
            .map { it.startVersion to it.endVersion }
        val erwartet = (1 until NotizenDatabase.VERSION).map { it to it + 1 }
        assertEquals(erwartet, stufen)
    }
}
