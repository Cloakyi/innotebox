package de.notizen.app.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.model.Transkriptsprache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Wie es um eine Sprache auf diesem Gerät steht. */
enum class Sprachpaket { INSTALLIERT, LAEDT, LADBAR, NICHT }

/** Ein Fehler der Spracherkennung des Systems, mit ihrem Code. */
class Erkennungsfehler(val code: Int) : Exception(beschreibung(code))

/**
 * Die Spracherkennung, die Android selbst mitbringt.
 *
 * `SpeechRecognizer.createOnDeviceSpeechRecognizer` ist eine feste Schnittstelle
 * des Systems seit Android 12, keine Vorabversion. Dahinter steht auf einem
 * Pixel der Erkennungsdienst von Google, der Ton verlässt das Gerät dabei
 * nicht: Der Erkenner auf dem Gerät darf laut Android kein Netz benutzen.
 *
 * Bis Oktober 2026 lief das Transkript über die Spracherkennung von ML Kit
 * (`genai-speech-recognition`, nur als Alpha veröffentlicht). Seit dem
 * Herbst 2026 lehnt Googles Sprachdienst diese Schnittstelle für fremde Apps
 * mit `PERMISSION_DENIED: Api access revoked` ab, auf dem Pixel 10 in beiden
 * Modi. Siehe docs/ENTSCHEIDUNGEN.md.
 *
 * Gebraucht wird Android 13 (API 33): erst ab da nimmt die Erkennung Ton aus
 * einer Datei an (`EXTRA_AUDIO_SOURCE`) und sagt, welche Sprachen sie kann
 * (`checkRecognitionSupport`). Auf Android 12 gibt es deshalb kein Transkript,
 * die Aufnahme geht trotzdem.
 *
 * Alle Aufrufe an `SpeechRecognizer` laufen auf dem Hauptthread; so verlangt
 * es die Klasse.
 */
