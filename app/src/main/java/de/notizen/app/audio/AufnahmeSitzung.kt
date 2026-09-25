package de.notizen.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Was gerade aufgenommen wird.
 *
 * **Während der Aufnahme gibt es keinen Text.** Aufgenommen wird erst, erkannt
 * später — das ist der Kern des Entwurfs. Der Zustand hier trägt deshalb nur,
 * was man beim Sprechen wirklich sehen will: läuft es, wie lange schon, und
 * kommt überhaupt Ton an.
 */
data class AufnahmeStatus(
    val laeuft: Boolean = false,
    val notizId: String? = null,
    val dauerMs: Long = 0,
    val pegel: Float = 0f,
    /**
     * Die letzten Sekunden Lautstärke, für die mitlaufende Welle.
     *
     * Bewusst begrenzt: Eine Stunde Aufnahme wären hunderttausend Werte, von
     * denen nur die letzten paar Dutzend je gezeichnet werden.
     */
    val verlauf: List<Float> = emptyList(),
    /**
     * Wie viele Werte der Welle seit Beginn der Aufnahme gemessen wurden, auch
     * die schon vergessenen. Die Welle läuft daran entlang wie an einer Uhr
     * (Phase 18): Der letzte Wert in [verlauf] hat die Nummer `verlaufGesamt - 1`.
     */
    val verlaufGesamt: Long = 0,
    val fehler: String? = null,
    /**
     * Ob je etwas anderes als Stille ankam.
     *
     * Ohne diese Angabe sähe eine Aufnahme mit stummem Mikrofon genauso aus wie
     * eine gelungene — man merkte es erst, wenn hinterher kein Transkript
     * herauskommt, und suchte den Fehler an der falschen Stelle.
     */
    val hatTon: Boolean = false,
)

/** Wie weit das nachträgliche Transkribieren ist. */
data class TranskriptStatus(
    val laeuft: Boolean = false,
    val abschnitte: Int = 0,
    val fertig: Int = 0,
    val modus: Erkennungsmodus? = null,
    val teile: List<Transkriptteil> = emptyList(),
    val fehler: String? = null,
) {
    val anteil: Float get() = if (abschnitte == 0) 0f else fertig.toFloat() / abschnitte

    val text: String
        get() = teile.sortedBy { it.startMs }.joinToString(" ") { it.text }.trim()
}

/** Fünfzehn Sekunden bei zwanzig Werten je Sekunde ([WELLE_WERTE_JE_SEKUNDE]). Mehr passt nie ins Bild. */
private const val VERLAUF_LAENGE = 300

/**
 * Der gemeinsame Zustand zwischen Dienst und Oberfläche.
 *
 * Liegt außerhalb von beidem: Der Dienst überlebt das Verlassen des Screens,
 * und der Screen überlebt einen Neustart des Dienstes. Ein Zustand, der einem
 * von beiden gehörte, ginge beim Drehen des Geräts verloren — und eine laufende
 * Aufnahme darf das nicht.
 */
@Singleton
class AufnahmeSitzung @Inject constructor() {

    private val _aufnahme = MutableStateFlow(AufnahmeStatus())
    val aufnahme: StateFlow<AufnahmeStatus> = _aufnahme.asStateFlow()

    private val _transkript = MutableStateFlow(TranskriptStatus())
    val transkript: StateFlow<TranskriptStatus> = _transkript.asStateFlow()

    // ------------------------------------------------------------- Aufnahme

    fun beginnen(notizId: String) = _aufnahme.update {
        AufnahmeStatus(laeuft = true, notizId = notizId)
    }

    fun fortschritt(
        dauerMs: Long,
        pegel: Float,
        hatTon: Boolean,
        feinpegel: FloatArray = FloatArray(0),
    ) = _aufnahme.update {
        it.copy(
            dauerMs = dauerMs,
            pegel = pegel,
            hatTon = hatTon,
            verlauf = (it.verlauf + feinpegel.toList()).takeLast(VERLAUF_LAENGE),
            verlaufGesamt = it.verlaufGesamt + feinpegel.size,
        )
    }

    fun aufnahmeFehler(text: String) = _aufnahme.update { it.copy(fehler = text) }

    fun beenden() = _aufnahme.update { it.copy(laeuft = false, pegel = 0f) }

    fun aufnahmeZuruecksetzen() = _aufnahme.update { AufnahmeStatus() }

    // ----------------------------------------------------------- Transkript

    fun transkriptBeginnen(modus: Erkennungsmodus) = _transkript.update {
        TranskriptStatus(laeuft = true, modus = modus)
    }

    fun zerlegt(abschnitte: Int) = _transkript.update { it.copy(abschnitte = abschnitte) }

    fun transkriptFortschritt(fertig: Int, gesamt: Int) = _transkript.update {
        it.copy(fertig = fertig, abschnitte = gesamt)
    }

    fun teil(teil: Transkriptteil) = _transkript.update { it.copy(teile = it.teile + teil) }

    fun transkriptFehler(text: String) = _transkript.update { it.copy(fehler = text) }

    fun transkriptFertig() = _transkript.update { it.copy(laeuft = false) }

    fun transkriptZuruecksetzen() = _transkript.update { TranskriptStatus() }
}
