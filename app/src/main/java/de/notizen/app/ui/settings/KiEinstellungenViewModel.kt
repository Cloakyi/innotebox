package de.notizen.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.ai.Aufraeumen
import de.notizen.app.ai.Geraetepruefung
import de.notizen.app.ai.KiZustimmung
import de.notizen.app.audio.Modellzustand
import de.notizen.app.audio.Transkription
import de.notizen.app.uebersetzung.Uebersetzung
import de.notizen.app.uebersetzung.Uebersetzungslage
import de.notizen.app.uebersetzung.Wegstand
import de.notizen.core.data.model.Uebersetzungsweg
import de.notizen.core.data.util.Clock
import de.notizen.app.uebersetzung.Uebersetzungspruefung
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.model.Geraetestand
import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KiEinstellung(
    val sprache: Transkriptsprache = Transkriptsprache.STANDARD,
    val aktiv: Boolean = false,

    /**
     * Was dieses Geraet ueberhaupt kann.
     *
     * `null` heisst „noch nicht gefragt" und ist ausdruecklich nicht dasselbe
     * wie `false`. Beim Aufgehen des Bildschirms steht die Antwort noch nicht
     * fest; „geht auf diesem Geraet nicht" waere in diesem Moment eine
     * Behauptung, die niemand geprueft hat.
     */
    val textkiDa: Boolean? = null,
    val spracherkennungDa: Boolean? = null,

    /** Was das Geraet an Uebersetzung mitbringt. `null` = noch nicht gefragt. */
    val uebersetzung: Uebersetzungslage? = null,

    /** Der gemerkte Geraetestand (Phase 15). `null` = noch nie gemessen. */
    val geraetestand: Geraetestand? = null,

    /** Wann der gültigen, versiegelten Zustimmung zugestimmt wurde; `null` ohne. */
    val zustimmungAm: Long? = null,

    /** Ob beim Verschieben ein Titel verlangt wird (Phase 15). */
    val titelpflicht: Boolean = true,

    /** Der gewaehlte Weg zum Uebersetzen und was jeder Weg hier kann (Phase 16). */
    val uebersetzungsweg: Uebersetzungsweg = Uebersetzungsweg.STANDARD,
    val wege: Map<Uebersetzungsweg, Wegstand> = emptyMap(),

    /** Ob die App ins Netz darf (Phase 15, Opt-in). */
    val netzErlaubt: Boolean = false,
)

/**
 * Sprache der Erkennung und der Schalter für die KI-Aufbereitung.
 *
 * Beide stehen beieinander, hängen aber ausdrücklich **nicht** am selben
 * Schalter: Die Spracherkennung ist keine Textgenerierung. Wer die
 * KI-Aufbereitung abschaltet, will keine erfundenen Titel — ein Transkript will
 * er trotzdem.
 */
