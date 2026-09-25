package de.notizen.app.uebersetzung

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import de.notizen.app.util.warten
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weg 3: ML Kit Translate (`com.google.mlkit:translate`).
 *
 * Läuft ohne AICore und ohne Übersetzungsdienst des Systems, also auch auf
 * dem Xiaomi. Der Preis: Die Sprachmodelle (rund 30 MB je Sprache) lädt die
 * Bibliothek einmal über das Netz, danach bleibt alles auf dem Gerät. Deshalb
 * nur hinter dem Netz-Schalter (`Einstellungen.netzErlaubt`): Ohne
 * ihn meldet dieser Weg „nicht verfügbar", und zwar mit dem Grund.
 *
 * Signaturen am 2026-09-19 aus dem AAR 17.0.3 gelesen: `Translation.getClient`,
 * `Translator.downloadModelIfNeeded(DownloadConditions)`, `translate(String)`,
 * `close()`, `RemoteModelManager.isModelDownloaded`, `TranslateLanguage.fromLanguageTag`.
 *
 * Geladen wird nur mit [laden]. Der erste Versuch ohne geladenes Modell
 * meldet [Uebersetzungsergebnis.SprachpaarFehlt] mit `ladbar = true`; der
 * Dialog bietet dann den Knopf, und erst der lädt. Nichts geht von selbst
 * ins Netz, auch nicht mit Schalter.
 */
@Singleton
class MlKitUebersetzer @Inject constructor(
    private val einstellungen: Einstellungen,
) : Uebersetzer {

    /** Ob beide Modelle schon auf dem Gerät liegen. `null`, wenn eine Sprache unbekannt ist. */
    suspend fun geladen(von: String, nach: String): Boolean? = withContext(Dispatchers.IO) {
        val quelle = TranslateLanguage.fromLanguageTag(von) ?: return@withContext null
        val ziel = TranslateLanguage.fromLanguageTag(nach) ?: return@withContext null
        runCatching {
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(TranslateRemoteModel.Builder(quelle).build()).warten() &&
                manager.isModelDownloaded(TranslateRemoteModel.Builder(ziel).build()).warten()
        }.getOrDefault(false)
    }

    override suspend fun uebersetzen(
        texte: List<String>,
        von: String,
        nach: String,
        laden: Boolean,
    ): Uebersetzungsergebnis = withContext(Dispatchers.IO) {
        if (!einstellungen.netzErlaubt().first()) {
            return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                "Die Sprachpakete kommen aus dem Netz. Dafür muss in den Einstellungen " +
                    "„Verarbeitung im Netz\" eingeschaltet sein.",
            )
        }
        val quelle = TranslateLanguage.fromLanguageTag(von)
        val ziel = TranslateLanguage.fromLanguageTag(nach)
        if (quelle == null || ziel == null) {
            return@withContext Uebersetzungsergebnis.SprachpaarFehlt(ladbar = false)
        }
        if (!laden && geladen(von, nach) != true) {
            return@withContext Uebersetzungsergebnis.SprachpaarFehlt(ladbar = true)
        }

        val uebersetzer = try {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(quelle)
                    .setTargetLanguage(ziel)
                    .build(),
            )
        } catch (t: Throwable) {
            return@withContext Uebersetzungsergebnis.NichtVerfuegbar(
                "ML Kit ist auf diesem Gerät nicht erreichbar.",
            )
        }
        try {
            // Ohne Bedingungen: Der Nutzer hat gerade auf „Laden" getippt und
            // wartet. Eine WLAN-Bedingung liesse den Knopf im Mobilfunk still
            // scheitern.
            uebersetzer.downloadModelIfNeeded(DownloadConditions.Builder().build()).warten()
            val ergebnis = texte.map { text ->
                if (text.isBlank()) "" else uebersetzer.translate(text).warten()
            }
            Uebersetzungsergebnis.Erfolg(ergebnis)
        } catch (t: Throwable) {
            Uebersetzungsergebnis.Fehler(
                "ML Kit meldet: " + (t.message ?: t::class.java.simpleName) +
                    ". Ohne Netz lässt sich kein Sprachpaket laden.",
            )
        } finally {
            runCatching { uebersetzer.close() }
        }
    }
}
