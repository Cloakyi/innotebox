package de.notizen.app.ai

/** Ein Stück Text und ob es gegenüber dem Original neu ist. */
data class Textteil(val text: String, val neu: Boolean)

/**
 * Vergleicht die bearbeitete Fassung mit dem Rohtranskript.
 *
 * **Wozu:** Wer sieht, was die Aufbereitung geändert hat, kann ihr überhaupt
 * erst misstrauen. Ein Sprachmodell, das ein Diktat „aufräumt", schreibt
 * gelegentlich etwas hinein, das nie gesagt wurde — das ist in einer Notiz-App
 * der teuerste denkbare Fehler, weil man dem eigenen Text später glaubt.
 * Markierte Änderungen machen genau diese Stellen sichtbar.
 *
 * Verglichen wird **wortweise** über die längste gemeinsame Folge. Zeilenweise
 * wäre billiger, würde aber bei einem umgeschriebenen Absatz alles als neu
 * markieren, auch die Wörter, die stehen geblieben sind — und dann sagt die
 * Markierung nichts mehr aus.
 */
object Textvergleich {

    /**
     * Ab dieser Wortzahl wird grob verglichen.
     *
     * Die Tabelle des Wortvergleichs wächst mit dem Produkt beider Längen. Bei
     * 1200 Wörtern je Seite sind das anderthalb Millionen Felder — noch
     * vertretbar. Bei einem Transkript von einer Stunde wären es das
     * Hundertfache, und die App bliebe beim Umschalten stehen. Dann lieber grob
     * als gar nicht: gemeinsamer Anfang, gemeinsames Ende, der Rest gilt als
     * geändert.
     */
    private const val GRENZE = 1200

    fun vergleiche(original: String, bearbeitet: String): List<Textteil> {
        val alt = zerlegen(original)
        val neu = zerlegen(bearbeitet)

        if (alt.isEmpty()) return listOf(Textteil(bearbeitet, neu = bearbeitet.isNotEmpty()))
        if (neu.isEmpty()) return emptyList()

        val teile = if (alt.size > GRENZE || neu.size > GRENZE) {
            grob(alt, neu)
        } else {
            genau(alt, neu)
        }
        return zusammenfassen(teile)
    }

    /** Wörter samt anhängendem Leerraum, damit die Ausgabe wieder zusammenpasst. */
    private fun zerlegen(text: String): List<String> =
        Regex("\\S+\\s*").findAll(text).map { it.value }.toList()

    /** Nur zum Vergleichen: ohne Leerraum, ohne Satzzeichen, kleingeschrieben. */
    private fun kern(wort: String): String =
        wort.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * Wortvergleich über die längste gemeinsame Folge.
     *
     * Verglichen wird der [kern] der Wörter: Kommas und Punkte setzt die
     * Aufbereitung fast immer neu, und ein Text, in dem jedes Satzende als
     * Änderung leuchtet, ist unlesbar.
     */
    private fun genau(alt: List<String>, neu: List<String>): List<Textteil> {
        val a = alt.map(::kern)
        val b = neu.map(::kern)

        val tabelle = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                tabelle[i][j] = if (a[i] == b[j]) {
                    tabelle[i + 1][j + 1] + 1
                } else {
                    maxOf(tabelle[i + 1][j], tabelle[i][j + 1])
                }
            }
        }

        val ergebnis = mutableListOf<Textteil>()
        var i = 0
        var j = 0
        while (j < neu.size) {
            when {
                i < a.size && a[i] == b[j] -> {
                    ergebnis += Textteil(neu[j], neu = false)
                    i++
                    j++
                }
                // Im Original gestrichen: ueberspringen. Geloeschtes wird NICHT
                // angezeigt -- die bearbeitete Fassung soll lesbar bleiben, und
                // wer das Gestrichene sucht, schaltet auf Original um.
                i < a.size && tabelle[i + 1][j] >= tabelle[i][j + 1] -> i++

                else -> {
                    ergebnis += Textteil(neu[j], neu = true)
                    j++
                }
            }
        }
        return ergebnis
    }

    /** Für sehr lange Texte: gemeinsamer Anfang und gemeinsames Ende, Rest neu. */
    private fun grob(alt: List<String>, neu: List<String>): List<Textteil> {
        val a = alt.map(::kern)
        val b = neu.map(::kern)

        var vorne = 0
        while (vorne < a.size && vorne < b.size && a[vorne] == b[vorne]) vorne++

        var hinten = 0
        while (
            hinten < a.size - vorne &&
            hinten < b.size - vorne &&
            a[a.size - 1 - hinten] == b[b.size - 1 - hinten]
        ) {
            hinten++
        }

        return neu.mapIndexed { i, wort ->
            Textteil(wort, neu = i >= vorne && i < neu.size - hinten)
        }
    }

    /** Benachbarte Stücke gleicher Art zusammenziehen — sonst wären es Tausende. */
    private fun zusammenfassen(teile: List<Textteil>): List<Textteil> {
        val ergebnis = mutableListOf<Textteil>()
        teile.forEach { teil ->
            val letzter = ergebnis.lastOrNull()
            if (letzter != null && letzter.neu == teil.neu) {
                ergebnis[ergebnis.lastIndex] = letzter.copy(text = letzter.text + teil.text)
            } else {
                ergebnis += teil
            }
        }
        return ergebnis
    }
}
