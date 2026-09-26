package de.notizen.app.ui.daten

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.sync.sicherung.SICHERUNG_VERSION
import de.notizen.core.sync.sicherung.Sicherung
import de.notizen.core.sync.sicherung.Sicherungsergebnis
import de.notizen.core.sync.sicherung.Sicherungskopf
import de.notizen.core.sync.sicherung.dateiname
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Was gerade läuft, wenn etwas läuft. */
enum class Vorgang { NICHTS, SICHERN, WIEDERHERSTELLEN }

/**
 * Die Rückfrage vor dem Einlesen.
 *
 * [zeilen] sagen, was in der Datei steckt: wann und von wem sie stammt und wie
 * viel darin ist. Eingelesen wird erst, wenn der Nutzer das bestätigt.
 */
data class Rueckfrage(val quelle: Uri, val zeilen: List<String>)

data class Sicherungslage(
    val vorgang: Vorgang = Vorgang.NICHTS,
    val meldung: String? = null,
    val rueckfrage: Rueckfrage? = null,
) {
    val laeuft: Boolean get() = vorgang != Vorgang.NICHTS
}

/**
 * Sichern und Wiederherstellen, von der Oberfläche aus gesehen.
 *
 * Die Datei wählt das System, nicht die App. Beide Wege gehen über den
 * Dateiwähler von Android: Die App bekommt eine Adresse, die genau auf diese
 * eine Datei zeigt, und sonst nichts. Sie braucht dafür keine Berechtigung auf
 * den Speicher, und sie kann auch nichts anderes anfassen.
 *
 * Die Arbeit hängt am Lebenslauf dieses Bildschirms. Wer ihn während einer
 * großen Sicherung verlässt, bricht sie ab. Für eine Aufgabe, die der Nutzer
 * ausdrücklich anstößt und deren Ergebnis er sofort sehen will, ist das die
 * ehrlichere Wahl gegenüber einem Hintergrundauftrag, dessen Ausgang niemand
 * mehr zu Gesicht bekommt. Der Bildschirm sagt es, solange es läuft.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sicherung: Sicherung,
) : ViewModel() {

    private val _lage = MutableStateFlow(Sicherungslage())
    val lage: StateFlow<Sicherungslage> = _lage.asStateFlow()

    /** Der Name, den der Dateiwähler vorschlägt. */
    fun vorschlag(): String = dateiname(System.currentTimeMillis())

    fun sichern(ziel: Uri) {
        if (_lage.value.laeuft) return
        _lage.value = Sicherungslage(Vorgang.SICHERN)

        viewModelScope.launch {
            val ergebnis = withContext<Sicherungsergebnis>(Dispatchers.IO) {
                // "wt" schneidet eine vorhandene Datei ab. Ohne das "t" bliebe
                // beim Überschreiben einer größeren Sicherung deren Rest hinten
                // stehen, und die Datei wäre unbrauchbar, ohne dass man es ihr
                // ansieht.
                val strom = runCatching { context.contentResolver.openOutputStream(ziel, "wt") }
                    .getOrNull()
                    ?: return@withContext Sicherungsergebnis.Fehler("Die Datei ließ sich nicht anlegen")

                strom.use { sicherung.sichern(it, kennung()) }
            }
            _lage.value = Sicherungslage(meldung = text(ergebnis))
        }
    }

    /**
     * Sieht in eine Datei, bevor sie eingelesen wird.
     *
     * Eingelesen wird nie ungefragt. Eine Datei kann auch von einer anderen App
     * kommen, die sie InNoteBox hinreicht, und neuere Einträge darin ersetzen
     * vorhandene Notizen. Deshalb steht vorher da, was in der Datei steckt, und
     * erst ein Tippen auf „Einlesen" liest sie ein.
     */
    fun pruefen(quelle: Uri) {
        if (_lage.value.laeuft) return
        viewModelScope.launch {
            val kopf = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(quelle) }.getOrNull()
                    ?.use { sicherung.vorschau(it) }
            }
            _lage.update {
                when {
                    kopf == null -> it.copy(meldung = text(Sicherungsergebnis.KeineSicherung))
                    kopf.formatVersion > SICHERUNG_VERSION ->
                        it.copy(meldung = text(Sicherungsergebnis.ZuNeu(kopf.formatVersion)))
                    else -> it.copy(rueckfrage = Rueckfrage(quelle, beschreibung(kopf)))
                }
            }
        }
    }

    /** Der Nutzer hat „Einlesen" angetippt. */
    fun einlesenBestaetigt() {
        val frage = _lage.value.rueckfrage ?: return
        _lage.update { it.copy(rueckfrage = null) }
        wiederherstellen(frage.quelle)
    }

    fun einlesenVerworfen() = _lage.update { it.copy(rueckfrage = null) }

    private fun beschreibung(kopf: Sicherungskopf): List<String> {
        val datum = DATUM.format(Instant.ofEpochMilli(kopf.erzeugtAm).atZone(ZoneId.systemDefault()))
        return listOf(
            "Erstellt am $datum von ${kopf.erzeugtVon.take(80)}.",
            "Darin sind " + mengen(kopf.notizen, "Notiz", "Notizen") + ", " +
                mengen(kopf.tags, "Tag", "Tags") + " und " +
                mengen(kopf.ordner, "Ordner", "Ordner") + ".",
            "Neuere Notizen aus der Datei ersetzen hier die mit derselben Kennung. " +
                "Gelöscht wird nichts.",
        )
    }

    private fun wiederherstellen(quelle: Uri) {
        if (_lage.value.laeuft) return
        _lage.value = Sicherungslage(Vorgang.WIEDERHERSTELLEN)

        viewModelScope.launch {
            val ergebnis = withContext<Sicherungsergebnis>(Dispatchers.IO) {
                val strom = runCatching { context.contentResolver.openInputStream(quelle) }
                    .getOrNull()
                    ?: return@withContext Sicherungsergebnis.Fehler("Die Datei ließ sich nicht öffnen")

                strom.use { sicherung.wiederherstellen(it) }
            }
            _lage.value = Sicherungslage(meldung = text(ergebnis))
        }
    }

    fun meldungGelesen() = _lage.update { it.copy(meldung = null) }

    private companion object {
        val DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM yyyy 'um' HH:mm", Locale.GERMAN)
    }

    /** Wer die Datei geschrieben hat. Steht in ihrem Kopf. */
    private fun kennung(): String {
        val fassung = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return "InNoteBox $fassung (Android)"
    }

    private fun text(ergebnis: Sicherungsergebnis): String = when (ergebnis) {
        is Sicherungsergebnis.Gesichert -> buildString {
            append("Gesichert wurden ")
            append(mengen(ergebnis.notizen, "Notiz", "Notizen"))
            append(", ")
            append(mengen(ergebnis.tags, "Tag", "Tags"))
            append(" und ")
            append(mengen(ergebnis.dateien, "Datei", "Dateien"))
            append(".")
        }

        is Sicherungsergebnis.Eingelesen -> buildString {
            append(mengen(ergebnis.neu, "Notiz kam", "Notizen kamen"))
            append(" neu dazu, ")
            append(ergebnis.ersetzt)
            append(if (ergebnis.ersetzt == 1) " wurde ersetzt" else " wurden ersetzt")
            append(" und ")
            append(ergebnis.behalten)
            append(if (ergebnis.behalten == 1) " blieb unverändert." else " blieben unverändert.")

            // Der Satz steht nur da, wenn es ihn zu sagen gibt. Sonst fragte
            // man sich bei jedem Einlesen, warum von gelöschten Notizen die
            // Rede ist.
            if (ergebnis.zurueckgeholt == 1) {
                append(" Eine davon war hier gelöscht. Sie liegt jetzt als neue ")
                append("Notiz im Eingang.")
            } else if (ergebnis.zurueckgeholt > 1) {
                append(" ${ergebnis.zurueckgeholt} davon waren hier gelöscht. ")
                append("Sie liegen jetzt als neue Notizen im Eingang.")
            }
        }

        Sicherungsergebnis.KeineSicherung ->
            "Diese Datei ist keine Sicherung von InNoteBox."

        is Sicherungsergebnis.ZuNeu ->
            "Diese Sicherung stammt aus einer neueren Fassung der App. " +
                "Bitte aktualisiere InNoteBox und versuche es dann noch einmal."

        is Sicherungsergebnis.Fehler ->
            "Das hat nicht geklappt. ${ergebnis.grund}"
    }
}

/**
 * Zahl und Wort, in der richtigen Zahlform.
 *
 * „1 Notizen" liest sich wie ein Fehler, und genau so einer wäre es auch.
 */
private fun mengen(anzahl: Int, einzahl: String, mehrzahl: String): String =
    "$anzahl ${if (anzahl == 1) einzahl else mehrzahl}"
