package de.notizen.app.uebersetzung

import de.notizen.app.ai.MlKitStart

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weg 2: die KI auf dem Gerät (Prompt API, Gemini Nano über AICore).
 *
 * Dieselbe Anbindung wie beim Titelvorschlag (`TitelKi`): Client öffnen, eine
 * Frage stellen, Client schließen. Kein Sprachpaket, kein Netz; das Modell
 * übersetzt, so gut es die Sprache kann. Auf einem Gerät ohne AICore ist der
 * Weg nicht da, und das sagt [Uebersetzungsergebnis.NichtVerfuegbar].
 *
 * Je Text eine Anfrage. Alle Texte in einer Anfrage zu bündeln hieße, dem
 * Modell eine Liste zu geben und eine gleich lange zurückzuerwarten, und
 * darauf ist bei einem Sprachmodell kein Verlass.
 */
@Singleton
class KiUebersetzer @Inject constructor() : Uebersetzer {

    override suspend fun uebersetzen(
        texte: List<String>,
        von: String,
        nach: String,
        laden: Boolean,
    ): Uebersetzungsergebnis = withContext(Dispatchers.IO) {
        val modell = try {
            MlKitStart.sicherstellen()
            Generation.getClient()
        } catch (t: Throwable) {
            return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                "Die KI auf dem Gerät ist nicht erreichbar.",
            )
        }
        try {
            when (modell.checkStatus()) {
                FeatureStatus.AVAILABLE -> Unit
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                    return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                        "Das Sprachmodell ist noch nicht geladen. Der Dialog beim Start " +
                            "und die Einstellungen bieten das Laden an.",
                    )
                else -> return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                    "Dieses Gerät bietet die KI auf dem Gerät nicht an.",
                )
            }
            val ergebnis = texte.map { text ->
                if (text.isBlank()) return@map ""
                val antwort = modell.generateContent(prompt(text, von, nach))
                antwort.candidates.firstOrNull()?.text?.let(::saeubere)
                    ?: return@withContext Uebersetzungsergebnis.Fehler(
                        "Die KI hat keine Übersetzung geliefert.",
                    )
            }
            Uebersetzungsergebnis.Erfolg(ergebnis)
        } catch (t: Throwable) {
            Uebersetzungsergebnis.Fehler(
                "Die KI meldet: " + (t.message ?: t::class.java.simpleName),
            )
        } finally {
            runCatching { modell.close() }
        }
    }

    private fun prompt(text: String, von: String, nach: String): String =
        """
        Uebersetze den folgenden Text von ${sprachname(von)} nach ${sprachname(nach)}.
        Antworte ausschliesslich mit der Uebersetzung, ohne Anfuehrungszeichen,
        ohne Erklaerung und ohne Anrede. Behalte Zeilenumbrueche und Aufzaehlungen bei.

        Text:
        $text
        """.trimIndent()

    /**
     * Modelle halten sich nicht immer an „nur die Übersetzung": ein
     * „Übersetzung:" davor, Anführungszeichen darum. Deshalb eingesammelt.
     */
    private fun saeubere(roh: String): String? {
        val text = roh.trim()
            .removePrefix("Übersetzung:").removePrefix("Uebersetzung:")
            .trim()
        val ohneRand = if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
            text.substring(1, text.length - 1)
        } else {
            text
        }
        return ohneRand.trim().ifBlank { null }
    }
}
