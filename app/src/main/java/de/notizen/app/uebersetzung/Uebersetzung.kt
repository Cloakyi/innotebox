package de.notizen.app.uebersetzung

import de.notizen.app.ai.KiZustand
import de.notizen.app.ai.TitelKi
import de.notizen.core.data.model.Uebersetzungsweg
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ob ein Weg auf diesem Gerät geht, mit dem Satz dazu für die Einstellungen.
 */
data class Wegstand(val verfuegbar: Boolean, val hinweis: String)

/**
 * Die Übersetzung der App (Phase 16): wählt den Weg aus den Einstellungen und
 * sagt, welche Wege dieses Gerät überhaupt hat.
 *
 * **Erst fragen, dann anbieten.** Der Editor zeigt „Übersetzen" nur, wenn
 * [irgendeinWeg] ja sagt; die Einstellungen zeigen Wege, die nicht gehen,
 * ausgegraut mit dem Grund. Das ist dieselbe Haltung wie bei AICore: Was das
 * Gerät nicht kann, wird gemessen, nicht geraten.
 */
@Singleton
class Uebersetzung @Inject constructor(
    private val einstellungen: Einstellungen,
    private val system: SystemUebersetzer,
    private val ki: KiUebersetzer,
    private val mlkit: MlKitUebersetzer,
    private val uebersetzungspruefung: Uebersetzungspruefung,
    private val titelKi: TitelKi,
) {

    /** Was jeder Weg auf diesem Gerät gerade kann. Misst, lädt nichts. */
    suspend fun wegstaende(): Map<Uebersetzungsweg, Wegstand> {
        val systemLage = runCatching { uebersetzungspruefung.lage() }
            .getOrDefault(Uebersetzungslage.NichtErreichbar)
        val kiZustand = runCatching { titelKi.zustand() }.getOrDefault(KiZustand.FEHLER)
        val kiAn = einstellungen.kiAktiv().first()
        val netz = einstellungen.netzErlaubt().first()

        return mapOf(
            Uebersetzungsweg.SYSTEM to when (systemLage) {
                is Uebersetzungslage.Bereit -> Wegstand(
                    true,
                    when (systemLage.bereit.size) {
                        0 -> "Der Dienst ist da. Sprachen lädst du in den Einstellungen des Geräts"
                        1 -> "Eine Sprachkombination liegt bereit"
                        else -> "${systemLage.bereit.size} Sprachkombinationen liegen bereit"
                    },
                )
                Uebersetzungslage.KeineSprachen -> Wegstand(
                    true,
                    "Der Dienst ist da, noch ohne Sprachen. Die lädst du in den Einstellungen des Geräts",
                )
                Uebersetzungslage.NichtErreichbar -> Wegstand(
                    false,
                    "Dieses Gerät bringt keine Übersetzung des Systems mit",
                )
            },
            Uebersetzungsweg.GERAETE_KI to when {
                !kiAn -> Wegstand(false, "Die KI ist in den Einstellungen abgeschaltet")
                kiZustand == KiZustand.BEREIT -> Wegstand(true, "Übersetzt ohne Sprachpakete, so gut das Modell die Sprache kann")
                kiZustand == KiZustand.LADBAR || kiZustand == KiZustand.LAEDT ->
                    Wegstand(false, "Das Sprachmodell ist noch nicht geladen")
                else -> Wegstand(false, "Dieses Gerät bietet die KI auf dem Gerät nicht an")
            },
            Uebersetzungsweg.MLKIT to if (netz) {
                Wegstand(true, "Lädt je Sprache einmal rund 30 MB aus dem Netz, danach bleibt alles auf dem Gerät")
            } else {
                Wegstand(false, "Braucht den Schalter „Verarbeitung im Netz\" weiter oben")
            },
        )
    }

    /** Ob wenigstens ein Weg geht. Darüber entscheidet, ob es „Übersetzen" im Editor gibt. */
    suspend fun irgendeinWeg(): Boolean = wegstaende().values.any { it.verfuegbar }

    /** Der eingestellte Weg. */
    suspend fun weg(): Uebersetzungsweg = einstellungen.uebersetzungsweg().first()

    /**
     * Übersetzt über den eingestellten Weg. Leere Texte gehen nicht zum
     * Übersetzer und kommen leer zurück, damit die Zuordnung stimmt.
     */
    suspend fun uebersetzen(
        texte: List<String>,
        von: String,
        nach: String,
        laden: Boolean = false,
    ): Uebersetzungsergebnis {
        if (von == nach) {
            return Uebersetzungsergebnis.Fehler("Ausgangs- und Zielsprache sind dieselbe.")
        }
        val gefuellt = texte.mapIndexedNotNull { i, t -> if (t.isBlank()) null else i to t }
        if (gefuellt.isEmpty()) return Uebersetzungsergebnis.Erfolg(texte.map { "" })

        val uebersetzer: Uebersetzer = when (weg()) {
            Uebersetzungsweg.SYSTEM -> system
            Uebersetzungsweg.GERAETE_KI -> ki
            Uebersetzungsweg.MLKIT -> mlkit
        }
        val ergebnis = uebersetzer.uebersetzen(gefuellt.map { it.second }, von, nach, laden)
        if (ergebnis !is Uebersetzungsergebnis.Erfolg) return ergebnis

        val zusammen = texte.map { "" }.toMutableList()
        gefuellt.forEachIndexed { k, (i, _) -> zusammen[i] = ergebnis.texte.getOrElse(k) { "" } }
        einstellungen.setUebersetzungSprachen(von, nach)
        return Uebersetzungsergebnis.Erfolg(zusammen)
    }

    /** Der Weg in die Systemeinstellung fuer Sprachpakete (nur Weg 1). */
    fun systemeinstellung() = system.einstellungen()
}
