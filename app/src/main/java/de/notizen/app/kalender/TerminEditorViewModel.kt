package de.notizen.app.kalender

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.app.bild.Bildspeicher
import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.kalender.Serie
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.kalender.serie
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.repository.KalenderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.io.File
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Was im Editor steht. Zeiten als Datum und Minute des Tages, in Ortszeit. */
data class Entwurf(
    val art: Terminart = Terminart.TERMIN,
    val titel: String = "",
    val kalenderId: String = KalenderRepository.MEIN_KALENDER,
    val ganztaegig: Boolean = false,
    val startTag: LocalDate = LocalDate.now(),
    val startMinute: Int = 9 * 60,
    val endTag: LocalDate = LocalDate.now(),
    val endMinute: Int = 10 * 60,
    val wiederholung: Wiederholung = Wiederholung.KEINE,
    /** Benutzerdefiniert: Abstand, Wochentage und Ende, siehe [Serie]. */
    val alle: Int = 1,
    val wochentage: Set<DayOfWeek> = emptySet(),
    val bis: LocalDate? = null,
    val anzahl: Int? = null,
    val erinnerungen: List<Int> = emptyList(),
    val wichtig: Boolean = false,
    val farbe: Int? = null,
    val ort: String = "",
    val beschreibung: String = "",
    /** Nur fuer Aufgaben: steht dann nur in der Aufgabenliste, nie im Kalender. */
    val ohneDatum: Boolean = false,
    /** Eigenes Bild ueber dem Termin, siehe `EventEntity.bannerId`. */
    val bild: String? = null,
) {
    /** Aufgaben und Geburtstage haben kein Ende, nur einen Tag. */
    val mitEnde: Boolean get() = art == Terminart.TERMIN

    /** Eine Aufgabe ohne Datum hat weder Zeit noch Wiederholung noch Benachrichtigung. */
    val datiert: Boolean get() = !(art == Terminart.AUFGABE && ohneDatum)

    val serie: Serie get() = Serie(wiederholung, alle, wochentage, bis, anzahl)

    /** Beginn und Ende als Ortszeit, fuer den Block im Raster. */
    val beginn: LocalDateTime get() = LocalDateTime.of(startTag, LocalTime.of(startMinute / 60, startMinute % 60))
    val ende: LocalDateTime get() = LocalDateTime.of(endTag, LocalTime.of(endMinute / 60, endMinute % 60))
}

/**
 * Der Editor eines Termins. Er liegt als Blatt ueber dem Kalender und lebt so
 * lange wie dieser; [oeffnen] setzt ihn auf einen Termin oder einen neuen.
 *
 * Anders als bei Notizen mit einem Knopf „Speichern“. Ein halb eingegebener Termin, der schon klingelt, waere schlimmer
 * als einer, der erst beim Speichern entsteht.
 */
