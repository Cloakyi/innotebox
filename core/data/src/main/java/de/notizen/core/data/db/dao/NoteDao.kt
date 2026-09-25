package de.notizen.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.dao.Ordnerzaehlung
import de.notizen.core.data.db.relation.Kalenderzeile
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.db.relation.Termineintrag
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.Stage
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf Notizen.
 *
 * WICHTIG: Kein Schreibzugriff hier zaehlt `updatedAt` selbst hoch. Das
 * entscheidet ausschliesslich das Repository, weil nur dort bekannt ist, ob
 * eine Aenderung eine echte INHALTS-Aenderung war. Siehe SYNC.md 14.2.
 */
@Dao
interface NoteDao {

    // ---------------------------------------------------------------- lesen

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteWithRelations?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteWithRelations?>

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getPlainByIds(ids: List<String>): List<NoteEntity>

    /**
     * Notizen einer Stufe, Favoriten oben (Spezifikation Abschnitt 7).
     * Papierkorb bleibt aussen vor.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE stage = :stage AND deletedAt IS NULL
        ORDER BY isFavorite DESC, createdAt DESC
        """,
    )
    fun observeByStageNewestFirst(stage: Stage): Flow<List<NoteWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE stage = :stage AND deletedAt IS NULL
        ORDER BY isFavorite DESC, updatedAt DESC
        """,
    )
    fun observeByStageRecentlyChanged(stage: Stage): Flow<List<NoteWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE stage = :stage AND deletedAt IS NULL
        ORDER BY isFavorite DESC, lastOpenedAt DESC
        """,
    )
    fun observeByStageRecentlyOpened(stage: Stage): Flow<List<NoteWithRelations>>

    /**
     * Notizen in einem Ordner. `null` ist der Hauptordner.
     *
     * ACHTUNG, `IS` UND NICHT `=`. In SQL ist `folderId = NULL` niemals wahr,
     * auch nicht fuer eine Zeile, in der wirklich NULL steht. Der Hauptordner
     * waere damit fuer immer leer. `IS` vergleicht auch NULL richtig.
     *
     * Die Unterordner bleiben aussen vor: Ein Ordner zeigt, was in ihm liegt,
     * nicht, was irgendwo unter ihm liegt. Sonst stuende dieselbe Notiz in
     * jedem Ordner ihres Weges noch einmal.
     *
     * [archiviert] waehlt den Baum (SYNC.md 13): `false` den normalen, `true`
     * das Archiv des Ordnermodus. Beide Baeume haben einen eigenen Hauptordner
     * (`folderId IS NULL`), deshalb reicht die Ordnerkennung allein nicht.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE folderId IS :ordnerId AND deletedAt IS NULL
          AND (ordnerArchiviertAt IS NOT NULL) = :archiviert
        ORDER BY isFavorite DESC, createdAt DESC
        """,
    )
    fun observeByFolderNewestFirst(ordnerId: String?, archiviert: Boolean): Flow<List<NoteWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE folderId IS :ordnerId AND deletedAt IS NULL
          AND (ordnerArchiviertAt IS NOT NULL) = :archiviert
        ORDER BY isFavorite DESC, updatedAt DESC
        """,
    )
    fun observeByFolderRecentlyChanged(ordnerId: String?, archiviert: Boolean): Flow<List<NoteWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE folderId IS :ordnerId AND deletedAt IS NULL
          AND (ordnerArchiviertAt IS NOT NULL) = :archiviert
        ORDER BY isFavorite DESC, lastOpenedAt DESC
        """,
    )
    fun observeByFolderRecentlyOpened(ordnerId: String?, archiviert: Boolean): Flow<List<NoteWithRelations>>

    /** Wie viele Notizen im Archiv des Ordnermodus liegen, ueber alle Archivordner. */
    @Query("SELECT COUNT(*) FROM notes WHERE ordnerArchiviertAt IS NOT NULL AND deletedAt IS NULL")
    fun observeAnzahlImOrdnerArchiv(): Flow<Int>

