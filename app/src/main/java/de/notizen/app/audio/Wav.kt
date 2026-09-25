package de.notizen.app.audio

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * WAV-Kopf für die Rohaufnahme.
 *
 * WAV und nicht ein gepacktes Format: Die Aufnahme ist das Original, aus dem
 * sich alles andere wieder herstellen lässt. Sie zu komprimieren hieße, den
 * einen Bestandteil zu beschädigen, der nicht nachproduzierbar ist — und ein
 * roher PCM-Strom ohne Kopf wäre eine Datei, die außerhalb dieser App niemand
 * öffnen kann.
 *
 * Der Kopf enthält zwei Längenangaben, die man erst am **Ende** kennt. Deshalb
 * wird beim Start Platz freigelassen ([platzFuerKopf]) und beim Schließen
 * nachgetragen ([kopfNachtragen]). Wer stattdessen erst am Ende die ganze Datei
 * neu schreibt, verdoppelt bei langen Aufnahmen den Platzbedarf und riskiert,
 * dass ein Abbruch alles mitnimmt.
 */
private const val KOPF_GROESSE = 44
private const val KANAELE = 1
private const val BITS = 16

fun platzFuerKopf(strom: OutputStream) {
    strom.write(ByteArray(KOPF_GROESSE))
}

/**
 * Trägt die Längen nach.
 *
 * Bleibt die Datei leer, wird sie gelöscht statt mit einem Kopf ohne Inhalt
 * zurückgelassen — eine 44 Byte große Audiodatei, die sich öffnen lässt und
 * nichts enthält, ist irreführender als gar keine.
 */
fun kopfNachtragen(datei: File, audioBytes: Long) {
    if (audioBytes <= 0) {
        datei.delete()
        return
    }

    val byteRate = ABTASTRATE * KANAELE * BITS / 8
    val blockAusrichtung = KANAELE * BITS / 8

    RandomAccessFile(datei, "rw").use { raf ->
        raf.seek(0)
        raf.write("RIFF".toByteArray())
        raf.write(kleinEndian32((audioBytes + KOPF_GROESSE - 8).toInt()))
        raf.write("WAVE".toByteArray())

        raf.write("fmt ".toByteArray())
        raf.write(kleinEndian32(16))
        raf.write(kleinEndian16(1)) // PCM, unkomprimiert
        raf.write(kleinEndian16(KANAELE))
        raf.write(kleinEndian32(ABTASTRATE))
        raf.write(kleinEndian32(byteRate))
        raf.write(kleinEndian16(blockAusrichtung))
        raf.write(kleinEndian16(BITS))

        raf.write("data".toByteArray())
        raf.write(kleinEndian32(audioBytes.toInt()))
    }
}

private fun kleinEndian32(wert: Int) = byteArrayOf(
    (wert and 0xFF).toByte(),
    ((wert shr 8) and 0xFF).toByte(),
    ((wert shr 16) and 0xFF).toByte(),
    ((wert shr 24) and 0xFF).toByte(),
)

private fun kleinEndian16(wert: Int) = byteArrayOf(
    (wert and 0xFF).toByte(),
    ((wert shr 8) and 0xFF).toByte(),
)
