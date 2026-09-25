package de.notizen.app.audio

/** Ein Stück Aufnahme, in dem gesprochen wird. */
data class Sprechabschnitt(val startMs: Long, val endeMs: Long) {
    val dauerMs: Long get() = endeMs - startMs
}

/**
 * Zerlegt eine Aufnahme an den Sprechpausen.
 *
 * **Das macht ausdrücklich keine KI, und das ist kein Rückschritt.** Die
 * Prompt API auf diesem Gerät nimmt nur Text und Bilder entgegen — `Part` kennt
 * genau zwei Ausprägungen, `TextPart` und `ImagePart` (im AAR nachgesehen am
 * 2026-08-21). Ein Sprachmodell, das Audio zerschneidet, gibt es hier also
 * nicht.
 *
 * Es wäre aber auch die falsche Wahl. „Wo ist eine Sprechpause?" ist eine
 * Messfrage, keine Ermessensfrage: der Pegel ist über einige hundert
 * Millisekunden niedrig, oder er ist es nicht. Rechnen liefert darauf jedes Mal
 * dieselbe Antwort, ist prüfbar, braucht kein Modell und keine Wartezeit. Eine
 * KI würde dieselbe Frage teurer und unzuverlässiger beantworten.
 *
 * **Der Grundpegel wird gemessen, nicht angenommen.** Eine feste Schwelle
 * müsste raten, wie laut die Umgebung ist — im stillen Zimmer schnitte sie
 * mitten in Wörter, im Zug fände sie überhaupt keine Pause. Gemessen werden
 * deshalb zwei Punkte der Aufnahme, Ruhe und Sprechpegel, und die Schwelle
 * dazwischen gelegt. Siehe [schwelleFuer] — dort steht auch, welcher naive
 * Ansatz daran gescheitert ist.
 */
object Pausenschnitt {

    /** Länge eines Messfensters. Kurz genug für Wortgrenzen, lang genug fürs Mitteln. */
    const val RAHMEN_MS = 20

    /** So lange muss es leise sein, damit es als Pause gilt. */
    private const val MINDEST_PAUSE_MS = 600

    /** Kürzere Sprechstücke sind Husten, Klicken oder ein Stuhl. */
    private const val MINDEST_ABSCHNITT_MS = 300

    /**
     * Damit der erste und letzte Laut nicht abgeschnitten wird.
     *
     * Von 120 auf 300 ms erhöht, nachdem am Gerät Wörter fehlten. Sprache
     * beginnt und endet leiser, als sie in der Mitte ist — ein knapper Rand
     * schneidet genau die Konsonanten weg, an denen die Erkennung ein Wort
     * erkennt. Großzügig zu sein kostet hier nichts außer etwas Rechenzeit.
     */
    private const val RAND_MS = 300

    /**
     * Ab dieser Länge wird ein Abschnitt geteilt, auch ohne Pause.
     *
     * Wer minutenlang ohne Atempause redet, erzeugt sonst ein einziges
     * Riesenstück. Die Erkennung beendet sich in solchen Fällen irgendwann von
     * selbst, und alles danach wäre verloren. Lieber ein Schnitt an einer
     * beliebigen Stelle als ein abgeschnittenes Ende — und der Fortschritt
     * bewegt sich sichtbar, statt minutenlang zu stehen.
     *
     * Von 45 auf 15 Sekunden gesenkt: Die Erkennung hat auch bei Zuspielung aus
     * einer Datei mit `AUDIO_BUFFER_OVERFLOW` abgebrochen — ihr interner Puffer
     * fasst offenbar weniger, als ein langes Stück mitbringt. Kürzere Häppchen
     * sind der zuverlässige Weg; `Transkriptor` halbiert zusätzlich noch
     * einmal, wenn es trotzdem klemmt.
     */
    private const val HOECHSTLAENGE_MS = 15_000L

    /**
     * Wo zwischen Ruhe und Sprache die Schwelle liegt.
     *
     * 0,08 heißt: dicht über dem Grundrauschen. Von 0,2 herabgesetzt, nachdem
     * am Gerät Wörter fehlten — je höher die Schwelle, desto mehr leise
     * Sprache gilt als Pause und fällt heraus. Ein bisschen Stille zu viel im
     * Häppchen stört die Erkennung nicht; ein fehlendes Wort schon.
     */
    private const val ANTEIL_UEBER_RUHE = 0.08f

    /**
     * Ab welchem Verhältnis von laut zu leise die Aufnahme überhaupt Dynamik
     * hat.
     *
     * Darunter ist sie durchgehend gleich -- entweder wird die ganze Zeit
     * gesprochen oder gar nicht. Beides gibt es wirklich, und beides hat der
     * frühere Ansatz falsch beantwortet.
     */
    private const val MINDEST_DYNAMIK = 2.0f

    /**
     * Ab dieser Lautstärke kann überhaupt jemand gesprochen haben.
     *
     * Nötig, damit eine vollkommen stille Aufnahme nicht plötzlich überall
     * Sprache sieht: relativ zu einem Grundrauschen nahe null ist auch das
     * Rauschen selbst schon ein deutlicher Ausschlag.
     */
    private const val UNTERGRENZE = 0.008f

