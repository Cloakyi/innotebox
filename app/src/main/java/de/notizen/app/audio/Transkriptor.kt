package de.notizen.app.audio

import de.notizen.app.ai.MlKitStart

import android.content.Context
import android.os.ParcelFileDescriptor
import com.google.mlkit.genai.common.audio.AudioSource
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.model.Transkriptsprache
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Ein fertig erkannter Abschnitt mit seiner Lage in der Aufnahme. */
data class Transkriptteil(
    val text: String,
    val startMs: Long,
    val endeMs: Long,
)

/** Fortschritt beim Erstellen eines Transkripts. */
sealed interface Transkriptschritt {
    data class Zerlegt(val abschnitte: Int) : Transkriptschritt
    data class Fortschritt(val fertig: Int, val gesamt: Int) : Transkriptschritt
    data class Teil(val teil: Transkriptteil) : Transkriptschritt
    data class Fehler(val text: String) : Transkriptschritt
    data object Fertig : Transkriptschritt
}

/**
 * Erstellt aus einer fertigen Aufnahme ein Transkript.
 *
 * Nachträglich statt live, und das löst mehrere Probleme auf einmal:
 *
 *  - Die Aufnahme hängt nicht mehr davon ab, ob ein Sprachmodell bereitsteht.
 *    Aufgenommen wird immer; das Transkript ist ein zweiter, wiederholbarer
 *    Schritt. Geht er schief, ist nichts verloren.
 *  - Das Endpointing der API, sie beendet den Strom bei Sprechpausen von
 *    selbst, ist kein Ärgernis mehr, sondern erwünscht: jeder Abschnitt ist
 *    ohnehin genau ein Stück zwischen zwei Pausen.
 *  - Die Zeitstempel sind nicht mehr geschätzt. Sie stehen vorher fest, weil
 *    der Schnitt sie bestimmt, und der erkannte Text erbt sie. Vorher wurden
 *    sie an der Uhr abgelesen, wenn ein Ergebnis eintraf, also immer etwas zu
 *    spät.
 *
 * Stille wird gar nicht erst hingeschickt. Das spart die Wartezeit für Teile,
 * in denen niemand spricht, bei einer Aufnahme, die man nebenher laufen ließ,
 * ist das der größte Teil.
 */
/** Kürzer wird nicht mehr geteilt, dann liegt es nicht an der Länge. */
private const val MINDESTLAENGE_MS = 3_000L

