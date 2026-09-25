package de.notizen.core.data.repository

import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.model.ArchiveTrigger
import de.notizen.core.data.model.Stage
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Was ein Lauf gefunden hat, getrennt nach Grund. */
data class Archivplan(
    val nachAlter: List<NoteEntity> = emptyList(),
    val nachMenge: List<NoteEntity> = emptyList(),
) {
    val alle: List<NoteEntity> get() = nachAlter + nachMenge
    val leer: Boolean get() = alle.isEmpty()
}

/**
 * Die Politik der automatischen Archivierung: wer wegkommt und warum.
 *
 * Getrennt von [ArchiveRepository], das nur die Mechanik kennt (protokollieren
 * und zurücknehmen). Die Trennung ist der Grund, warum sich beides ohne Gerät
 * prüfen lässt, hier steht keine Zeile Android.
 *
 * Zwei Auslöser, und sie sind nicht dasselbe:
 *
 *  * Alter, seit so vielen Tagen nicht mehr geöffnet. Trifft die Notizen,
 *    die man vergessen hat.
 *  * Menge, die Stufe hält höchstens so viele. Trifft die Notizen, die man
 *    zwar sieht, aber unter denen die anderen verschwinden. Ein Eingang mit
 *    zweihundert Einträgen ist auch dann kein Eingang mehr, wenn jeder von
 *    gestern ist.
 *
 * Nie betroffen sind Favoriten und Notizen mit einer noch nicht
 * ausgelösten Erinnerung. Beides sind ausdrückliche Zeichen, dass man die Notiz
 * noch braucht, sie wegzuräumen wäre, das Gegenteil dessen zu tun, was man
 * gesagt bekommen hat. Die Ausnahme steckt schon in den Abfragen des
 * [NoteDao], nicht erst hier.
 *
 * Das Archiv ist nie Quelle. Dorthin wird archiviert; von dort aus gibt es
 * keine nächste Stufe.
 */
@Singleton
class AutoArchiv @Inject constructor(
    private val noteDao: NoteDao,
    private val einstellungen: Einstellungen,
    private val archiv: ArchiveRepository,
    private val clock: Clock,
) {

    /** Die Stufen, aus denen überhaupt archiviert wird. */
    private val quellen = listOf(Stage.INBOX, Stage.WORKSPACE)

    suspend fun eingeschaltet(): Boolean = einstellungen.autoArchivAn().first()

    /**
     * Sucht die Kandidaten, ohne etwas zu verändern.
     *
     * Eigener Schritt, damit der Aufrufer die Titel noch ergänzen kann, bevor
     * die Notizen im Archiv landen, dort wird gesucht, und gesucht wird über
     * Titel.
     */
    suspend fun planen(): Archivplan {
        if (!eingeschaltet()) return Archivplan()

        val nachAlter = mutableListOf<NoteEntity>()
        val nachMenge = mutableListOf<NoteEntity>()

        quellen.forEach { stufe ->
            val tage = einstellungen.altersgrenze(stufe).first()
            if (tage > 0) {
                val schwelle = clock.now() - tage * EIN_TAG
                nachAlter += noteDao.archiveCandidatesByAge(stufe, schwelle)
            }

            val grenze = einstellungen.mengengrenze(stufe).first()
            if (grenze > 0) {
                // Die Alters-Kandidaten DIESER Stufe gehen ohnehin weg. Sie
                // muessen deshalb aus dem Bestand herausgerechnet werden, BEVOR
                // der Ueberschuss entsteht -- sonst raeumt der Lauf eine Notiz
                // zu viel weg. (Nach Stufe gefiltert, weil `nachAlter` ueber
                // alle Stufen hinweg gesammelt wird.)
                val schonDrin = nachAlter
                    .filter { it.stage == stufe }
                    .mapTo(mutableSetOf()) { it.id }
                val ueberschuss = noteDao.countIn(stufe) - schonDrin.size - grenze

                if (ueberschuss > 0) {
                    nachMenge += noteDao
                        .archiveCandidatesByCount(stufe, ueberschuss + schonDrin.size)
                        .filterNot { it.id in schonDrin }
                        .take(ueberschuss)
                }
            }
        }
        return Archivplan(nachAlter, nachMenge)
    }

    /**
     * Führt einen geplanten Lauf aus.
     *
     * Beide Auslöser landen in einem Batch. Zwei getrennte Läufe wären zwei
     * Benachrichtigungen und zwei Undo-Knöpfe für dasselbe nächtliche Aufräumen,
     * und wer den einen drückt, wundert sich über den anderen.
     */
    suspend fun ausfuehren(plan: Archivplan): String? {
        if (plan.leer) return null
        val trigger = when {
            plan.nachMenge.isEmpty() -> ArchiveTrigger.AGE
            plan.nachAlter.isEmpty() -> ArchiveTrigger.COUNT
            else -> ArchiveTrigger.BOTH
        }
        return archiv.archive(plan.alle, trigger)
    }

    private companion object {
        const val EIN_TAG = 24L * 60 * 60 * 1000
    }
}
