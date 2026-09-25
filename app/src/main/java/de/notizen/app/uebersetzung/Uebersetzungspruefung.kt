package de.notizen.app.uebersetzung

import android.content.Context
import android.view.translation.TranslationCapability
import android.view.translation.TranslationManager
import android.view.translation.TranslationSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Was das Geraet an Uebersetzung mitbringt.
 *
 * Drei Zustaende, und der Unterschied zwischen den ersten beiden ist der
 * wichtige. „Nicht erreichbar" heisst, dass es auf diesem Geraet gar keinen
 * Uebersetzungsdienst gibt. „Keine Sprachen" heisst, dass es einen gibt, er
 * aber nichts geladen hat -- dann fuehrt ein Weg in die Systemeinstellung und
 * es kann danach gehen. Wer beides zusammenwirft, sagt einem halben Gerät
 * unnoetig ab.
 */
sealed interface Uebersetzungslage {

    /** Das System kennt keinen Uebersetzungsdienst. Hier ist nichts zu holen. */
    data object NichtErreichbar : Uebersetzungslage

    /** Es gibt einen Dienst, aber kein einziges Sprachpaar. */
    data object KeineSprachen : Uebersetzungslage

    /**
     * Es geht. [bereit] sind die Paare, die auf dem Geraet liegen, [ladbar]
     * die, die sich in der Systemeinstellung nachladen liessen.
     */
    data class Bereit(
        val bereit: List<Sprachpaar>,
        val ladbar: List<Sprachpaar>,
    ) : Uebersetzungslage
}

/** Ein Sprachpaar, so wie das System es meldet. */
data class Sprachpaar(val von: String, val nach: String)

/**
 * Fragt die Uebersetzung des Systems, was sie kann.
 *
 * `android.view.translation.TranslationManager`, seit API 31 und damit seit
 * unserem minSdk. Kein Netz, keine Bibliothek, kein Text, der das Geraet
 * verlaesst. Die geprueften Signaturen stehen in docs/ENTSCHEIDUNGEN.md unter „Uebersetzung".
 *
 * Diese Klasse misst nur, sie uebersetzt nicht. Sie beantwortet die eine
 * Frage, die vor dem Bauen der Uebersetzung geklaert sein muss: Traegt dieses
 * Geraet ueberhaupt einen Uebersetzungsdienst? Auf einem Pixel ja, auf einem
 * Geraet ohne die Systemdienste von Google womoeglich nicht, und dazwischen
 * liegt alles, was Hersteller so ausliefern.
 *
 * Jeder Fehlschlag wird zu [Uebersetzungslage.NichtErreichbar]. Ein
 * Systemdienst, den es nicht gibt, ist kein Absturzgrund.
 */
@Singleton
class Uebersetzungspruefung @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun lage(): Uebersetzungslage = runCatching {
        val manager = context.getSystemService(TranslationManager::class.java)
            ?: return Uebersetzungslage.NichtErreichbar

        val faehigkeiten = manager.getOnDeviceTranslationCapabilities(
            TranslationSpec.DATA_FORMAT_TEXT,
            TranslationSpec.DATA_FORMAT_TEXT,
        )

        if (faehigkeiten.isEmpty()) return Uebersetzungslage.KeineSprachen

        val bereit = faehigkeiten
            .filter { it.state == TranslationCapability.STATE_ON_DEVICE }
            .map { it.alsPaar() }

        val ladbar = faehigkeiten
            .filter { it.state == TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD }
            .map { it.alsPaar() }

        if (bereit.isEmpty() && ladbar.isEmpty()) {
            Uebersetzungslage.KeineSprachen
        } else {
            Uebersetzungslage.Bereit(bereit, ladbar)
        }
    }.getOrDefault(
        // Ein Fehlschlag zaehlt wie "gibt es nicht". Fuer den Nutzer ist der
        // Unterschied zwischen einem fehlenden Dienst und einem, der beim
        // Fragen fliegt, keiner.
        Uebersetzungslage.NichtErreichbar,
    )

    private fun TranslationCapability.alsPaar() = Sprachpaar(
        von = sourceSpec.locale.language,
        nach = targetSpec.locale.language,
    )
}

/**
 * Was in den Einstellungen steht.
 *
 * Reine Funktion, damit sich die Saetze pruefen lassen, ohne ein Geraet zu
 * starten. Zahlen statt Sprachlisten: Sechzig Paare als Aufzaehlung waeren eine
 * Wand, und die Frage lautet ohnehin nur, ob es geht.
 */
fun uebersetzungstext(lage: Uebersetzungslage): String = when (lage) {
    Uebersetzungslage.NichtErreichbar ->
        "Dieses Gerät bringt keine Übersetzung mit. Die Notizen bleiben, wie sie sind."

    Uebersetzungslage.KeineSprachen ->
        "Die Übersetzung ist da, es ist nur noch keine Sprache geladen. " +
            "Das holst du in den Einstellungen des Geräts nach."

    is Uebersetzungslage.Bereit -> {
        val fertig = when (lage.bereit.size) {
            0 -> "Noch keine Sprachkombination geladen"
            1 -> "Eine Sprachkombination liegt bereit"
            else -> "${lage.bereit.size} Sprachkombinationen liegen bereit"
        }
        val dazu = when (lage.ladbar.size) {
            0 -> ""
            1 -> ", eine weitere ließe sich laden"
            else -> ", ${lage.ladbar.size} weitere ließen sich laden"
        }
        fertig + dazu + "."
    }
}
