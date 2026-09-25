package de.notizen.app.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Was gerade abgespielt wird. */
data class Abspielstatus(
    val notizId: String? = null,
    val laeuft: Boolean = false,
    val positionMs: Int = 0,
    val dauerMs: Int = 0,
) {
    val anteil: Float get() = if (dauerMs <= 0) 0f else (positionMs.toFloat() / dauerMs)

    fun laeuftFuer(id: String): Boolean = laeuft && notizId == id
}

/**
 * Spielt Aufnahmen ab.
 *
 * Einer für die ganze App. Zwei gleichzeitig laufende Aufnahmen wären
 * Lärm, und der Abspieler muss von der Karte in der Übersicht und vom
 * geöffneten Editor aus bedienbar sein, beide sehen denselben Zustand. Startet
 * man eine zweite Notiz, hört die erste auf.
 *
 * Die Position wird abgefragt und nicht gemeldet: `MediaPlayer` hat keinen
 * Rückruf dafür. Zehnmal pro Sekunde ist mehr als genug für einen Balken, der
 * ein paar hundert Pixel breit ist, und der Ticker läuft nur, solange wirklich
 * etwas spielt.
 */
@Singleton
class Wiedergabe @Inject constructor() {

    private val _status = MutableStateFlow(Abspielstatus())
    val status: StateFlow<Abspielstatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var spieler: MediaPlayer? = null
    private var ticker: Job? = null

    /** Wo gesprochen wird. Leer heißt: nichts überspringen. */
    private var gesprochen: List<Sprechabschnitt> = emptyList()

    /**
     * Startet, pausiert oder wechselt, je nachdem, was gerade läuft.
     *
     * Ein einziger Einstieg statt `start`/`pause`/`wechsle`: Die Oberfläche hat
     * genau einen Knopf dafür, also soll sie auch genau eine Frage stellen
     * müssen.
     */
    fun umschalten(notizId: String, datei: File) {
        val jetzt = _status.value

        if (jetzt.notizId == notizId && spieler != null) {
            if (jetzt.laeuft) pausieren() else fortsetzen()
            return
        }

        loslassen()
        if (!datei.exists()) return

        spieler = MediaPlayer().apply {
            runCatching {
                setDataSource(datei.absolutePath)
                prepare()
                setOnCompletionListener { amEnde() }
                start()
            }.onFailure {
                release()
                spieler = null
                return
            }
        }

        _status.value = Abspielstatus(
            notizId = notizId,
            laeuft = true,
            positionMs = 0,
            dauerMs = spieler?.duration ?: 0,
        )
        tickerStarten()
    }

    fun pausieren() {
        runCatching { spieler?.pause() }
        ticker?.cancel()
        _status.update { it.copy(laeuft = false) }
    }

    private fun fortsetzen() {
        runCatching { spieler?.start() }
        _status.update { it.copy(laeuft = true) }
        tickerStarten()
    }

    /** Springt an eine Stelle, angegeben als Anteil der Gesamtdauer. */
    fun springeZu(anteil: Float) {
        val spiel = spieler ?: return
        val ziel = (spiel.duration * anteil.coerceIn(0f, 1f)).toInt()
        runCatching { spiel.seekTo(ziel) }
        _status.update { it.copy(positionMs = ziel) }
    }

    /**
     * Springt um eine Anzahl Sekunden vor oder zurück.
     *
     * Fünf zurück, zehn vor -- die übliche Aufteilung, und sie hat einen Grund:
     * Zurückspringen heißt „das habe ich nicht verstanden", da genügt wenig.
     * Vorspringen heißt „hier passiert nichts", da will man mehr überspringen.
     */
    fun springenUm(sekunden: Int) {
        val spiel = spieler ?: return
        val ziel = (spiel.currentPosition + sekunden * 1000).coerceIn(0, spiel.duration)
        runCatching { spiel.seekTo(ziel) }
        _status.update { it.copy(positionMs = ziel) }
    }

    /**
     * Legt fest, welche Stellen beim Abspielen übersprungen werden.
     *
     * Eine leere Liste heißt: alles abspielen. Die Abschnitte kommen aus
     * derselben Zerlegung wie das Transkript, was man hört, ist genau das, was
     * im Text steht.
     */
    fun setzeUeberspringen(abschnitte: List<Sprechabschnitt>) {
        gesprochen = abschnitte
    }

    /**
     * Gibt den Abspieler frei.
     *
     * Muss beim Verlassen gerufen werden: Ein `MediaPlayer`, den niemand
     * freigibt, hält eine Audiospur des Systems offen, und davon gibt es nur
     * eine begrenzte Zahl für das ganze Gerät.
     */
    fun loslassen() {
        ticker?.cancel()
        ticker = null
        spieler?.runCatching {
            stop()
            release()
        }
        spieler = null
        _status.value = Abspielstatus()
    }

    private fun amEnde() {
        ticker?.cancel()
        // Position auf null statt auf das Ende: Der naechste Druck auf Play
        // soll von vorn anfangen und nicht sofort wieder fertig sein.
        _status.update { it.copy(laeuft = false, positionMs = 0) }
        runCatching { spieler?.seekTo(0) }
    }

    private fun tickerStarten() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val spiel = spieler ?: break
                val position = runCatching { spiel.currentPosition }.getOrNull() ?: break

                // Im Takt der Anzeige mitgeprueft, statt einen zweiten Ticker
                // zu starten: Es ist dieselbe Frage, nur eine andere Antwort.
                val ziel = Stillesprung.naechsteStelle(
                    abschnitte = gesprochen,
                    positionMs = position.toLong(),
                    dauerMs = spiel.duration.toLong(),
                )
                if (ziel != null && ziel > position) {
                    runCatching { spiel.seekTo(ziel.toInt()) }
                    _status.update { it.copy(positionMs = ziel.toInt()) }
                } else {
                    _status.update { it.copy(positionMs = position) }
                }
                delay(TAKT_MS)
            }
        }
    }

    private companion object {
        const val TAKT_MS = 100L
    }
}