@HiltViewModel
class KiEinstellungenViewModel @Inject constructor(
    private val einstellungen: Einstellungen,
    private val aufraeumen: Aufraeumen,
    private val transkription: Transkription,
    private val uebersetzungspruefung: Uebersetzungspruefung,
    private val uebersetzung: Uebersetzung,
    private val geraetepruefung: Geraetepruefung,
    private val kiZustimmung: KiZustimmung,
    private val clock: Clock,
) : ViewModel() {

    private val geraet = MutableStateFlow(KiEinstellung())

    private val gespeichert = combine(
        einstellungen.transkriptsprache(),
        einstellungen.kiAktiv(),
        einstellungen.geraetestand(),
        einstellungen.titelpflicht(),
        einstellungen.uebersetzungsweg(),
    ) { sprache, aktiv, stand, titelpflicht, weg ->
        KiEinstellung(
            sprache = sprache,
            aktiv = aktiv,
            geraetestand = stand,
            titelpflicht = titelpflicht,
            uebersetzungsweg = weg,
        )
    }

    val state: StateFlow<KiEinstellung> = combine(
        gespeichert,
        geraet,
        einstellungen.netzErlaubt(),
        einstellungen.kiZustimmung(),
    ) { fest, koennen, netz, zustimmung ->
        fest.copy(
            textkiDa = koennen.textkiDa,
            spracherkennungDa = koennen.spracherkennungDa,
            uebersetzung = koennen.uebersetzung,
            wege = koennen.wege,
            netzErlaubt = netz,
            // Nur eine gültige Zustimmung hat ein Datum; `aktiv` sagt, ob sie gilt.
            zustimmungAm = if (fest.aktiv) kiZustimmung.zeitpunkt(zustimmung) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, KiEinstellung())

    // Der KI-Schalter und der Netz-Schalter aendern, welche Wege gehen. Nach
    // jeder Aenderung wird neu gemessen, sonst stuende ein Weg ausgegraut da,
    // der gerade frei geworden ist.
    init {
        viewModelScope.launch {
            combine(einstellungen.kiAktiv(), einstellungen.netzErlaubt()) { a, b -> a to b }
                .collect { wegeMessen() }
        }
    }

    /**
     * Fragt das Geraet, was es kann.
     *
     * **Nicht beim Erzeugen, sondern beim Aufgehen des Bildschirms.** Beide
     * Pruefungen sprechen mit einem Systemdienst; das gehoert nicht in den
     * Konstruktor eines ViewModels. Und die Antwort kann sich aendern: Ein
     * Modell, das gestern noch fehlte, kann heute da sein.
     *
     * Die Spracherkennung gilt auch dann als vorhanden, wenn sie erst geladen
     * werden muss. Herunterladen ist ein Weg, kein Hindernis.
     */
    fun geraetPruefen() {
        viewModelScope.launch {
            // Ohne eingeschaltete KI wird ML Kit nicht gefragt (Zustimmung,
            // seit Alpha 9). `null` heisst dann „nicht gefragt", und die
            // Hinweise zum Geraet bleiben weg, statt etwas zu behaupten.
            val kiAn = einstellungen.kiAktiv().first()
            val textki = if (kiAn) runCatching { aufraeumen.verfuegbar() }.getOrDefault(false) else null
            val sprache = if (kiAn) runCatching { transkription.zustand().first }.getOrNull() else null

            geraet.value = KiEinstellung(
                textkiDa = textki,
                spracherkennungDa = if (!kiAn) {
                    null
                } else {
                    sprache is Modellzustand.Bereit ||
                        sprache is Modellzustand.Ladbar ||
                        sprache is Modellzustand.Laedt
                },
                // MESSEN STATT RATEN. Ob ein Geraet die Uebersetzung des
                // Systems mitbringt, laesst sich von aussen nicht sagen -- es
                // haengt am Hersteller und an dem, was er mitliefert. Also
                // fragt die App und schreibt die Antwort hin. Dasselbe
                // Vorgehen wie beim AICore-Check aus Phase 0.
                uebersetzung = uebersetzungspruefung.lage(),
                wege = geraet.value.wege,
            )
            wegeMessen()
        }
    }

    private suspend fun wegeMessen() {
        val wege = runCatching { uebersetzung.wegstaende() }.getOrDefault(emptyMap())
        geraet.value = geraet.value.copy(wege = wege)
    }

    fun setUebersetzungsweg(weg: Uebersetzungsweg) {
        viewModelScope.launch { einstellungen.setUebersetzungsweg(weg) }
    }

    /** Ausschalten geht sofort; Einschalten nur ueber den Dialog mit den Bedingungen. */
    fun netzErlauben(an: Boolean) {
        viewModelScope.launch { einstellungen.setNetzErlaubt(an, clock.now()) }
    }

    fun setSprache(wert: Transkriptsprache) {
        viewModelScope.launch { einstellungen.setTranskriptsprache(wert) }
    }

    /**
     * Einschalten nur aus dem Dialog mit der Zustimmung heraus. Danach wird
     * das Geraet gemessen, damit die Zeile zum Geraetestand stimmt; geladen
     * wird dabei nichts.
     */
    fun kiEinschalten() {
        viewModelScope.launch {
            if (kiZustimmung.erteilen()) {
                runCatching { geraetepruefung.messen() }.getOrNull()?.let { einstellungen.setGeraetestand(it) }
            }
            geraetPruefen()
        }
    }

    /** Ausschalten geht sofort und löscht Zustimmung und Schlüssel restlos. */
    fun kiAusschalten() {
        viewModelScope.launch {
            kiZustimmung.widerrufen()
            geraetPruefen()
        }
    }

    fun setTitelpflicht(an: Boolean) {
        viewModelScope.launch { einstellungen.setTitelpflicht(an) }
    }
}