    /** Wie viele Notizen im normalen Ordnerbaum liegen, das Archiv nicht mitgezaehlt. */
    @Query("SELECT COUNT(*) FROM notes WHERE ordnerArchiviertAt IS NULL AND deletedAt IS NULL")
    fun observeAnzahlNichtArchiviert(): Flow<Int>

    /**
     * Archiviert Notizen im Ordnermodus (Phase 14b).
     *
     * Die Notiz landet oben im Archiv (`folderId` NULL im Archivbaum) und
     * merkt sich in `herkunftOrdnerId`, woher sie kam; SQLite liest dabei den
     * alten Wert von `folderId`. Die Stufe bleibt unangetastet: zwei getrennte
     * Systeme. `updatedAt` steigt, denn beides geht mit nach Drive.
     */
    @Query(
        """
        UPDATE notes
        SET ordnerArchiviertAt = :now, herkunftOrdnerId = folderId, folderId = NULL,
            ehemaligerOrdnerId = NULL, updatedAt = :now
        WHERE id IN (:ids) AND ordnerArchiviertAt IS NULL
        """,
    )
    suspend fun imOrdnerArchivieren(ids: List<String>, now: Long)

    /**
     * Holt Notizen aus dem Archiv des Ordnermodus in [zielId] zurueck. `null`
     * ist der Hauptordner des normalen Baums.
     */
    @Query(
        """
        UPDATE notes
        SET ordnerArchiviertAt = NULL, herkunftOrdnerId = NULL, folderId = :zielId,
            ehemaligerOrdnerId = NULL, updatedAt = :now
        WHERE id IN (:ids) AND ordnerArchiviertAt IS NOT NULL
        """,
    )
    suspend fun ausOrdnerArchivZurueck(ids: List<String>, zielId: String?, now: Long)

    /**
     * Nimmt ein Archivieren zurueck: jede Notiz wieder dorthin, woher sie
     * kam. Fuer die Undo-Leiste, nicht fuer das Zurueckholen von Hand; das
     * ist immer eine Wahl (Phase 14b).
     */
    @Query(
        """
        UPDATE notes
        SET folderId = herkunftOrdnerId, ordnerArchiviertAt = NULL, herkunftOrdnerId = NULL,
            updatedAt = :now
        WHERE id IN (:ids) AND ordnerArchiviertAt IS NOT NULL
        """,
    )
    suspend fun archivierungZuruecknehmen(ids: List<String>, now: Long)

    /**
     * Stellt den Archivstand einer Notiz wieder her: Archivordner, Herkunft
     * und Zeitpunkt. Das Undo zum Zurueckholen; ein blosses Neu-Archivieren
     * wuerfe beides weg.
     */
    @Query(
        """
        UPDATE notes
        SET folderId = :folderId, herkunftOrdnerId = :herkunftId,
            ordnerArchiviertAt = :archiviertAt, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun archivstandSetzen(
        id: String,
        folderId: String?,
        herkunftId: String?,
        archiviertAt: Long,
        now: Long,
    )

    /** Papierkorb: alles mit gesetztem deletedAt, ueber alle Stufen. */
    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteWithRelations>>

    /**
     * Alle Notizen ueber alle Stufen hinweg, ohne Papierkorb.
     *
     * Fuer die Suche: ohne Suchtext, aber mit gesetzten Filtern muss es etwas
     * zu filtern geben. Dieselbe Reihenfolge wie die Trefferliste, damit das
     * Loeschen des Suchtextes die Liste nicht umsortiert.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        ORDER BY isFavorite DESC, updatedAt DESC
        """,
    )
    fun observeAlle(): Flow<List<NoteWithRelations>>

