package de.notizen.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10

/**
 * Das Aufnahmeformat.
 *
 * 16 kHz, Mono, 16 Bit Little Endian. Es ist zugleich das Format, das später
 * beim Transkribieren durch den Deskriptor geschickt wird, und **seit dem
 * 2026-09-19 ist belegt, dass die Erkennung genau das erwartet**: Die
 * Dokumentation der Speech-API (Stand 2026-09-17) verlangt für
 * `AudioSource.fromPfd()` rohes 16-Bit-PCM ohne Kopf, ein Kanal, 16 kHz.
 * Lange war das eine Annahme, weil `SpeechRecognizerOptions` kein Feld dafür
 * hat (siehe `Transkriptor`).
 */
const val ABTASTRATE = 16_000

/**
 * Ein Wert der mitlaufenden Welle je fünfzig Millisekunden Ton (Phase 18).
 *
 * Fest, unabhängig von der Blockgröße des Mikrofons: Die Welle läuft mit
 * dieser Rate über die Zeit, und die muss sie kennen, ohne die Blöcke zu
 * zählen. Zwanzig je Sekunde sind genug, dass eine Pause als Lücke zu sehen
 * ist, und wenig genug, dass ein Bildschirm ein paar Sekunden zeigt.
 */
const val WELLE_FENSTER_MS = 50
const val WELLE_WERTE_JE_SEKUNDE = 1_000f / WELLE_FENSTER_MS
private const val KANAL = AudioFormat.CHANNEL_IN_MONO
private const val KODIERUNG = AudioFormat.ENCODING_PCM_16BIT
private const val BITS_PRO_ABTASTUNG = 16

/**
 * Wie stark gerichtet aufgenommen wird: −1 rundum, 1 stark gebündelt.
 *
 * Bewusst in der Mitte. Ganz gebündelt klänge ein Diktat gut, würde aber bei
 * einer Besprechung alles außer dem Nächstsitzenden wegdrücken — und Notizen
 * entstehen in beiden Lagen.
 */
private const val RICHTWIRKUNG = 0.5f

/**
 * Nimmt vom Mikrofon in eine WAV-Datei auf.
 *
 * **Hier wird nur aufgenommen.** Die Erkennung läuft später über die fertige
 * Datei — sie hat mit diesem Vorgang nichts zu tun.
 *
 * Das war einmal anders: Solange live erkannt wurde, schrieb diese Klasse jeden
 * Block zusätzlich in eine Pipe. Nach dem Umbau auf zwei Schritte las diese
 * Pipe niemand mehr, und **eine Pipe, die niemand leert, blockiert den
 * Schreibenden**, sobald ihr Puffer voll ist — nach knapp zwei Sekunden. Die
 * Aufnahmeschleife hing dann fest: Der Timer blieb stehen, „Fertig" bewirkte
 * nichts, der Dienst ließ sich nicht mehr beenden und überlebte sogar das
 * Wegwischen der App. Am Gerät gefunden am 2026-08-21.
 *
 * Deshalb steht hier ausdrücklich: **kein zweiter Abnehmer in dieser Klasse.**
 * Wer wieder einen braucht, muss dafür sorgen, dass er auch liest — oder er
 * legt die Aufnahme still.
 */
class Mitschnitt(private val ziel: File) : AutoCloseable {

    /**
     * Rund 60 Millisekunden je Block, sofern das System nicht mehr verlangt.
     *
     * Vorher waren es 200 — die Welle bekam damit nur fünfmal je Sekunde neue
     * Werte und ruckte sichtbar. Kleinere Blöcke heißen häufiger lesen, was
     * hier nichts kostet: Die Schleife wartet ohnehin auf das Mikrofon.
     */
    private val puffergroesse: Int = maxOf(
        AudioRecord.getMinBufferSize(ABTASTRATE, KANAL, KODIERUNG),
        ABTASTRATE / 16 * 2,
    )

    private var recorder: AudioRecord? = null
    private var datei: FileOutputStream? = null
    private val puffer = ByteArray(puffergroesse)

    /** Gesamtzahl geschriebener Audio-Bytes — für den WAV-Kopf und die Dauer. */
    private var geschrieben: Long = 0

    /** Lautstärke des zuletzt gelesenen Blocks, 0..1. */
    @Volatile
    var pegel: Float = 0f
        private set

