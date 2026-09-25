package de.notizen.core.sync

import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.util.Clock
import de.notizen.core.sync.sicherung.Schnappschuss
import de.notizen.core.sync.sicherung.Schnappschussergebnis
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Wie ein Tagesdurchlauf ausgegangen ist. */
sealed interface Tagesergebnis {
    /** Alles durch, mit Bericht. `snapshot` ist null, wenn der Bericht einen Befund hatte. */
    data class Fertig(
        val bericht: Pruefbericht,
        val entferntImPurge: Int,
        val snapshot: String?,
        val snapshotFehler: String? = null,
    ) : Tagesergebnis

    data object AnmeldungNoetig : Tagesergebnis
    data object KeinNetz : Tagesergebnis
    data class Fehler(val grund: String) : Tagesergebnis
}

/**
 * Der taegliche Durchlauf (SYNC.md 9), in genau dieser Reihenfolge:
 *
 * 1. Outbox leeren und Vollabgleich: ein Lauf von [Abgleich] tut beides, denn
 *    er sieht ohnehin jede Entitaet an.
 * 2. Pruefbericht: kommt aus demselben Lauf.
 * 3. Selbstheilung: hat der Lauf schon getan (fehlende Dateien neu hochgeladen).
 * 4. Purge nach SYNC.md 7.
 * 5. Snapshot, nur ohne Befund. Ein kaputter Stand wird nie zum Backup.
 *
 * Einmal je Kalendertag beim ersten Oeffnen der App, und auf Knopfdruck
 * („Jetzt sichern", dann mit Uhrzeit im Namen).
 */
@Singleton
class Tagesdurchlauf @Inject constructor(
    private val abgleich: Abgleich,
    private val schnappschuss: Schnappschuss,
    private val einstellungen: Einstellungen,
    private val clock: Clock,
) {

    /** Ob heute schon ein Durchlauf war. */
    suspend fun heuteSchonGelaufen(): Boolean = einstellungen.letzterTagesdurchlauf().first() == heute()

    /**
     * Fuehrt den Durchlauf aus. [vonHand] heisst: nicht als Tagesdurchlauf
     * verbuchen, aber einen Snapshot mit Uhrzeit schreiben.
     */
    suspend fun ausfuehren(
        token: String,
        vonHand: Boolean = false,
        /**
         * Ob ein Snapshot geschrieben werden darf. Die App verneint das im
         * getakteten Netz, wenn "nur im WLAN" gilt: Ein Snapshot traegt alle
         * Bilder und Aufnahmen. Dann bleibt der Tag offen und wird beim
         * naechsten Oeffnen im WLAN nachgeholt.
         */
        mitSnapshot: Boolean = true,
    ): Tagesergebnis {
        val lauf = when (val ergebnis = abgleich.lauf(token)) {
            is Abgleichergebnis.Fertig -> ergebnis
            Abgleichergebnis.AnmeldungNoetig -> return Tagesergebnis.AnmeldungNoetig
            Abgleichergebnis.KeinNetz -> return Tagesergebnis.KeinNetz
            is Abgleichergebnis.Fehler -> return Tagesergebnis.Fehler(ergebnis.grund)
        }

        val entfernt = runCatching { abgleich.purge(token) }.getOrDefault(0)

        var snapshot: String? = null
        var snapshotFehler: String? = null
        if (lauf.bericht.ohneBefund && mitSnapshot) {
            when (val s = schnappschuss.schreiben(token, vonHand)) {
                is Schnappschussergebnis.Geschrieben -> snapshot = s.name
                is Schnappschussergebnis.Fehler -> snapshotFehler = s.grund
            }
        }

        if (!vonHand && mitSnapshot) einstellungen.setLetzterTagesdurchlauf(heute())
        return Tagesergebnis.Fertig(lauf.bericht, entfernt, snapshot, snapshotFehler)
    }

    private fun heute(): String =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
