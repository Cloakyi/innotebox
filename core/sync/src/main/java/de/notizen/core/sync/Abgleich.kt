package de.notizen.core.sync

import androidx.room.withTransaction
import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.aussen.endungFuer
import de.notizen.core.data.aussen.Weckdienst
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TagDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.entity.SyncStateEntity
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Herkunft
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.util.Clock
import de.notizen.core.sync.Abgleichregeln.Aufloesung
import de.notizen.core.sync.Abgleichregeln.Handlung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Woran der Abgleich gerade arbeitet.
 *
 * Fuer die Anzeige. Ein Vorgang, der eine halbe Minute dauert und dabei nichts
 * von sich sagt, ist von einem haengenden nicht zu unterscheiden.
 */
sealed interface Abgleichschritt {
    data object Vorbereiten : Abgleichschritt

    /** Tags und Ordner, jeweils eine Datei je Eintrag. */
    data object Ordnung : Abgleichschritt

    data class Notizen(val fertig: Int, val gesamt: Int) : Abgleichschritt

    data object Aufraeumen : Abgleichschritt
}

/**
 * Der Pruefbericht eines Laufs (SYNC.md 9, Schritt 3 und 4).
 *
 * Was gefunden und was davon gleich behoben wurde. `ohneBefund` entscheidet,
 * ob danach ein Snapshot geschrieben werden darf.
 */
@Serializable
data class Pruefbericht(
    val erstelltAm: Long,
    /** Dateien, die nach der Buchfuehrung oben liegen muessten und fehlten. Neu hochgeladen. */
    val fehlendeDateien: Int = 0,
    /** Dateien in Drive, die sich nicht lesen liessen. Namen fuer den Menschen. */
    val unlesbar: List<String> = emptyList(),
    /** Notizen, deren `folderId` auf keinen lebenden Ordner zeigt. Nur gemeldet. */
    val verweiseInsLeere: Int = 0,
    /** Konfliktkopien, die dieser Lauf angelegt hat. */
    val konfliktkopien: Int = 0,
    val notizenLokal: Int = 0,
    val notizenDrueben: Int = 0,
    val ordnerLokal: Int = 0,
    val ordnerDrueben: Int = 0,
    val tagsLokal: Int = 0,
    val tagsDrueben: Int = 0,
) {
    /** Zaehlwerke duerfen abweichen, solange nichts fehlt: DELETED-Dateien zaehlen drueben mit. */
    val ohneBefund: Boolean
        get() = fehlendeDateien == 0 && unlesbar.isEmpty() && verweiseInsLeere == 0
}

/** Der gespeicherte Pruefbericht aus den Einstellungen, oder null. */
fun pruefberichtAus(json: String): Pruefbericht? =
    json.takeIf { it.isNotBlank() }?.let { runCatching { Sync.decodeFromString<Pruefbericht>(it) }.getOrNull() }

/** Wie ein Abgleich ausgegangen ist. */
sealed interface Abgleichergebnis {
    data class Fertig(
        val hochgeladen: Int,
        val geholt: Int,
        val konflikte: Int,
        val geloescht: Int = 0,
        /** Wie viele Notizen auf Wunsch aus der Cloud genommen wurden. */
        val ausgenommen: Int = 0,
        val bericht: Pruefbericht,
    ) : Abgleichergebnis
    data object AnmeldungNoetig : Abgleichergebnis
    data object KeinNetz : Abgleichergebnis
    data class Fehler(val grund: String) : Abgleichergebnis
}

/**
 * Der Abgleich mit Google Drive nach Schema 4 (SYNC.md).
 *
 * Eine Datei je Entitaet, eine Huelle fuer alle, ein Zaehler statt Zeit.
 * Fuer jede Notiz, jeden Ordner und jeden Tag, den dieses Geraet oder Drive
 * kennt, wird genau eine Handlung bestimmt ([Abgleichregeln]) und ausgefuehrt.
 * Was hier steht, ist nur das Ausfuehren; die Regeln sind ohne Netz pruefbar.
 *
 * Regel 1 gilt im ganzen Code: Aus einer fehlenden Datei wird nie geloescht.
 * Fehlt drueben etwas, das hier oben war, ist das ein Befund im Pruefbericht,
 * und die Datei geht neu hoch.
 *
 * Der Dateiname ist der Schluessel, nie die Drive-Kennung. Die gemerkte
 * Kennung dient nur dazu, beim Schreiben einen Suchlauf zu sparen, und gilt
 * nur, solange die Dateiliste sie noch fuehrt: Ein Assistent, der eine Datei
 * neu anlegt, gibt ihr eine neue Kennung (SYNC.md 3 und 8).
 *
 * Das Token kommt von aussen. Dieses Modul kennt weder Google Play Services
 * noch Android; deshalb laesst sich der ganze Abgleich mit zwei simulierten
 * Clients auf der JVM pruefen (`ZweiClientsTest`).
 */