@Singleton
class Systemerkennung @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Ob dieses Gerät überhaupt Transkripte aus einer Datei erstellen kann. */
    val moeglich: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Fragt den Dienst, ob die Sprache auf dem Gerät liegt. */
    suspend fun sprachpaket(sprache: Transkriptsprache): Sprachpaket {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !moeglich) return Sprachpaket.NICHT
        return withContext(Dispatchers.Main) { unterstuetzung(sprache) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun unterstuetzung(sprache: Transkriptsprache): Sprachpaket =
        suspendCancellableCoroutine { fortsetzung ->
            val erkenner = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            fortsetzung.invokeOnCancellation { erkenner.destroy() }
            erkenner.checkRecognitionSupport(
                anfrage(sprache),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        erkenner.destroy()
                        if (fortsetzung.isActive) fortsetzung.resume(einordnen(support, sprache))
                    }

                    override fun onError(error: Int) {
                        erkenner.destroy()
                        if (fortsetzung.isActive) fortsetzung.resumeWithException(Erkennungsfehler(error))
                    }
                },
            )
        }

    /**
     * Lädt das Sprachpaket. Liefert den Fortschritt in Prozent, solange der
     * Dienst einen meldet. Endet, wenn das Paket da ist oder es nicht geht.
     *
     * Erst ab Android 14 meldet der Dienst, wie es läuft. Auf Android 13 wird
     * der Download nur angestoßen, und der Fluss endet sofort.
     */
    fun laden(sprache: Transkriptsprache): Flow<Int> = callbackFlow {
        if (!moeglich) {
            close(Erkennungsfehler(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
            return@callbackFlow
        }
        val erkenner = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            erkenner.triggerModelDownload(
                anfrage(sprache),
                context.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        trySend(completedPercent)
                    }

                    override fun onSuccess() {
                        close()
                    }

                    override fun onScheduled() {
                        // Der Dienst lädt später, etwa erst im WLAN. Mehr
                        // erfährt die App darüber nicht.
                        close()
                    }

                    override fun onError(error: Int) {
                        close(Erkennungsfehler(error))
                    }
                },
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            erkenner.triggerModelDownload(anfrage(sprache))
            close()
        }
        awaitClose { erkenner.destroy() }
    }.flowOn(Dispatchers.Main)

    /**
     * Erkennt rohes PCM (16 Bit, ein Kanal, 16 kHz, ohne Kopf) aus einer Datei.
     *
     * Eine segmentierte Sitzung, die mit dem Ende der Datei endet: Der Dienst
     * liefert dabei jedes Stück zwischen zwei Pausen einzeln und hört nicht
     * nach der ersten Pause auf.
     */
    suspend fun erkennen(pcm: File, sprache: Transkriptsprache): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw Erkennungsfehler(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        }
        return withContext(Dispatchers.Main) { sitzung(pcm, sprache) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun sitzung(pcm: File, sprache: Transkriptsprache): String =
        suspendCancellableCoroutine { fortsetzung ->
            val quelle = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
            val erkenner = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val teile = mutableListOf<String>()

            fun aufraeumen() {
                erkenner.destroy()
                runCatching { quelle.close() }
            }

            fun fertig(fehler: Throwable? = null) {
                aufraeumen()
                if (!fortsetzung.isActive) return
                if (fehler == null) {
                    fortsetzung.resume(teile.joinToString(" "))
                } else {
                    fortsetzung.resumeWithException(fehler)
                }
            }

            fortsetzung.invokeOnCancellation { aufraeumen() }

            erkenner.setRecognitionListener(object : RecognitionListener {
                override fun onSegmentResults(segmentResults: Bundle) {
                    besterText(segmentResults)?.let { teile += it }
                }

                override fun onEndOfSegmentedSession() = fertig()

                override fun onResults(results: Bundle) {
                    besterText(results)?.let { teile += it }
                    fertig()
                }

                override fun onError(error: Int) {
                    // Nichts verstanden ist kein Fehler, sondern ein leerer Abschnitt.
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        fertig()
                    } else {
                        fertig(Erkennungsfehler(error))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val auftrag = anfrage(sprache).apply {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, quelle)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, ABTASTRATE)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            erkenner.startListening(auftrag)
        }

    private fun anfrage(sprache: Transkriptsprache) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sprache.alsLocale().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun besterText(ergebnis: Bundle): String? =
        ergebnis.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * Ordnet die Antwort des Dienstes ein. Die Sprachkennungen kommen je nach
 * Dienst als `de-DE` oder `de_DE` und in wechselnder Schreibung; verglichen
 * wird deshalb nach Sprache und Land, nicht nach der Zeichenkette.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun einordnen(support: RecognitionSupport, sprache: Transkriptsprache): Sprachpaket =
    sprachpaketAus(
        installiert = support.installedOnDeviceLanguages,
        ausstehend = support.pendingOnDeviceLanguages,
        moeglich = support.supportedOnDeviceLanguages,
        sprache = sprache.alsLocale(),
    )

/** Der rechnende Teil von [einordnen], ohne Android-Klassen, damit er testbar ist. */
internal fun sprachpaketAus(
    installiert: List<String>,
    ausstehend: List<String>,
    moeglich: List<String>,
    sprache: Locale,
): Sprachpaket {
    fun passt(kennung: String): Boolean {
        val ort = Locale.forLanguageTag(kennung.replace('_', '-'))
        return ort.language.equals(sprache.language, ignoreCase = true) &&
            (ort.country.isEmpty() || ort.country.equals(sprache.country, ignoreCase = true))
    }
    return when {
        installiert.any(::passt) -> Sprachpaket.INSTALLIERT
        ausstehend.any(::passt) -> Sprachpaket.LAEDT
        moeglich.any(::passt) -> Sprachpaket.LADBAR
        else -> Sprachpaket.NICHT
    }
}

/** Die Fehlercodes von `SpeechRecognizer` als Satz. */
internal fun beschreibung(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        "Diese Sprache kennt die Spracherkennung des Geräts nicht"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        "Das Sprachpaket fehlt auf diesem Gerät"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Die Spracherkennung braucht die Erlaubnis für das Mikrofon"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        "Die Spracherkennung ist gerade mit etwas anderem beschäftigt"
    SpeechRecognizer.ERROR_AUDIO -> "Die Spracherkennung konnte die Aufnahme nicht lesen"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Die Spracherkennung wurde unterbrochen"
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
        "Die Spracherkennung sagt nicht, welche Sprachen sie kann"
    else -> "Die Spracherkennung meldet einen Fehler"
} + " (Code $code)."

