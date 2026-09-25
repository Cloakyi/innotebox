package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.TagDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tags -- das thematische Ordnungssystem, das Ordner ersetzt.
 *
 * Zur Erinnerung: `colorArgb` faerbt NUR den Chip und den Punkt im Drawer.
 * Mit der Farbe der Notizkarte (NoteColor) hat es nichts zu tun -- das sind
 * zwei getrennte Systeme.
 */
@Singleton
class TagRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val tagDao: TagDao,
    private val syncDao: SyncDao,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    suspend fun getAll(): List<TagEntity> = tagDao.getAll()

    suspend fun usageCount(tagId: String): Int = tagDao.usageCount(tagId)

    /**
     * Legt einen Tag an -- oder gibt den bestehenden zurueck, wenn der Name
     * schon vergeben ist. Der Namensvergleich ignoriert Gross-/Kleinschreibung,
     * damit nicht "Arbeit" und "arbeit" nebeneinander stehen.
     */
    suspend fun create(name: String, colorArgb: Int): TagEntity {
        val getrimmt = name.trim()
        tagDao.findByName(getrimmt)?.let { return it }

        val now = clock.now()
        val tag = TagEntity(
            id = UUID.randomUUID().toString(),
            name = getrimmt,
            colorArgb = colorArgb,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            tagDao.upsert(tag)
            syncDao.markDirty(EntityType.TAG, tag.id, now)
        }
        return tag
    }

    suspend fun rename(id: String, name: String) {
        val now = clock.now()
        db.withTransaction {
            tagDao.rename(id, name.trim(), now)
            syncDao.markDirty(EntityType.TAG, id, now)
        }
    }

    suspend fun recolor(id: String, colorArgb: Int) {
        val now = clock.now()
        db.withTransaction {
            tagDao.recolor(id, colorArgb, now)
            syncDao.markDirty(EntityType.TAG, id, now)
        }
    }

    /**
     * Fuehrt [vonId] in [aufId] zusammen: alle Notizen wechseln den Tag, der
     * alte verschwindet endgueltig.
     *
     * Traegt eine Notiz beide Tags, wuerde das Umhaengen den Primaerschluessel
     * von `note_tags` verletzen -- deshalb `UPDATE OR REPLACE` im DAO.
     */
    suspend fun merge(vonId: String, aufId: String) {
        if (vonId == aufId) return
        val now = clock.now()
        db.withTransaction {
            tagDao.repointTags(vonId, aufId)
            val basis = syncDao.baseRevOf(EntityType.TAG, vonId) ?: 0
            syncDao.upsertTombstone(TombstoneEntity(EntityType.TAG, vonId, now, rev = basis + 1))
            syncDao.dropState(EntityType.TAG, vonId)
            tagDao.purge(vonId)
        }
    }

    /**
     * Endgueltig. Schreibt zuerst einen Tombstone, sonst laesst der andere
     * Client den Tag beim naechsten Abgleich wiederauferstehen.
     *
     * Die Zuordnungen in `note_tags` verschwinden per ON DELETE CASCADE mit;
     * die Notizen selbst bleiben unberuehrt und verlieren nur den Chip.
     */
    suspend fun delete(id: String) {
        val now = clock.now()
        db.withTransaction {
            val basis = syncDao.baseRevOf(EntityType.TAG, id) ?: 0
            syncDao.upsertTombstone(TombstoneEntity(EntityType.TAG, id, now, rev = basis + 1))
            syncDao.dropState(EntityType.TAG, id)
            tagDao.purge(id)
        }
    }
}
