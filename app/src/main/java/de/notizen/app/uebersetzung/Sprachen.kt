package de.notizen.app.uebersetzung

/**
 * Eine Sprache, wie sie im Dialog steht: Sprachcode nach BCP 47 (`de`) und
 * deutscher Name.
 */
data class Sprache(val code: String, val name: String)

/**
 * Die Sprachen, die die App zum Übersetzen anbietet (Phase 16).
 *
 * **Bewusst eine Liste von Hand, nicht alle Locales des Systems.** Alle drei
 * Wege können jede dieser Sprachen zumindest im Grundsatz (ML Kit führt sie
 * alle, die Übersetzung des Systems je nach geladenen Paketen, die KI nach
 * ihrem Vermögen); ob ein Paar auf **diesem** Gerät gerade geht, sagt erst
 * der Versuch, und der Dialog sagt es dann. Deutsch steht vorn, weil die App
 * eine deutschsprachige ist; danach die Nachbarn und die häufigsten Sprachen
 * in Deutschland, dann der Rest alphabetisch.
 */
val SPRACHEN: List<Sprache> = listOf(
    Sprache("de", "Deutsch"),
    Sprache("en", "Englisch"),
    Sprache("fr", "Französisch"),
    Sprache("es", "Spanisch"),
    Sprache("it", "Italienisch"),
    Sprache("tr", "Türkisch"),
    Sprache("pl", "Polnisch"),
    Sprache("nl", "Niederländisch"),
    Sprache("uk", "Ukrainisch"),
    Sprache("ru", "Russisch"),
    Sprache("ar", "Arabisch"),
    Sprache("pt", "Portugiesisch"),
    Sprache("ro", "Rumänisch"),
    Sprache("el", "Griechisch"),
    Sprache("hr", "Kroatisch"),
    Sprache("cs", "Tschechisch"),
    Sprache("da", "Dänisch"),
    Sprache("sv", "Schwedisch"),
    Sprache("no", "Norwegisch"),
    Sprache("fi", "Finnisch"),
    Sprache("hu", "Ungarisch"),
    Sprache("fa", "Persisch"),
    Sprache("vi", "Vietnamesisch"),
    Sprache("zh", "Chinesisch"),
    Sprache("ja", "Japanisch"),
    Sprache("ko", "Koreanisch"),
)

/** Der Name zu einem Sprachcode, oder der Code selbst, wenn er unbekannt ist. */
fun sprachname(code: String): String = SPRACHEN.firstOrNull { it.code == code }?.name ?: code
