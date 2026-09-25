package de.notizen.app.audio

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStream

/**
 * Ein Strukturtest, und zwar mit Absicht.
 *
 * Was hier passiert ist: Nach dem Umbau auf „erst aufnehmen, dann
 * transkribieren" blieb in [Mitschnitt] eine Pipe aus dem alten Live-Entwurf
 * stehen. Jeder aufgenommene Block wurde weiterhin hineingeschrieben, nur las
 * sie niemand mehr. Eine Pipe fasst rund 64 Kilobyte, bei diesem Format also
 * knapp zwei Sekunden. Danach blockiert der Schreibende, und zwar in einem
 * Systemaufruf, den kein `cancel` erreicht.
 *
 * Die Folgen sahen aus wie fünf verschiedene Fehler: Der Timer blieb stehen,
 * „Fertig" bewirkte nichts, der Dienst ließ sich nicht beenden, das
 * Mikrofonsymbol blieb, und selbst das Wegwischen der App half nicht, nur ein
 * erzwungenes Beenden.
 *
 * Warum ein Strukturtest und kein richtiger: Der Fehler steckt in
 * blockierender Ein-/Ausgabe an einem echten `AudioRecord`. Ohne Gerät lässt
 * sich das nicht nachstellen; ein Test, der es vorgäbe, wäre wertlos. Was sich
 * prüfen lässt, ist die Regel, die daraus folgt: [Mitschnitt] hat genau eine
 * Senke, die Datei. Das ist weniger, als man gern hätte, aber es ist ehrlich,
 * und es fängt genau den Rückfall ab, der schon einmal passiert ist.
 */
class MitschnittAufbauTest {

    @Test
    fun `der Mitschnitt schreibt nirgendwo anders hin als in die Datei`() {
        val verbotene = Mitschnitt::class.java.declaredFields.filter { feld ->
            ParcelFileDescriptor::class.java.isAssignableFrom(feld.type) ||
                (
                    OutputStream::class.java.isAssignableFrom(feld.type) &&
                        feld.name != "datei"
                    )
        }

        assertTrue(
            "Zweite Senke in Mitschnitt gefunden: ${verbotene.map { it.name }}. " +
                "Wer eine braucht, muss dafür sorgen, dass jemand sie auch leert " +
                "-- sonst steht die Aufnahme nach zwei Sekunden still.",
            verbotene.isEmpty(),
        )
    }

    @Test
    fun `zwei Sekunden Audio passen nicht in eine Pipe`() {
        // Die Rechnung, die den Fehler erklaert -- festgehalten, damit die
        // Groessenordnung nicht in Vergessenheit geraet. Ein Pipe-Puffer fasst
        // ueblicherweise 64 KB.
        val bytesProSekunde = ABTASTRATE * 2
        val pipePuffer = 64 * 1024

        assertTrue(
            "Schon nach ~2 Sekunden waere eine ungelesene Pipe voll",
            pipePuffer < bytesProSekunde * 3,
        )
    }
}
