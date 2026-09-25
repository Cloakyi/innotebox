package de.notizen.core.data.repository

import androidx.room.withTransaction
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.Ordnerzaehlung
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.ordner.darfHinein
import de.notizen.core.data.ordner.nachkommen
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordner. Das selbstgebaute Ordnungssystem neben dem Fluss.
 *
 * Der Baum wird hier nicht berechnet. Wer wissen will, was unter einem
 * Ordner liegt, fragt `Ordnerregeln`; dieses Repository schreibt nur. Die
 * Trennung ist Absicht: Die Rechenregeln sind die Stelle, an der ein Kreis im
 * Baum entsteht, und die soll ohne Datenbank pruefbar bleiben.
 *
 * Geloescht wird weich. Ein Ordner behaelt seine Zeile und bekommt ein
 * `deletedAt`. Anders als bei den Tags gibt es fuer Ordner keinen Grabstein in
 * Drive: Der Abgleich schickt ausschliesslich Grabsteine vom Typ NOTE. Die
 * weiche Loeschung ist damit die einzige Nachricht, die das andere Geraet je
 * erreicht.
 */
@Singleton
class FolderRepository @Inject constructor(
    private val db: NotizenDatabase,
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val clock: Clock,
) {

    /**
     * Die lebenden Ordner eines Bereichs (SYNC.md 13). Ohne Angabe der
     * normale Baum; das Archiv des Ordnermodus hat seinen eigenen.
     */
    fun observeAll(bereich: Bereich = Bereich.ORDNER): Flow<List<FolderEntity>> =
        folderDao.observeAll(bereich)

    fun observeZaehlung(): Flow<List<Ordnerzaehlung>> = folderDao.observeZaehlung()

    suspend fun getAll(bereich: Bereich = Bereich.ORDNER): List<FolderEntity> =
        folderDao.getAll(bereich)

    suspend fun get(id: String): FolderEntity? = folderDao.getById(id)

    /** Alle Zeilen, auch die im Papierkorb. Fuer Pfade, die durch geloeschte Ordner fuehren. */
    suspend fun getAllIncludingDeleted(): List<FolderEntity> = folderDao.getAllIncludingDeleted()

    /**
     * Legt einen Ordner an und gibt ihn zurueck.
     *
     * Gleiche Namen sind erlaubt, auch unter denselben Eltern. Ein Dateisystem
     * verboete das; hier waere es eine Huerde ohne Gewinn, denn zwei Ordner
     * "Rechnungen" nebeneinander sind ein Versehen, das man sieht und in einer
     * Sekunde behebt.
     *
     * Der Bereich erbt sich vom Elternteil. Ein Unterordner eines
     * Archivordners ist ein Archivordner; [bereich] zaehlt nur auf der obersten
     * Ebene, wo es kein Elternteil gibt, das es sagen koennte.
     */
    suspend fun anlegen(
        name: String,
        elternId: String? = null,
        bereich: Bereich = Bereich.ORDNER,
    ): FolderEntity {
        val jetzt = clock.now()
        val eltern = elternId?.let { folderDao.getById(it) }
        val ordner = FolderEntity(
            id = UUID.randomUUID().toString(),
            parentId = elternId,
            name = name.trim(),
            createdAt = jetzt,
            updatedAt = jetzt,
            bereich = eltern?.bereich ?: bereich,
        )
        db.withTransaction {
            folderDao.upsert(ordner)
            syncDao.markDirty(EntityType.FOLDER, ordner.id, jetzt)
        }
        return ordner
    }

    suspend fun umbenennen(id: String, name: String) {
        val jetzt = clock.now()
        db.withTransaction {
            folderDao.rename(id, name.trim(), jetzt)
            syncDao.markDirty(EntityType.FOLDER, id, jetzt)
        }
    }

    /**
     * Schreibt die eigene Reihenfolge einer Geschwisterreihe: die
     * Stelle in [ids] wird der `sortIndex`. Nur was sich geaendert hat, wird
     * geschrieben und abgeglichen; wer den Haken drueckt, ohne gezogen zu
     * haben, loest keinen Abgleich aus.
     */
    suspend fun reihenfolgeSetzen(ids: List<String>) {
        val jetzt = clock.now()
        val bisher = ids.mapNotNull { folderDao.getById(it) }.associateBy { it.id }
        db.withTransaction {
            ids.forEachIndexed { stelle, id ->
                if (bisher[id]?.sortIndex != stelle) {
                    folderDao.setSortIndex(id, stelle, jetzt)
                    syncDao.markDirty(EntityType.FOLDER, id, jetzt)
                }
            }
        }
    }

    suspend fun umfaerben(id: String, colorArgb: Int?) {
        val jetzt = clock.now()
        db.withTransaction {
            folderDao.recolor(id, colorArgb, jetzt)
            syncDao.markDirty(EntityType.FOLDER, id, jetzt)
        }
    }

    /**
     * Haengt einen Ordner woandershin. `null` ist die oberste Ebene.
     *
     * Die Pruefung steht hier und nicht nur in der Oberflaeche. Die
     * Ordnerauswahl bietet unmoegliche Ziele zwar gar nicht erst an, aber der
     * Baum kann sich zwischen dem Aufgehen der Auswahl und dem Antippen
     * geaendert haben -- etwa durch einen Abgleich, der einen Ordner
     * verschoben hat. Ein Ordner in seinem eigenen Unterordner haengt danach
     * an nichts mehr und ist nirgends mehr zu erreichen.
     *
     * Gibt zurueck, ob verschoben wurde.
     */
    suspend fun verschieben(id: String, zielId: String?): Boolean {
        val alle = folderDao.getAlleLebenden()
        if (!darfHinein(alle, id, zielId)) return false

        val jetzt = clock.now()
        db.withTransaction {
            folderDao.reparent(id, zielId, jetzt)
            syncDao.markDirty(EntityType.FOLDER, id, jetzt)
        }
        return true
    }

    /**
     * Loescht einen Ordner, ohne seinen Inhalt mitzunehmen.
     *
     * Notizen und Unterordner wandern nach [zielId]; ohne Angabe eine Ebene
     * hoeher. Das ist der schonende Weg, und er war lange der einzige: Wer
     * einen Ordner wegraeumt, will meistens den Ordner los sein und nicht das,
     * was darin lag.
     *
     * Der Ordner selbst landet trotzdem im Papierkorb und nicht im Nichts. Wer
     * ihn zurueckholt, bekommt ihn leer zurueck -- die Notizen liegen ja
     * inzwischen woanders, und sie ein zweites Mal zu verschieben waere eine
     * Ueberraschung. Was herausgerueckt ist, merkt sich aber, woher es kam
     * (`ehemaligerOrdnerId`, `ehemaligerElternId`): Der Ordner im Papierkorb
     * zeigt es ausgegraut als seinen frueheren Inhalt.
     */
    suspend fun loeschen(id: String, zielId: String? = null) {
        val jetzt = clock.now()
        val ordner = folderDao.getById(id) ?: return
        val ziel = zielId ?: ordner.parentId

        // Vor der Transaktion geholt: Danach traegt keine Notiz mehr diesen
        // Ordner, und es waere nicht mehr herauszufinden, welche gemeint waren.
        val betroffene = noteDao.idsImOrdner(id)
        val kinder = folderDao.getAll(ordner.bereich).filter { it.parentId == id }

        db.withTransaction {
            noteDao.moveFolderContents(id, ziel, jetzt)
            kinder.forEach {
                folderDao.herausruecken(it.id, ziel, id, jetzt)
                syncDao.markDirty(EntityType.FOLDER, it.id, jetzt)
            }
            folderDao.softDelete(id, jetzt)
            syncDao.markDirty(EntityType.FOLDER, id, jetzt)
            betroffene.forEach { syncDao.markDirty(EntityType.NOTE, it, jetzt) }
        }
    }

    /**
     * Loescht einen Ordner MIT allem, was darin liegt.
     *
     * Alles landet im Papierkorb, nichts verschwindet. Die Notizen behalten
     * dabei ihre `folderId` -- genau das ist der Grund, warum sich der Ordner
     * spaeter samt Inhalt zurueckholen laesst. Ohne sie waeren es hinterher
     * dreissig einzelne Notizen im Papierkorb, und niemand wuesste mehr, dass
     * sie zusammengehoerten.
     *
     * Die Unterordner gehen mit, und ihre Notizen auch. Ein Loeschen „samt
     * Inhalt", das beim ersten Unterordner haltmacht, waere die Sorte
     * Halbheit, die man erst bemerkt, wenn man den Papierkorb durchsucht.
     */
    suspend fun loeschenMitInhalt(id: String) {
        val jetzt = clock.now()
        val ordner = folderDao.getById(id) ?: return

        val alle = folderDao.getAll(ordner.bereich)
        val betroffeneOrdner = listOf(id) + nachkommen(alle, id)
        val betroffeneNotizen = noteDao.lebendeInOrdnern(betroffeneOrdner)

        db.withTransaction {
            if (betroffeneNotizen.isNotEmpty()) {
                noteDao.softDelete(betroffeneNotizen, jetzt)
                betroffeneNotizen.forEach { syncDao.markDirty(EntityType.NOTE, it, jetzt) }
            }
            betroffeneOrdner.forEach {
                folderDao.softDelete(it, jetzt)
                syncDao.markDirty(EntityType.FOLDER, it, jetzt)
            }
        }
    }

    /**
     * Holt einen Ordner aus dem Papierkorb zurueck.
     *
     * [mitNotizen] entscheidet, ob die Notizen mitkommen, die mit ihm
     * hineingewandert sind. Beides ist ein sinnvoller Wunsch: Wer den Ordner
     * versehentlich geloescht hat, will alles zurueck; wer ihn aufgeraeumt hat
     * und nur die Huelle braucht, will genau das.
     *
     * Die Eltern werden mit zurueckgeholt, wenn sie auch im Papierkorb
     * liegen. Sonst haette der Ordner ein Elternteil, das es nicht gibt, und
     * haenge nach `Ordnerregeln.kinder` an der Wurzel statt dort, wo er war.
     */
    suspend fun wiederherstellen(id: String, mitNotizen: Boolean) {
        val jetzt = clock.now()
        val alle = folderDao.getAllIncludingDeleted().associateBy { it.id }

        // Der Weg nach oben, solange die Kette im Papierkorb liegt. Der Merker
        // ist kein Beiwerk: Zeigen zwei geloeschte Ordner ueber Kreuz
        // aufeinander, laeuft die Schleife ohne ihn ewig.
        val zurueck = LinkedHashSet<String>()
        var jetziger = alle[id]
        while (jetziger != null && jetziger.deletedAt != null && zurueck.add(jetziger.id)) {
            jetziger = jetziger.parentId?.let { alle[it] }
        }

        val notizen = if (mitNotizen) {
            zurueck.flatMap { noteDao.geloeschteImOrdner(it) }
        } else {
            emptyList()
        }

        db.withTransaction {
            zurueck.forEach {
                folderDao.restore(it, jetzt)
                syncDao.markDirty(EntityType.FOLDER, it, jetzt)
            }
            if (notizen.isNotEmpty()) {
                noteDao.restore(notizen)
                notizen.forEach { syncDao.markDirty(EntityType.NOTE, it, jetzt) }
            }
        }
    }

    /**
     * Macht [loeschenMitInhalt] rueckgaengig: der Ordner, alle geloeschten
     * Nachkommen und alle Notizen darin kommen zurueck.
     *
     * Fuer die Undo-Leiste. Anders als [wiederherstellen] geht es hier nach
     * UNTEN, nicht nach oben: Wer eben "Ordner und Inhalt" angetippt hat und
     * es sich anders ueberlegt, will den Stand von vorher, und dazu gehoeren
     * die Unterordner. Der Weg nach oben wird trotzdem mitgenommen, aus
     * demselben Grund wie dort.
     */
    suspend fun wiederherstellenSamtInhalt(id: String) {
        val jetzt = clock.now()
        val alle = folderDao.getAllIncludingDeleted()
        val nachId = alle.associateBy { it.id }

        val zurueck = LinkedHashSet<String>()
        var jetziger = nachId[id]
        while (jetziger != null && jetziger.deletedAt != null && zurueck.add(jetziger.id)) {
            jetziger = jetziger.parentId?.let { nachId[it] }
        }
        nachkommen(alle, id).filter { nachId[it]?.deletedAt != null }.forEach { zurueck.add(it) }

        val notizen = zurueck.flatMap { noteDao.geloeschteImOrdner(it) }

        db.withTransaction {
            zurueck.forEach {
                folderDao.restore(it, jetzt)
                syncDao.markDirty(EntityType.FOLDER, it, jetzt)
            }
            if (notizen.isNotEmpty()) {
                noteDao.restore(notizen)
                notizen.forEach { syncDao.markDirty(EntityType.NOTE, it, jetzt) }
            }
        }
    }

    /**
     * Entfernt einen Ordner endgueltig aus dem Papierkorb.
     *
     * Die Notizen darin gehen NICHT mit. Sie liegen weiter im Papierkorb
     * und lassen sich einzeln zurueckholen; nur ihre Ordnerkennung zeigt danach
     * ins Leere, was heisst: Hauptordner. Endgueltiges Loeschen von Notizen
     * laeuft ueber den einen Weg, den es dafuer gibt (`NoteRepository.purge`),
     * mit Grabsteinen und dem Aufraeumen der Dateien. Ein zweiter Weg hier
     * waere die Stelle, an der man eines von beidem vergisst.
     */
    suspend fun endgueltigLoeschen(id: String) {
        val jetzt = clock.now()
        db.withTransaction {
            // Seit Schema 4 gibt es auch fuer Ordner einen Grabstein, der als
            // DELETED-Datei nach Drive geht (SYNC.md 3.2). Vorher war das
            // Entfernen der Zeile die einzige Nachricht, und die kam nie an:
            // der Geisterordner vom 2026-09-15.
            val basis = syncDao.baseRevOf(EntityType.FOLDER, id) ?: 0
            syncDao.upsertTombstone(TombstoneEntity(EntityType.FOLDER, id, jetzt, rev = basis + 1))
            syncDao.dropState(EntityType.FOLDER, id)
            folderDao.purge(id)
        }
    }

    fun observeImPapierkorb(): Flow<List<FolderEntity>> = folderDao.observeImPapierkorb()

    /**
     * Leert den Papierkorb von Ordnern, die laenger als [tage] darin liegen.
     *
     * Bis zum 2026-09-14 kannte das automatische Leeren nur Notizen; ein
     * Ordner im Papierkorb blieb fuer immer liegen. Dieselbe Frist wie bei den
     * Notizen und keine eigene Einstellung: Was im Papierkorb liegt, liegt dort
     * nicht ohne Grund. Laeuft ueber [endgueltigLoeschen], denselben Weg wie von
     * Hand. Die Notizen des Ordners bleiben, wo sie sind; um sie kuemmert sich
     * `NoteRepository.papierkorbAufraeumen` mit derselben Frist.
     *
     * @return wie viele Ordner entfernt wurden
     */
    suspend fun papierkorbAufraeumen(tage: Int): Int {
        if (tage <= 0) return 0
        val schwelle = clock.now() - tage * 24L * 60 * 60 * 1000
        val faellig = folderDao.ueberfaelligImPapierkorb(schwelle)
        faellig.forEach { endgueltigLoeschen(it) }
        return faellig.size
    }

    fun observeZaehlungImPapierkorb(): Flow<List<Ordnerzaehlung>> =
        noteDao.observeZaehlungImPapierkorb()

    fun observeInhaltImPapierkorb(ordnerId: String) =
        noteDao.observeImPapierkorbVon(ordnerId)

    /**
     * Was aus einem geloeschten Ordner herausgerueckt ist und jetzt woanders
     * lebt: der fruehere Inhalt, ausgegraut im Papierkorb.
     */
    fun observeHerausgerueckteNotizen(ordnerId: String) =
        noteDao.observeHerausgerueckte(ordnerId)

    fun observeHerausgerueckteOrdner(ordnerId: String): Flow<List<FolderEntity>> =
        folderDao.observeHerausgerueckte(ordnerId)
}