    /**
     * Die Werte der mitlaufenden Welle, die in diesem Block fertig wurden.
     *
     * Ein Wert je [WELLE_FENSTER_MS] Ton, gemessen über Blockgrenzen hinweg
     * ([Pegelfenster]): meistens einer, manchmal keiner oder zwei. Nicht mehr
     * „so viele je Block", denn die Blockgröße bestimmt das Gerät, und die
     * Welle soll auf jedem gleich schnell laufen.
     */
    @Volatile
    var feinpegel: FloatArray = FloatArray(0)
        private set

    private val fenster = Pegelfenster()

    /**
     * Ob das System die Mikrofonausrichtung übernommen hat.
     *
     * Rein zur Auskunft: Die Geräte dürfen den Wunsch ablehnen, und dann ist es
     * keine Störung, sondern nur die Voreinstellung.
     */
    var richtungAngenommen: Boolean = false
        private set

    /** Ob überhaupt je etwas anderes als Stille ankam. */
    @Volatile
    var hatTon: Boolean = false
        private set

    val dauerMs: Long
        get() = geschrieben * 1000 / (ABTASTRATE.toLong() * BITS_PRO_ABTASTUNG / 8)

    /**
     * Öffnet Mikrofon und Datei.
     *
     * `@SuppressLint`: die Berechtigung wird eine Ebene höher geprüft, im
     * Dienst, der ohne sie gar nicht erst startet.
     */
    @SuppressLint("MissingPermission")
    fun starten() {
        datei = FileOutputStream(ziel).also { platzFuerKopf(it) }

        val aufnehmer = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            ABTASTRATE,
            KANAL,
            KODIERUNG,
            puffergroesse,
        )
        // Ein AudioRecord kann sich weigern, ohne eine Ausnahme zu werfen --
        // etwa wenn eine andere App das Mikrofon haelt. Dann lieber hier
        // scheitern als eine Aufnahme vortaeuschen, die nie ankommt.
        check(aufnehmer.state == AudioRecord.STATE_INITIALIZED) {
            "Das Mikrofon ließ sich nicht öffnen."
        }
        // Das Handy hat mehrere Mikrofone. Diese beiden Wuensche sagen dem
        // System, dass die Stimme vor dem Geraet gemeint ist und nicht die
        // Umgebung -- es darf sie ablehnen, dann bleibt es beim Standard.
        // Beides gibt es seit Android 10 (in android.jar der API 37
        // nachgesehen, 2026-08-21).
        richtungAngenommen = aufnehmer.setPreferredMicrophoneDirection(
            MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER,
        ) && aufnehmer.setPreferredMicrophoneFieldDimension(RICHTWIRKUNG)

        aufnehmer.startRecording()
        check(aufnehmer.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            "Die Aufnahme ist nicht angelaufen."
        }
        recorder = aufnehmer
    }

    /**
     * Liest einen Block und schreibt ihn weg. `false` heißt: es kommt nichts
     * mehr.
     *
     * Wird in einer Schleife aus dem Dienst gerufen. Bewusst kein eigener
     * Thread hier drin: wer die Schleife dreht, entscheidet auch, wann sie
     * endet.
     */
    fun blockLesen(): Boolean {
        val aufnehmer = recorder ?: return false
        val gelesen = aufnehmer.read(puffer, 0, puffer.size)

        // Negative Rueckgaben sind echte Fehler (ERROR_INVALID_OPERATION,
        // ERROR_DEAD_OBJECT). Weiterzudrehen hiesse, endlos denselben Fehler zu
        // holen -- genau die Art Schleife, die sich nicht mehr beenden laesst.
        if (gelesen < 0) return false
        if (gelesen == 0) return true

        datei?.write(puffer, 0, gelesen)
        geschrieben += gelesen
        messen(gelesen)
        return true
    }

    /**
     * Spitzenpegel des Blocks — **in Dezibel, nicht linear.**
     *
     * Das war ein echter Anzeigefehler: Normale Sprache liegt bei etwa −20 dBFS,
     * linear also bei 0,1. Der Ausschlag stand damit bei einem Zehntel, obwohl
     * die Aufnahme völlig in Ordnung war — es sah aus, als käme kaum etwas an.
     * Am Gerät gemeldet am 2026-08-21.
     *
     * Pegelanzeigen sind aus genau diesem Grund überall logarithmisch: Das Ohr
     * hört in Dezibel, und der Bereich, in dem Sprache stattfindet, ist linear
     * ein schmaler Streifen ganz unten. Gezeigt wird jetzt der Bereich von
     * [ANZEIGE_DB] bis Vollausschlag.
     */
    private fun messen(laenge: Int) {
        feinpegel = fenster.verarbeiten(puffer, laenge)
        val gesamtspitze = fenster.blockspitze
        pegel = alsAusschlag(gesamtspitze)
        if (gesamtspitze > STILLE_SCHWELLE) hatTon = true
    }

    override fun close() {
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null

        datei?.let { strom ->
            runCatching { strom.close() }
            // Erst jetzt kann der Kopf geschrieben werden: vorher ist die
            // Laenge nicht bekannt.
            runCatching { kopfNachtragen(ziel, geschrieben) }
        }
        datei = null
    }

    companion object {
        /** Unter diesem Ausschlag gilt ein Block als Stille. */
        const val STILLE_SCHWELLE = 500
    }
}

