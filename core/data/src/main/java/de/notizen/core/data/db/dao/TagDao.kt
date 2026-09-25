package de.notizen.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import de.notizen.core.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** Tags -- das thematische Ordnungssystem, das Ordner ersetzt. */
@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortIndex ASC, name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY sortIndex ASC, name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    /**
     * ALLE Tags, auch die in den Papierkorb gelegten.
     *
     * Fuer den Abgleich: Ein Tag mit gesetztem `deletedAt` ist geloescht und
     * das muss drueben ankommen. Wer hier nur die lebenden liefert, laesst den
     * geloeschten Tag auf dem anderen Geraet stehen -- und beim naechsten
     * Abgleich kommt er von dort zurueck.
     */
    @Query("SELECT * FROM tags")
    suspend fun getAllIncludingDeleted(): List<TagEntity>

    /** Namensvergleich ohne Beachtung der Gross-/Kleinschreibung. */
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    /**
     * Wie oft ein Tag verwendet wird -- fuer den Verwaltungsscreen.
     * Notizen im Papierkorb zaehlen nicht mit.
     */
    @Query(
        """
        SELECT COUNT(*) FROM note_tags
        JOIN notes ON notes.id = note_tags.noteId
        WHERE note_tags.tagId = :tagId AND notes.deletedAt IS NULL
        """,
    )
    suspend fun usageCount(tagId: String): Int

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("UPDATE tags SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE tags SET colorArgb = :colorArgb, updatedAt = :now WHERE id = :id")
    suspend fun recolor(id: String, colorArgb: Int, now: Long)

    @Query("UPDATE tags SET deletedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Endgueltig. Der Aufrufer MUSS vorher einen Tombstone schreiben. */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun purge(id: String)

    /**
     * Haengt alle Notizen von [vonTagId] auf [aufTagId] um -- Grundlage des
     * Zusammenfuehrens. `OR REPLACE`, weil eine Notiz beide Tags tragen kann
     * und der Primaerschluessel sonst kollidiert.
     */
    @Query(
        """
        UPDATE OR REPLACE note_tags SET tagId = :aufTagId WHERE tagId = :vonTagId
        """,
    )
    suspend fun repointTags(vonTagId: String, aufTagId: String)
}
