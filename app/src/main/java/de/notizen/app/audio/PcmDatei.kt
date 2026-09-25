package de.notizen.app.audio

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.sqrt

/** Größe des WAV-Kopfes, den [platzFuerKopf] anlegt. */
private const val KOPF = 44

/**
 * Liest die Aufnahme, ohne sie in den Speicher zu holen.
 *
 * Eine Stunde Aufnahme sind rund 115 MB. Die am Stück zu laden, um darin nach
 * Pausen zu suchen, wäre der sichere Weg in einen Absturz auf einem Gerät, das
 * nebenbei noch etwas anderes tut. Gelesen wird deshalb strömend, und übrig
 * bleibt je 20 Millisekunden eine Zahl, für eine Stunde sind das 180.000
 * Werte, also weniger als ein Megabyte.
 */
fun pcmPegel(datei: File): FloatArray {
    val proRahmen = ABTASTRATE * Pausenschnitt.RAHMEN_MS / 1000
    val pegel = ArrayList<Float>((datei.length() / (proRahmen * 2)).toInt().coerceAtLeast(16))

    datei.inputStream().use { strom ->
        strom.skip(KOPF.toLong())
        val puffer = ByteArray(proRahmen * 2)

        while (true) {
            val gelesen = strom.readNBytes(puffer, 0, puffer.size)
            if (gelesen < puffer.size) break
            pegel += effektivwert(puffer, gelesen)
        }
    }
    return pegel.toFloatArray()
}

/**
 * Effektivwert eines Blocks, 0..1.
 *
 * Effektivwert und nicht Spitzenwert: ein einzelnes Knacken hebt die Spitze auf
 * Anschlag, ohne dass jemand spricht. Der Effektivwert misst, wie viel Energie
 * über das ganze Fenster ankommt, und genau das unterscheidet Sprache von einem
 * Geräusch.
 */
internal fun effektivwert(puffer: ByteArray, laenge: Int): Float {
    var summe = 0.0
    var anzahl = 0
    var i = 0
    while (i + 1 < laenge) {
        val wert = ((puffer[i + 1].toInt() shl 8) or (puffer[i].toInt() and 0xFF)).toShort().toInt()
        summe += wert.toDouble() * wert
        anzahl++
        i += 2
    }
    if (anzahl == 0) return 0f
    return (sqrt(summe / anzahl) / 32_768.0).toFloat().coerceIn(0f, 1f)
}

/**
 * Schreibt den Audioteil eines Zeitabschnitts irgendwohin.
 *
 * Rohes PCM ohne Kopf: Was die Erkennung über den Deskriptor liest, ist ein
 * Datenstrom, keine Datei.
 */
fun pcmAbschnitt(datei: File, abschnitt: Sprechabschnitt, ziel: OutputStream) {
    val bytesProMs = ABTASTRATE * 2L / 1000
    val von = KOPF + abschnitt.startMs * bytesProMs
    val laenge = abschnitt.dauerMs * bytesProMs

    datei.inputStream().use { strom ->
        strom.skipNBytes(von)
        kopiere(strom, ziel, laenge)
    }
}

private fun kopiere(von: InputStream, nach: OutputStream, bytes: Long) {
    val puffer = ByteArray(32 * 1024)
    var rest = bytes
    while (rest > 0) {
        val gelesen = von.read(puffer, 0, minOf(puffer.size.toLong(), rest).toInt())
        if (gelesen <= 0) break
        nach.write(puffer, 0, gelesen)
        rest -= gelesen
    }
}

/**
 * Die Dauer einer Aufnahme, aus der Dateigröße gerechnet.
 *
 * Ohne die Datei zu öffnen und ohne einen laufenden Abspieler: Bei einem festen
 * Format ist die Dauer schlicht eine Division. Das ist wichtiger, als es klingt,
 * wer sie erst vom Abspieler erfragt, hat sie vor dem ersten Abspielen nicht,
 * und alles, was auf ihr aufbaut, rechnet mit null.
 */
fun dauerVon(datei: File): Long {
    if (!datei.exists()) return 0
    val audioBytes = (datei.length() - KOPF).coerceAtLeast(0)
    return audioBytes * 1000 / (ABTASTRATE.toLong() * 2)
}
