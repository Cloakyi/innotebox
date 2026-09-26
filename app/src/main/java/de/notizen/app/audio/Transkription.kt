package de.notizen.app.audio

import de.notizen.app.ai.MlKitStart

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** In welchem Modus die Erkennung tatsächlich läuft. */
enum class Erkennungsmodus { ADVANCED, BASIC }

/** Zustand des Sprachmodells, bevor überhaupt eine Aufnahme-UI erscheint. */
sealed interface Modellzustand {
    data object Bereit : Modellzustand
    data object Ladbar : Modellzustand
    data class Laedt(val bytes: Long) : Modellzustand
    data class NichtVerfuegbar(val code: Int) : Modellzustand
    data class Fehler(val ursache: Throwable) : Modellzustand
}

/**
 * Die Spracherkennung.
 *
 * `checkStatus()` läuft, bevor der Transkript-Knopf erscheint. Ein Knopf,
 * der erst nach dem Drücken zugibt, dass das Modell fehlt, ist schlechter als
 * einer, der von vornherein sagt, was zu tun ist. Die AUFNAHME hängt davon
 * ausdrücklich nicht ab -- sie läuft auch ohne Modell.
 *
 * Rückfall Advanced -> Basic: Advanced ist derzeit Pixel-10-exklusiv, Basic
 * läuft breit. Fällt die App zurück, wird das gezeigt und nicht still
 * gemacht, die Erkennungsqualität unterscheidet sich hörbar, und wer das nicht
 * weiß, hält das Ergebnis für einen Fehler der App.
 *
 * Das eigentliche Erkennen steht in [Transkriptor]; hier liegt nur, was VOR dem
 * ersten Wort geklärt sein muss.
 */
@Singleton
class Transkription @Inject constructor(
    private val einstellungen: Einstellungen,
) {

    /** Die eingestellte Sprache, einmal gelesen statt bei jedem Aufruf. */
    suspend fun sprache(): Transkriptsprache = einstellungen.transkriptsprache().first()

    private fun optionen(modus: Erkennungsmodus, sprache: Transkriptsprache) =
        speechRecognizerOptions {
            locale = sprache.alsLocale()
            preferredMode = when (modus) {
                Erkennungsmodus.ADVANCED -> SpeechRecognizerOptions.Mode.MODE_ADVANCED
                Erkennungsmodus.BASIC -> SpeechRecognizerOptions.Mode.MODE_BASIC
            }
        }

    /**
     * Prüft, ob das Modell bereitsteht.
     *
     * Erst Advanced, dann Basic: ist Advanced auf diesem Gerät nicht
     * ausgerollt, heißt das nicht, dass gar nichts geht.
     */
    suspend fun zustand(): Pair<Modellzustand, Erkennungsmodus> {
        val sprache = sprache()
        Erkennungsmodus.entries.forEach { modus ->
            val ergebnis = runCatching {
                MlKitStart.sicherstellen()
                SpeechRecognition.getClient(optionen(modus, sprache)).use { it.checkStatus() }
            }
            val zustand = ergebnis.fold(
                onSuccess = { code ->
                    when (code) {
                        FeatureStatus.AVAILABLE -> Modellzustand.Bereit
                        FeatureStatus.DOWNLOADABLE -> Modellzustand.Ladbar
                        FeatureStatus.DOWNLOADING -> Modellzustand.Laedt(0)
                        else -> Modellzustand.NichtVerfuegbar(code)
                    }
                },
                onFailure = { Modellzustand.Fehler(it) },
            )
            if (zustand is Modellzustand.Bereit || zustand is Modellzustand.Ladbar) {
                return zustand to modus
            }
        }
        return Modellzustand.NichtVerfuegbar(FeatureStatus.UNAVAILABLE) to Erkennungsmodus.BASIC
    }

    /**
     * Lädt das Modell.
     *
     * `DownloadProgress` liefert nur `totalBytesDownloaded`, keine
     * Gesamtgröße, ein Prozentbalken ist mit dieser API nicht baubar. Die
     * Oberfläche zeigt deshalb einen unbestimmten Fortschritt mit mitlaufender
     * Byte-Zahl. Nicht versuchen, das anders hinzubiegen.
     */
    fun laden(modus: Erkennungsmodus): Flow<Modellzustand> = flow {
        MlKitStart.sicherstellen()
        SpeechRecognition.getClient(optionen(modus, sprache())).use { client ->
            client.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadProgress ->
                        emit(Modellzustand.Laedt(status.totalBytesDownloaded))
                    is DownloadStatus.DownloadCompleted -> emit(Modellzustand.Bereit)
                    is DownloadStatus.DownloadFailed ->
                        emit(Modellzustand.Fehler(IllegalStateException(status.toString())))
                    else -> Unit
                }
            }
        }
    }
}