    /**
     * Die Kennungen ALLER Notizen, auch der geloeschten.
     *
     * Fuer die Sicherungsdatei. Sie holt jede Notiz einzeln nach, statt alle
     * auf einmal zu laden: Ein paar hundert Notizen mit Transkripten waeren
     * schnell einige Dutzend Megabyte im Speicher, obwohl immer nur eine
     * geschrieben wird.
     *
     * Der Papierkorb geht mit. Was dort liegt, ist noch nicht weg, und eine
     * Sicherung, die ihn auslaesst, loescht beim Wiederherstellen still.
     */
    @Query("SELECT id FROM notes ORDER BY createdAt ASC")
    suspend fun alleIds(): List<String>

    /**
     * Welche dieser Notizen es hier gibt und am Abgleich teilnehmen.
     *
     * Fuer die Grabsteine: Ein Grabstein fuer eine Notiz, die es gibt, ist ein
     * Widerspruch und muss weg. Der Papierkorb zaehlt dabei als vorhanden, denn
     * er ist ein weiches Loeschen und wird mit abgeglichen.
     *
     * ACHTUNG: Bei leerer Liste NICHT aufrufen. Room macht daraus `IN ()`, und
     * das ist in SQLite ein Syntaxfehler.
     */
    @Query("SELECT id FROM notes WHERE id IN (:ids) AND syncEnabled = 1")
    suspend fun lebendeVon(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM notes WHERE stage = :stage AND deletedAt IS NULL")
    fun observeCount(stage: Stage): Flow<Int>

    /**
     * Alle Notizen ausserhalb des Papierkorbs.
     *
     * Im Ordnermodus tritt diese Zahl an die Stelle der drei Stufenzaehler:
     * Dort gibt es kein Eingang und kein Archiv mehr, wohl aber die Frage, wie
     * viel ueberhaupt da ist.
     */
    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    fun observeAnzahlGesamt(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE stage = :stage AND deletedAt IS NULL")
    suspend fun countIn(stage: Stage): Int

    // -------------------------------------------------------------- schreiben

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    /**
     * Setzt NUR `lastOpenedAt`. Ansehen ist nicht Bearbeiten -- `updatedAt`
     * bleibt unangetastet, sonst zerschiesst es die Konfliktaufloesung.
     */
    @Query("UPDATE notes SET lastOpenedAt = :now WHERE id = :id")
    suspend fun markOpened(id: String, now: Long)

    @Query(
        """
        UPDATE notes
        SET stage = :stage, stageChangedAt = :now, autoArchivedBatchId = :batchId
        WHERE id IN (:ids)
        """,
    )
    suspend fun setStage(ids: List<String>, stage: Stage, now: Long, batchId: String?)

    @Query("UPDATE notes SET isFavorite = :favorite, favoritedAt = :now WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean, now: Long?)

    /**
     * Legt Notizen in einen Ordner. `null` ist der Hauptordner.
     *
     * Zaehlt `updatedAt` hoch, denn `folderId` geht mit nach Drive (SYNC.md
     * 6.2) und das andere Geraet muss den Umzug erfahren. Anders als beim
     * Kalenderschalter, der hier bleibt.
     */
    @Query(
        """
        UPDATE notes SET folderId = :ordnerId, ehemaligerOrdnerId = NULL, updatedAt = :now
        WHERE id IN (:ids)
        """,
    )
    suspend fun setFolder(ids: List<String>, ordnerId: String?, now: Long)

    /**
     * Haengt den Inhalt eines Ordners eine Ebene hoeher.
     *
     * Wird beim Loeschen eines Ordners gebraucht. Die Notizen mitzuloeschen
     * waere die falsche Antwort auf "der Ordner soll weg": Wer einen Ordner
     * wegraeumt, will den Ordner los sein, nicht das, was darin lag.
     *
     * `ehemaligerOrdnerId` merkt sich, woher die Notiz kam: Der Ordner im
     * Papierkorb zeigt sie dann ausgegraut als seinen frueheren Inhalt
     * (Phase 14c). [setFolder] leert das wieder, sobald jemand die Notiz von
     * Hand verschiebt.
     */
    @Query(
        """
        UPDATE notes SET folderId = :ziel, ehemaligerOrdnerId = :ordnerId, updatedAt = :now
        WHERE folderId = :ordnerId
        """,
    )
    suspend fun moveFolderContents(ordnerId: String, ziel: String?, now: Long)

    /** Die lebenden Notizen, die aus [ehemaligerOrdnerId] herausgerueckt wurden (Phase 14c). */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE ehemaligerOrdnerId = :ehemaligerOrdnerId AND deletedAt IS NULL
        ORDER BY isFavorite DESC, createdAt DESC
        """,
    )
    fun observeHerausgerueckte(ehemaligerOrdnerId: String): Flow<List<NoteWithRelations>>

    /** Welche Notizen unmittelbar in diesem Ordner liegen. Fuer die Sync-Buchung. */
    @Query("SELECT id FROM notes WHERE folderId = :ordnerId")
    suspend fun idsImOrdner(ordnerId: String): List<String>

    /**
     * Die lebenden Notizen mehrerer Ordner auf einmal.
     *
     * Fuer das Loeschen eines Ordners samt Inhalt: Betroffen sind nicht nur
     * seine eigenen Notizen, sondern auch die aller Unterordner.
     *
     * ACHTUNG: Bei leerer Liste NICHT aufrufen, `IN ()` ist ein Syntaxfehler.
     */
    @Query("SELECT id FROM notes WHERE folderId IN (:ordnerIds) AND deletedAt IS NULL")
    suspend fun lebendeInOrdnern(ordnerIds: List<String>): List<String>

    /**
     * Was aus einem geloeschten Ordner im Papierkorb liegt.
     *
     * Genau die Notizen, die mit ihm hineingewandert sind. Sie tragen weiter
     * seine Kennung -- das ist der ganze Grund, warum sich der Ordner samt
     * Inhalt zurueckholen laesst.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE folderId = :ordnerId AND deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
        """,
    )
    fun observeImPapierkorbVon(ordnerId: String): Flow<List<NoteWithRelations>>

    /** Wie viele Notizen je geloeschtem Ordner im Papierkorb liegen. */
    @Query(
        """
        SELECT folderId AS ordnerId, COUNT(*) AS anzahl FROM notes
        WHERE folderId IS NOT NULL AND deletedAt IS NOT NULL
        GROUP BY folderId
        """,
    )
    fun observeZaehlungImPapierkorb(): Flow<List<Ordnerzaehlung>>

    /** Die weggeworfenen Notizen eines Ordners, einmalig. Fuer das Zurueckholen. */
    @Query("SELECT id FROM notes WHERE folderId = :ordnerId AND deletedAt IS NOT NULL")
    suspend fun geloeschteImOrdner(ordnerId: String): List<String>

    /** Farbwechsel ist eine normale Aenderung -- das Repository setzt updatedAt. */
    @Query("UPDATE notes SET colorId = :color, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setColor(ids: List<String>, color: NoteColor, now: Long)

    /**
     * Nur der Titel. Gebraucht fuer die Titelpflicht ab WORKSPACE, wo ein Titel
     * gesetzt wird, ohne dass der Editor offen ist. Der Zeitpunkt kommt auch
     * hier vom Repository.
     */
    @Query("UPDATE notes SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun setTitle(id: String, title: String, now: Long)

    /** Papierkorb. Wiederherstellbar; endgueltig loescht erst [purge]. */
    /**
     * Setzt oder loescht das Hintergrundbild.
     *
     * Zaehlt `updatedAt` hoch, denn das ist eine echte Inhaltsaenderung -- die
     * Notiz sieht danach anders aus, und der andere Client muss das erfahren.
     */
    @Query("UPDATE notes SET backgroundAttachmentId = :anhangId, updatedAt = :now WHERE id = :id")
    suspend fun setBackground(id: String, anhangId: String?, now: Long)

    /**
     * Loest den Hintergrundverweis, der auf diesen Anhang zeigt.
     *
     * Muss von Hand geschehen: Es gibt keinen Fremdschluessel von `notes` auf
     * `attachments` (siehe NoteEntity.backgroundAttachmentId), die Datenbank
     * raeumt hier also nichts auf. Ohne diese Zeile bliebe nach dem Loeschen
     * eines Bildes ein Zeiger ins Leere stehen.
     */
    @Query(
        """
        UPDATE notes SET backgroundAttachmentId = NULL, updatedAt = :now
        WHERE backgroundAttachmentId = :anhangId
        """,
    )
    suspend fun clearBackground(anhangId: String, now: Long)

    @Query("UPDATE notes SET deletedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>)

    /**
     * Notizen, die seit [schwelle] im Papierkorb liegen.
     *
     * Gemessen wird an `deletedAt`, nicht an `updatedAt`: Wann etwas
     * weggeworfen wurde, ist die Frage -- nicht, wann es zuletzt bearbeitet
     * wurde. Eine Notiz von 2019, die man gestern weggeworfen hat, ist gestern
     * weggeworfen worden.
     */
    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :schwelle")
    suspend fun ueberfaelligImPapierkorb(schwelle: Long): List<String>

    /** Endgueltig. Der Aufrufer MUSS vorher Tombstones schreiben. */
    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun purge(ids: List<String>)

    // ------------------------------------------------------- Auto-Archivierung

    /**
     * Kandidaten nach ALTER: seit [schwelle] nicht mehr geoeffnet.
     *
     * Ausgenommen sind Favoriten (Spezifikation Abschnitt 7) und Notizen mit
     * einer noch nicht ausgeloesten Erinnerung (Abschnitt 8).
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE stage = :stage
          AND deletedAt IS NULL
          AND isFavorite = 0
          AND lastOpenedAt < :schwelle
          AND id NOT IN (SELECT noteId FROM reminders WHERE isFired = 0)
        ORDER BY lastOpenedAt ASC
        """,
    )
    suspend fun archiveCandidatesByAge(stage: Stage, schwelle: Long): List<NoteEntity>

    /**
     * Kandidaten nach MENGE: die aeltesten ueber der Grenze, gleiche Ausnahmen.
     * [ueberschuss] ist die Anzahl, die weichen muss.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE stage = :stage
          AND deletedAt IS NULL
          AND isFavorite = 0
          AND id NOT IN (SELECT noteId FROM reminders WHERE isFired = 0)
        ORDER BY lastOpenedAt ASC
        LIMIT :ueberschuss
        """,
    )
    suspend fun archiveCandidatesByCount(stage: Stage, ueberschuss: Int): List<NoteEntity>

    // ------------------------------------------------------ Checklisteneintraege

    @Query("SELECT * FROM note_items WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun itemsOf(noteId: String): List<NoteItemEntity>

    @Upsert
    suspend fun upsertItems(items: List<NoteItemEntity>)

    @Query("DELETE FROM note_items WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<String>)

    @Query("DELETE FROM note_items WHERE noteId = :noteId")
    suspend fun deleteAllItemsOf(noteId: String)

    // ------------------------------------------------------------ Tag-Zuordnung

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTags(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTagsOf(noteId: String)

    /**
     * Nimmt eine Notiz vom Abgleich aus oder wieder hinein.
     *
     * Fasst `updatedAt` NICHT an: Das ist keine Inhaltsaenderung, und ein
     * hochgezaehltes `updatedAt` haette auf dem anderen Geraet ausgesehen wie
     * eine Bearbeitung, die es nie gab.
     */
    @Query("UPDATE notes SET syncEnabled = :an WHERE id = :id")
    suspend fun setSyncEnabled(id: String, an: Boolean)

    @Query("UPDATE notes SET calendarEnabled = :an WHERE id = :id")
    suspend fun setCalendarEnabled(id: String, an: Boolean)

    /**
     * Merkt sich den Termin, der zu dieser Notiz im Kalender steht.
     *
     * Ohne diesen Vermerk waere ein einmal eingetragener Termin nicht mehr
     * wiederzufinden: Beim Aendern der Erinnerung entstuende ein zweiter
     * daneben, und beim Loeschen bliebe der alte fuer immer stehen.
     */
    @Query("UPDATE notes SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: String, eventId: Long?)

    /**
     * Alles, was den Kalender angeht, in einer Abfrage.
     *
     * Zwei Gruppen, und beide werden gebraucht: Notizen mit einer offenen
     * Erinnerung (die gehoeren in den Kalender) und Notizen mit einem
     * eingetragenen Termin (bei denen zu pruefen ist, ob er noch hingehoert).
     * Die Schnittmenge ist der Normalfall, die beiden Raender sind es, an denen
     * Termine sonst haengen bleiben.
     *
     * Bewusst eine schmale Projektion. Der Fluss meldet sich sonst bei jeder
     * Aenderung an irgendeiner Notiz, auch wenn sich am Kalender nichts aendert.
     */
    @Query(
        """
        SELECT n.id AS noteId,
               n.title AS title,
               n.calendarEnabled AS calendarEnabled,
               n.calendarEventId AS calendarEventId,
               n.deletedAt AS deletedAt,
               (SELECT MIN(r.triggerAt) FROM reminders r
                 WHERE r.noteId = n.id AND r.isFired = 0) AS triggerAt
        FROM notes n
        WHERE n.calendarEventId IS NOT NULL
           OR EXISTS (SELECT 1 FROM reminders r WHERE r.noteId = n.id AND r.isFired = 0)
        ORDER BY n.id
        """,
    )
    fun observeKalenderzeilen(): Flow<List<Kalenderzeile>>

    /** Dasselbe einmalig, fuer das Aufraeumen beim Abschalten. */
    @Query("SELECT id, calendarEventId FROM notes WHERE calendarEventId IS NOT NULL")
    suspend fun mitKalendertermin(): List<Termineintrag>

    /**
     * Ausgenommene Notizen, die noch in Drive liegen.
     *
     * Sie muessen dort weg. Erkennbar sind sie daran, dass es zu ihnen noch
     * eine Sync-Buchung gibt -- die faellt weg, sobald aufgeraeumt ist, und
     * damit findet dieselbe Abfrage sie beim naechsten Lauf nicht mehr.
     */
    @Query(
        """
        SELECT n.id FROM notes n
        WHERE n.syncEnabled = 0 AND EXISTS (
            SELECT 1 FROM sync_state s WHERE
                (s.entityType = 'NOTE' AND s.entityId = n.id)
                OR (s.entityType = 'ATTACHMENT'
                    AND s.entityId IN (SELECT id FROM attachments WHERE noteId = n.id))
                OR (s.entityType = 'TRANSCRIPT'
                    AND s.entityId IN (SELECT id FROM transcripts WHERE noteId = n.id))
                OR (s.entityType = 'REMINDER'
                    AND s.entityId IN (SELECT id FROM reminders WHERE noteId = n.id))
        )
        """,
    )
    suspend fun ausgenommenMitAblage(): List<String>

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlinkTag(noteId: String, tagId: String)

    @Query("SELECT * FROM note_tags WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun tagRefsOf(noteId: String): List<NoteTagCrossRef>

    /** Alle Notiz-IDs mit einem bestimmten Tag -- fuer Filter und Zusammenfuehren. */
    @Query("SELECT noteId FROM note_tags WHERE tagId = :tagId")
    suspend fun noteIdsWithTag(tagId: String): List<String>
}
