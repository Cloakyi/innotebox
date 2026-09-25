package de.notizen.core.data.model

/**
 * Ob eine KI-Funktion auf diesem Geraet geht.
 *
 * Drei Stufen, und die mittlere ist die wichtige: `LADBAR` heisst, das Modell
 * fehlt noch, laesst sich aber holen. Wer das mit `NICHT` zusammenwirft, sagt
 * einem Geraet ab, das nur einen Download entfernt ist.
 */
enum class Faehigkeit {
    VERFUEGBAR,
    LADBAR,
    NICHT,
}

/**
 * Was dieses Geraet an KI kann, einmal gemessen und gemerkt.
 *
 * Gemessen wird beim ersten Start (und nach Datenloeschung), danach bei jedem
 * Start nur leicht nachgeprueft: Weicht das Ergebnis ab, meldet die App das
 * und prueft neu. Zweck: Die App versucht nie, eine Funktion zu laden, die es
 * nicht mehr gibt, und fragt nicht bei jedem Start alles durch.
 *
 * [geprueftAm] und [appVersion] sagen, wie alt der Stand ist.
 */
data class Geraetestand(
    val spracherkennung: Faehigkeit,
    val textki: Faehigkeit,
    val uebersetzung: Faehigkeit,
    val geprueftAm: Long,
    val appVersion: Int,
) {
    val allesVerfuegbar: Boolean
        get() = spracherkennung == Faehigkeit.VERFUEGBAR &&
            textki == Faehigkeit.VERFUEGBAR &&
            uebersetzung == Faehigkeit.VERFUEGBAR

    val etwasLadbar: Boolean
        get() = listOf(spracherkennung, textki, uebersetzung).any { it == Faehigkeit.LADBAR }

    val etwasFehlt: Boolean
        get() = listOf(spracherkennung, textki, uebersetzung).any { it == Faehigkeit.NICHT }

    /** Ob sich das Geraet gegenueber [alt] veraendert hat. Zeit und Version zaehlen nicht. */
    fun weichtAbVon(alt: Geraetestand): Boolean =
        spracherkennung != alt.spracherkennung ||
            textki != alt.textki ||
            uebersetzung != alt.uebersetzung
}
