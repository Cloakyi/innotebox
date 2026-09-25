package de.notizen.app.uebersetzung

import android.app.PendingIntent
import android.content.Context
import android.icu.util.ULocale
import android.os.CancellationSignal
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Weg 1: die Übersetzung des Systems (`android.view.translation`, seit API 31).
 *
 * Kein Netz von uns, keine Bibliothek. Die Signaturen stehen in docs/ENTSCHEIDUNGEN.md
 * unter „Übersetzung" und sind am 2026-09-19 noch einmal gegen das
 * `android.jar` der API 37 geprüft. Ob das Gerät den Dienst hat, misst
 * [Uebersetzungspruefung]; hier wird nur übersetzt.
 *
 * Sprachpakete lädt die App nicht selbst. Fehlt ein Paar, führt
 * [einstellungen] in die Systemeinstellung, in der man es lädt.
 */
@Singleton
class SystemUebersetzer @Inject constructor(
    @ApplicationContext private val context: Context,
) : Uebersetzer {

    private val manager: TranslationManager?
        get() = runCatching { context.getSystemService(TranslationManager::class.java) }.getOrNull()

    /** Die Systemeinstellung, in der Sprachpakete geladen werden, oder `null`. */
    fun einstellungen(): PendingIntent? =
        runCatching { manager?.onDeviceTranslationSettingsActivityIntent }.getOrNull()

    override suspend fun uebersetzen(
        texte: List<String>,
        von: String,
        nach: String,
        laden: Boolean,
    ): Uebersetzungsergebnis = withContext(Dispatchers.IO) {
        val manager = manager
            ?: return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                "Dieses Gerät bringt keine Übersetzung des Systems mit.",
            )

        // Erst nachsehen, ob das Paar da ist. `createOnDeviceTranslator` ruft
        // sonst einfach nie zurueck, und ein Dialog, der ewig laedt, ist
        // schlechter als ein Satz.
        val faehigkeiten = runCatching {
            manager.getOnDeviceTranslationCapabilities(
                TranslationSpec.DATA_FORMAT_TEXT,
                TranslationSpec.DATA_FORMAT_TEXT,
            )
        }.getOrDefault(emptySet())
        val paar = faehigkeiten.firstOrNull {
            it.sourceSpec.locale.language == von && it.targetSpec.locale.language == nach
        }
        when (paar?.state) {
            TranslationCapability.STATE_ON_DEVICE -> Unit
            TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD,
            TranslationCapability.STATE_DOWNLOADING,
            -> return@withContext Uebersetzungsergebnis.SprachpaarFehlt(ladbar = true)
            else -> return@withContext Uebersetzungsergebnis.SprachpaarFehlt(ladbar = false)
        }

        val kontext = TranslationContext.Builder(
            TranslationSpec(ULocale(von), TranslationSpec.DATA_FORMAT_TEXT),
            TranslationSpec(ULocale(nach), TranslationSpec.DATA_FORMAT_TEXT),
        ).build()
        val direkt = Executor { it.run() }

        val uebersetzer = withTimeoutOrNull(ANTWORT_MS) {
            suspendCancellableCoroutine<Translator?> { fortsetzung ->
                runCatching {
                    manager.createOnDeviceTranslator(kontext, direkt) { t -> fortsetzung.resume(t) }
                }.onFailure { fortsetzung.resume(null) }
            }
        } ?: return@withContext Uebersetzungsergebnis.Fehler(
            "Die Übersetzung des Systems hat nicht geantwortet.",
        )

        try {
            val anfrage = TranslationRequest.Builder()
                .setTranslationRequestValues(texte.map { TranslationRequestValue.forText(it) })
                .build()
            val antwort = withTimeoutOrNull(ANTWORT_MS) {
                suspendCancellableCoroutine<TranslationResponse?> { fortsetzung ->
                    val abbruch = CancellationSignal()
                    fortsetzung.invokeOnCancellation { abbruch.cancel() }
                    runCatching {
                        uebersetzer.translate(anfrage, abbruch, direkt) { r ->
                            // Ohne `FLAG_PARTIAL_RESPONSES` kommt genau eine
                            // Antwort, und die ist die ganze.
                            if (fortsetzung.isActive) fortsetzung.resume(r)
                        }
                    }.onFailure { if (fortsetzung.isActive) fortsetzung.resume(null) }
                }
            } ?: return@withContext Uebersetzungsergebnis.Fehler(
                "Die Übersetzung des Systems hat nicht geantwortet.",
            )

            if (antwort.translationStatus != TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                return@withContext Uebersetzungsergebnis.Fehler(
                    "Die Übersetzung des Systems meldet einen Fehler (Code " +
                        antwort.translationStatus + ").",
                )
            }
            val werte = antwort.translationResponseValues
            val ergebnis = texte.indices.map { i ->
                val wert = werte.get(i)
                if (wert == null || wert.statusCode != TranslationResponseValue.STATUS_SUCCESS) {
                    return@withContext Uebersetzungsergebnis.Fehler(
                        "Ein Teil des Textes ließ sich nicht übersetzen.",
                    )
                }
                wert.text?.toString() ?: ""
            }
            Uebersetzungsergebnis.Erfolg(ergebnis)
        } finally {
            runCatching { uebersetzer.destroy() }
        }
    }

    private companion object {
        /** Laenger wartet niemand auf eine Uebersetzung auf dem Geraet. */
        const val ANTWORT_MS = 30_000L
    }
}
