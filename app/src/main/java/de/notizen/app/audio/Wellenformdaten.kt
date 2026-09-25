package de.notizen.app.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verdichtet den Pegelverlauf auf so viele Balken, wie gezeichnet werden.
 *
 * **Spitzenwert je Balken, nicht Mittelwert.** Über eine Sekunde gemittelt wird
 * alles gleich grau; das Bild soll aber zeigen, wo etwas los war. Der
 * Spitzenwert behält die Struktur, die man in einer Wellenform sucht — hier
 * war eine Pause, dort wurde laut gesprochen.
 *
 * Anschließend logarithmisch skaliert, aus demselben Grund wie bei der
 * Live-Anzeige: linear läge Sprache bei einem Zehntel, und die Wellenform wäre
 * ein flacher Strich.
 */
internal fun verdichten(pegel: FloatArray, balken: Int): FloatArray {
    if (pegel.isEmpty() || balken <= 0) return FloatArray(0)

    return FloatArray(balken) { i ->
        val von = (i.toLong() * pegel.size / balken).toInt()
        val bis = (((i + 1).toLong() * pegel.size / balken).toInt()).coerceAtLeast(von + 1)

        var spitze = 0f
        for (j in von until minOf(bis, pegel.size)) {
            if (pegel[j] > spitze) spitze = pegel[j]
        }
        ausschlagAusAnteil(spitze)
    }
}

/**
 * Liefert die Wellenform einer Aufnahme und merkt sie sich.
 *
 * Das Berechnen liest die ganze Datei — bei einer Stunde Aufnahme über hundert
 * Megabyte. Das darf **nicht** bei jedem Neuzeichnen passieren, und schon gar
 * nicht auf dem Bildschirm-Thread. Deshalb einmal rechnen, dann merken; der
 * Schlüssel enthält die Dateigröße, damit eine neue Aufnahme nicht die alte
 * Wellenform erbt.
 */
@Singleton
class Wellenformdaten @Inject constructor() {

    private val gemerkt = mutableMapOf<String, FloatArray>()
    private val schloss = Mutex()

    suspend fun fuer(datei: File, balken: Int = STANDARD_BALKEN): FloatArray {
        if (!datei.exists() || datei.length() <= 0) return FloatArray(0)

        val schluessel = "${datei.absolutePath}:${datei.length()}:$balken"
        schloss.withLock { gemerkt[schluessel] }?.let { return it }

        val werte = withContext(Dispatchers.IO) {
            runCatching { verdichten(pcmPegel(datei), balken) }.getOrDefault(FloatArray(0))
        }
        schloss.withLock { gemerkt[schluessel] = werte }
        return werte
    }

    /** Nach einer neuen Aufnahme: die alte Wellenform gilt nicht mehr. */
    suspend fun vergessen(datei: File) = schloss.withLock {
        gemerkt.keys.removeAll { it.startsWith("${datei.absolutePath}:") }
    }

    companion object {
        /** Genug für eine Karte, wenig genug, um es flott zu zeichnen. */
        const val STANDARD_BALKEN = 56
    }
}
