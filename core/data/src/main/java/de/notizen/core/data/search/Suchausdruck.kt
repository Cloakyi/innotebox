package de.notizen.core.data.search

/**
 * Baut aus dem, was der Nutzer tippt, einen gültigen FTS4-MATCH-Ausdruck.
 *
 * Das ist keine Formsache, sondern die Stelle, an der eine Volltextsuche
 * üblicherweise zerbricht: die Eingabe des Nutzers ist keine Abfragesprache.
 * FTS4 liest `-` als Ausschluss, `"` als Beginn einer Phrase, `*` als Präfix,
 * `OR` und `NEAR` als Operatoren. Wer den Rohtext durchreicht, bekommt bei
 * „Meier-Schmidt" das falsche Ergebnis und bei einem einzelnen
 * Anführungszeichen einen SQL-Fehler mitten im Tippen.
 *
 * Deshalb wird nicht maskiert, sondern zerlegt: gesucht wird nach den
 * zusammenhängenden Folgen aus Buchstaben und Ziffern, alles andere trennt.
 * Jedes Wort kommt in Anführungszeichen, damit auch `OR` als Wort und nicht
 * als Operator gilt, und bekommt ein `*`, damit schon während des Tippens
 * gefunden wird.
 *
 * Die Wörter werden durch Leerzeichen verbunden, nicht durch `AND`. In der
 * Standard-Abfragesyntax von FTS3/4 ist das Leerzeichen bereits ein Und; das
 * Wort `AND` wäre dort ein Suchbegriff wie jeder andere. Nachgeprüft wird das
 * am echten SQLite, nicht in der Dokumentation, siehe
 * `VolltextsucheTest.beide Woerter muessen vorkommen`.
 */
object Suchausdruck {

    /**
     * Der fertige MATCH-Ausdruck, oder `null`, wenn nichts Suchbares übrig
     * bleibt.
     *
     * `null` heißt ausdrücklich nicht „nichts gefunden", sondern „gar nicht
     * gesucht". Der Unterschied entscheidet, ob die Oberfläche alle Notizen
     * zeigt oder einen leeren Ergebnisbereich.
     */
    fun bauen(eingabe: String): String? {
        val woerter = woerter(eingabe)
        if (woerter.isEmpty()) return null
        // DER STERN STEHT IN DEN ANFUEHRUNGSZEICHEN, nicht dahinter. Bis zum
        // 2026-09-19 stand er dahinter (`"wort"*`), und FTS4 las das nicht
        // als Praefix: Gefunden wurden nur ganze Woerter, „Bespr" fand
        // „Besprechung" nicht. Aufgefallen am Test fuer das unterstrichene
        // Wort (14d), der die Praefixsuche ueber diesen Ausdruck prueft, waehrend
        // der aeltere Praefixtest den Ausdruck von Hand schrieb.
        return woerter.joinToString(" ") { "\"$it*\"" }
    }

    /**
     * Die Wörter, nach denen tatsächlich gesucht wird.
     *
     * Die Oberfläche braucht sie zum Hervorheben: hervorgehoben werden muss
     * dasselbe, wonach gesucht wurde, sonst leuchtet die falsche Stelle.
     */
    fun woerter(eingabe: String): List<String> = buildList {
        val puffer = StringBuilder()
        eingabe.forEach { zeichen ->
            if (zeichen.isLetterOrDigit()) {
                puffer.append(zeichen)
            } else if (puffer.isNotEmpty()) {
                add(puffer.toString())
                puffer.clear()
            }
        }
        if (puffer.isNotEmpty()) add(puffer.toString())
    }
}