    /**
     * Findet die Sprechabschnitte.
     *
     * [rahmenPegel] ist der Effektivwert je Messfenster, 0..1 — genau das, was
     * [pcmPegel] aus der Datei liest. Die Funktion selbst kennt weder Datei noch
     * Format und ist deshalb ohne Gerät prüfbar.
     */
    fun zerlegen(rahmenPegel: FloatArray): List<Sprechabschnitt> {
        if (rahmenPegel.isEmpty()) return emptyList()

        val schwelle = schwelleFuer(rahmenPegel)
        val proRahmen = RAHMEN_MS.toLong()
        val pauseRahmen = MINDEST_PAUSE_MS / RAHMEN_MS
        val randRahmen = RAND_MS / RAHMEN_MS

        val roh = mutableListOf<IntRange>()
        var beginn = -1
        var stilleSeit = 0

        rahmenPegel.forEachIndexed { i, pegel ->
            if (pegel >= schwelle) {
                if (beginn < 0) beginn = i
                stilleSeit = 0
            } else if (beginn >= 0) {
                stilleSeit++
                // Erst wenn die Pause lang genug war, ist der Abschnitt zu Ende.
                // Kurze Luecken zwischen Woertern duerfen nicht trennen.
                if (stilleSeit >= pauseRahmen) {
                    roh += beginn..(i - stilleSeit)
                    beginn = -1
                    stilleSeit = 0
                }
            }
        }
        if (beginn >= 0) roh += beginn..rahmenPegel.lastIndex

        return roh
            // Zuerst aussortieren, DANN den Rand anlegen. Andersherum wuerde
            // der Rand ein 60 ms langes Knacken auf ueber 300 ms aufblasen und
            // damit genau das durchlassen, was hier weg soll.
            .filter { (it.last - it.first + 1) * proRahmen >= MINDEST_ABSCHNITT_MS }
            .map { bereich ->
                val von = (bereich.first - randRahmen).coerceAtLeast(0)
                val bis = (bereich.last + randRahmen).coerceAtMost(rahmenPegel.lastIndex)
                Sprechabschnitt(von * proRahmen, (bis + 1) * proRahmen)
            }
            .flatMap { teile(it) }
    }

    /** Zerteilt ein zu langes Stück in gleich große Häppchen. */
    private fun teile(abschnitt: Sprechabschnitt): List<Sprechabschnitt> {
        if (abschnitt.dauerMs <= HOECHSTLAENGE_MS) return listOf(abschnitt)

        val anzahl = ((abschnitt.dauerMs + HOECHSTLAENGE_MS - 1) / HOECHSTLAENGE_MS).toInt()
        val laenge = abschnitt.dauerMs / anzahl
        return (0 until anzahl).map { i ->
            Sprechabschnitt(
                startMs = abschnitt.startMs + i * laenge,
                // Das letzte Stueck bekommt den Rest, damit am Ende nichts
                // durch die Ganzzahlteilung verlorengeht.
                endeMs = if (i == anzahl - 1) abschnitt.endeMs else abschnitt.startMs + (i + 1) * laenge,
            )
        }
    }

    /**
     * Die Lautstärke, ab der es als Sprache zählt.
     *
     * Gemessen werden zwei Punkte: das **Grundrauschen** (20. Perzentil, der
     * Wert, unter dem ein Fünftel der Aufnahme liegt) und der **Sprechpegel**
     * (90. Perzentil). Die Schwelle wird dazwischen gelegt, nah an der Ruhe.
     *
     * Der Mittelwert taugt dafür nicht — er würde von der Sprache selbst nach
     * oben gezogen, und je mehr gesprochen wird, desto weniger fände man.
     *
     * **Warum nicht einfach ein Vielfaches des Grundrauschens:** Weil das bei
     * einer Aufnahme, die durchgehend Sprache ist, das Dreifache der SPRACHE
     * als Schwelle setzt — und dann findet man nichts. Genau dieser Fall ist
     * beim Testen aufgefallen und hat den Ansatz gekippt. Liegen laut und leise
     * dicht beieinander, gibt es keine Dynamik, und dann entscheidet allein die
     * absolute Lautstärke, ob die ganze Aufnahme Sprache ist oder Stille.
     */
    internal fun schwelleFuer(rahmenPegel: FloatArray): Float {
        val sortiert = rahmenPegel.sortedArray()
        val ruhe = sortiert[(sortiert.size * 20 / 100).coerceAtMost(sortiert.lastIndex)]
        val sprache = sortiert[(sortiert.size * 90 / 100).coerceAtMost(sortiert.lastIndex)]

        if (sprache < ruhe * MINDEST_DYNAMIK) {
            // Kein Unterschied zwischen laut und leise. Entweder alles Sprache
            // oder alles Stille -- die Schwelle wird so gelegt, dass genau das
            // herauskommt.
            return if (sprache >= UNTERGRENZE) 0f else Float.MAX_VALUE
        }

        return maxOf(ruhe + (sprache - ruhe) * ANTEIL_UEBER_RUHE, UNTERGRENZE)
    }
}
