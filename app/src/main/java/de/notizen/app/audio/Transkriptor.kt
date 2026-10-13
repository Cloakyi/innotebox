package de.notizen.app.audio

import android.content.Context
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.model.Transkriptsprache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Ein fertig erkannter Abschnitt mit seiner Lage in der Aufnahme. */
data class Transkriptteil(
    val text: String,
    val startMs: Long,
    val endeMs: Long,
)

/** Fortschritt beim Erstellen eines Transkripts. */
sealed interface Transkriptschritt {
    data class Zerlegt(val abschnitte: Int) : Transkriptschritt
    data class Fortschritt(val fertig: Int, val gesamt: Int) : Transkriptschritt
    data class Teil(val teil: Transkriptteil) : Transkriptschritt
    data class Fehler(val text: String) : Transkriptschritt
    data object Fertig : Transkriptschritt
}

/**
 * Erstellt aus einer fertigen Aufnahme ein Transkript.
 *
 * Nachträglich statt live, und das löst mehrere Probleme auf einmal:
 *
 *  - Die Aufnahme hängt nicht mehr davon ab, ob ein Sprachmodell bereitsteht.
 *    Aufgenommen wird immer; das Transkript ist ein zweiter, wiederholbarer
 *    Schritt. Geht er schief, ist nichts verloren.
 *  - Das Endpointing der API, sie beendet den Strom bei Sprechpausen von
 *    selbst, ist kein Ärgernis mehr, sondern erwünscht: jeder Abschnitt ist
 *    ohnehin genau ein Stück zwischen zwei Pausen.
 *  - Die Zeitstempel sind nicht mehr geschätzt. Sie stehen vorher fest, weil
 *    der Schnitt sie bestimmt, und der erkannte Text erbt sie. Vorher wurden
 *    sie an der Uhr abgelesen, wenn ein Ergebnis eintraf, also immer etwas zu
 *    spät.
 *
 * Stille wird gar nicht erst hingeschickt. Das spart die Wartezeit für Teile,
 * in denen niemand spricht, bei einer Aufnahme, die man nebenher laufen ließ,
 * ist das der größte Teil.
 */
/** Kürzer wird nicht mehr geteilt, dann liegt es nicht an der Länge. */
private const val MINDESTLAENGE_MS = 3_000L

@Singleton
class Transkriptor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val systemerkennung: Systemerkennung,
) {

    /**
     * Zerlegt die Aufnahme und erkennt Abschnitt für Abschnitt.
     *
     * Jeder Abschnitt bekommt eine eigene Sitzung. Das ist bewusst so: eine
     * gemeinsame Sitzung müsste zwischen den Abschnitten wissen, dass etwas
     * übersprungen wurde, und dafür gibt es keine Schnittstelle.
     */
    fun transkribieren(
        datei: File,
        sprache: Transkriptsprache,
    ): Flow<Transkriptschritt> = flow {
        val abschnitte = Pausenschnitt.zerlegen(pcmPegel(datei))
        emit(Transkriptschritt.Zerlegt(abschnitte.size))

        if (abschnitte.isEmpty()) {
            emit(Transkriptschritt.Fehler("In der Aufnahme ist nichts Gesprochenes zu finden."))
            emit(Transkriptschritt.Fertig)
            return@flow
        }

        abschnitte.forEachIndexed { i, abschnitt ->
            val text = runCatching { erkenneNotfallsGeteilt(datei, abschnitt, sprache) }
                .getOrElse { fehler ->
                    emit(Transkriptschritt.Fehler(fehler.message ?: "Erkennung fehlgeschlagen"))
                    ""
                }

            if (text.isNotBlank()) {
                emit(
                    Transkriptschritt.Teil(
                        Transkriptteil(
                            text = text.trim(),
                            startMs = abschnitt.startMs,
                            endeMs = abschnitt.endeMs,
                        ),
                    ),
                )
            }
            emit(Transkriptschritt.Fortschritt(i + 1, abschnitte.size))
        }
        emit(Transkriptschritt.Fertig)
    }

    /**
     * Erkennt einen Abschnitt, und halbiert ihn, wenn es schiefgeht.
     *
     * Selbstkorrektur statt geratener Zahl. Wie viel Ton die Erkennung am
     * Stück verträgt, steht nirgends; am Gerät hat sie mit
     * `AUDIO_BUFFER_OVERFLOW` abgebrochen. Statt eine Höchstlänge zu raten und
     * bei der nächsten Gerätegeneration wieder danebenzuliegen, wird im
     * Fehlerfall geteilt und erneut versucht. Das nähert sich von selbst dem
     * an, was das jeweilige Gerät kann.
     *
     * Unterhalb von [MINDESTLAENGE_MS] wird nicht weiter geteilt: Dann liegt
     * es nicht an der Länge, und immer kleinere Bruchstücke würden nur immer
     * schlechter erkannt.
     */
    private suspend fun erkenneNotfallsGeteilt(
        datei: File,
        abschnitt: Sprechabschnitt,
        sprache: Transkriptsprache,
    ): String = try {
        erkenne(datei, abschnitt, sprache)
    } catch (fehler: Throwable) {
        if (abschnitt.dauerMs <= MINDESTLAENGE_MS || bleibend(fehler)) throw fehler

        val mitte = abschnitt.startMs + abschnitt.dauerMs / 2
        val vorne = erkenneNotfallsGeteilt(datei, Sprechabschnitt(abschnitt.startMs, mitte), sprache)
        val hinten = erkenneNotfallsGeteilt(datei, Sprechabschnitt(mitte, abschnitt.endeMs), sprache)
        listOf(vorne, hinten).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Schiebt einen Abschnitt durch die Erkennung.
     *
     * Über eine Datei, nicht über eine Pipe. Der erste Anlauf schrieb den
     * Abschnitt durch eine Pipe, so schnell die sie annahm, also weit
     * schneller als Echtzeit, und die Erkennung quittierte das am Gerät mit
     * einem Pufferüberlauf (2026-08-21). Über eine Datei bestimmt die
     * Erkennung selbst, wie schnell sie liest.
     *
     * Die Zwischendatei enthält rohes PCM ohne Kopf und wird sofort wieder
     * gelöscht.
     */
    private suspend fun erkenne(
        datei: File,
        abschnitt: Sprechabschnitt,
        sprache: Transkriptsprache,
    ): String {
        val haeppchen = File.createTempFile("abschnitt", ".pcm", context.cacheDir)
        try {
            haeppchen.outputStream().use { pcmAbschnitt(datei, abschnitt, it) }
            return systemerkennung.erkennen(haeppchen, sprache)
        } finally {
            haeppchen.delete()
        }
    }
}

/** Fehler, die ein kürzeres Stück nicht behebt: Teilen hieße nur, sie öfter zu bekommen. */
private fun bleibend(fehler: Throwable): Boolean =
    fehler is Erkennungsfehler && fehler.code in setOf(
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
        SpeechRecognizer.ERROR_CLIENT,
    )
