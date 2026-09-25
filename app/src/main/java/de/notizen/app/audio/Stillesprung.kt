package de.notizen.app.audio

/**
 * Überspringt beim Abspielen, wo nicht gesprochen wurde.
 *
 * Wozu: Wer eine Besprechung mitlaufen lässt, hat hinterher Minuten
 * Aufnahme, in denen niemand redet. Die abzuhören ist verlorene Zeit, und
 * blindes Vorspulen trifft die nächste Stelle selten.
 *
 * Die Abschnitte sind dieselben, aus denen auch das Transkript entsteht
 * ([Pausenschnitt]), sie werden also nicht eigens berechnet, und was man hört,
 * ist genau das, was auch im Text steht.
 */
object Stillesprung {

    /**
     * Wohin von [positionMs] aus gesprungen werden muss, oder `null`, wenn
     * gerade gesprochen wird.
     *
     * Gibt das Ende zurück, wenn hinter der Position nichts mehr kommt, das
     * ist der Nachlauf am Schluss, den man sich sonst noch anhört, obwohl die
     * Aufnahme inhaltlich vorbei ist.
     */
    fun naechsteStelle(
        abschnitte: List<Sprechabschnitt>,
        positionMs: Long,
        dauerMs: Long,
    ): Long? {
        if (abschnitte.isEmpty()) return null

        // Innerhalb eines Abschnitts: laufen lassen.
        if (abschnitte.any { positionMs >= it.startMs && positionMs < it.endeMs }) return null

        val naechster = abschnitte.firstOrNull { it.startMs > positionMs }
        return naechster?.startMs ?: dauerMs
    }

    /**
     * Wie viel Zeit das Überspringen spart.
     *
     * Steht auf dem Schalter, damit man vor dem Einschalten sieht, ob es sich
     * überhaupt lohnt. Bei einem durchgehenden Diktat sind es null Sekunden,
     * und dann soll der Schalter das auch sagen.
     */
    fun ersparnisMs(abschnitte: List<Sprechabschnitt>, dauerMs: Long): Long {
        if (abschnitte.isEmpty()) return 0
        val gesprochen = abschnitte.sumOf { it.dauerMs }
        return (dauerMs - gesprochen).coerceAtLeast(0)
    }
}
