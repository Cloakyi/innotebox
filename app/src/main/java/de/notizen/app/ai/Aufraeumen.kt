package de.notizen.app.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Was mit dem Rohtranskript geschehen soll. */
enum class Aufbereitung(val beschriftung: String) {
    AUFRAEUMEN("Aufräumen"),
    ZUSAMMENFASSEN("Zusammenfassen"),
    STICHPUNKTE("Als Stichpunkte"),
}

/**
 * Macht aus dem Rohtranskript lesbaren Text.
 *
 * Das Rohtranskript wird dabei nie angefasst (Abschnitt 12). Das Ergebnis
 * landet im `body` der Notiz, das Original bleibt in `transcripts`. Wer beides
 * zusammenlegt, verliert genau dann etwas, wenn die Aufbereitung daneben lag,
 * und das merkt man erst später.
 *
 * Die Prompts sind bewusst knapp und verbieten ausdrücklich das Hinzuerfinden.
 * Ein Sprachmodell, das ein lückenhaftes Diktat „sinnvoll ergänzt", schreibt
 * Dinge in die Notiz, die nie gesagt wurden, und in einer Notiz-App ist das
 * der schlimmste denkbare Fehler, weil man ihm später glaubt.
 */
@Singleton
class Aufraeumen @Inject constructor() {

    suspend fun verfuegbar(): Boolean = withContext(Dispatchers.IO) {
        mitModell({ false }) { it.checkStatus() == FeatureStatus.AVAILABLE }
    }

    /**
     * Gibt den aufbereiteten Text zurück, oder `null`, wenn es nicht ging.
     *
     * `null` und keine Ausnahme: ein misslungenes Aufräumen ist kein Fehler der
     * App, sondern ein Ergebnis. Die Oberfläche sagt es und lässt das Original
     * stehen.
     */
    suspend fun aufbereiten(roh: String, art: Aufbereitung): String? {
        if (roh.isBlank()) return null

        return withContext(Dispatchers.IO) {
            mitModell({ null }) { modell ->
                if (modell.checkStatus() != FeatureStatus.AVAILABLE) return@mitModell null
                val antwort = modell.generateContent(prompt(roh, art))
                antwort.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }

    /**
     * `GenerativeModel` hat ein `close()`, implementiert aber KEIN `Closeable`
     * -- `use {}` gibt es hier also nicht. Dieselbe Klammer wie in `TitelKi`.
     */
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

    private fun prompt(roh: String, art: Aufbereitung): String = when (art) {
        Aufbereitung.AUFRAEUMEN ->
            "Der folgende Text ist ein wörtliches Diktat. Entferne Füllwörter, " +
                "Versprecher und Wiederholungen, setze Satzzeichen und Absätze. " +
                "Erfinde nichts hinzu und lasse nichts Inhaltliches weg. " +
                "Antworte nur mit dem überarbeiteten Text.\n\n$roh"

        Aufbereitung.ZUSAMMENFASSEN ->
            "Fasse den folgenden Text in wenigen Sätzen zusammen. " +
                "Verwende nur, was darin steht. " +
                "Antworte nur mit der Zusammenfassung.\n\n$roh"

        Aufbereitung.STICHPUNKTE ->
            "Wandle den folgenden Text in eine Liste von Stichpunkten um, " +
                "jeder Punkt in einer Zeile mit einem vorangestellten Strich. " +
                "Verwende nur, was darin steht, und erfinde nichts hinzu. " +
                "Antworte nur mit der Liste.\n\n$roh"
    }
}
