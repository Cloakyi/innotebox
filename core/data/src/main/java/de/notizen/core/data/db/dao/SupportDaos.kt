package de.notizen.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.notizen.core.data.db.entity.ArchiveRunEntity
import de.notizen.core.data.db.entity.ArchiveRunItemEntity
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.db.relation.Notiztermin
import de.notizen.core.data.db.entity.SyncStateEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    suspend fun of(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE remoteId IS NULL")
    suspend fun notYetUploaded(): List<AttachmentEntity>

    /**
     * Alle Anhangszeilen.
     *
     * Fuer das Wiederherstellen: Bevor eine Datei aus dem Archiv geschrieben
     * wird, muss feststehen, ob es sie hier schon gibt. Sie sonst zu
     * ueberschreiben waere ueberfluessig, und eine zweite Kopie daneben
     * anzulegen waere schlimmer als ueberfluessig.
     */
    @Query("SELECT * FROM attachments")
    suspend fun alle(): List<AttachmentEntity>

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    /**
     * Hintergrund-Anhaenge einer Notiz, auf die NICHTS mehr zeigt.
     *
     * Ein solcher Anhang ist ueber keine Oberflaeche mehr erreichbar -- er
     * erscheint nicht im Raster (das zeigt nur INHALT) und ist nicht die
     * Flaeche. Ohne dieses Aufraeumen sammelte jedes Wechseln des
     * Hintergrundbildes eine weitere unerreichbare Datei an.
     */
    @Query(
        """
        SELECT a.* FROM attachments a
        JOIN notes n ON n.id = a.noteId
        WHERE a.noteId = :noteId
          AND a.role = 'HINTERGRUND'
          AND (n.backgroundAttachmentId IS NULL OR n.backgroundAttachmentId != a.id)
        """,
    )
    suspend fun verwaisteHintergruende(noteId: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    /**
     * Zu welcher Notiz ein Anhang gehoert.
     *
     * Braucht der Abgleich: Ein geaenderter Anhang bedeutet eine geaenderte
     * Notiz-Datei in Drive, denn die Anhangsdaten liegen in ihr eingebettet.
     * Ohne diesen Rueckweg bliebe ein neues Bild ewig liegen -- die Notiz selbst
     * gilt ja als unveraendert.
     */
    @Query("SELECT noteId FROM attachments WHERE id = :id")
    suspend fun noteIdOf(id: String): String?
}

@Dao
interface TranscriptDao {

    @Query("SELECT * FROM transcripts WHERE noteId = :noteId ORDER BY startMs ASC")
    suspend fun of(noteId: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE noteId = :noteId ORDER BY startMs ASC")
    fun observeOf(noteId: String): Flow<List<TranscriptEntity>>

    @Upsert
    suspend fun upsertAll(segments: List<TranscriptEntity>)

    @Query("DELETE FROM transcripts WHERE noteId = :noteId")
    suspend fun deleteAllOf(noteId: String)

    /** Siehe [AttachmentDao.noteIdOf]. */
    @Query("SELECT noteId FROM transcripts WHERE id = :id")
    suspend fun noteIdOf(id: String): String?

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE noteId = :noteId")
    suspend fun of(noteId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY triggerAt ASC")
    fun observeOf(noteId: String): Flow<List<ReminderEntity>>

    /**
     * Hoechste vergebene Alarmkennung.
     *
     * Grundlage fuer die naechste: aus der UUID abgeleitete Kennungen koennten
     * kollidieren, und zwei Erinnerungen mit derselben Kennung bestellen sich
     * gegenseitig ab. Das faende man im Nachhinein nie.
     */
    @Query("SELECT MAX(alarmId) FROM reminders")
    suspend fun maxAlarmId(): Int?

    /** Noch nicht ausgeloest -- diese Notizen sind vor Auto-Archiv geschuetzt. */
    @Query("SELECT * FROM reminders WHERE isFired = 0 ORDER BY triggerAt ASC")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isFired = 0 ORDER BY triggerAt ASC")
    fun observePending(): Flow<List<ReminderEntity>>

    /**
     * Die Erinnerungen eines Zeitraums, mit dem, was der Kalender davon zeigt.
     *
     * **Auch die bereits ausgeloesten.** In einem Kalender ist der letzte
     * Dienstag genauso eine Auskunft wie der naechste; nur kuenftige Termine zu
     * zeigen waere eine Liste und kein Kalender.
     *
     * Der Papierkorb bleibt draussen. Was weggeworfen ist, hat keinen Termin
     * mehr.
     */
    @Query(
        """
        SELECT r.noteId AS noteId,
               n.title AS title,
               n.colorId AS colorId,
               r.triggerAt AS triggerAt,
               r.isFired AS isFired
        FROM reminders r
        JOIN notes n ON n.id = r.noteId
        WHERE n.deletedAt IS NULL AND r.triggerAt >= :von AND r.triggerAt < :bis
        ORDER BY r.triggerAt ASC
        """,
    )
    fun observeImZeitraum(von: Long, bis: Long): Flow<List<Notiztermin>>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isFired = 1 WHERE id = :id")
    suspend fun markFired(id: String)

    /** Siehe [AttachmentDao.noteIdOf]. */
    @Query("SELECT noteId FROM reminders WHERE id = :id")
    suspend fun noteIdOf(id: String): String?

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)
}

/** Protokoll und Rueckabwicklung der Auto-Archiv-Laeufe. */
@Dao
interface ArchiveDao {

    @Query("SELECT * FROM archive_runs ORDER BY runAt DESC LIMIT :limit")
    fun observeRuns(limit: Int = 50): Flow<List<ArchiveRunEntity>>

    @Query("SELECT * FROM archive_runs WHERE batchId = :batchId")
    suspend fun run(batchId: String): ArchiveRunEntity?

    @Upsert
    suspend fun upsertRun(run: ArchiveRunEntity)

    @Upsert
    suspend fun upsertItems(items: List<ArchiveRunItemEntity>)

    /**
     * Was ein Lauf angefasst hat, inklusive der Stufe VOR dem Lauf.
     * Ohne diese Zeilen ist das Batch-Undo nicht baubar.
     */
    @Query("SELECT * FROM archive_run_items WHERE batchId = :batchId")
    suspend fun itemsOf(batchId: String): List<ArchiveRunItemEntity>

    @Query("UPDATE archive_runs SET undoneAt = :now WHERE batchId = :batchId")
    suspend fun markUndone(batchId: String, now: Long)
}

/**
 * Ordner. Seit Phase 13 in Gebrauch (vorher nur vorbereitet).
 *
 * Der Baum wird NICHT in SQL gebaut. Diese Abfragen liefern flache Zeilen; wer
 * daraus Eltern, Kinder und Pfade macht, ist `Ordnerregeln` -- und zwar in
 * reinem Kotlin, damit sich die Faelle pruefen lassen, die eine Datenbank nur
 * schwer nachstellt: ein Kreis im Baum und ein Ordner ohne Eltern.
 */
@Dao
interface FolderDao {

    /**
     * Die lebenden Ordner EINES Bereichs (SYNC.md 13). Der normale Baum und
     * der Archivbaum sind zwei Baeume; wer beide auf einmal holt, zeichnet
     * Archivordner in die Seitenspalte.
     */
    @Query(
        "SELECT * FROM folders WHERE deletedAt IS NULL AND bereich = :bereich " +
            "ORDER BY sortIndex ASC, name ASC",
    )
    fun observeAll(bereich: Bereich): Flow<List<FolderEntity>>

    @Query(
        "SELECT * FROM folders WHERE deletedAt IS NULL AND bereich = :bereich " +
            "ORDER BY sortIndex ASC, name ASC",
    )
    suspend fun getAll(bereich: Bereich): List<FolderEntity>

    /** Alle lebenden Ordner beider Bereiche. Fuer Pfade und Zaehlungen, die den Bereich kennen. */
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY sortIndex ASC, name ASC")
    suspend fun getAlleLebenden(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    /**
     * ALLE Ordner, auch die geloeschten.
     *
     * Fuer den Abgleich, aus demselben Grund wie bei den Tags: Ein geloeschter
     * Ordner ist keine fehlende Zeile, sondern eine mit `deletedAt`. Wer hier
     * nur die lebenden liefert, bekommt den Ordner beim naechsten Lauf vom
     * anderen Geraet zurueck.
     */
    @Query("SELECT * FROM folders")
    suspend fun getAllIncludingDeleted(): List<FolderEntity>

    /**
     * Wie viele Notizen unmittelbar in jedem Ordner liegen.
     *
     * Nur die unmittelbaren. Was in den Unterordnern liegt, zaehlt
     * `Ordnerregeln.gesamtzahlen` dazu -- eine Abfrage mit rekursivem CTE
     * waere hier die schwerere Loesung fuer dieselbe Antwort.
     *
     * Der Papierkorb bleibt aussen vor: Eine Zahl, die weggeworfene Notizen
     * mitzaehlt, verspricht einen Inhalt, den der Ordner nicht mehr zeigt.
     */
    @Query(
        """
        SELECT folderId AS ordnerId, COUNT(*) AS anzahl FROM notes
        WHERE folderId IS NOT NULL AND deletedAt IS NULL
        GROUP BY folderId
        """,
    )
    fun observeZaehlung(): Flow<List<Ordnerzaehlung>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Upsert
    suspend fun upsertAll(ordner: List<FolderEntity>)

    @Query("UPDATE folders SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE folders SET colorArgb = :colorArgb, updatedAt = :now WHERE id = :id")
    suspend fun recolor(id: String, colorArgb: Int?, now: Long)

    /** Die eigene Reihenfolge (Phase 14e). Geht mit nach Drive, deshalb steigt `updatedAt`. */
    @Query("UPDATE folders SET sortIndex = :sortIndex, updatedAt = :now WHERE id = :id")
    suspend fun setSortIndex(id: String, sortIndex: Int, now: Long)

    /**
     * Haengt einen Ordner von Hand woandershin.
     *
     * Leert `ehemaligerElternId`: Wer den Ordner selbst verschiebt, hat sich
     * entschieden, und der geloeschte Ordner im Papierkorb zeigt ihn nicht
     * mehr als frueheren Inhalt (SYNC.md 13).
     */
    @Query(
        "UPDATE folders SET parentId = :elternId, ehemaligerElternId = NULL, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun reparent(id: String, elternId: String?, now: Long)

    /**
     * Rueckt einen Ordner beim Loeschen seines Elternteils „nur der Ordner"
     * heraus und merkt sich, woher er kam.
     */
    @Query(
        "UPDATE folders SET parentId = :elternId, ehemaligerElternId = :ehemaligerElternId, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun herausruecken(id: String, elternId: String?, ehemaligerElternId: String, now: Long)

    /** Die lebenden Ordner, die aus [ehemaligerElternId] herausgerueckt wurden (Phase 14c). */
    @Query(
        "SELECT * FROM folders WHERE ehemaligerElternId = :ehemaligerElternId AND deletedAt IS NULL " +
            "ORDER BY sortIndex ASC, name ASC",
    )
    fun observeHerausgerueckte(ehemaligerElternId: String): Flow<List<FolderEntity>>

    /**
     * Weiches Loeschen, und das ist hier der ganze Weg -- ein Ordner wird nie
     * endgueltig entfernt.
     *
     * Anders als bei einem Tag, der beim Loeschen samt Grabstein verschwindet:
     * Der Abgleich schickt ausschliesslich Grabsteine vom Typ NOTE, und ein
     * ordentlich geloeschter Ordner braucht deshalb eine Zeile, die er
     * mitnehmen kann. Sie ist ein paar Dutzend Byte gross und darf bleiben.
     */
    @Query("UPDATE folders SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /**
     * Was im Papierkorb liegt.
     *
     * Der zuletzt weggeworfene Ordner steht oben, wie bei den Notizen auch.
     * Wer etwas sucht, sucht meistens das, was er eben verloren hat.
     */
    @Query("SELECT * FROM folders WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeImPapierkorb(): Flow<List<FolderEntity>>

    /** Ordner, die laenger als die Frist im Papierkorb liegen. Gemessen an `deletedAt`. */
    @Query("SELECT id FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :schwelle")
    suspend fun ueberfaelligImPapierkorb(schwelle: Long): List<String>

    /**
     * Holt einen Ordner aus dem Papierkorb zurueck.
     *
     * `updatedAt` steigt mit, sonst gewaenne beim naechsten Abgleich die
     * geloeschte Fassung des anderen Geraets, und der Ordner waere sofort
     * wieder weg.
     */
    @Query("UPDATE folders SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    /**
     * Endgueltig, ohne Grabstein.
     *
     * Fuer Ordner gibt es in Drive keine Grabsteine. Wer die Zeile hier
     * entfernt, nimmt dem anderen Geraet die einzige Nachricht ueber die
     * Loeschung weg -- deshalb wird das NUR aufgerufen, wenn der Ordner drueben
     * ohnehin schon verschwunden ist oder es nie einen Abgleich gab.
     */
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun purge(id: String)
}

/** Eine Zeile aus [FolderDao.observeZaehlung]. */
data class Ordnerzaehlung(
    val ordnerId: String,
    val anzahl: Int,
)

/** Sync-Buchfuehrung und Tombstones. Beides rein lokal bzw. lokal gefuehrt. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_state WHERE entityType = :type AND entityId = :id")
    suspend fun stateOf(type: EntityType, id: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE syncStatus = :status")
    suspend fun withStatus(status: SyncStatus): List<SyncStateEntity>

    /** Die ganze Buchfuehrung, fuer die Zaehlerstaende in der Sicherung. */
    @Query("SELECT * FROM sync_state")
    suspend fun alleStaende(): List<SyncStateEntity>

    @Query("SELECT COUNT(*) FROM sync_state WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    /**
     * Wie viele NOTIZEN noch nicht gesichert sind.
     *
     * Nicht dasselbe wie [observeUnsyncedCount], und der Unterschied ist der
     * Grund fuer diese zweite Abfrage:
     *
     * - Gezaehlt werden **Notizen**, nicht Buchungszeilen. Ein Bild und ein
     *   Transkript derselben Notiz sind eine offene Notiz, nicht drei.
     * - Notizen, die der Nutzer vom Abgleich **ausgenommen** hat, zaehlen nicht
     *   mit. Sie sollen ja gar nicht hoch -- eine Anzeige, die deswegen fuer
     *   immer "nicht gesichert" sagt, waere schlicht falsch.
     * - Anhaenge, Transkripte und Erinnerungen zaehlen ueber ihre Notiz mit.
     *   Sie liegen eingebettet in deren Datei und haben drueben keine eigene
     *   Adresse.
     */
    @Query(
        """
        SELECT COUNT(*) FROM notes n
        WHERE n.syncEnabled = 1 AND EXISTS (
            SELECT 1 FROM sync_state s WHERE s.syncStatus != 'SYNCED' AND (
                (s.entityType = 'NOTE' AND s.entityId = n.id)
                OR (s.entityType = 'ATTACHMENT'
                    AND s.entityId IN (SELECT id FROM attachments WHERE noteId = n.id))
                OR (s.entityType = 'TRANSCRIPT'
                    AND s.entityId IN (SELECT id FROM transcripts WHERE noteId = n.id))
                OR (s.entityType = 'REMINDER'
                    AND s.entityId IN (SELECT id FROM reminders WHERE noteId = n.id))
            )
        )
        """,
    )
    fun observeOffeneNotizen(): Flow<Int>

    /**
     * Dieselbe Frage, aber mit den Kennungen.
     *
     * Die Uebersicht zeigt an der einzelnen Karte, ob GERADE DIESE Notiz noch
     * aussteht. Eine blosse Zahl in der Kopfzeile sagt "irgendwo haengt etwas"
     * -- und laesst offen, wo.
     */
    @Query(
        """
        SELECT n.id FROM notes n
        WHERE n.syncEnabled = 1 AND EXISTS (
            SELECT 1 FROM sync_state s WHERE s.syncStatus != 'SYNCED' AND (
                (s.entityType = 'NOTE' AND s.entityId = n.id)
                OR (s.entityType = 'ATTACHMENT'
                    AND s.entityId IN (SELECT id FROM attachments WHERE noteId = n.id))
                OR (s.entityType = 'TRANSCRIPT'
                    AND s.entityId IN (SELECT id FROM transcripts WHERE noteId = n.id))
                OR (s.entityType = 'REMINDER'
                    AND s.entityId IN (SELECT id FROM reminders WHERE noteId = n.id))
            )
        )
        """,
    )
    fun observeOffeneNotizIds(): Flow<List<String>>

    @Upsert
    suspend fun upsertState(state: SyncStateEntity)

    @Upsert
    suspend fun upsertStates(states: List<SyncStateEntity>)

    /**
     * Markiert eine Entitaet als aenderungsbeduerftig.
     *
     * Bewusst KEIN @Upsert mit einem frisch gebauten Objekt: das wuerde
     * `remoteId`, `remoteRevision` und `lastSyncedAt` ueberschreiben und damit
     * die Zuordnung zur Drive-Datei verlieren. Beim Konflikt werden deshalb
     * gezielt nur die zwei Felder angefasst, die sich wirklich aendern.
     */
    @Query(
        """
        INSERT INTO sync_state (entityType, entityId, remoteId, remoteRevision,
                                localUpdatedAt, lastSyncedAt, syncStatus, baseRev)
        VALUES (:type, :id, NULL, NULL, :localUpdatedAt, NULL, 'DIRTY', 0)
        ON CONFLICT(entityType, entityId) DO UPDATE SET
            localUpdatedAt = :localUpdatedAt,
            syncStatus = 'DIRTY'
        """,
    )
    suspend fun markDirty(type: EntityType, id: String, localUpdatedAt: Long)

    /**
     * Alle Eintraege wieder auf Anfang: DIRTY, ohne Drive-Kennung, `baseRev` 0.
     *
     * Fuer die Umstellung auf Schema 4 (SYNC.md 11) und fuer das Einlesen
     * eines Snapshots (SYNC.md 10): Danach geht alles als neue Fassung hoch.
     */
    @Query(
        """
        UPDATE sync_state SET syncStatus = 'DIRTY', remoteId = NULL,
            remoteRevision = NULL, baseRev = 0
        """,
    )
    suspend fun allesNeuVormerken()

    /** Der Zaehlerstand, mit dem zuletzt abgeglichen wurde. Null = nie. */
    @Query("SELECT baseRev FROM sync_state WHERE entityType = :type AND entityId = :id")
    suspend fun baseRevOf(type: EntityType, id: String): Long?

    @Query("DELETE FROM sync_state WHERE entityType = :type AND entityId = :id")
    suspend fun dropState(type: EntityType, id: String)


    /**
     * Hakt eine Entitaet als abgeglichen ab.
     *
     * Fuer Anhaenge, Transkripte und Erinnerungen: Sie gehen eingebettet in der
     * Notiz-Datei hoch und haben keine eigene Drive-Kennung. Ohne dieses
     * Abhaken blieben ihre Zeilen fuer immer auf DIRTY stehen, und der Zaehler
     * der ungesicherten Aenderungen faende nie zur Null zurueck.
     */
    @Query(
        """
        UPDATE sync_state SET syncStatus = 'SYNCED', lastSyncedAt = :now
        WHERE entityType = :type AND entityId = :id
        """,
    )
    suspend fun markSynced(type: EntityType, id: String, now: Long)

    /**
     * Hakt eine Entitaet mit ihrem neuen Zaehlerstand ab (SYNC.md 6).
     *
     * Legt die Zeile an, falls es sie nicht gibt: Eine von drueben geholte
     * Entitaet hatte vorher keinen Eintrag.
     */
    @Query(
        """
        INSERT INTO sync_state (entityType, entityId, remoteId, remoteRevision,
                                localUpdatedAt, lastSyncedAt, syncStatus, baseRev)
        VALUES (:type, :id, :remoteId, :remoteRevision, :localUpdatedAt, :now, 'SYNCED', :rev)
        ON CONFLICT(entityType, entityId) DO UPDATE SET
            remoteId = :remoteId,
            remoteRevision = :remoteRevision,
            localUpdatedAt = :localUpdatedAt,
            lastSyncedAt = :now,
            syncStatus = 'SYNCED',
            baseRev = :rev
        """,
    )
    suspend fun abgeglichen(
        type: EntityType,
        id: String,
        rev: Long,
        remoteId: String?,
        remoteRevision: String?,
        localUpdatedAt: Long,
        now: Long,
    )

    // -------------------------------------------------------------- Tombstones

    @Upsert
    suspend fun upsertTombstone(tombstone: TombstoneEntity)

    @Upsert
    suspend fun upsertTombstones(tombstones: List<TombstoneEntity>)

    @Query("SELECT * FROM tombstones WHERE entityType = :type")
    suspend fun tombstonesOf(type: EntityType): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones")
    suspend fun allTombstones(): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE entityType = :type AND entityId = :id")
    suspend fun tombstone(type: EntityType, id: String): TombstoneEntity?

    /**
     * Entfernt einen Grabstein.
     *
     * NUR fuer Anhaenge gedacht. Deren Grabstein ist kein Vertrag mit dem
     * anderen Geraet, sondern ein Merkzettel fuer dieses hier: "die Datei in
     * Drive muss noch weg". Ist sie weg, hat er seinen Zweck erfuellt.
     *
     * Fuer NOTIZEN gilt das ausdruecklich NICHT -- ihr Grabstein ist das
     * einzige, was die geloeschte Notiz davon abhaelt, vom anderen Geraet
     * zurueckzukommen.
     */
    @Query("DELETE FROM tombstones WHERE entityType = :type AND entityId = :id")
    suspend fun dropTombstone(type: EntityType, id: String)

    /**
     * Aufraeumen alter Tombstones. NUR aufrufen, wenn sicher ist, dass beide
     * Clients laengst synchronisiert haben -- sonst kann ein geloeschter
     * Datensatz wiederauferstehen.
     */
    @Query("DELETE FROM tombstones WHERE deletedAt < :aelterAls")
    suspend fun pruneTombstones(aelterAls: Long)
}