@HiltViewModel
class TerminEditorViewModel @Inject constructor(
    private val repo: KalenderRepository,
    private val einstellungen: Einstellungen,
    private val symbolwahl: Symbolwahl,
    private val wecker: Terminwecker,
    private val bildspeicher: Bildspeicher,
    private val ablage: Anhangablage,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private var id: String? = null

    /** Ein neuer Termin, kein bearbeiteter. */
    val neu: Boolean get() = id == null

    /** Der Termin, der gerade bearbeitet wird; sein Block im Kalender steht solange nicht da. */
    val terminId: String? get() = id

    private var original: EventEntity? = null
    private var anfang: Entwurf? = null

    private val _entwurf = MutableStateFlow<Entwurf?>(null)
    val entwurf: StateFlow<Entwurf?> = _entwurf.asStateFlow()

    val kalender: StateFlow<List<EventCalendarEntity>> = repo.beobachteKalender()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _fertig = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fertig: SharedFlow<Unit> = _fertig.asSharedFlow()

    private var standardErinnerung = 30
    private var standardDauer = 60
    private var vorgaben: Map<String, Kalendervorgabe> = emptyMap()
    /** Ob der neue Termin ohne Uhrzeit begann, also ueber das Plus. */
    private var ohneUhrzeit = false
    private var laden: Job? = null

    /** Laeuft gerade ein Speichern? Ein zweiter Tipp darf keinen zweiten Termin anlegen. */
    private val _speichert = MutableStateFlow(false)
    val speichert: StateFlow<Boolean> = _speichert.asStateFlow()

    /** Bilder, die in diesem Editor gewaehlt und noch nicht gespeichert wurden. */
    private val neueBilder = HashSet<String>()

    /** Wo das Bild [id] auf diesem Geraet liegt. */
    fun bilddatei(id: String): File = ablage.ziel(id, BILDTYP)

    /**
     * Setzt den Editor auf den Termin [termin] oder, ohne ihn, auf einen neuen
     * der Art [art] am [tag], um [minute] oder zur naechsten vollen Stunde.
     */
    fun oeffnen(termin: String?, art: Terminart = Terminart.TERMIN, tag: LocalDate = LocalDate.now(), minute: Int? = null, ohneDatum: Boolean = false) {
        laden?.cancel()
        unbenutzteBilderWeg()
        id = termin
        original = null
        anfang = null
        _speichert.value = false
        _entwurf.value = null
        laden = viewModelScope.launch {
            standardErinnerung = einstellungen.standardErinnerung().first()
            standardDauer = einstellungen.terminDauer().first()
            vorgaben = einstellungen.kalendervorgaben().first().mapValues { Kalendervorgabe.lesen(it.value) }
            val geladen = termin?.let { repo.termin(it) }
            val e = if (geladen != null) {
                original = geladen
                ausTermin(geladen)
            } else {
                id = null
                neuerEntwurf(art, tag, minute).copy(ohneDatum = ohneDatum && art == Terminart.AUFGABE)
            }
            anfang = e
            _entwurf.value = e
        }
    }

    /** Leert den Editor, wenn das Blatt zugeht. */
    fun schliessen() {
        laden?.cancel()
        unbenutzteBilderWeg()
        id = null
        original = null
        anfang = null
        _speichert.value = false
        _entwurf.value = null
    }

    /**
     * Uebernimmt ein Bild aus der Galerie: verkleinert, aufrecht, als JPEG
     * neben den Anhaengen (SYNC.md 16.2). Jedes Bild bekommt eine neue
     * Kennung, eine Datei aendert sich nie.
     */
    fun bildWaehlen(quelle: Uri) {
        viewModelScope.launch {
            val neu = UUID.randomUUID().toString()
            val abgelegt = bildspeicher.uebernehmen(quelle, neu) ?: return@launch
            val ziel = bilddatei(neu)
            val umgezogen = abgelegt.datei.renameTo(ziel) || runCatching {
                abgelegt.datei.copyTo(ziel, overwrite = true)
                abgelegt.datei.delete()
                true
            }.getOrDefault(false)
            if (!umgezogen) return@launch
            neueBilder += neu
            aendern { it.copy(bild = neu) }
        }
    }

    fun bildEntfernen() = aendern { it.copy(bild = null) }

    /** Loescht gewaehlte Bilder, die nie gespeichert wurden. */
    private fun unbenutzteBilderWeg() {
        neueBilder.forEach { runCatching { bilddatei(it).delete() } }
        neueBilder.clear()
    }

    private suspend fun neuerEntwurf(art: Terminart, tag: LocalDate, minute: Int?): Entwurf {
        val start = minute ?: if (tag == LocalDate.now()) {
            minOf(LocalTime.now().hour + 1, 23) * 60
        } else {
            9 * 60
        }
        val standard = einstellungen.standardkalender().first()
        val vorhanden = repo.kalender()
        val kalender = when (art) {
            Terminart.AUFGABE -> KalenderRepository.AUFGABEN
            Terminart.GEBURTSTAG -> KalenderRepository.GEBURTSTAGE
            Terminart.TERMIN -> standard.takeIf { s -> vorhanden.any { it.id == s } } ?: KalenderRepository.MEIN_KALENDER
        }
        val kalenderId = kalender.takeIf { k -> vorhanden.any { it.id == k } } ?: vorhanden.firstOrNull()?.id ?: kalender
        val vorgabe = vorgabeVon(kalenderId)
        val dauer = vorgabe.dauer ?: standardDauer
        ohneUhrzeit = minute == null
        val basis = Entwurf(
            art = art,
            kalenderId = kalenderId,
            startTag = tag,
            startMinute = start,
            endTag = if (start + dauer >= 24 * 60) tag.plusDays(1) else tag,
            endMinute = (start + dauer) % (24 * 60),
        )
        return when (art) {
            Terminart.TERMIN -> {
                val ganz = vorgabe.ganztaegig && minute == null
                basis.copy(ganztaegig = ganz, erinnerungen = vorgabe.erinnerungen(ganz, standardErinnerung))
            }
            Terminart.AUFGABE -> basis.copy(ganztaegig = minute == null, erinnerungen = listOf(if (minute == null) -540 else 0))
            Terminart.GEBURTSTAG -> basis.copy(ganztaegig = true, wiederholung = Wiederholung.JAEHRLICH, erinnerungen = listOf(-540))
        }
    }

    private fun ausTermin(t: EventEntity): Entwurf {
        val (startTag, startMinute, endTag, endMinute) = if (t.allDay) {
            listOf(Terminregeln.datumAus(t.startAt), 0, Terminregeln.datumAus(t.endAt), 0)
        } else {
            val s = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.startAt), zone)
            val e = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.endAt), zone)
            listOf(s.toLocalDate(), s.hour * 60 + s.minute, e.toLocalDate(), e.hour * 60 + e.minute)
        }
        return Entwurf(
            art = t.art,
            titel = t.title,
            kalenderId = t.calendarId,
            ganztaegig = t.allDay,
            startTag = startTag as LocalDate,
            startMinute = startMinute as Int,
            endTag = endTag as LocalDate,
            endMinute = endMinute as Int,
            wiederholung = t.recurrence,
            alle = t.serie.alle,
            wochentage = t.serie.tage,
            bis = t.serie.bis,
            anzahl = t.serie.anzahl,
            erinnerungen = t.reminders,
            wichtig = t.important,
            farbe = t.colorArgb,
            ort = t.location,
            beschreibung = t.description,
            ohneDatum = t.undated,
            bild = t.bannerId,
        )
    }

    fun geaendert(): Boolean = _entwurf.value != null && _entwurf.value != anfang

    fun aendern(neu: (Entwurf) -> Entwurf) {
        _entwurf.value = _entwurf.value?.let(neu)
    }

    /** Wechselt die Art und mit ihr, was zu ihr gehoert: Kalender, ganztaegig, Wiederholung. */
    fun setArt(art: Terminart) = aendern { e ->
        if (e.art == art) return@aendern e
        val vorhanden = kalender.value
        val ziel = when (art) {
            Terminart.AUFGABE -> KalenderRepository.AUFGABEN
            Terminart.GEBURTSTAG -> KalenderRepository.GEBURTSTAGE
            Terminart.TERMIN -> KalenderRepository.MEIN_KALENDER
        }.takeIf { z -> vorhanden.any { it.id == z } } ?: e.kalenderId
        val ganz = when (art) {
            Terminart.GEBURTSTAG -> true
            else -> e.ganztaegig
        }
        e.copy(
            art = art,
            kalenderId = ziel,
            ganztaegig = ganz,
            wiederholung = if (art == Terminart.GEBURTSTAG) Wiederholung.JAEHRLICH else e.wiederholung,
            endTag = if (art == Terminart.TERMIN) e.endTag else e.startTag,
            erinnerungen = if (ganz != e.ganztaegig) standardErinnerungen(ganz, ziel) else e.erinnerungen,
        )
    }

    fun setGanztaegig(ganz: Boolean) = aendern { e ->
        if (e.ganztaegig == ganz) e else e.copy(ganztaegig = ganz, erinnerungen = standardErinnerungen(ganz))
    }

    private fun standardErinnerungen(ganz: Boolean, kalenderId: String? = _entwurf.value?.kalenderId) =
        vorgabeVon(kalenderId).erinnerungen(ganz, standardErinnerung)

    private fun vorgabeVon(kalenderId: String?): Kalendervorgabe =
        kalenderId?.takeIf { hatVorgaben(it) }?.let { vorgaben[it] } ?: Kalendervorgabe()

    /**
     * Wechselt den Kalender. Bei einem neuen Termin folgen Benachrichtigung,
     * Dauer und ganztaegig dem neuen Kalender, aber nur, solange sie noch so
     * stehen, wie der alte Kalender sie vorgab.
     */
    fun setKalender(kalenderId: String) = aendern { e ->
        if (e.kalenderId == kalenderId) return@aendern e
        var r = e.copy(kalenderId = kalenderId)
        if (id != null || e.art != Terminart.TERMIN) return@aendern r
        val alt = vorgabeVon(e.kalenderId)
        val neu = vorgabeVon(kalenderId)
        if (ohneUhrzeit && e.ganztaegig == alt.ganztaegig && neu.ganztaegig != alt.ganztaegig) {
            r = r.copy(ganztaegig = neu.ganztaegig)
        }
        if (e.erinnerungen == alt.erinnerungen(e.ganztaegig, standardErinnerung)) {
            r = r.copy(erinnerungen = neu.erinnerungen(r.ganztaegig, standardErinnerung))
        }
        val beginn = LocalDateTime.of(e.startTag, LocalTime.of(e.startMinute / 60, e.startMinute % 60))
        val ende = LocalDateTime.of(e.endTag, LocalTime.of(e.endMinute / 60, e.endMinute % 60))
        val altDauer = (alt.dauer ?: standardDauer).toLong()
        if (java.time.Duration.between(beginn, ende).toMinutes() == altDauer) {
            val schluss = beginn.plusMinutes((neu.dauer ?: standardDauer).toLong())
            r = r.copy(endTag = schluss.toLocalDate(), endMinute = schluss.hour * 60 + schluss.minute)
        }
        r
    }

    /**
     * Ein neuer Beginn schiebt das Ende mit: Die Dauer bleibt.
     */
    fun setBeginn(tag: LocalDate, minute: Int) = aendern { e ->
        val alt = LocalDateTime.of(e.startTag, LocalTime.of(e.startMinute / 60, e.startMinute % 60))
        val altEnde = LocalDateTime.of(e.endTag, LocalTime.of(e.endMinute / 60, e.endMinute % 60))
        val dauer = java.time.Duration.between(alt, altEnde).let { if (it.isNegative) java.time.Duration.ZERO else it }
        val neu = LocalDateTime.of(tag, LocalTime.of(minute / 60, minute % 60))
        val ende = neu.plus(dauer)
        e.copy(
            startTag = tag,
            startMinute = minute,
            endTag = if (e.mitEnde) ende.toLocalDate() else tag,
            endMinute = ende.hour * 60 + ende.minute,
        )
    }

    /**
     * Beginn und Ende zugleich, wie beim Ziehen des Blocks im Raster. Ein Ende
     * vor dem Beginn wird nicht angenommen; eine Viertelstunde bleibt immer.
     */
    fun setZeitraum(beginn: LocalDateTime, ende: LocalDateTime) = aendern { e ->
        val schluss = if (ende.isAfter(beginn)) ende else beginn.plusMinutes(15)
        e.copy(
            ganztaegig = false,
            ohneDatum = false,
            startTag = beginn.toLocalDate(),
            startMinute = beginn.hour * 60 + beginn.minute,
            endTag = if (e.mitEnde) schluss.toLocalDate() else beginn.toLocalDate(),
            endMinute = schluss.hour * 60 + schluss.minute,
            erinnerungen = if (e.ganztaegig) standardErinnerungen(false) else e.erinnerungen,
        )
    }

    fun setOhneDatum(ohne: Boolean) = aendern { e -> e.copy(ohneDatum = ohne) }

    /**
     * Setzt die Serie. Woechentlich an Tagen, zu denen der Beginn nicht gehoert,
     * rueckt der Beginn auf den naechsten dieser Tage; sonst
     * kaeme das erste Vorkommen in der Serie gar nicht vor.
     */
    fun setSerie(s: Serie) = aendern { e ->
        val tage = if (s.einheit == Wiederholung.WOECHENTLICH) s.tage else emptySet()
        val neu = e.copy(
            wiederholung = s.einheit,
            alle = maxOf(1, s.alle),
            // Nur der Tag des Beginns ist dasselbe wie keine Auswahl.
            wochentage = if (tage == setOf(e.startTag.dayOfWeek)) emptySet() else tage,
            bis = s.bis,
            anzahl = if (s.bis == null) s.anzahl else null,
        )
        if (tage.isEmpty() || e.startTag.dayOfWeek in tage) return@aendern neu
        val schritt = (1L..6L).first { e.startTag.plusDays(it).dayOfWeek in tage }
        neu.copy(startTag = e.startTag.plusDays(schritt), endTag = e.endTag.plusDays(schritt))
    }

    /** Ein Ende vor dem Beginn wird nicht angenommen; dann rueckt der Beginn nach. */
    fun setEnde(tag: LocalDate, minute: Int) = aendern { e ->
        val start = LocalDateTime.of(e.startTag, LocalTime.of(e.startMinute / 60, e.startMinute % 60))
        val ende = LocalDateTime.of(tag, LocalTime.of(minute / 60, minute % 60))
        if (ende.isBefore(start) || (e.ganztaegig && tag.isBefore(e.startTag))) {
            e.copy(endTag = tag, endMinute = minute, startTag = tag, startMinute = if (e.ganztaegig) e.startMinute else minute)
        } else {
            e.copy(endTag = tag, endMinute = minute)
        }
    }

    fun erinnerungDazu(minuten: Int) = aendern { e ->
        if (minuten in e.erinnerungen) e else e.copy(erinnerungen = (e.erinnerungen + minuten).sorted())
    }

    fun erinnerungWeg(minuten: Int) = aendern { e -> e.copy(erinnerungen = e.erinnerungen - minuten) }

    fun darfExaktWecken(): Boolean = wecker.darfExaktWecken()

    fun darfVollbild(): Boolean = wecker.darfVollbild()

    fun exaktEinstellungen() = wecker.exaktEinstellungen()

    fun vollbildEinstellungen() = wecker.vollbildEinstellungen()

    fun speichern() {
        val e = _entwurf.value ?: return
        if (_speichert.value) return
        _speichert.value = true
        viewModelScope.launch {
            val jetzt = System.currentTimeMillis()
            val (startAt, endAt) = if (!e.datiert) {
                // Ohne Datum steht der Tag des Anlegens darin; gelesen wird er nicht.
                Terminregeln.alsTagesmillis(LocalDate.now()).let { it to it }
            } else if (e.ganztaegig) {
                Terminregeln.alsTagesmillis(e.startTag) to
                    Terminregeln.alsTagesmillis(if (e.mitEnde) maxOf(e.endTag, e.startTag) else e.startTag)
            } else {
                val s = Terminregeln.zeitpunkt(e.startTag, LocalTime.of(e.startMinute / 60, e.startMinute % 60), zone)
                val en = if (e.mitEnde) {
                    Terminregeln.zeitpunkt(e.endTag, LocalTime.of(e.endMinute / 60, e.endMinute % 60), zone)
                } else {
                    s
                }
                s to maxOf(s, en)
            }
            val vorher = original
            val termin = (vorher ?: EventEntity(
                id = UUID.randomUUID().toString(),
                calendarId = e.kalenderId,
                title = "",
                startAt = startAt,
                endAt = endAt,
                timeZone = zone.id,
                createdAt = jetzt,
                updatedAt = jetzt,
            )).copy(
                calendarId = e.kalenderId,
                art = e.art,
                title = e.titel.trim(),
                description = e.beschreibung.trim(),
                location = e.ort.trim(),
                allDay = e.ganztaegig || !e.datiert,
                startAt = startAt,
                endAt = endAt,
                timeZone = zone.id,
                recurrence = if (e.datiert) e.wiederholung else Wiederholung.KEINE,
                repeatInterval = if (e.datiert) maxOf(1, e.alle) else 1,
                repeatDays = if (e.datiert && e.wiederholung == Wiederholung.WOECHENTLICH) {
                    e.wochentage.map { it.value }.sorted()
                } else {
                    emptyList()
                },
                repeatUntil = e.bis?.takeIf { e.datiert }?.let { Terminregeln.alsTagesmillis(maxOf(it, e.startTag)) },
                repeatCount = e.anzahl?.takeIf { e.datiert && e.bis == null && it > 0 },
                reminders = if (e.datiert) e.erinnerungen.distinct().sorted() else emptyList(),
                important = e.wichtig && e.datiert,
                colorArgb = e.farbe,
                bannerId = e.bild,
                undated = !e.datiert,
            )
            val gespeichert = repo.terminSpeichern(termin)
            // Das gespeicherte Bild bleibt, ein ersetztes geht, auch aus Drive.
            neueBilder -= e.bild.orEmpty()
            unbenutzteBilderWeg()
            vorher?.bannerId?.takeIf { it != e.bild }?.let { alt ->
                repo.bildVergessen(alt)
                runCatching { bilddatei(alt).delete() }
            }
            anfang = e
            symbolwahl.nachSpeichern(vorher, gespeichert)
            _fertig.tryEmit(Unit)
        }
    }
}

private const val BILDTYP = "image/jpeg"