@Singleton
class Transkriptor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private fun optionen(modus: Erkennungsmodus, sprache: Transkriptsprache) =
        speechRecognizerOptions {
            // Dieselbe Sprache wie bei der Aufnahme -- sonst naehme man in einer
            // Sprache auf und liesse in einer anderen erkennen, und das Ergebnis
            // waere unerklaerlich schlecht.
            locale = sprache.alsLocale()
            preferredMode = when (modus) {
                Erkennungsmodus.ADVANCED -> SpeechRecognizerOptions.Mode.MODE_ADVANCED
                Erkennungsmodus.BASIC -> SpeechRecognizerOptions.Mode.MODE_BASIC
            }
        }

    /**
     * Zerlegt die Aufnahme und erkennt Abschnitt für Abschnitt.
     *
     * Jeder Abschnitt bekommt eine eigene Sitzung. Das ist bewusst so: eine
     * gemeinsame Sitzung müsste zwischen den Abschnitten wissen, dass etwas
     * übersprungen wurde, und dafür gibt es keine Schnittstelle.
     */
    fun transkribieren(
        datei: File,
        modus: Erkennungsmodus,
        sprache: Transkriptsprache,
    ): Flow<Transkriptschritt> = flow {
        val abschnitte = Pausenschnitt.zerlegen(pcmPegel(datei))
        emit(Transkriptschritt.Zerlegt(abschnitte.size))

        if (abschnitte.isEmpty()) {
            emit(Transkriptschritt.Fehler("In der Aufnahme ist nichts Gesprochenes zu finden."))
            emit(Transkriptschritt.Fertig)
            return@flow
        }

        MlKitStart.sicherstellen()
        SpeechRecognition.getClient(optionen(modus, sprache)).use { client ->
            abschnitte.forEachIndexed { i, abschnitt ->
                val text = runCatching { erkenneNotfallsGeteilt(client, datei, abschnitt) }
                    .getOrElse { fehler ->
                        emit(Transkriptschritt.Fehler(fehler.message ?: "Erkennung fehlgeschlagen"))
                        ""
                    }

                if (text.isNotBlank()) {
                    emit(
                        Transkriptschritt.Teil(
                            Transkriptteil(
                                text = text.trim(),
                                startMs = abschnitt.startMs,
                                endeMs = abschnitt.endeMs,
                            ),
                        ),
                    )
                }
                emit(Transkriptschritt.Fortschritt(i + 1, abschnitte.size))
            }
        }
        emit(Transkriptschritt.Fertig)
    }

    /**
     * Erkennt einen Abschnitt, und halbiert ihn, wenn es schiefgeht.
     *
     * Selbstkorrektur statt geratener Zahl. Wie viel Ton die Erkennung am
     * Stück verträgt, steht nirgends; am Gerät hat sie mit
     * `AUDIO_BUFFER_OVERFLOW` abgebrochen. Statt eine Höchstlänge zu raten und
     * bei der nächsten Gerätegeneration wieder danebenzuliegen, wird im
     * Fehlerfall geteilt und erneut versucht. Das nähert sich von selbst dem
     * an, was das jeweilige Gerät kann.
     *
     * Unterhalb von [MINDESTLAENGE_MS] wird nicht weiter geteilt: Dann liegt
     * es nicht an der Länge, und immer kleinere Bruchstücke würden nur immer
     * schlechter erkannt.
     */
    private suspend fun erkenneNotfallsGeteilt(
        client: SpeechRecognizer,
        datei: File,
        abschnitt: Sprechabschnitt,
    ): String = try {
        erkenne(client, datei, abschnitt)
    } catch (fehler: Throwable) {
        if (abschnitt.dauerMs <= MINDESTLAENGE_MS) throw fehler

        val mitte = abschnitt.startMs + abschnitt.dauerMs / 2
        val vorne = erkenneNotfallsGeteilt(
            client,
            datei,
            Sprechabschnitt(abschnitt.startMs, mitte),
        )
        val hinten = erkenneNotfallsGeteilt(
            client,
            datei,
            Sprechabschnitt(mitte, abschnitt.endeMs),
        )
        listOf(vorne, hinten).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Schiebt einen Abschnitt durch die Erkennung.
     *
     * Über eine Datei, nicht über eine Pipe. Der erste Anlauf schrieb den
     * Abschnitt durch eine Pipe, so schnell die sie annahm, also weit
     * schneller als Echtzeit. Die Erkennung ist ein Streaming-Verfahren und
     * erwartet Ton ungefähr im Sprechtempo; sie quittierte das am Gerät mit
     * `ERROR_TYPE_AUDIO_BUFFER_OVERFLOW` (2026-08-21).
     *
     * Man könnte stattdessen das Schreiben auf Echtzeit bremsen, dann dauerte
     * das Transkript einer halben Stunde eine halbe Stunde. Über eine Datei
     * bestimmt die Erkennung selbst, wie schnell sie liest: kein Überlauf
     * möglich, und so schnell, wie das Gerät eben kann.
     *
     * Die Zwischendatei enthält rohes PCM ohne Kopf und wird sofort wieder
     * gelöscht.
     */
    private suspend fun erkenne(
        client: SpeechRecognizer,
        datei: File,
        abschnitt: Sprechabschnitt,
    ): String {
        val haeppchen = File.createTempFile("abschnitt", ".pcm", context.cacheDir)
        try {
            haeppchen.outputStream().use { pcmAbschnitt(datei, abschnitt, it) }

            val quelle = ParcelFileDescriptor.open(
                haeppchen,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            val teile = mutableListOf<String>()
            quelle.use {
                val anfrage = speechRecognizerRequest { audioSource = AudioSource.fromPfd(it) }
                client.startRecognition(anfrage).collect { antwort ->
                    when (antwort) {
                        is SpeechRecognizerResponse.FinalTextResponse -> teile += antwort.text
                        is SpeechRecognizerResponse.ErrorResponse -> throw antwort.e
                        // Zwischenstaende interessieren hier nicht: es gibt keine
                        // Live-Anzeige mehr, und das Endgueltige kommt ohnehin.
                        else -> Unit
                    }
                }
            }
            return teile.joinToString(" ")
        } finally {
            haeppchen.delete()
        }
    }
}
