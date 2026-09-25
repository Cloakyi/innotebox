package de.notizen.core.data.model

import java.util.Locale

/**
 * In welcher Sprache diktiert wird.
 *
 * **Bewusst eine kurze Liste statt aller Locales des Systems.** Die
 * Spracherkennung läuft auf dem Gerät, und AICore rollt ihre Sprachmodelle
 * einzeln aus — eine Auswahl mit hundert Einträgen wäre zu 95 Prozent eine
 * Sammlung von Enttäuschungen. Hier stehen die Sprachen, für die es die Modelle
 * gibt; ob eine davon auf **diesem** Gerät bereitsteht, sagt ohnehin erst
 * `checkStatus()`, und die Oberfläche zeigt das an.
 *
 * Die Voreinstellung ist Deutsch, weil die App eine deutschsprachige ist.
 */
enum class Transkriptsprache(
    val beschriftung: String,
    private val sprachcode: String,
    private val land: String,
) {
    DEUTSCH("Deutsch", "de", "DE"),
    ENGLISCH_US("Englisch (USA)", "en", "US"),
    ENGLISCH_GB("Englisch (UK)", "en", "GB"),
    FRANZOESISCH("Französisch", "fr", "FR"),
    SPANISCH("Spanisch", "es", "ES"),
    ITALIENISCH("Italienisch", "it", "IT"),
    ;

    fun alsLocale(): Locale = Locale.of(sprachcode, land)

    companion object {
        val STANDARD = DEUTSCH

        /** Unbekannte gespeicherte Werte fallen still auf den Standard zurück. */
        fun ausName(name: String?): Transkriptsprache =
            entries.firstOrNull { it.name == name } ?: STANDARD
    }
}
