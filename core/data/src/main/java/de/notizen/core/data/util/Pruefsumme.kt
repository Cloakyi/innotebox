package de.notizen.core.data.util

import java.io.File
import java.security.MessageDigest

/**
 * Inhaltshash einer Anhangsdatei (SHA-256, klein geschrieben in Hex).
 *
 * Erspart in Phase 9 das erneute Hochladen unveraenderter Anhaenge. **Kein
 * Dublettenerkenner:** Bilder werden beim Import neu kodiert (siehe
 * `Bildspeicher`), zwei Geraete kommen beim selben Foto deshalb nicht
 * zwangslaeufig auf denselben Wert. So steht es auch in SYNC.md 14.6.
 *
 * In Bloecken gelesen und nicht am Stueck: eine einstuendige Aufnahme ist rund
 * 115 MB, und die passen nicht sinnvoll in den Speicher.
 */
fun pruefsummeVon(datei: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    datei.inputStream().use { strom ->
        val puffer = ByteArray(64 * 1024)
        while (true) {
            val gelesen = strom.read(puffer)
            if (gelesen <= 0) break
            digest.update(puffer, 0, gelesen)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
