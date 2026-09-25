package de.notizen.core.sync.sicherung

import androidx.room.withTransaction
import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.aussen.Weckdienst
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TagDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.db.entity.NoteTagCrossRef
import de.notizen.core.data.db.entity.TombstoneEntity
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.util.Clock
import de.notizen.core.sync.alsAnhaenge
import de.notizen.core.sync.alsDokument
import de.notizen.core.sync.alsEintraege
import de.notizen.core.sync.alsNotiz
import de.notizen.core.sync.alsOrdner
import de.notizen.core.sync.alsTag
import de.notizen.core.sync.alsTranskripte
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Wie eine Sicherung oder eine Wiederherstellung ausgegangen ist. */
sealed interface Sicherungsergebnis {

    data class Gesichert(
        val notizen: Int,
        val tags: Int,
        val ordner: Int,
        val dateien: Int,
    ) : Sicherungsergebnis

    data class Eingelesen(
        val neu: Int,
        val ersetzt: Int,
        val behalten: Int,
        val tags: Int,
        val dateien: Int,
        /** Wie viele davon hier geloescht waren und als neue Notiz zurueckkamen. */
        val zurueckgeholt: Int = 0,
    ) : Sicherungsergebnis

    /** Die gewaehlte Datei ist keine Sicherung dieser App. */
    data object KeineSicherung : Sicherungsergebnis

    /** Die Datei kommt aus einer neueren Fassung der App. */
    data class ZuNeu(val formatVersion: Int) : Sicherungsergebnis

    data class Fehler(val grund: String) : Sicherungsergebnis
}

/**
 * Sichern und Wiederherstellen.
 *
 * Der Zweck ist ein anderer als beim Abgleich. Der Abgleich haelt zwei
 * Geraete beieinander; eine Sicherung stellt dieses eine wieder her, wenn nichts
 * mehr da ist. Deshalb geht hier auch mit, was der Abgleich absichtlich
 * weglaesst (siehe [Sicherungsnotiz]), und deshalb liegt alles in EINER Datei,
 * die man kopieren, verschicken und weglegen kann.
 *
 * Das Format ist trotzdem dasselbe. Eine Notiz sieht in der Sicherung aus
 * wie in Google Drive, Feld fuer Feld. Ein zweites Format daneben waere eine
 * zweite Stelle, an der dieselbe Wahrheit steht, und die beiden liefen frueher
 * oder spaeter auseinander.
 *
 * Beim Wiederherstellen wird nie geloescht. Es gewinnt der neuere Stand,
 * und was es nur hier gibt, bleibt. Wer eine alte Sicherung einliest, soll
 * dabei nicht verlieren, was er seither geschrieben hat.
 */
