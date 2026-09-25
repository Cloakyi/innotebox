package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.SearchDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteFtsEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Sortierung einer Stufen-Ansicht. */
enum class NoteSort {
    CREATED,
    UPDATED,
    LAST_OPENED,
    ;

    /**
     * Was in der Auswahl steht.
     *
     * „Zuletzt geöffnet" ist ausdrücklich etwas anderes als „zuletzt
     * bearbeitet": Ansehen ist nicht Bearbeiten, und beide Zeitstempel laufen
     * getrennt (siehe die `updatedAt`-Disziplin in docs/ENTSCHEIDUNGEN.md). Wer das in der
     * Beschriftung verwischt, verwischt es bald auch im Code.
     */
    fun beschriftung(): String = when (this) {
        CREATED -> "Zuletzt erstellt"
        UPDATED -> "Zuletzt bearbeitet"
        LAST_OPENED -> "Zuletzt geöffnet"
    }
}

/**
 * Der Inhalt einer Notiz, so wie der Editor ihn sieht.
 * Dient dem Vergleich "hat sich wirklich etwas geändert?".
 */
data class NoteContent(
    val title: String,
    val body: String,
    val items: List<NoteItemEntity> = emptyList(),
)

/**
 * Zugriff auf Notizen.
 *
 * HIER LIEGT DIE `updatedAt`-DISZIPLIN. Wer sie bricht, zerschiesst die
 * Konfliktaufloesung zwischen Android- und Web-Client:
 *
 *  - `updatedAt` steigt NUR bei echter Inhaltsaenderung. [updateContent]
 *    vergleicht vorher und schreibt gar nicht, wenn sich nichts geaendert hat.
 *  - `lastOpenedAt` setzt ausschliesslich [open] und ist nicht sync-relevant.
 *
 * Siehe SYNC.md 14.2.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val noteDao: NoteDao,
    private val transcriptDao: TranscriptDao,
    private val attachmentDao: AttachmentDao,
    private val searchDao: SearchDao,
    private val syncDao: SyncDao,
    private val clock: Clock,
) {

    // ---------------------------------------------------------------- lesen

    fun observe(stage: Stage, sort: NoteSort = NoteSort.CREATED): Flow<List<NoteWithRelations>> =
        when (sort) {
            NoteSort.CREATED -> noteDao.observeByStageNewestFirst(stage)
            NoteSort.UPDATED -> noteDao.observeByStageRecentlyChanged(stage)
            NoteSort.LAST_OPENED -> noteDao.observeByStageRecentlyOpened(stage)
        }

    /**
     * Die Notizen eines Ordners. `null` ist der Hauptordner.
     *
     * Dieselben drei Sortierungen wie im Fluss, damit die Umschaltung in der
     * Kopfzeile in beiden Ansichten dasselbe bedeutet.
     */
    fun observeImOrdner(
        ordnerId: String?,
        sort: NoteSort = NoteSort.CREATED,
        archiviert: Boolean = false,
    ): Flow<List<NoteWithRelations>> =
        when (sort) {
            NoteSort.CREATED -> noteDao.observeByFolderNewestFirst(ordnerId, archiviert)
            NoteSort.UPDATED -> noteDao.observeByFolderRecentlyChanged(ordnerId, archiviert)
            NoteSort.LAST_OPENED -> noteDao.observeByFolderRecentlyOpened(ordnerId, archiviert)
        }

    fun observeCount(stage: Stage): Flow<Int> = noteDao.observeCount(stage)

    /** Alle Notizen ausserhalb des Papierkorbs. Fuer den Ordnermodus. */
    fun observeAnzahlGesamt(): Flow<Int> = noteDao.observeAnzahlGesamt()

    /** Die beiden Baeume des Ordnermodus, je fuer sich gezaehlt (Phase 14b). */
    fun observeAnzahlImOrdnerArchiv(): Flow<Int> = noteDao.observeAnzahlImOrdnerArchiv()

    fun observeAnzahlNichtArchiviert(): Flow<Int> = noteDao.observeAnzahlNichtArchiviert()

    /**
     * Archiviert Notizen im Ordnermodus (Phase 14b, SYNC.md 13).
     *
     * Sie landen oben im Archiv und merken sich ihren Herkunftsordner. Die
     * Stufe bleibt, wie sie ist: Wer im Ordnersystem archiviert, veraendert
     * das Stufensystem nicht.
     */
    suspend fun imOrdnerArchivieren(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            noteDao.imOrdnerArchivieren(ids, now)
            markDirty(ids)
        }
    }

    /**
     * Holt Notizen aus dem Archiv des Ordnermodus zurueck, nach [zielId]
     * (`null` = Hauptordner). Wohin, ist immer eine Wahl des Nutzers; das
     * Repository schlaegt nichts vor.
     */
    suspend fun ausOrdnerArchivZurueck(ids: List<String>, zielId: String?) {
        if (ids.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            noteDao.ausOrdnerArchivZurueck(ids, zielId, now)
            markDirty(ids)
        }
    }

    /** Das Undo zum Archivieren: zurueck dorthin, woher jede Notiz kam. */
    suspend fun archivierungZuruecknehmen(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            noteDao.archivierungZuruecknehmen(ids, now)
            markDirty(ids)
        }
    }

    /** Das Undo zum Zurueckholen: der Archivstand von vorher, Feld fuer Feld. */
    suspend fun archivstandSetzen(id: String, folderId: String?, herkunftId: String?, archiviertAt: Long) {
        val now = clock.now()
        db.withTransaction {
            noteDao.archivstandSetzen(id, folderId, herkunftId, archiviertAt, now)
            markDirty(listOf(id))
        }
    }

    fun observeTrash(): Flow<List<NoteWithRelations>> = noteDao.observeTrash()

    fun observeById(id: String): Flow<NoteWithRelations?> = noteDao.observeById(id)

    suspend fun get(id: String): NoteWithRelations? = noteDao.getById(id)

    // -------------------------------------------------------------- anlegen

    /**
     * Neue Notiz. Landet immer in INBOX (Spezifikation Abschnitt 14).
     *
     * [ordnerId] legt sie zusaetzlich in einen Ordner. Die Stufe bleibt
     * trotzdem der Eingang, auch im Ordnermodus: Sie wird dort nicht gezeigt,
     * aber sie bleibt richtig gefuehrt. Wer zurueck in den Fluss schaltet,
     * findet seine neuen Notizen dort, wo neue Notizen hingehoeren.
     */
    suspend fun create(type: NoteType = NoteType.TEXT, ordnerId: String? = null): String {
        val now = clock.now()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            stage = Stage.INBOX,
            type = type,
            folderId = ordnerId,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
            stageChangedAt = now,
        )
        db.withTransaction {
            noteDao.upsert(note)
            reindex(note.id)
            markDirty(note.id)
        }
        return note.id
    }

    /**
     * Legt eine Kopie einer Notiz an und gibt deren Kennung zurueck.
     *
     * WAS MITGEHT und was nicht, ist eine inhaltliche Entscheidung, keine
     * technische:
     *
     *  * **Mit:** Titel, Text, Farbe, Hintergrundbild, Eintraege, Tags,
     *    Anhaenge und Transkripte. Das alles beschreibt, was in der Notiz
     *    steht -- eine Kopie ohne das waere keine.
     *  * **Ohne: Favorit und Erinnerung.** Beides sind Aussagen ueber DIESE
     *    Notiz, nicht ueber ihren Inhalt. Eine mitkopierte Erinnerung wuerde
     *    zweimal klingeln, ein mitkopierter Stern die Favoritenliste
     *    verdoppeln.
     *  * **Ohne: Stufe und Auto-Archiv-Vermerk.** Die Kopie ist neu und faengt
     *    im Eingang an, wie jede neue Notiz.
     *
     * [zielDatei] entscheidet, wohin die Datei eines Anhangs kopiert wird --
     * das muss die App-Schicht sagen, denn nur sie kennt die Ablage. Gibt sie
     * `null` zurueck oder scheitert das Kopieren, wird der Anhang
     * **uebersprungen**: Eine Anhangszeile ohne Datei saehe aus wie ein Bild,
     * das man ansehen kann.
     */
    suspend fun duplizieren(
        id: String,
        zielDatei: (AttachmentEntity, String, String) -> File?,
    ): String? {
        val quelle = noteDao.getById(id) ?: return null
        val now = clock.now()
        val neueId = UUID.randomUUID().toString()

        // Die Anhaenge werden VOR der Transaktion kopiert: Dateiarbeit hat in
        // einer Datenbanktransaktion nichts zu suchen, und was nicht kopiert
        // werden konnte, soll gar nicht erst in die Tabelle.
        val anhaenge = quelle.attachments.mapNotNull { alt ->
            val neueAnhangId = UUID.randomUUID().toString()
            val ziel = zielDatei(alt, neueAnhangId, neueId) ?: return@mapNotNull null
            val her = File(alt.localPath)
            if (!her.exists()) return@mapNotNull null
            val geglueckt = runCatching {
                ziel.parentFile?.mkdirs()
                her.copyTo(ziel, overwrite = true)
            }.isSuccess
            if (!geglueckt) return@mapNotNull null

            // `backgroundAttachmentId` zeigt auf eine Anhangskennung, und die
            // ist neu -- der Verweis muss mitwandern.
            val warHintergrund = quelle.note.backgroundAttachmentId == alt.id
            Triple(
                alt.copy(
                    id = neueAnhangId,
                    noteId = neueId,
                    localPath = ziel.absolutePath,
                    // Die Kopie liegt nur lokal. Sie als bereits hochgeladen zu
                    // fuehren, hiesse dem Sync eine Datei zu versprechen, die es
                    // auf Drive nicht gibt.
                    remoteId = null,
                ),
                warHintergrund,
                Unit,
            )
        }

        val kopie = quelle.note.copy(
            id = neueId,
            stage = Stage.INBOX,
            isFavorite = false,
            favoritedAt = null,
            autoArchivedBatchId = null,
            deletedAt = null,
            backgroundAttachmentId = anhaenge.firstOrNull { it.second }?.first?.id,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
            stageChangedAt = now,
        )

        db.withTransaction {
            noteDao.upsert(kopie)

            val eintraege = quelle.orderedItems.map {
                it.copy(id = UUID.randomUUID().toString(), noteId = neueId)
            }
            if (eintraege.isNotEmpty()) noteDao.upsertItems(eintraege)

            val tagIds = quelle.tags.map { it.id }
            if (tagIds.isNotEmpty()) {
                noteDao.linkTags(tagIds.mapIndexed { i, t -> NoteTagCrossRef(neueId, t, i) })
            }

            if (anhaenge.isNotEmpty()) {
                attachmentDao.upsertAll(anhaenge.map { it.first })
                anhaenge.forEach {
                    syncDao.markDirty(EntityType.ATTACHMENT, it.first.id, now)
                }
            }

            val segmente = quelle.orderedTranscripts.map {
                it.copy(id = UUID.randomUUID().toString(), noteId = neueId)
            }
            if (segmente.isNotEmpty()) {
                transcriptDao.upsertAll(segmente)
                segmente.forEach { syncDao.markDirty(EntityType.TRANSCRIPT, it.id, now) }
            }

            reindex(neueId)
            markDirty(neueId)
        }
        return neueId
    }

    // -------------------------------------------------------------- oeffnen

    /**
     * Setzt NUR `lastOpenedAt`. Ansehen ist nicht Bearbeiten -- `updatedAt`
     * bleibt unberuehrt und es wird nichts als aenderungsbeduerftig markiert.
     */
    suspend fun open(id: String) = noteDao.markOpened(id, clock.now())

    // ------------------------------------------------------------- schreiben

    /**
     * Speichert den Editor-Inhalt.
     *
     * Gibt `false` zurueck und schreibt NICHTS, wenn sich gegenueber dem
     * gespeicherten Stand nichts geaendert hat. Das ist keine Optimierung,
     * sondern Voraussetzung dafuer, dass `updatedAt` etwas bedeutet: der
     * Autosave feuert alle ~800 ms, auch wenn der Nutzer nur den Cursor bewegt.
     */
    suspend fun updateContent(id: String, content: NoteContent): Boolean {
        val current = noteDao.getById(id) ?: return false
        if (!hasChanged(current, content)) return false

        val now = clock.now()
        db.withTransaction {
            noteDao.update(current.note.copy(title = content.title, body = content.body, updatedAt = now))
            noteDao.deleteAllItemsOf(id)
            if (content.items.isNotEmpty()) {
                noteDao.upsertItems(content.items.mapIndexed { i, item -> item.copy(noteId = id, position = i) })
            }
            reindex(id)
            markDirty(id)
        }
        return true
    }

    private fun hasChanged(current: NoteWithRelations, content: NoteContent): Boolean {
        if (current.note.title != content.title) return true
        if (current.note.body != content.body) return true
        val alt = current.orderedItems.map { it.text to it.isChecked }
        val neu = content.items.map { it.text to it.isChecked }
        return alt != neu
    }

    /**
     * Verwirft eine Notiz, die weder Titel noch Inhalt hat.
     *
     * Der Editor hat keinen Speichern-Knopf: wer eine leere Notiz oeffnet und
     * wieder verlaesst, will sie nicht behalten (Spezifikation Abschnitt 9).
     * Gibt zurueck, ob geloescht wurde.
     */
    suspend fun discardIfEmpty(id: String): Boolean {
        val n = noteDao.getById(id) ?: return false
        if (!n.istLeer) return false
        purge(listOf(id))
        return true
    }

    /**
     * Legt Notizen in einen Ordner. `null` nimmt sie aus jedem Ordner heraus.
     *
     * Zaehlt `updatedAt` hoch, anders als das Favorisieren. Der Unterschied ist
     * kein Zufall: `folderId` geht mit nach Drive (SYNC.md 14.2), der Stern
     * nicht. Was drueben ankommen soll, muss auch drueben gewinnen koennen.
     */
    suspend fun setOrdner(ids: List<String>, ordnerId: String?) {
        if (ids.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            noteDao.setFolder(ids, ordnerId, now)
            markDirty(ids)
        }
    }

    /** Farbwechsel ist eine normale Aenderung: updatedAt steigt, Sync folgt. */
    suspend fun setColor(ids: List<String>, color: NoteColor) {
        if (ids.isEmpty()) return
        val now = clock.now()
        db.withTransaction {
            noteDao.setColor(ids, color, now)
            markDirty(ids)
        }
    }

    /**
     * Favorisieren. Zaehlt NICHT als Inhaltsaenderung -- `updatedAt` bleibt,
     * damit ein Stern beim anderen Client keinen Textstand ueberholt.
     * Der Zeitpunkt landet in `favoritedAt`.
     */
    suspend fun setFavorite(ids: List<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        db.withTransaction {
            noteDao.setFavorite(ids, favorite, if (favorite) clock.now() else null)
            markDirty(ids)
        }
    }

    /**
     * Setzt nur den Titel.
     *
     * Fuer die Titelpflicht ab WORKSPACE: dort bekommt eine Notiz einen Titel,
     * ohne dass der Editor offen ist -- [updateContent] waere dafuer das falsche
     * Werkzeug, weil es Body und Eintraege mitschreiben will.
     *
     * Ein Titel ist echter Inhalt: `updatedAt` steigt, der FTS-Index wird
     * nachgezogen. Gibt zurueck, ob sich etwas geaendert hat.
     */
    suspend fun setTitle(id: String, title: String): Boolean {
        val current = noteDao.getById(id) ?: return false
        if (current.note.title == title) return false

        val now = clock.now()
        db.withTransaction {
            noteDao.setTitle(id, title, now)
            reindex(id)
            markDirty(id)
        }
        return true
    }

    /**
     * Stufenwechsel. [batchId] nur setzen, wenn ein Auto-Archiv-Lauf es tut --
     * daran haengt das Batch-Undo.
     */
    suspend fun moveTo(ids: List<String>, stage: Stage, batchId: String? = null) {
        if (ids.isEmpty()) return
        db.withTransaction {
            noteDao.setStage(ids, stage, clock.now(), batchId)
            markDirty(ids)
        }
    }

    /**
     * Nimmt eine Notiz vom Abgleich aus oder wieder hinein.
     *
     * Beim Wiedereinschalten wird sie als aenderungsbeduerftig markiert, damit
     * sie beim naechsten Lauf hochgeht. Ohne das laege sie fuer immer nur hier:
     * Am Inhalt hat sich ja nichts geaendert, es gaebe also nichts zu tun.
     *
     * Beim Ausschalten passiert hier ABSICHTLICH nichts weiter. Das Wegraeumen
     * in Drive braucht Netz, und Netz gehoert nicht in eine Schaltflaeche --
     * das erledigt der naechste Abgleich.
     */
    suspend fun setAbgleich(noteId: String, an: Boolean) {
        db.withTransaction {
            noteDao.setSyncEnabled(noteId, an)
            if (an) markDirty(noteId)
        }
    }

    /**
     * Meldet eine Notiz erneut zum Hochladen an.
     *
     * Für den Fall, dass jemand es genau jetzt wissen will. Fasst `updatedAt`
     * nicht an — es hat sich ja nichts geändert, und ein hochgezähltes
     * `updatedAt` sähe auf dem anderen Gerät aus wie eine Bearbeitung, die es
     * nie gab. Kostet im schlimmsten Fall einen überflüssigen Upload.
     */
    suspend fun nochmalSichern(noteId: String) = markDirty(noteId)

    /**
     * Nimmt eine Notiz vom Kalender aus oder wieder hinein.
     *
     * **Kein `markDirty`, kein `updatedAt`.** Das Feld ist rein lokal, genau wie
     * `syncEnabled`: Welcher Kalender gemeint ist, weiss nur dieses Geraet. Den
     * Abgleich anzustossen hiesse, eine Entscheidung zu verschicken, die
     * drueben nichts bedeutet.
     *
     * Der Termin selbst wird hier nicht angefasst. Darum kuemmert sich der
     * Kalenderspiegel in `:app`, der ohnehin auf diese Tabelle sieht -- eine
     * zweite Stelle, die Termine schreibt, waere eine Stelle zu viel.
     */
    suspend fun setKalender(noteId: String, an: Boolean) {
        noteDao.setCalendarEnabled(noteId, an)
    }

    /** Ersetzt die Tag-Zuordnung. Reihenfolge bestimmt `position`. */
    suspend fun setTags(noteId: String, tagIds: List<String>) {
        db.withTransaction {
            noteDao.clearTagsOf(noteId)
            if (tagIds.isNotEmpty()) {
                noteDao.linkTags(tagIds.mapIndexed { i, t -> NoteTagCrossRef(noteId, t, i) })
            }
            markDirty(noteId)
        }
    }

    // -------------------------------------------------------------- loeschen

    /** In den Papierkorb. Wiederherstellbar, siehe SYNC.md 14.13 Stufe 1. */
    suspend fun trash(ids: List<String>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            noteDao.softDelete(ids, clock.now())
            ids.forEach { searchDao.deleteIndex(it) }
            markDirty(ids)
        }
    }

    suspend fun restore(ids: List<String>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            noteDao.restore(ids)
            ids.forEach { reindex(it) }
            markDirty(ids)
        }
    }

    /**
     * Endgueltig. Schreibt ZUERST Tombstones -- ohne die laesst der andere
     * Client die Notiz beim naechsten Abgleich wiederauferstehen.
     */
    suspend fun purge(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = clock.now()

        // Die Dateien VOR der Transaktion einsammeln: nach `noteDao.purge` sind
        // die Anhangszeilen weg, und mit ihnen der einzige Hinweis darauf, wo
        // die Dateien liegen.
        val anhaenge = ids.flatMap { attachmentDao.of(it) }
        val dateien = anhaenge.map { it.localPath }

        // Dasselbe fuer die Kalendertermine, und aus demselben Grund: Nach der
        // Transaktion ist die Notizzeile weg, und mit ihr der einzige Hinweis
        // darauf, welcher Termin zu ihr gehoerte.
        val termine = noteDao.getPlainByIds(ids).mapNotNull { it.calendarEventId }

        db.withTransaction {
            // IMMER, ohne Bedingung. Der Gedanke, sich den Grabstein fuer eine
            // Notiz zu sparen, die es nie nach Drive geschafft hat, war
            // naheliegend und falsch: Er waere richtig, solange die lokale
            // Buchfuehrung stimmt -- und genau die kann fehlen (Neuinstallation,
            // zurueckgespieltes Backup, eine zwischendurch ausgenommene Notiz).
            // Ein ueberfluessiger Grabstein kostet eine Zeile. Ein fehlender
            // laesst eine geloeschte Notiz wiederauferstehen.
            syncDao.upsertTombstones(
                ids.map { id ->
                    // rev = baseRev + 1: Der Grabstein ist eine gewoehnliche,
                    // hoeherwertige Fassung (SYNC.md 6.2), kein Sonderfall.
                    val basis = syncDao.baseRevOf(EntityType.NOTE, id) ?: 0
                    TombstoneEntity(EntityType.NOTE, id, now, rev = basis + 1)
                },
            )

            // Auch fuer die Anhaenge. Sonst bleiben ihre Dateien in Drive
            // liegen: Nach dieser Transaktion ist die Anhangszeile weg, und mit
            // ihr der einzige Hinweis darauf, dass es sie je gab. Der Abgleich
            // raeumt danach auf und entfernt den Grabstein wieder -- er ist ein
            // Merkzettel fuer dieses Geraet, kein Vertrag mit dem anderen.
            val anhaengeOben = anhaenge.filter { it.remoteId != null }
            if (anhaengeOben.isNotEmpty()) {
                syncDao.upsertTombstones(
                    anhaengeOben.map { TombstoneEntity(EntityType.ATTACHMENT, it.id, now) },
                )
            }
            // Ein Merkzettel je verwaistem Termin. Ohne ihn bliebe er fuer
            // immer im Kalender stehen, und niemand koennte im Nachhinein
            // sagen, wozu er einmal gehoerte. Der Kalenderspiegel raeumt ihn
            // ab und nimmt den Merkzettel dann weg.
            //
            // Der Normalfall braucht das gar nicht: Eine Notiz, die im
            // Papierkorb lag, hat ihren Termin schon dort verloren. Hier geht
            // es um die Notiz, die ohne Umweg verschwindet, etwa weil das
            // andere Geraet sie geloescht hat.
            if (termine.isNotEmpty()) {
                syncDao.upsertTombstones(
                    termine.map { TombstoneEntity(EntityType.CALENDAR, it.toString(), now) },
                )
            }

            ids.forEach {
                searchDao.deleteIndex(it)
                syncDao.dropState(EntityType.NOTE, it)
            }
            // Eintraege, Anhaenge, Transkripte und Erinnerungen haengen per
            // ON DELETE CASCADE daran und verschwinden mit.
            noteDao.purge(ids)
        }

        // CASCADE raeumt die TABELLE, nicht die PLATTE. Ohne diese Zeilen
        // bleiben Aufnahmen und Fotos endgueltig geloeschter Notizen liegen --
        // ein Leck, das man erst am vollen Speicher bemerkt und dann keiner
        // Ursache mehr zuordnen kann. Ausserhalb der Transaktion, weil
        // Dateisystemarbeit in einer Datenbanktransaktion nichts zu suchen hat.
        dateien.forEach { pfad -> runCatching { File(pfad).delete() } }
    }

    /**
     * Leert den Papierkorb von allem, was länger als [tage] darin liegt.
     *
     * Gibt zurück, wie viele Notizen es getroffen hat.
     *
     * **Hier wird endgültig gelöscht, und das ist der Unterschied zum
     * Auto-Archiv.** Deshalb drei Vorsichtsmaßnahmen:
     *
     *  1. `tage <= 0` heißt **aus** und tut nichts. Der Standardwert ist 0.
     *  2. Es läuft über [purge] und damit über denselben Weg wie das
     *     Löschen von Hand — mit Tombstones und mit dem Aufräumen der Dateien.
     *     Ein eigener, kürzerer Weg wäre die Stelle, an der man eines von
     *     beidem vergisst.
     *  3. Gemessen wird an `deletedAt`. Wer eine Notiz heute wegwirft, hat noch
     *     die volle Frist — auch wenn die Notiz selbst uralt ist.
     *
     * **Kein Undo.** Ein Papierkorb, dessen Leeren man rückgängig machen kann,
     * ist ein zweiter Papierkorb. Die Frist IST die Rücknahmemöglichkeit.
     */
    suspend fun papierkorbAufraeumen(tage: Int): Int {
        if (tage <= 0) return 0
        val schwelle = clock.now() - tage * 24L * 60 * 60 * 1000
        val faellig = noteDao.ueberfaelligImPapierkorb(schwelle)
        if (faellig.isEmpty()) return 0
        purge(faellig)
        return faellig.size
    }

    // ------------------------------------------------------------ Volltext

    /**
     * Setzt den FTS-Eintrag einer Notiz neu.
     *
     * Der Index ist eine eigenstaendige Tabelle und wird NICHT von SQLite
     * gepflegt -- er speist sich aus drei Quelltabellen (siehe SYNC.md 14.14).
     * Jede Schreiboperation, die Text beruehrt, muss hier vorbeikommen.
     */
    suspend fun reindex(noteId: String) {
        val n = noteDao.getById(noteId) ?: return
        val segmente = transcriptDao.of(noteId)
        searchDao.reindex(
            NoteFtsEntity(
                noteId = noteId,
                title = n.note.title,
                body = n.note.body,
                itemsText = n.orderedItems.joinToString("\n") { it.text },
                transcriptText = segmente.sortedBy { it.startMs }.joinToString("\n") { it.text },
            ),
        )
    }

    // ------------------------------------------------------------ Sync-Marke

    private suspend fun markDirty(id: String) = markDirty(listOf(id))

    /**
     * Geht ueber [SyncDao.markDirty] statt ueber einen Upsert, damit
     * `remoteId` und `remoteRevision` erhalten bleiben. Sonst verloere die
     * Notiz bei jeder Aenderung ihre Zuordnung zur Drive-Datei und wuerde beim
     * naechsten Abgleich als neue Datei hochgeladen.
     */
    private suspend fun markDirty(ids: List<String>) {
        val notes = noteDao.getPlainByIds(ids).associateBy { it.id }
        ids.forEach { id ->
            syncDao.markDirty(EntityType.NOTE, id, notes[id]?.updatedAt ?: 0L)
        }
    }
}
