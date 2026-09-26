package de.notizen.app.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Zustand des KI-Modells, uebersetzt aus `FeatureStatus`.
 *
 * [LADBAR] ist der Zustand, der weh tut: das Modell ist NICHT auf dem Geraet.
 * Der Titeldialog darf darauf nicht warten (siehe docs/ENTSCHEIDUNGEN.md).
 */
enum class KiZustand {
    BEREIT,
    LADBAR,
    LAEDT,
    NICHT_VERFUEGBAR,
    FEHLER,
}

/** Laenge, ab der ein abgeleiteter Titel gekuerzt wird. */
private const val MAX_FALLBACK = 50

/** Laenge, ab der ein KI-Titel gekuerzt wird. */
private const val MAX_TITEL = 60

/** So viel Notiztext geht an das Modell. Mehr braucht ein Titel nicht. */
private const val MAX_QUELLE = 1200

/**
 * Titel aus dem Inhalt ableiten -- ohne KI, ohne Warten, ohne Fehlerfall.
 *
 * Das ist der Vorschlag, der IMMER sofort dasteht. Die KI liefert nur eine
 * moeglicherweise bessere Alternative nach; sie ersetzt diesen Weg nicht.
 *
 * Gibt "" zurueck, wenn es keinen Inhalt gibt. Der Aufrufer entscheidet, ob
 * daraus "Ohne Titel" wird oder ob die Notiz verworfen gehoert.
 */
fun fallbackTitel(body: String, eintraege: List<String> = emptyList()): String {
    val zeile = body.lineSequence().firstOrNull { it.isNotBlank() }
        ?: eintraege.firstOrNull { it.isNotBlank() }
        ?: return ""

    val roh = zeile.trim()
    if (roh.length <= MAX_FALLBACK) return roh

    val gekuerzt = roh.take(MAX_FALLBACK)
    val anWortgrenze = gekuerzt.substringBeforeLast(' ', gekuerzt)
    return anWortgrenze.trimEnd() + "..."
}

/**
 * Titelvorschlaege ueber die ML-Kit Prompt API (Gemini Nano, on-device).
 *
 * Bewusst zustandslos: jeder Aufruf oeffnet den Client und schliesst ihn wieder.
 * Das Modell ist ein Systemdienst, kein Objekt, das wir am Leben halten muessten
 * -- und ein offen gelassener Client waere ein Leck.
 *
 * KEIN Aufruf hier wirft. Ein Titelvorschlag ist Beiwerk; wenn er scheitert,
 * bleibt der Fallback-Titel, und der Nutzer merkt nichts ausser einem Hinweis.
 */
@Singleton
class TitelKi @Inject constructor() {

    /** Ist das Modell da? Antwortet schnell, laedt nichts. */
    suspend fun zustand(): KiZustand = withContext(Dispatchers.IO) {
        mitModell({ KiZustand.FEHLER }) { modell -> zuZustand(modell.checkStatus()) }
    }

    /**
     * Laedt das Modell und meldet den Zustand DANACH.
     *
     * Nur auf ausdrueckliche Ansage des Nutzers aufrufen. Auf dem Testgeraet kostete
     * der Uebergang 0 Bytes, weil die Daten schon lokal lagen -- auf einem
     * frisch aufgesetzten Geraet kann derselbe Aufruf ein echter, langsamer
     * Transfer sein (siehe docs/ENTSCHEIDUNGEN.md).
     *
     * Fortschritt wird hier bewusst NICHT gemeldet: `DownloadProgress` liefert
     * nur die geladenen Bytes, keine Gesamtgroesse. Ein Balken ist damit nicht
     * baubar.
     */
    suspend fun laden(): KiZustand = withContext(Dispatchers.IO) {
        mitModell({ KiZustand.FEHLER }) { modell ->
            modell.download().collect { }
            zuZustand(modell.checkStatus())
        }
    }

    /**
     * Ein Titelvorschlag zum Notizinhalt, oder `null`, wenn keiner zustande
     * kommt -- Modell nicht da, Modell schweigt, Aufruf scheitert.
     */
    suspend fun vorschlag(quelle: String): String? = withContext(Dispatchers.IO) {
        if (quelle.isBlank()) return@withContext null
        mitModell({ null }) { modell ->
            if (zuZustand(modell.checkStatus()) != KiZustand.BEREIT) return@mitModell null
            val antwort = modell.generateContent(prompt(quelle))
            antwort.candidates.firstOrNull()?.text?.let(::saeubere)
        }
    }

    // ------------------------------------------------------------------ intern

    private suspend fun <T> mitModell(
        beiFehler: (Throwable) -> T,
        block: suspend (GenerativeModel) -> T,
    ): T = try {
        MlKitStart.sicherstellen()
        val modell = Generation.getClient()
        try {
            block(modell)
        } finally {
            modell.close()
        }
    } catch (t: Throwable) {
        beiFehler(t)
    }

    private fun zuZustand(status: Int): KiZustand = when (status) {
        FeatureStatus.AVAILABLE -> KiZustand.BEREIT
        FeatureStatus.DOWNLOADABLE -> KiZustand.LADBAR
        FeatureStatus.DOWNLOADING -> KiZustand.LAEDT
        FeatureStatus.UNAVAILABLE -> KiZustand.NICHT_VERFUEGBAR
        else -> KiZustand.FEHLER
    }

    private fun prompt(quelle: String): String =
        """
        Du bekommst den Inhalt einer Notiz. Schlage genau einen kurzen Titel dafuer vor.
        Antworte ausschliesslich mit dem Titel selbst: hoechstens sechs Woerter,
        auf Deutsch, ohne Anfuehrungszeichen und ohne Punkt am Ende.

        Notiz:
        ${quelle.take(MAX_QUELLE)}
        """.trimIndent()

    /**
     * Modelle halten sich nicht immer an "nur der Titel": ein "Titel:" davor,
     * Anfuehrungszeichen darum, eine zweite Zeile Erklaerung dahinter. Deshalb
     * hier eingesammelt statt der Antwort blind vertraut.
     */
    private fun saeubere(roh: String): String? {
        val zeile = roh.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
            ?: return null
        val ohnePrefix = zeile.removePrefix("Titel:").removePrefix("titel:").trim()
        val ohneRand = ohnePrefix.trim('"', '\'', '*', '#', '-', ' ').trimEnd('.').trim()
        return ohneRand.take(MAX_TITEL).ifBlank { null }
    }
}