@Singleton
class Sicherung @Inject constructor(
    private val notes: NoteRepository,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val folderDao: FolderDao,
    private val attachmentDao: AttachmentDao,
    private val transcriptDao: TranscriptDao,
    private val syncDao: SyncDao,
    private val ablage: Anhangablage,
    private val weckdienst: Weckdienst,
    private val db: NotizenDatabase,
    private val clock: Clock,
) {

    private val paket = Sicherungspaket()

    /** Die Zaehlerstaende aus der gerade gelesenen Datei, je "TYP:id". */
    private var zaehlerstand: Map<String, Long> = emptyMap()

    /**
     * Setzt `baseRev` aus der Sicherung und merkt die Entitaet zum Hochladen
     * vor. Der naechste Abgleich schreibt dann `rev + 1` und ueberschreibt den
     * Spiegel, statt von ihm ueberstimmt zu werden.
     */
    private suspend fun vormerken(typ: EntityType, id: String, updatedAt: Long) {
        val rev = zaehlerstand[typ.name + ":" + id]
        if (rev != null) {
            syncDao.abgeglichen(typ, id, rev, null, null, updatedAt, clock.now())
        }
        syncDao.markDirty(typ, id, updatedAt)
    }

    /**
     * Schreibt den gesamten Bestand in [ziel].
     *
     * [erzeugtVon] steht im Kopf der Datei und sagt spaeteren Lesern, wer sie
     * geschrieben hat. Bei einer Datei, die Jahre liegen kann, ist das die
     * Angabe, nach der man am ehesten sucht.
     */
    suspend fun sichern(ziel: OutputStream, erzeugtVon: String): Sicherungsergebnis = try {
        val ids = noteDao.alleIds()
        val tags = tagDao.getAllIncludingDeleted().map { it.alsDokument() }

        // Auch die geloeschten Ordner gehen mit, aus demselben Grund wie bei
        // den Tags: Ein Ordner mit `deletedAt` ist eine Loeschung, und wer sie
        // weglaesst, holt den Ordner beim Wiederherstellen zurueck.
        val ordner = folderDao.getAllIncludingDeleted().map { it.alsDokument() }

        // Fassung 3: die Zaehlerstaende und die Grabsteine (SYNC.md 10). Ohne
        // die Zaehler verloere ein wiederhergestellter Stand beim naechsten
        // Abgleich gegen den Spiegel; ohne die Grabsteine kaeme Geloeschtes
        // aus dem Spiegel zurueck.
        val zaehler = syncDao.alleStaende()
            .filter { it.entityType == EntityType.NOTE || it.entityType == EntityType.FOLDER || it.entityType == EntityType.TAG }
            .filter { it.baseRev > 0 }
            .map { Zaehlerstand(it.entityType.name, it.entityId, it.baseRev) }
        val grabsteine = syncDao.allTombstones()
            .filter { it.entityType == EntityType.NOTE || it.entityType == EntityType.FOLDER || it.entityType == EntityType.TAG }
            .map { Grabsteineintrag(it.entityType.name, it.entityId, it.deletedAt, it.rev) }

        val bericht = paket.packen(
            ziel = ziel,
            kopf = Sicherungskopf(
                erzeugtAm = clock.now(),
                erzeugtVon = erzeugtVon,
                notizen = ids.size,
                tags = tags.size,
                ordner = ordner.size,
                geloescht = grabsteine.size,
            ),
            tags = tags,
            ordner = ordner,
            zaehler = zaehler,
            geloescht = grabsteine,
            notizIds = ids,
            notizFuer = { id ->
                noteDao.getById(id)?.let { voll ->
                    val doku = voll.alsDokument()
                    Sicherungsnotiz(
                        // Die Drive-Kennung geht NICHT mit. Sie gilt nur in dem
                        // Google-Konto, in dem sie vergeben wurde. In einer
                        // Datei, die auch woanders landen kann, ist sie
                        // entweder bedeutungslos oder irrefuehrend.
                        notiz = doku.copy(
                            attachments = doku.attachments.map { it.copy(remoteId = null) },
                        ),
                        lastOpenedAt = voll.note.lastOpenedAt,
                        syncEnabled = voll.note.syncEnabled,
                    )
                }
            },
            dateiFuer = { anhang -> attachmentDao.byId(anhang.id)?.let { File(it.localPath) } },
        )

        Sicherungsergebnis.Gesichert(
            bericht.notizen,
            bericht.tags,
            bericht.ordner,
            bericht.dateien,
        )
    } catch (fehler: Throwable) {
        Sicherungsergebnis.Fehler(fehler.message ?: fehler::class.java.simpleName)
    }

    /**
     * Liest eine Sicherung ein.
     *
     * Die Dateien der Anhaenge gehen sofort auf die Platte, die Notizen erst
     * danach in die Datenbank. Das ist die richtige Reihenfolge: Eine Zeile, die
     * auf eine Datei zeigt, die noch nicht da ist, gaebe ein leeres Bild; eine
     * Datei ohne Zeile ist nur Platz, den beim naechsten Aufraeumen jemand
     * zurueckholt.
     */
    suspend fun wiederherstellen(quelle: InputStream): Sicherungsergebnis = try {
        // Was es hier schon gibt, wird nicht ueberschrieben. Anhaenge sind in
        // dieser App unveraenderlich: Ein Bild wird einmal angelegt und nie
        // wieder angefasst. Zwei Dateien mit derselben Kennung sind deshalb
        // dieselbe Datei, und die vorhandene liegt womoeglich im Bild- oder
        // Aufnahmeordner, wo die Notiz sie erwartet.
        val vorhanden = attachmentDao.alle().associate { it.id to it.localPath }

        val inhalt = paket.entpacken(quelle) { name ->
            val alt = vorhanden[anhangIdAus(name)]?.let { File(it) }
            if (alt != null && alt.isFile && alt.length() > 0L) null else ablage.ziel(name)
        }

        if (inhalt.kopf == null && inhalt.notizen.isEmpty()) {
            return Sicherungsergebnis.KeineSicherung
        }
        val kopf = inhalt.kopf
        if (kopf != null && kopf.formatVersion > SICHERUNG_VERSION) {
            return Sicherungsergebnis.ZuNeu(kopf.formatVersion)
        }

        val tags = tagsUebernehmen(inhalt)

        // Fassung 3: Grabsteine kommen mit, damit Geloeschtes nicht aus dem
        // Spiegel zurueckkommt, und die Zaehlerstaende, damit der
        // wiederhergestellte Stand den Spiegel ueberschreibt (SYNC.md 10).
        zaehlerstand = inhalt.zaehler.associate { (it.type + ":" + it.id) to it.rev }
        for (stein in inhalt.geloescht) {
            val typ = runCatching { EntityType.valueOf(stein.type) }.getOrNull() ?: continue
            if (syncDao.tombstone(typ, stein.id) == null) {
                syncDao.upsertTombstone(TombstoneEntity(typ, stein.id, stein.deletedAt, stein.rev))
            }
        }

        // VOR den Notizen. Eine Notiz verweist ueber `folderId` auf einen
        // Ordner; kommt sie zuerst, liegt sie fuer einen Augenblick im
        // Hauptordner. Das faellt zwar nicht auf, weil danach ohnehin neu
        // gezeichnet wird, aber die Reihenfolge ist dieselbe wie beim Abgleich,
        // und zwei Wege, die dasselbe tun, sollen es gleich tun.
        ordnerUebernehmen(inhalt)
        var neu = 0
        var ersetzt = 0
        var behalten = 0
        var zurueckgeholt = 0

        // Erst nach den Tags: Eine Notiz verweist auf Tag-Kennungen, und eine
        // Verknuepfung auf einen Tag, den es noch nicht gibt, weist die
        // Datenbank ab.
        val bekannteTags = tagDao.getAllIncludingDeleted().mapTo(HashSet()) { it.id }

        for (eintrag in inhalt.notizen) {
            val doku = eintrag.notiz
            val lokal = noteDao.getPlainByIds(listOf(doku.id)).firstOrNull()

            // EINE HIER GELOESCHTE NOTIZ KOMMT MIT EINER NEUEN KENNUNG ZURUECK.
            //
            // Sie unter ihrer alten wiederzubeleben hiesse, gegen ihren
            // Grabstein anzutreten -- und der ist keine Kleinigkeit, sondern die
            // Nachricht an alle Geraete, dass es diese Notiz nicht mehr gibt.
            // Wer sie ueberstimmt, muss sie ueberall zurueckziehen, und in dem
            // Moment, in dem das irgendwo nicht ankommt, loescht das andere
            // Geraet die Notiz wieder. Genau so ist es am 2026-08-23 am Geraet
            // schiefgegangen.
            //
            // Eine neue Kennung hat gar keinen Grabstein. Es gibt also nichts zu
            // streiten, statt den Streit gewinnen zu muessen. Der alte
            // Grabstein bleibt gueltig und macht weiter seine Arbeit.
            //
            // Nur fuer den geloeschten Fall, nicht fuer jeden. Wer eine
            // Sicherung auf ein frisches Geraet einliest, will seine Notizen
            // dort wiederhaben, wo sie waren, und nicht alles doppelt im
            // Eingang.
            if (lokal == null && syncDao.tombstone(EntityType.NOTE, doku.id) != null) {
                val auflage = neuAuflegen(eintrag, clock.now()) { UUID.randomUUID().toString() }
                val umgezogen = dateienUmziehen(auflage, inhalt.dateien)

                schreiben(auflage.eintrag, umgezogen, vorhanden, bekannteTags)
                erinnerungenStellen(auflage.eintrag)
                neu++
                zurueckgeholt++
                continue
            }

            when (entscheiden(lokal?.updatedAt, doku.updatedAt)) {
                Uebernahme.BEHALTEN -> {
                    behalten++
                    continue
                }

                Uebernahme.NEU -> neu++
                Uebernahme.ERSETZEN -> ersetzt++
            }

            schreiben(eintrag, inhalt.dateien, vorhanden, bekannteTags)
            erinnerungenStellen(eintrag)
        }

        Sicherungsergebnis.Eingelesen(
            neu = neu,
            ersetzt = ersetzt,
            behalten = behalten,
            tags = tags,
            dateien = inhalt.dateien.size,
            zurueckgeholt = zurueckgeholt,
        )
    } catch (fehler: Throwable) {
        Sicherungsergebnis.Fehler(fehler.message ?: fehler::class.java.simpleName)
    }

    // ------------------------------------------------------------- Bausteine

    private suspend fun tagsUebernehmen(inhalt: Sicherungsinhalt): Int {
        var uebernommen = 0
        for (tag in inhalt.tags) {
            val lokal = tagDao.getById(tag.id)
            if (entscheiden(lokal?.updatedAt, tag.updatedAt) == Uebernahme.BEHALTEN) continue

            tagDao.upsert(tag.alsTag())
            vormerken(EntityType.TAG, tag.id, tag.updatedAt)
            uebernommen++
        }
        return uebernommen
    }

    /**
     * Die Ordner aus der Datei.
     *
     * Dieselbe Entscheidung wie bei den Tags und bei den Notizen: Der neuere
     * Stand gewinnt, geloescht wird nie. Ein Ordner, den es nur hier gibt,
     * bleibt also stehen, auch wenn die Sicherung ihn nicht kennt.
     */
    private suspend fun ordnerUebernehmen(inhalt: Sicherungsinhalt): Int {
        var uebernommen = 0
        for (ordner in inhalt.ordner) {
            val lokal = folderDao.getById(ordner.id)
            if (entscheiden(lokal?.updatedAt, ordner.updatedAt) == Uebernahme.BEHALTEN) continue

            folderDao.upsert(ordner.alsOrdner())
            vormerken(EntityType.FOLDER, ordner.id, ordner.updatedAt)
            uebernommen++
        }
        return uebernommen
    }

    /**
     * Die Notiz und alles, was in ihrer Datei steht.
     *
     * Eintraege und Transkripte werden ersetzt, Anhangszeilen nicht. Wer in
     * der Sicherung einen Checklisteneintrag nicht mehr hat, soll ihn danach
     * auch hier nicht mehr haben. Eine Anhangszeile dagegen haengt an einer
     * Datei auf der Platte, und die zu entfernen waere ein Loeschen. Geloescht
     * wird beim Wiederherstellen nicht.
     */
    private suspend fun schreiben(
        eintrag: Sicherungsnotiz,
        dateien: Map<String, File>,
        vorhanden: Map<String, String>,
        bekannteTags: Set<String>,
    ) {
        val doku = eintrag.notiz

        // Reihenfolge mit Bedacht: Erst die eben ausgepackte Datei, dann die,
        // die hier schon lag. Eine Aufnahme, die auf diesem Geraet entstanden
        // ist, liegt im Aufnahmeordner und nicht bei den ausgepackten Dateien.
        // Ihr jetzt einen anderen Pfad zu geben hiesse, sie fuer die Notiz
        // verschwinden zu lassen, obwohl sie unveraendert daliegt.
        val anhaenge = doku.alsAnhaenge { anhang ->
            dateien[anhang.id]?.absolutePath
                ?: vorhanden[anhang.id]
                ?: ablage.ziel(anhang.id, anhang.mimeType).absolutePath
        }

        db.withTransaction {
            noteDao.upsert(
                doku.alsNotiz(lastOpenedAt = eintrag.lastOpenedAt)
                    .copy(syncEnabled = eintrag.syncEnabled),
            )

            noteDao.deleteAllItemsOf(doku.id)
            doku.alsEintraege().takeIf { it.isNotEmpty() }?.let { noteDao.upsertItems(it) }

            transcriptDao.deleteAllOf(doku.id)
            doku.alsTranskripte().takeIf { it.isNotEmpty() }?.let { transcriptDao.upsertAll(it) }

            if (anhaenge.isNotEmpty()) attachmentDao.upsertAll(anhaenge)

            noteDao.clearTagsOf(doku.id)
            doku.tagIds.filter { it in bekannteTags }
                .takeIf { it.isNotEmpty() }
                ?.let { ids ->
                    noteDao.linkTags(ids.mapIndexed { i, t -> NoteTagCrossRef(doku.id, t, i) })
                }
        }

        notes.reindex(doku.id)

        // Ohne diese Vormerkung bliebe eine wiederhergestellte Notiz fuer immer
        // nur auf diesem Geraet: Nach einer Neuinstallation ist die Buchfuehrung
        // des Abgleichs leer, und was dort nicht steht, geht auch nicht hoch.
        //
        // Ausgenommene Notizen bekommen sie nicht. Sie sollen ja gerade nicht in
        // die Cloud, und eine Vormerkung, die nie abgearbeitet wird, stuende
        // fuer immer als "noch nicht gesichert" in der Kopfzeile.
        if (eintrag.syncEnabled) {
            vormerken(EntityType.NOTE, doku.id, doku.updatedAt)
        }
    }

    /**
     * Bringt die ausgepackten Anhangsdateien unter ihre neuen Kennungen.
     *
     * Umbenennen statt kopieren: Die Datei kam gerade erst aus dem Archiv, und
     * die alte Notiz, zu der sie gehoerte, gibt es nicht mehr. Klappt das
     * Umbenennen nicht, etwa ueber Dateisystemgrenzen hinweg, wird kopiert.
     *
     * Was sich nicht bewegen laesst, faellt still weg. Die Anhangszeile steht
     * dann ohne Datei da, und damit kommt die Oberflaeche zurecht: Ein
     * fehlendes Bild faellt auf die Notizfarbe zurueck.
     */
    private fun dateienUmziehen(
        auflage: Neuauflage,
        dateien: Map<String, File>,
    ): Map<String, File> {
        val umgezogen = HashMap<String, File>()

        for (anhang in auflage.eintrag.notiz.attachments) {
            val alteKennung = auflage.anhangKennungen.entries
                .firstOrNull { it.value == anhang.id }?.key ?: continue
            val alt = dateien[alteKennung] ?: continue

            val ziel = ablage.ziel(anhang.id, anhang.mimeType)
            val geschafft = runCatching { alt.renameTo(ziel) }.getOrDefault(false) ||
                runCatching {
                    alt.copyTo(ziel, overwrite = true)
                    alt.delete()
                    true
                }.getOrDefault(false)

            if (geschafft) umgezogen[anhang.id] = ziel
        }
        return umgezogen
    }

    /**
     * Die Wecker zu einer wiederhergestellten Notiz.
     *
     * Termine, die schon vorbei sind, werden uebergangen, genau wie beim
     * Abgleich. Eine Sicherung kann Monate alt sein; ihre Erinnerungen alle zu
     * stellen hiesse, dass nach dem Wiederherstellen ein Schwall Meldungen fuer
     * Vergangenes hereinkaeme.
     */
    private suspend fun erinnerungenStellen(eintrag: Sicherungsnotiz) {
        val termine = eintrag.notiz.reminders.map { it.triggerAt }.filter { it > clock.now() }
        if (termine.isEmpty()) return
        weckdienst.uebernehmen(eintrag.notiz.id, eintrag.notiz.title, termine)
    }
}