/**
 * Zerlegt den Ton in Fenster fester Länge und misst je Fenster die Spitze.
 *
 * Für die mitlaufende Welle (Phase 18): ein Wert je [WELLE_FENSTER_MS], egal
 * wie groß die Blöcke sind, die das Mikrofon liefert. Ein Fenster darf über
 * eine Blockgrenze reichen; der angefangene Rest wandert in den nächsten
 * Aufruf. Ausgelagert und `internal`, damit es ohne Mikrofon prüfbar ist.
 */
internal class Pegelfenster(
    private val fensterBytes: Int = ABTASTRATE * BITS_PRO_ABTASTUNG / 8 * WELLE_FENSTER_MS / 1000,
) {
    private var spitze = 0
    private var gefuellt = 0

    /** Spitze des zuletzt verarbeiteten Blocks, für Pegel und Stilleprüfung. */
    var blockspitze = 0
        private set

    /** Verarbeitet einen Block und liefert die Werte der fertig gewordenen Fenster. */
    fun verarbeiten(puffer: ByteArray, laenge: Int): FloatArray {
        val fertig = ArrayList<Float>(2)
        var blockspitze = 0
        var i = 0
        while (i + 1 < laenge) {
            val wert = (puffer[i + 1].toInt() shl 8) or (puffer[i].toInt() and 0xFF)
            val betrag = abs(wert.toShort().toInt())
            if (betrag > spitze) spitze = betrag
            if (betrag > blockspitze) blockspitze = betrag
            // Nicht jede Abtastung -- fuer einen Ausschlag reicht jede
            // achte, und die Schleife laeuft im Aufnahmetakt.
            i += SCHRITT
            gefuellt += SCHRITT
            if (gefuellt >= fensterBytes) {
                fertig += alsAusschlag(spitze)
                spitze = 0
                gefuellt -= fensterBytes
            }
        }
        this.blockspitze = blockspitze
        return fertig.toFloatArray()
    }

    private companion object {
        /** Bytes je Schritt: jede achte Abtastung zu zwei Bytes. */
        const val SCHRITT = 16
    }
}

/** Unteres Ende der Pegelanzeige. Darunter ist es für das Auge sowieso still. */
private const val ANZEIGE_DB = -55f

/**
 * Rechnet einen Abtastwert in einen Ausschlag von 0 bis 1 um.
 *
 * Ausgelagert und `internal`, damit die Umrechnung prüfbar ist — sie ist der
 * Grund, warum die Anzeige vorher tot wirkte.
 */
internal fun alsAusschlag(spitze: Int): Float = ausschlagAusAnteil(spitze / 32_768f)

/**
 * Dasselbe für einen bereits normierten Wert von 0 bis 1.
 *
 * Wird von der Wellenform gebraucht, die mit Effektivwerten arbeitet statt mit
 * Abtastwerten -- die Skalierung muss aber dieselbe sein, sonst sieht dieselbe
 * Aufnahme live anders aus als hinterher.
 */
internal fun ausschlagAusAnteil(anteil: Float): Float {
    if (anteil <= 0f) return 0f
    val db = 20f * log10(anteil)
    return ((db - ANZEIGE_DB) / -ANZEIGE_DB).coerceIn(0f, 1f)
}
