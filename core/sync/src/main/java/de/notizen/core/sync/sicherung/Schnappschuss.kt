package de.notizen.core.sync.sicherung

import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.util.Clock
import de.notizen.core.sync.BACKUP_ORDNERNAME
import de.notizen.core.sync.Drivezugang
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Wie ein Snapshot ausgegangen ist. */
sealed interface Schnappschussergebnis {
    data class Geschrieben(val name: String, val entfernt: Int) : Schnappschussergebnis
    data class Fehler(val grund: String) : Schnappschussergebnis
}

/**
 * Die Snapshots im Backup-Ordner (SYNC.md 2 und 10).
 *
 * **Getrennt vom Abgleich, mit Absicht.** Diese Klasse ist die einzige, die
 * den Ordner `InNoteBox-Backup` kennt. `Abgleich` hat keinen Weg dorthin; ein
 * Fehler im Spiegel kann damit konstruktionsbedingt kein Backup beschaedigen.
 *
 * Ein Snapshot ist die Sicherungsdatei aus Phase 10 (Fassung 3, mit
 * Zaehlerstaenden und Grabsteinen), einmal geschrieben und nie veraendert. Die
 * einzige Loeschung hier ist die Aufbewahrungsregel, und sie trifft nur
 * Snapshots.
 */
@Singleton
class Schnappschuss @Inject constructor(
    private val drive: Drivezugang,
    private val sicherung: Sicherung,
    private val ablage: Anhangablage,
    private val einstellungen: Einstellungen,
    private val clock: Clock,
) {

    /**
     * Schreibt einen Snapshot und raeumt danach nach der Aufbewahrungsregel auf.
     *
     * [vonHand]: auf Knopfdruck bekommt die Datei die Uhrzeit im Namen, damit
     * sie den Tagesstand nicht ueberschreibt, sondern daneben liegt.
     */
    suspend fun schreiben(token: String, vonHand: Boolean): Schnappschussergebnis {
        val jetzt = clock.now()
        val name = dateiname(jetzt, vonHand)
        val zwischen = ablage.ziel("schnappschuss.$ENDUNG")
        try {
            val label = einstellungen.geraeteLabel().first()
            val ergebnis = zwischen.outputStream().use { sicherung.sichern(it, "InNoteBox auf $label") }
            if (ergebnis is Sicherungsergebnis.Fehler) return Schnappschussergebnis.Fehler(ergebnis.grund)

            val ordner = drive.ordner(token, BACKUP_ORDNERNAME)
            val vorhanden = drive.inhalt(token, ordner)

            // Gibt es die Datei dieses Tages schon (zweiter Durchlauf am selben
            // Tag), bleibt sie: Ein Snapshot wird nie veraendert.
            if (vorhanden.any { it.name == name }) {
                return Schnappschussergebnis.Geschrieben(name, 0)
            }

            drive.anlegenBinaer(token, ordner, name, "application/octet-stream", zwischen)

            val heute = tag(jetzt)
            val behalten = behalten(vorhanden.map { it.name } + name, heute)
            var entfernt = 0
            for (datei in vorhanden) {
                if (datei.name in behalten) continue
                if (!istSnapshot(datei.name)) continue
                if (runCatching { drive.loeschen(token, datei.id) }.isSuccess) entfernt++
            }
            return Schnappschussergebnis.Geschrieben(name, entfernt)
        } catch (fehler: Throwable) {
            return Schnappschussergebnis.Fehler(fehler.message ?: fehler::class.java.simpleName)
        } finally {
            zwischen.delete()
        }
    }

    private fun tag(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun dateiname(millis: Long, vonHand: Boolean): String {
        val zeit = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val datum = zeit.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return if (vonHand) {
            datum + "_" + zeit.format(DateTimeFormatter.ofPattern("HHmm")) + "." + ENDUNG
        } else {
            "$datum.$ENDUNG"
        }
    }

    companion object {
        private val MUSTER = Regex("""^(\d{4}-\d{2}-\d{2})(?:_\d{4})?\.$ENDUNG$""")

        fun istSnapshot(name: String): Boolean = MUSTER.matches(name)

        /** Das Datum aus einem Snapshot-Namen, oder `null` bei einem fremden Namen. */
        fun datumVon(name: String): LocalDate? =
            MUSTER.find(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /**
         * Die Aufbewahrungsregel (SYNC.md 10), als reine Funktion.
         *
         * Behalten werden: die letzten 7 Tage, dazu 4 Wochenstaende (je Montag)
         * und 12 Monatsstaende (je Monatserster). Namen, die keinem Snapshot
         * entsprechen, werden nie angefasst und stehen deshalb immer in der
         * Antwort. Mehrere Snapshots eines behaltenen Tages (von Hand) bleiben
         * alle.
         */
        fun behalten(namen: List<String>, heute: LocalDate): Set<String> {
            val tagesgrenze = heute.minusDays(6)
            val wochengrenze = heute.minusDays(27)
            val monatsgrenze = heute.minusMonths(11).withDayOfMonth(1)
            return namen.filterTo(LinkedHashSet()) { name ->
                val datum = datumVon(name) ?: return@filterTo true
                when {
                    datum > heute -> true
                    !datum.isBefore(tagesgrenze) -> true
                    datum.dayOfWeek == DayOfWeek.MONDAY && !datum.isBefore(wochengrenze) -> true
                    datum.dayOfMonth == 1 && !datum.isBefore(monatsgrenze) -> true
                    else -> false
                }
            }
        }
    }
}