@Singleton
class Abgleich @Inject constructor(
    private val drive: Drivezugang,
    private val notes: NoteRepository,
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val transcriptDao: TranscriptDao,
    private val attachmentDao: AttachmentDao,
    private val reminderDao: ReminderDao,
    private val tagDao: TagDao,
    private val folderDao: FolderDao,
    private val ablage: Anhangablage,
    private val weckdienst: Weckdienst,
    private val db: NotizenDatabase,
    private val clock: Clock,
    private val einstellungen: Einstellungen,
) {

    private val schloss = Mutex()

    private val _laeuft = MutableStateFlow(false)

    /** Ob gerade abgeglichen wird. Die Karten zeigen es an. */
    val laeuft: StateFlow<Boolean> = _laeuft.asStateFlow()

    private val _schritt = MutableStateFlow<Abgleichschritt?>(null)

    /** Woran gerade gearbeitet wird, oder `null`, wenn nichts laeuft. */
    val schritt: StateFlow<Abgleichschritt?> = _schritt.asStateFlow()

    private fun schritt(neu: Abgleichschritt?) {
        _schritt.value = neu
    }

    /** Ein Grabstein, wie er beim letzten Lauf in Drive lag. Fuer den Purge. */
    private data class Grabsteinsicht(val datei: Drivedatei, val stateChangedAt: Long, val purgeAfter: Long?)

    /**
     * Die DELETED-Dateien des letzten Laufs. Der Purge liest damit nicht alle
     * Dateien ein zweites Mal; der Lauf hat sie ohnehin alle angesehen.
     */
    private var grabsteineDrueben: List<Grabsteinsicht>? = null

    /**
     * Immer nur ein Lauf gleichzeitig. Mehrere Ausloeser, ein Singleton:
     * Zwei gleichzeitige Laeufe wuerden dieselbe Notiz zweimal hochladen und
     * sich gegenseitig Konfliktkopien anlegen.
     */
    suspend fun lauf(token: String): Abgleichergebnis = schloss.withLock {
        _laeuft.value = true
        schritt(Abgleichschritt.Vorbereiten)
        try {
            arbeiten(token)
        } catch (fehler: Drivefehler.NichtErlaubt) {
            Abgleichergebnis.AnmeldungNoetig
        } catch (fehler: Drivefehler.KeinNetz) {
            Abgleichergebnis.KeinNetz
        } catch (fehler: Throwable) {
            Abgleichergebnis.Fehler(fehler.message ?: fehler::class.java.simpleName)
        } finally {
            _laeuft.value = false
            schritt(null)
        }
    }

    // ------------------------------------------------------------ Der Lauf

    /** Zaehler eines Laufs, an einer Stelle statt in zwanzig Variablen. */
    private class Zaehler {
        var hochgeladen = 0
        var geholt = 0
        var konflikte = 0
        var geloescht = 0
        var fehlend = 0
        val unlesbar = ArrayList<String>()
        val grabsteine = ArrayList<Grabsteinsicht>()

        fun gesehen(datei: Drivedatei, huelle: Huelle<*>) {
            if (huelle.state == Zustand.DELETED) {
                grabsteine += Grabsteinsicht(datei, huelle.stateChangedAt, huelle.purgeAfter)
            }
        }
    }

    private suspend fun arbeiten(token: String): Abgleichergebnis {
        val geraet = einstellungen.geraeteId()
        val wurzel = drive.ordner(token)
        val imWurzelordner = drive.inhalt(token, wurzel)
        val z = Zaehler()

        // Reihenfolge: Tags, Ordner, Notizen. Eine Notiz verweist auf beide;
        // kaeme sie vorher an, zeigten ihre Chips ins Leere und sie laege im
        // Hauptordner statt dort, wo sie hingehoert.
        schritt(Abgleichschritt.Ordnung)
        val tagordner = drive.ordner(token, TAGS, wurzel)
        tagsAbgleichen(token, geraet, tagordner, z)

        val ordnerordner = drive.ordner(token, ORDNER, wurzel)
        ordnerAbgleichen(token, geraet, ordnerordner, z)

        val notizordner = drive.ordner(token, NOTIZEN, wurzel)
        val anhaenge = Anhangordner(token, wurzel)
        notizenAbgleichen(token, geraet, notizordner, anhaenge, z)

        // -------------------------------------------------------- Aufraeumen
        schritt(Abgleichschritt.Aufraeumen)

        // Was der Nutzer vom Abgleich ausgenommen hat, muss aus Drive
        // verschwinden. Ohne Grabstein: "bleibt auf diesem Geraet" heisst nicht
        // "ueberall loeschen" (SYNC.md 6.4, die eine Ausnahme von Regel 4).
        var ausgenommen = 0
        val imNotizordner = drive.inhalt(token, notizordner).associateBy { it.name }
        for (notizId in noteDao.ausgenommenMitAblage()) {
            ausDerCloudNehmen(token, notizId, imNotizordner["$notizId.json"])
            ausgenommen++
        }

        // Die Dateien geloeschter Anhaenge. Der Grabstein eines Anhangs ist ein
        // Merkzettel fuer dieses Geraet, kein Vertrag: Die Gegenseite erfaehrt
        // vom geloeschten Anhang dadurch, dass er im Notizdokument fehlt.
        anhangmuellWegraeumen(token, anhaenge)

        // Grabsteine, die aelter sind als das Wasserzeichen, darf dieses Geraet
        // vergessen (SYNC.md 7).
        val wasserzeichen = indexSchreiben(token, wurzel, imWurzelordner, geraet)
        if (wasserzeichen > 0) syncDao.pruneTombstones(wasserzeichen)
        schemaSchreiben(token, wurzel, imWurzelordner)

        val bericht = Pruefbericht(
            erstelltAm = clock.now(),
            fehlendeDateien = z.fehlend,
            unlesbar = z.unlesbar,
            verweiseInsLeere = verweiseInsLeere(),
            konfliktkopien = z.konflikte,
            notizenLokal = noteDao.alleIds().size,
            notizenDrueben = drive.inhalt(token, notizordner).count { it.name.endsWith(".json") },
            ordnerLokal = folderDao.getAllIncludingDeleted().size,
            ordnerDrueben = drive.inhalt(token, ordnerordner).count { it.name.endsWith(".json") },
            tagsLokal = tagDao.getAllIncludingDeleted().size,
            tagsDrueben = drive.inhalt(token, tagordner).count { it.name.endsWith(".json") },
        )
        einstellungen.setLetzterPruefbericht(Sync.encodeToString(bericht))
        grabsteineDrueben = z.grabsteine

        // Erst hier, nicht beim Start: Ein abgebrochener Lauf hat nichts
        // abgeglichen, und ein Zeitstempel, der das Gegenteil behauptet, waere
        // schlimmer als gar keiner.
        einstellungen.setLetzterAbgleich(clock.now())
        return Abgleichergebnis.Fertig(
            hochgeladen = z.hochgeladen,
            geholt = z.geholt,
            konflikte = z.konflikte,
            geloescht = z.geloescht,
            ausgenommen = ausgenommen,
            bericht = bericht,
        )
    }

    // ------------------------------------------------------ Gemeinsames

    /** Wie eine Entitaet hier steht, aus Buchfuehrung, Zustand und Grabstein. */
    private suspend fun lokalstand(
        typ: EntityType,
        id: String,
        vorhanden: Boolean,
        deletedAt: Long?,
        updatedAt: Long,
        geaendertOhneEintrag: Boolean = false,
    ): Abgleichregeln.Lokal? {
        val grabstein = syncDao.tombstone(typ, id)
        if (grabstein != null && !vorhanden) {
            return Abgleichregeln.Lokal(
                baseRev = grabstein.rev - 1,
                geaendert = true,
                zustand = Zustand.DELETED,
                updatedAt = grabstein.deletedAt,
                rev = grabstein.rev,
            )
        }
        if (!vorhanden) return null

        val zustand = syncDao.stateOf(typ, id)
        return Abgleichregeln.Lokal(
            baseRev = zustand?.baseRev ?: 0,
            geaendert = zustand == null || zustand.syncStatus != SyncStatus.SYNCED ||
                geaendertOhneEintrag,
            zustand = zustandVon(deletedAt),
            updatedAt = updatedAt,
        )
    }

    /** Die Huelle einer fernen Datei, oder `null`, wenn sie sich nicht lesen laesst. */
    private suspend inline fun <reified T> fernHuelle(
        token: String,
        datei: Drivedatei,
        z: Zaehler,
    ): Huelle<T>? = runCatching {
        Sync.decodeFromString<Huelle<T>>(drive.lesen(token, datei.id))
    }.getOrElse {
        z.unlesbar += datei.name
        null
    }?.also { z.gesehen(datei, it) }

    private fun Huelle<*>.alsFern() = Abgleichregeln.Fern(rev, state, updatedAt)

    /**
     * Schreibt eine Huelle nach Drive: ueber die vorhandene Datei, sonst neu.
     *
     * Die gemerkte Kennung wird NICHT benutzt, wenn die Dateiliste sie nicht
     * fuehrt: Dann hat jemand die Datei neu angelegt, und die alte Kennung
     * zeigt ins Leere.
     */
    private suspend inline fun <reified T> schreiben(
        token: String,
        ordnerId: String,
        vorhanden: Drivedatei?,
        huelle: Huelle<T>,
    ): Drivedatei {
        val inhalt = Sync.encodeToString(huelle)
        return if (vorhanden != null) {
            drive.ersetzen(token, vorhanden.id, inhalt)
        } else {
            drive.anlegen(token, ordnerId, huelle.id + ".json", inhalt)
        }
    }

    /** Ein Grabstein geht als DELETED-Huelle hoch und merkt sich seinen Zaehler. */
    private suspend fun grabsteinHochladen(
        token: String,
        ordnerId: String,
        vorhanden: Drivedatei?,
        stein: TombstoneEntity,
        rev: Long,
        geraet: String,
    ) {
        schreiben<Notizinhalt>(
            token,
            ordnerId,
            vorhanden,
            grabsteinhuelle(stein.entityType, stein.entityId, rev, stein.deletedAt, geraet),
        )
        syncDao.upsertTombstone(stein.copy(rev = rev))
    }

    /** Ein DELETED von drueben: die Zeile hier weg, der Grabstein mit dem fernen Zaehler. */
    private suspend fun grabsteinUebernehmen(typ: EntityType, id: String, fern: Huelle<*>) {
        when (typ) {
            EntityType.NOTE -> notes.purge(listOf(id))
            EntityType.FOLDER -> db.withTransaction {
                syncDao.dropState(typ, id)
                folderDao.purge(id)
            }
            EntityType.TAG -> db.withTransaction {
                syncDao.dropState(typ, id)
                tagDao.purge(id)
            }
            else -> Unit
        }
        syncDao.upsertTombstone(TombstoneEntity(typ, id, fern.stateChangedAt, rev = fern.rev))
    }

    // ------------------------------------------------------------- Tags

    private suspend fun tagsAbgleichen(token: String, geraet: String, ordnerId: String, z: Zaehler) {
        val fern = drive.inhalt(token, ordnerId).filter { it.name.endsWith(".json") }.associateBy { it.name }
        val lokal = tagDao.getAllIncludingDeleted().associateBy { it.id }
        val steine = syncDao.tombstonesOf(EntityType.TAG).associateBy { it.entityId }
        val ids = lokal.keys + steine.keys + fern.keys.map { it.removeSuffix(".json") }

        for (id in ids) {
            val datei = fern["$id.json"]
            val eigen = lokal[id]
            val huelle = datei?.let { fernHuelle<Taginhalt>(token, it, z) }
            if (datei != null && huelle == null) continue

            val stand = lokalstand(EntityType.TAG, id, eigen != null, eigen?.deletedAt, eigen?.updatedAt ?: 0)
            when (val tat = Abgleichregeln.entscheiden(stand, huelle?.alsFern())) {
                Handlung.Nichts -> Unit

                is Handlung.Hochladen -> if (eigen != null) {
                    tagHochladen(token, ordnerId, datei, eigen, tat.rev, geraet)
                    z.hochgeladen++
                } else {
                    steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                }

                is Handlung.FehltDrueben -> if (eigen != null) {
                    tagHochladen(token, ordnerId, null, eigen, tat.rev, geraet)
                    z.fehlend++
                }

                Handlung.Herunterladen -> {
                    val h = huelle ?: continue
                    if (h.state == Zustand.DELETED) {
                        if (eigen != null) {
                            grabsteinUebernehmen(EntityType.TAG, id, h)
                            z.geloescht++
                        }
                    } else {
                        h.alsDokument()?.let { tagUebernehmen(it, h, datei!!) }
                        z.geholt++
                    }
                }

                is Handlung.Konflikt -> {
                    val h = huelle ?: continue
                    when (val a = tat.aufloesung) {
                        Aufloesung.GeloeschtGewinnt -> if (eigen != null) {
                            if (h.state == Zustand.DELETED) {
                                grabsteinUebernehmen(EntityType.TAG, id, h)
                                z.geloescht++
                            }
                        } else {
                            steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                        }

                        is Aufloesung.PapierkorbGewinnt -> {
                            val e = eigen ?: continue
                            val f = h.alsDokument()?.alsTag() ?: continue
                            val inhalt = if (a.inhaltVonDrueben) f else e
                            val vereint = inhalt.copy(
                                deletedAt = e.deletedAt ?: f.deletedAt,
                                updatedAt = maxOf(e.updatedAt, f.updatedAt),
                            )
                            tagDao.upsertAll(listOf(vereint))
                            tagHochladen(token, ordnerId, datei, vereint, tat.rev, geraet)
                            z.hochgeladen++
                        }

                        is Aufloesung.Kopie -> {
                            // Kein Tag wird kopiert: das hoehere updatedAt gewinnt.
                            val e = eigen ?: continue
                            if (a.fernGewinnt) {
                                h.alsDokument()?.let { tagUebernehmen(it, h, datei!!) }
                                z.geholt++
                            } else {
                                tagHochladen(token, ordnerId, datei, e, tat.rev, geraet)
                                z.hochgeladen++
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun tagHochladen(
        token: String,
        ordnerId: String,
        vorhanden: Drivedatei?,
        tag: TagEntity,
        rev: Long,
        geraet: String,
    ) {
        val huelle = Huelle(
            id = tag.id,
            type = EntityType.TAG.name,
            rev = rev,
            lastEditor = geraet,
            state = zustandVon(tag.deletedAt),
            stateChangedAt = tag.deletedAt ?: tag.createdAt,
            updatedAt = tag.updatedAt,
            payload = tag.alsDokument().alsInhalt(),
        )
        val datei = schreiben(token, ordnerId, vorhanden, huelle)
        syncDao.abgeglichen(EntityType.TAG, tag.id, rev, datei.id, datei.headRevisionId, tag.updatedAt, clock.now())
    }

    private suspend fun tagUebernehmen(doku: Tagdokument, huelle: Huelle<Taginhalt>, datei: Drivedatei) {
        val zeile = doku.alsTag()
        db.withTransaction {
            tagDao.upsertAll(listOf(zeile))
            syncDao.abgeglichen(
                EntityType.TAG, zeile.id, huelle.rev, datei.id, datei.headRevisionId, zeile.updatedAt, clock.now(),
            )
        }
    }

    // ----------------------------------------------------------- Ordner

    private suspend fun ordnerAbgleichen(token: String, geraet: String, ordnerId: String, z: Zaehler) {
        val fern = drive.inhalt(token, ordnerId).filter { it.name.endsWith(".json") }.associateBy { it.name }
        val lokal = folderDao.getAllIncludingDeleted().associateBy { it.id }
        val steine = syncDao.tombstonesOf(EntityType.FOLDER).associateBy { it.entityId }
        val ids = lokal.keys + steine.keys + fern.keys.map { it.removeSuffix(".json") }

        for (id in ids) {
            val datei = fern["$id.json"]
            val eigen = lokal[id]
            val huelle = datei?.let { fernHuelle<Ordnerinhalt>(token, it, z) }
            if (datei != null && huelle == null) continue

            val stand = lokalstand(EntityType.FOLDER, id, eigen != null, eigen?.deletedAt, eigen?.updatedAt ?: 0)
            when (val tat = Abgleichregeln.entscheiden(stand, huelle?.alsFern())) {
                Handlung.Nichts -> Unit

                is Handlung.Hochladen -> if (eigen != null) {
                    ordnerHochladen(token, ordnerId, datei, eigen, tat.rev, geraet)
                    z.hochgeladen++
                } else {
                    steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                }

                is Handlung.FehltDrueben -> if (eigen != null) {
                    ordnerHochladen(token, ordnerId, null, eigen, tat.rev, geraet)
                    z.fehlend++
                }

                Handlung.Herunterladen -> {
                    val h = huelle ?: continue
                    if (h.state == Zustand.DELETED) {
                        if (eigen != null) {
                            grabsteinUebernehmen(EntityType.FOLDER, id, h)
                            z.geloescht++
                        }
                    } else {
                        h.alsDokument()?.let { ordnerUebernehmen(it, h, datei!!) }
                        z.geholt++
                    }
                }

                is Handlung.Konflikt -> {
                    val h = huelle ?: continue
                    when (val a = tat.aufloesung) {
                        Aufloesung.GeloeschtGewinnt -> if (eigen != null) {
                            if (h.state == Zustand.DELETED) {
                                grabsteinUebernehmen(EntityType.FOLDER, id, h)
                                z.geloescht++
                            }
                        } else {
                            steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                        }

                        is Aufloesung.PapierkorbGewinnt -> {
                            val e = eigen ?: continue
                            val f = h.alsDokument()?.alsOrdner() ?: continue
                            val inhalt = if (a.inhaltVonDrueben) f else e
                            val vereint = inhalt.copy(
                                deletedAt = e.deletedAt ?: f.deletedAt,
                                updatedAt = maxOf(e.updatedAt, f.updatedAt),
                            )
                            folderDao.upsertAll(listOf(vereint))
                            ordnerHochladen(token, ordnerId, datei, vereint, tat.rev, geraet)
                            z.hochgeladen++
                        }

                        is Aufloesung.Kopie -> {
                            val e = eigen ?: continue
                            if (a.fernGewinnt) {
                                h.alsDokument()?.let { ordnerUebernehmen(it, h, datei!!) }
                                z.geholt++
                            } else {
                                ordnerHochladen(token, ordnerId, datei, e, tat.rev, geraet)
                                z.hochgeladen++
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun ordnerHochladen(
        token: String,
        ordnerId: String,
        vorhanden: Drivedatei?,
        ordner: FolderEntity,
        rev: Long,
        geraet: String,
    ) {
        val huelle = Huelle(
            id = ordner.id,
            type = EntityType.FOLDER.name,
            rev = rev,
            lastEditor = geraet,
            state = zustandVon(ordner.deletedAt),
            stateChangedAt = ordner.deletedAt ?: ordner.createdAt,
            updatedAt = ordner.updatedAt,
            payload = ordner.alsDokument().alsInhalt(),
        )
        val datei = schreiben(token, ordnerId, vorhanden, huelle)
        syncDao.abgeglichen(
            EntityType.FOLDER, ordner.id, rev, datei.id, datei.headRevisionId, ordner.updatedAt, clock.now(),
        )
    }

    private suspend fun ordnerUebernehmen(doku: Ordnerdokument, huelle: Huelle<Ordnerinhalt>, datei: Drivedatei) {
        val zeile = doku.alsOrdner()
        db.withTransaction {
            folderDao.upsertAll(listOf(zeile))
            syncDao.abgeglichen(
                EntityType.FOLDER, zeile.id, huelle.rev, datei.id, datei.headRevisionId, zeile.updatedAt, clock.now(),
            )
        }
    }

    // ---------------------------------------------------------- Notizen

    /** Der Anhangordner in Drive, erst geholt, wenn er wirklich gebraucht wird. */
    private inner class Anhangordner(private val token: String, private val wurzel: String) {
        private var id: String? = null
        private var liste: Map<String, Drivedatei>? = null

        suspend fun id(): String = id ?: drive.ordner(token, ANHAENGE, wurzel).also { id = it }

        suspend fun dateien(): Map<String, Drivedatei> =
            liste ?: drive.inhalt(token, id()).associateBy { it.name }.also { liste = it }

        fun vergessen() {
            liste = null
        }
    }

    private suspend fun notizenAbgleichen(
        token: String,
        geraet: String,
        ordnerId: String,
        anhaenge: Anhangordner,
        z: Zaehler,
    ) {
        val fern = drive.inhalt(token, ordnerId).filter { it.name.endsWith(".json") }.associateBy { it.name }
        val lokalIds = noteDao.alleIds().toSet()
        val steine = syncDao.tombstonesOf(EntityType.NOTE).associateBy { it.entityId }
        val schmutzig = zuNotizen(syncDao.withStatus(SyncStatus.DIRTY))
        val ids = lokalIds + steine.keys + fern.keys.map { it.removeSuffix(".json") }

        var gesehen = 0
        for (id in ids) {
            schritt(Abgleichschritt.Notizen(gesehen++, ids.size))
            val datei = fern["$id.json"]

            // Ausgenommene Notizen werden gar nicht erst gelesen. Weiter unten
            // fliegt ihre Datei ohnehin raus.
            if (istAusgenommen(id)) continue

            val eigen = if (id in lokalIds) noteDao.getById(id) else null
            val huelle = datei?.let { fernHuelle<Notizinhalt>(token, it, z) }
            if (datei != null && huelle == null) continue

            // Eine leere Notiz geht nicht hoch (NoteWithRelations.istLeer):
            // Beim Anlegen entsteht sofort eine Zeile, die waere in Drive,
            // bevor das erste Wort getippt ist. Die Vormerkung faellt weg,
            // sonst stuende sie fuer immer als "nicht gesichert" in der
            // Kopfzeile; sobald etwas drinsteht, meldet der naechste
            // Schreibvorgang sie erneut an.
            if (eigen != null && eigen.istLeer && datei == null) {
                syncDao.dropState(EntityType.NOTE, id)
                continue
            }

            val stand = lokalstand(
                typ = EntityType.NOTE,
                id = id,
                vorhanden = eigen != null,
                deletedAt = eigen?.note?.deletedAt,
                updatedAt = eigen?.note?.updatedAt ?: 0,
                geaendertOhneEintrag = id in schmutzig,
            )

            when (val tat = Abgleichregeln.entscheiden(stand, huelle?.alsFern())) {
                Handlung.Nichts -> {
                    // Ein Download, der beim letzten Mal schiefging, wird hier
                    // erneut versucht.
                    huelle?.alsDokument()?.let { nachzuegler(token, it, anhaenge) }
                }

                is Handlung.Hochladen -> if (eigen != null) {
                    notizHochladen(token, ordnerId, datei, eigen, tat.rev, geraet, anhaenge)
                    z.hochgeladen++
                } else {
                    steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                }

                is Handlung.FehltDrueben -> if (eigen != null) {
                    notizHochladen(token, ordnerId, null, eigen, tat.rev, geraet, anhaenge)
                    z.fehlend++
                }

                Handlung.Herunterladen -> {
                    val h = huelle ?: continue
                    if (h.state == Zustand.DELETED) {
                        if (eigen != null) {
                            grabsteinUebernehmen(EntityType.NOTE, id, h)
                            z.geloescht++
                        }
                    } else {
                        val doku = h.alsDokument() ?: continue
                        notizUebernehmen(token, doku, h, datei!!, anhaenge)
                        z.geholt++
                    }
                }

                is Handlung.Konflikt -> {
                    val h = huelle ?: continue
                    when (val a = tat.aufloesung) {
                        Aufloesung.GeloeschtGewinnt -> if (eigen != null) {
                            if (h.state == Zustand.DELETED) {
                                grabsteinUebernehmen(EntityType.NOTE, id, h)
                                z.geloescht++
                            }
                        } else {
                            steine[id]?.let { grabsteinHochladen(token, ordnerId, datei, it, tat.rev, geraet) }
                        }

                        is Aufloesung.PapierkorbGewinnt -> {
                            val e = eigen ?: continue
                            val doku = h.alsDokument() ?: continue
                            // Der Zustand TRASHED gewinnt, der neuere Inhalt bleibt.
                            val loeschzeit = e.note.deletedAt ?: doku.deletedAt ?: clock.now()
                            if (a.inhaltVonDrueben) {
                                notizUebernehmen(token, doku.copy(deletedAt = loeschzeit), h, datei!!, anhaenge)
                            } else if (e.note.deletedAt == null) {
                                noteDao.upsert(e.note.copy(deletedAt = loeschzeit))
                            }
                            val jetzt = noteDao.getById(id) ?: continue
                            notizHochladen(token, ordnerId, datei, jetzt, tat.rev, geraet, anhaenge)
                            z.hochgeladen++
                        }

                        is Aufloesung.Kopie -> {
                            val e = eigen ?: continue
                            val doku = h.alsDokument() ?: continue
                            // Kein stilles Ueberschreiben: lokal bleibt, drueben
                            // wird zur Kopie im Eingang, und lokal geht hoch.
                            kopieAnlegen(token, doku, anhaenge)
                            z.konflikte++
                            notizHochladen(token, ordnerId, datei, e, tat.rev, geraet, anhaenge)
                            z.hochgeladen++
                        }
                    }
                }
            }
        }

        // Was der Lauf selbst angelegt hat (Konfliktkopien), geht noch im
        // selben Lauf hoch. Sonst laege die Kopie bis zum naechsten Lauf nur
        // hier, und die Gegenseite saehe den Konflikt nicht.
        for (id in zuNotizen(syncDao.withStatus(SyncStatus.DIRTY)) - ids) {
            val neu = noteDao.getById(id) ?: continue
            if (neu.istLeer || !neu.note.syncEnabled) continue
            notizHochladen(token, ordnerId, null, neu, 1, geraet, anhaenge)
            z.hochgeladen++
        }
    }

    private suspend fun notizHochladen(
        token: String,
        ordnerId: String,
        vorhanden: Drivedatei?,
        notiz: NoteWithRelations,
        rev: Long,
        geraet: String,
        anhaenge: Anhangordner,
    ) {
        // ZUERST die Dateien, DANN das Dokument. Das Dokument traegt die
        // Drive-Kennung jedes Anhangs; wer es vorher schreibt, legt eine Notiz
        // ab, deren Bilder fuer die Gegenseite nirgends liegen.
        anhaengeHochladen(token, notiz.note.id, anhaenge)

        // Frisch lesen: die Anhaenge tragen jetzt ihre Kennungen.
        val voll = noteDao.getById(notiz.note.id) ?: return
        val doku = voll.alsDokument()
        val huelle = Huelle(
            id = doku.id,
            type = EntityType.NOTE.name,
            rev = rev,
            lastEditor = geraet,
            origin = Herkunft.APP,
            state = zustandVon(doku.deletedAt),
            stateChangedAt = doku.deletedAt ?: doku.stageChangedAt,
            updatedAt = doku.updatedAt,
            payload = doku.alsInhalt(),
        )
        val datei = schreiben(token, ordnerId, vorhanden, huelle)

        val jetzt = clock.now()
        db.withTransaction {
            if (voll.note.origin != Herkunft.APP) noteDao.upsert(voll.note.copy(origin = Herkunft.APP))
            syncDao.abgeglichen(EntityType.NOTE, doku.id, rev, datei.id, datei.headRevisionId, doku.updatedAt, jetzt)
            kinderAbhaken(voll.attachments.map { it.id }, EntityType.ATTACHMENT, jetzt)
            kinderAbhaken(voll.transcripts.map { it.id }, EntityType.TRANSCRIPT, jetzt)
            kinderAbhaken(voll.reminders.map { it.id }, EntityType.REMINDER, jetzt)
        }
    }

    /** Schreibt eine ferne Notiz vollstaendig lokal und holt, was dranhaengt. */
    private suspend fun notizUebernehmen(
        token: String,
        doku: Notizdokument,
        huelle: Huelle<Notizinhalt>,
        datei: Drivedatei,
        anhaenge: Anhangordner,
    ) {
        val lastOpenedAt = noteDao.getPlainByIds(listOf(doku.id)).firstOrNull()?.lastOpenedAt
        val herkunft = if (huelle.lastEditor == ASSISTENT || huelle.origin == Herkunft.EXTERNAL) {
            Herkunft.EXTERNAL
        } else {
            Herkunft.APP
        }
        db.withTransaction {
            noteDao.upsert(doku.alsNotiz(lastOpenedAt ?: doku.createdAt).copy(origin = herkunft))
            inhaltErsetzen(doku)
            // Die Notiz ist (wieder) da, ein Grabstein waere ein Widerspruch.
            syncDao.dropTombstone(EntityType.NOTE, doku.id)
            syncDao.abgeglichen(
                EntityType.NOTE, doku.id, huelle.rev, datei.id, datei.headRevisionId, doku.updatedAt, clock.now(),
            )
        }
        notes.reindex(doku.id)
        nebendinge(token, doku, anhaenge)
    }

    /**
     * Checklisteneintraege und Transkripte einer fernen Notiz uebernehmen.
     *
     * Transkripte nicht pauschal ersetzen: Eine gerade fertig aufgenommene Notiz
     * zaehlt `updatedAt` nicht hoch, nur das Transkript wird schmutzig. Ein
     * pauschales Loeschen naehme die Aufnahme mit, bevor sie je oben war.
     */
    private suspend fun inhaltErsetzen(doku: Notizdokument) {
        noteDao.deleteAllItemsOf(doku.id)
        doku.alsEintraege().takeIf { it.isNotEmpty() }?.let { noteDao.upsertItems(it) }

        val fernIds = doku.transcripts.mapTo(mutableSetOf()) { it.id }
        for (segment in transcriptDao.of(doku.id)) {
            if (segment.id in fernIds) continue
            if (nochNichtOben(EntityType.TRANSCRIPT, segment.id)) continue
            transcriptDao.delete(segment.id)
        }
        doku.alsTranskripte().takeIf { it.isNotEmpty() }?.let { transcriptDao.upsertAll(it) }
    }

    private suspend fun nochNichtOben(typ: EntityType, id: String): Boolean =
        syncDao.stateOf(typ, id)?.syncStatus == SyncStatus.DIRTY

    /**
     * Legt die ferne Fassung als eigene Notiz daneben (SYNC.md 6.1, Regel 3).
     *
     * Die Anhaenge bekommen eigene Kennungen und eigene Dateien: Wer spaeter
     * eine der beiden Notizen loescht, riss dem anderen sonst das Bild weg.
     */
    private suspend fun kopieAnlegen(token: String, doku: Notizdokument, anhaenge: Anhangordner) {
        val jetzt = clock.now()
        val titel = Konfliktloesung.konflikttitel(doku.title, datum(jetzt))
        val kopie = Konfliktloesung.alsKonfliktkopie(doku, UUID.randomUUID().toString(), titel, jetzt)

        val kopien = anhaengeFuerKopie(token, doku, kopie.id)
        val neueFlaeche = doku.backgroundAttachmentId?.let { alt -> kopien[alt]?.id }

        db.withTransaction {
            noteDao.upsert(kopie.copy(backgroundAttachmentId = neueFlaeche))
            doku.alsEintraege()
                .map { it.copy(id = UUID.randomUUID().toString(), noteId = kopie.id) }
                .takeIf { it.isNotEmpty() }
                ?.let { noteDao.upsertItems(it) }
            doku.alsTranskripte()
                .map { it.copy(id = UUID.randomUUID().toString(), noteId = kopie.id) }
                .takeIf { it.isNotEmpty() }
                ?.let { transcriptDao.upsertAll(it) }
            if (kopien.isNotEmpty()) attachmentDao.upsertAll(kopien.values.toList())
            if (doku.tagIds.isNotEmpty()) {
                noteDao.linkTags(doku.tagIds.mapIndexed { i, t -> NoteTagCrossRef(kopie.id, t, i) })
            }
            syncDao.markDirty(EntityType.NOTE, kopie.id, jetzt)
        }
        notes.reindex(kopie.id)
    }

    private suspend fun anhaengeFuerKopie(
        token: String,
        doku: Notizdokument,
        kopieId: String,
    ): Map<String, AttachmentEntity> {
        if (doku.attachments.isEmpty()) return emptyMap()
        val gemacht = LinkedHashMap<String, AttachmentEntity>()
        for (anhang in doku.attachments) {
            val kennung = anhang.remoteId ?: continue
            val neueId = UUID.randomUUID().toString()
            val ziel = ablage.ziel(neueId, anhang.mimeType)
            val geladen = runCatching { drive.herunterladen(token, kennung, ziel) }.isSuccess
            if (!geladen || !ziel.exists()) continue
            gemacht[anhang.id] = AttachmentEntity(
                id = neueId,
                noteId = kopieId,
                localPath = ziel.absolutePath,
                mimeType = anhang.mimeType,
                sizeBytes = ziel.length(),
                hash = anhang.hash,
                remoteId = null,
                role = anhang.role,
            )
        }
        return gemacht
    }

    // ------------------------------------------------------ Ausgenommene

    private suspend fun istAusgenommen(id: String): Boolean =
        noteDao.getPlainByIds(listOf(id)).firstOrNull()?.syncEnabled == false

    /**
     * Entfernt eine ausgenommene Notiz samt Dateien aus Drive. Kein Grabstein:
     * Gemeint ist "sie bleibt bei mir", nicht "sie gibt es nicht mehr".
     */
    private suspend fun ausDerCloudNehmen(token: String, notizId: String, datei: Drivedatei?) {
        datei?.let { runCatching { drive.loeschen(token, it.id) } }
        for (anhang in attachmentDao.of(notizId)) {
            anhang.remoteId?.let { runCatching { drive.loeschen(token, it) } }
            attachmentDao.upsertAll(listOf(anhang.copy(remoteId = null)))
            syncDao.dropState(EntityType.ATTACHMENT, anhang.id)
        }
        transcriptDao.of(notizId).forEach { syncDao.dropState(EntityType.TRANSCRIPT, it.id) }
        reminderDao.of(notizId).forEach { syncDao.dropState(EntityType.REMINDER, it.id) }
        syncDao.dropState(EntityType.NOTE, notizId)
    }

    // -------------------------------------------------------- Was dranhaengt

    /**
     * Welche Notizen ein schmutziges Kind haben. Ein geaendertes Bild ist eine
     * geaenderte Notiz: Anhaenge, Transkripte und Erinnerungen liegen
     * eingebettet in der Notizdatei.
     */
    private suspend fun zuNotizen(schmutzig: List<SyncStateEntity>): Set<String> {
        val ids = LinkedHashSet<String>()
        for (zustand in schmutzig) {
            when (zustand.entityType) {
                EntityType.NOTE -> ids += zustand.entityId
                EntityType.ATTACHMENT -> attachmentDao.noteIdOf(zustand.entityId)?.let { ids += it }
                EntityType.TRANSCRIPT -> transcriptDao.noteIdOf(zustand.entityId)?.let { ids += it }
                EntityType.REMINDER -> reminderDao.noteIdOf(zustand.entityId)?.let { ids += it }
                EntityType.TAG, EntityType.NOTE_ITEM, EntityType.FOLDER, EntityType.CALENDAR -> Unit
            }
        }
        return ids
    }

    private suspend fun kinderAbhaken(ids: List<String>, typ: EntityType, jetzt: Long) {
        ids.forEach { syncDao.markSynced(typ, it, jetzt) }
    }

    /**
     * Laedt die Dateien einer Notiz hoch, die drueben noch nicht liegen.
     *
     * Nur einmal je Datei: Ein Anhang aendert seinen Inhalt nie. Liegt sie
     * schon drueben (Neuanfang, eingelesene Sicherung), wird sie uebernommen
     * statt erneut geschickt.
     */
    private suspend fun anhaengeHochladen(token: String, notizId: String, anhaenge: Anhangordner) {
        val offen = attachmentDao.of(notizId).filter { it.remoteId == null }
        if (offen.isEmpty()) return
        val ziel = anhaenge.id()
        val drueben = anhaenge.dateien()
        for (anhang in offen) {
            val datei = File(anhang.localPath)
            if (!datei.exists() || datei.length() == 0L) continue
            val name = anhang.id + "." + endungFuer(anhang.mimeType)
            val abgelegt = drueben[name] ?: drive.anlegenBinaer(token, ziel, name, anhang.mimeType, datei)
                .also { anhaenge.vergessen() }
            attachmentDao.upsertAll(listOf(anhang.copy(remoteId = abgelegt.id)))
        }
    }

    /** Tags, Erinnerungen und Anhaenge einer geholten Notiz, ausserhalb der Transaktion. */
    private suspend fun nebendinge(token: String, doku: Notizdokument, anhaenge: Anhangordner) {
        db.withTransaction {
            noteDao.clearTagsOf(doku.id)
            if (doku.tagIds.isNotEmpty()) {
                noteDao.linkTags(doku.tagIds.mapIndexed { i, t -> NoteTagCrossRef(doku.id, t, i) })
            }
        }
        erinnerungenUebernehmen(doku)
        anhaengeUebernehmen(token, doku, anhaenge)
    }

    /**
     * Erinnerungen der Gegenseite eintragen und wecken. Vergangene werden
     * uebergangen, eine gerade gestellte, noch nicht hochgeladene bleibt.
     */
    private suspend fun erinnerungenUebernehmen(doku: Notizdokument) {
        val alt = reminderDao.of(doku.id)
        if (alt.any { nochNichtOben(EntityType.REMINDER, it.id) }) return
        val neu = doku.reminders.map { it.triggerAt }.filter { it > clock.now() }
        if (alt.map { it.triggerAt }.toSet() == neu.toSet()) return
        weckdienst.abbestellen(alt)
        weckdienst.uebernehmen(doku.id, doku.title, neu)
    }

    /**
     * Anhangszeilen schreiben und fehlende Dateien holen. Ein fehlgeschlagener
     * Download bricht den Abgleich nicht ab; beim naechsten Lauf wird es
     * erneut versucht.
     */
    private suspend fun anhaengeUebernehmen(token: String, doku: Notizdokument, anhaenge: Anhangordner) {
        val vorhanden = attachmentDao.of(doku.id).associateBy { it.id }

        // Was drueben nicht mehr steht, ist drueben geloescht worden. Nur
        // Zeilen anfassen, die schon einmal oben waren.
        val entfernt = vorhanden.values.filter { zeile ->
            zeile.remoteId != null &&
                doku.attachments.none { it.id == zeile.id } &&
                !nochNichtOben(EntityType.ATTACHMENT, zeile.id)
        }
        if (entfernt.isNotEmpty()) {
            attachmentDao.delete(entfernt.map { it.id })
            entfernt.forEach { runCatching { File(it.localPath).delete() } }
        }
        if (doku.attachments.isEmpty()) return

        val zeilen = doku.alsAnhaenge { anhang ->
            vorhanden[anhang.id]?.localPath ?: ablage.ziel(anhang.id, anhang.mimeType).absolutePath
        }
        attachmentDao.upsertAll(zeilen)
        dateienNachholen(token, zeilen, anhaenge)
    }

    /** Holt Anhangsdateien nach, die beim letzten Mal nicht ankamen. */
    private suspend fun nachzuegler(token: String, doku: Notizdokument, anhaenge: Anhangordner) {
        if (doku.attachments.isEmpty()) return
        dateienNachholen(token, attachmentDao.of(doku.id), anhaenge)
    }

    /**
     * Fehlende Dateien holen, ueber die gemerkte Kennung und sonst ueber den
     * Namen im Anhangordner: Nach einem Neuanfang zeigt die Kennung ins Leere.
     */
    private suspend fun dateienNachholen(
        token: String,
        zeilen: List<AttachmentEntity>,
        anhaenge: Anhangordner,
    ) {
        val fehlend = zeilen.filter { zeile ->
            val datei = File(zeile.localPath)
            !datei.exists() || datei.length() == 0L
        }
        if (fehlend.isEmpty()) return
        for (zeile in fehlend) {
            val name = zeile.id + "." + endungFuer(zeile.mimeType)
            val kennung = zeile.remoteId ?: anhaenge.dateien()[name]?.id ?: continue
            val geklappt = runCatching { drive.herunterladen(token, kennung, File(zeile.localPath)) }.isSuccess
            if (!geklappt && zeile.remoteId != null) {
                // Die gemerkte Kennung taugt nicht mehr, ueber den Namen weiter.
                anhaenge.dateien()[name]?.let { runCatching { drive.herunterladen(token, it.id, File(zeile.localPath)) } }
            }
        }
    }

    /**
     * Loescht in Drive, was zu geloeschten Anhaengen gehoert. Gefunden ueber den
     * Namen, denn die Zeile war zum Zeitpunkt des Loeschens schon weg.
     */
    private suspend fun anhangmuellWegraeumen(token: String, anhaenge: Anhangordner) {
        val steine = syncDao.tombstonesOf(EntityType.ATTACHMENT)
        if (steine.isEmpty()) return
        anhaenge.vergessen()
        val dateien = anhaenge.dateien().values.groupBy { it.name.substringBeforeLast('.') }
        for (stein in steine) {
            val treffer = dateien[stein.entityId].orEmpty()
            val geklappt = treffer.all { runCatching { drive.loeschen(token, it.id) }.isSuccess }
            if (geklappt) syncDao.dropTombstone(EntityType.ATTACHMENT, stein.entityId)
        }
        anhaenge.vergessen()
    }

    // ---------------------------------------------------------- Pruefen

    /** Notizen, deren Ordner es nicht (mehr) gibt. Nur gemeldet, nie geaendert. */
    private suspend fun verweiseInsLeere(): Int {
        val ordner = folderDao.getAlleLebenden().mapTo(HashSet()) { it.id }
        return noteDao.getPlainByIds(noteDao.alleIds()).count { it.folderId != null && it.folderId !in ordner }
    }

    // ------------------------------------------------------------ Index

    /**
     * Liest `index.json`, traegt dieses Geraet ein und schreibt sie zurueck.
     * Gibt das Wasserzeichen zurueck. Ein Fehlschlag hier kippt den Lauf
     * nicht: Die Notizen liegen zu diesem Zeitpunkt schon richtig.
     */
    private suspend fun indexSchreiben(
        token: String,
        wurzel: String,
        imWurzelordner: List<Drivedatei>,
        geraet: String,
    ): Long {
        val datei = imWurzelordner.firstOrNull { it.name == INDEX }
        val alt = datei?.let { runCatching { Sync.decodeFromString<Index4>(drive.lesen(token, it.id)) }.getOrNull() }
            ?: Index4()
        val label = einstellungen.geraeteLabel().first()
        val jetzt = clock.now()
        val geraete = alt.devices.filterNot { it.deviceId == geraet } + Geraet(geraet, label, jetzt)
        val neu = alt.copy(schemaVersion = SCHEMA_VERSION_5, devices = geraete.sortedBy { it.deviceId })
        runCatching {
            val inhalt = Sync.encodeToString(neu)
            if (datei != null) drive.ersetzen(token, datei.id, inhalt) else drive.anlegen(token, wurzel, INDEX, inhalt)
        }
        einstellungen.setPurgeWasserzeichen(neu.purgeWatermark)
        return neu.purgeWatermark
    }

    /** `SCHEMA.md` liegt selbstbeschreibend im Ordner, einmal geschrieben. */
    private suspend fun schemaSchreiben(token: String, wurzel: String, imWurzelordner: List<Drivedatei>) {
        if (imWurzelordner.any { it.name == SCHEMA_DATEI }) return
        runCatching { drive.anlegen(token, wurzel, SCHEMA_DATEI, SCHEMA_TEXT) }
    }

    // ------------------------------------------------------------ Purge

    /**
     * Der Purge (SYNC.md 7): die einzige Stelle, die Entitaetsdateien aus Drive
     * entfernt. Laeuft im taeglichen Durchlauf nach einem Lauf ohne Befund.
     *
     * Gibt zurueck, wie viele Dateien entfernt wurden.
     */
    suspend fun purge(token: String): Int = schloss.withLock {
        val wurzel = drive.ordner(token)
        val imWurzelordner = drive.inhalt(token, wurzel)
        val indexdatei = imWurzelordner.firstOrNull { it.name == INDEX } ?: return 0
        val index = runCatching { Sync.decodeFromString<Index4>(drive.lesen(token, indexdatei.id)) }
            .getOrNull() ?: return 0
        val jetzt = clock.now()

        // Die Grabsteine des letzten Laufs, oder, wenn es in diesem Prozess
        // noch keinen gab, alle Dateien einmal durchsehen.
        val kandidaten = grabsteineDrueben ?: alleGrabsteineLesen(token, wurzel)

        var entfernt = 0
        var aeltesterRest: Long? = null
        for (stein in kandidaten) {
            val reif = (stein.purgeAfter ?: Long.MAX_VALUE) < jetzt &&
                index.devices.isNotEmpty() &&
                index.devices.all { it.lastSeenAt > stein.stateChangedAt }
            if (reif) {
                if (runCatching { drive.loeschen(token, stein.datei.id) }.isSuccess) entfernt++
            } else {
                aeltesterRest = minOf(aeltesterRest ?: stein.stateChangedAt, stein.stateChangedAt)
            }
        }
        grabsteineDrueben = null

        val wasserzeichen = aeltesterRest ?: jetzt
        runCatching {
            drive.ersetzen(token, indexdatei.id, Sync.encodeToString(index.copy(purgeWatermark = wasserzeichen)))
        }
        einstellungen.setPurgeWasserzeichen(wasserzeichen)
        syncDao.pruneTombstones(wasserzeichen)
        entfernt
    }

    private suspend fun alleGrabsteineLesen(token: String, wurzel: String): List<Grabsteinsicht> {
        val gefunden = ArrayList<Grabsteinsicht>()
        for (unterordner in listOf(NOTIZEN, ORDNER, TAGS)) {
            val ordnerId = drive.ordner(token, unterordner, wurzel)
            for (datei in drive.inhalt(token, ordnerId)) {
                if (!datei.name.endsWith(".json")) continue
                val huelle = runCatching { Sync.decodeFromString<Huelle<Notizinhalt>>(drive.lesen(token, datei.id)) }
                    .getOrNull() ?: continue
                if (huelle.state == Zustand.DELETED) {
                    gefunden += Grabsteinsicht(datei, huelle.stateChangedAt, huelle.purgeAfter)
                }
            }
        }
        return gefunden
    }

    private fun datum(millis: Long) = SimpleDateFormat("d.M.", Locale.GERMANY).format(Date(millis))

    private companion object {
        const val NOTIZEN = "notes"
        const val ORDNER = "folders"
        const val TAGS = "tags"
        const val ANHAENGE = "attachments"
        const val INDEX = "index.json"
        const val SCHEMA_DATEI = "SCHEMA.md"
    }
}
