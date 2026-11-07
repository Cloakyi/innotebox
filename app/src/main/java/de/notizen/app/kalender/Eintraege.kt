package de.notizen.app.kalender

import de.notizen.core.data.db.entity.EventCalendarEntity
import de.notizen.core.data.db.entity.EventEntity
import de.notizen.core.data.db.relation.Notiztermin
import de.notizen.core.data.kalender.Serie
import de.notizen.core.data.kalender.Terminregeln
import de.notizen.core.data.kalender.Vorkommen
import de.notizen.core.data.model.Terminart
import de.notizen.core.data.model.Wiederholung
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Woher ein Eintrag im Kalender stammt. */
sealed interface Herkunft {
    /** Ein Termin der App, genauer ein Vorkommen davon. */
    data class Termin(val vorkommen: Vorkommen) : Herkunft

    /** Eine Notiz mit Erinnerung. */
    data class Notiz(val noteId: String) : Herkunft
}

/**
 * Ein Eintrag, so wie die Ansichten ihn zeichnen. Alle drei Quellen werden
 * hierauf gebracht, damit Raster, Monat und Liste nur eine Sorte kennen.
 */
data class Eintrag(
    val schluessel: String,
    val titel: String,
    val beginn: Long,
    val ende: Long,
    val ersterTag: LocalDate,
    val letzterTag: LocalDate,
    val ganztaegig: Boolean,
    val farbe: Int,
    val herkunft: Herkunft,
    val art: Terminart = Terminart.TERMIN,
    val wichtig: Boolean = false,
    val erledigt: Boolean = false,
    val symbol: String? = null,
    val ort: String = "",
) {
    /**
     * Steht oben in der Leiste fuer ganze Tage, nicht im Stundenraster.
     *
     * Oben steht nur, was ganztaegig ist oder mindestens einen vollen
     * Tag dauert. Ein Termin von 23 bis 1 Uhr steht im Raster, geteilt in zwei
     * Bloecke (siehe [teil]).
     */
    val oben: Boolean get() = ganztaegig || ende - beginn >= TAG_LANG
}

private const val TAG_LANG: Long = 24 * 3_600_000L

/**
 * „(1/2)“ fuer den ersten Block eines Termins, der ueber Mitternacht geht,
 * „(2/2)“ fuer den zweiten; `null`, wenn er an einem Tag liegt.
 */
fun teil(e: Eintrag, tag: LocalDate): String? {
    if (e.oben || !e.letzterTag.isAfter(e.ersterTag)) return null
    val gesamt = java.time.temporal.ChronoUnit.DAYS.between(e.ersterTag, e.letzterTag) + 1
    val nummer = java.time.temporal.ChronoUnit.DAYS.between(e.ersterTag, tag) + 1
    return "($nummer/$gesamt)"
}

/** Feste Farbe der Ebene mit den Notizen, die kein eigener Kalender ist. */
const val FARBE_NOTIZEN: Int = 0xFFF6BF26.toInt()

/** Die Farbe, wenn ein Kalender fehlt. */
const val FARBE_STANDARD: Int = 0xFF4285F4.toInt()

/** Die Farben zur Wahl fuer Kalender und Termine. Wie die Notizpalette eine Ausnahme von der Regel gegen feste Farben. */
val KALENDERFARBEN: List<Pair<Int, String>> = listOf(
    0xFFD50000.toInt() to "Rot",
    0xFFAD1457.toInt() to "Kirsche",
    0xFFD81B60.toInt() to "Pink",
    0xFFE67C73.toInt() to "Rosa",
    0xFFF4511E.toInt() to "Orange",
    0xFFEF6C00.toInt() to "Kürbis",
    0xFFF6BF26.toInt() to "Gelb",
    0xFFE4C441.toInt() to "Senf",
    0xFFC0CA33.toInt() to "Avocado",
    0xFF33B679.toInt() to "Hellgrün",
    0xFF0B8043.toInt() to "Grün",
    0xFF00897B.toInt() to "Petrol",
    0xFF4FC3F7.toInt() to "Himmelblau",
    0xFF039BE5.toInt() to "Türkis",
    0xFF4285F4.toInt() to "Blau",
    0xFF3F51B5.toInt() to "Dunkelblau",
    0xFF7986CB.toInt() to "Lavendel",
    0xFFB39DDB.toInt() to "Flieder",
    0xFF8E24AA.toInt() to "Lila",
    0xFF6A1B9A.toInt() to "Traube",
    0xFF795548.toInt() to "Braun",
    0xFFA1887F.toInt() to "Sand",
    0xFF607D8B.toInt() to "Schiefer",
    0xFF616161.toInt() to "Grau",
)

/** Kennung der Ebene „Erinnerungen aus Notizen“ in der Liste der ausgeblendeten Kalender. */
const val EBENE_NOTIZEN = "notizen"

/**
 * Alles, was zwischen [von] und [bis] (ausschliesslich) zu sehen ist.
 *
 * Ausgeblendete Kalender fallen heraus und mit ihnen ihre Termine; ein
 * Termin, dessen Kalender es nicht mehr gibt, ebenso. Sortiert nach Beginn,
 * ganze Tage zuerst.
 */
fun eintraege(
    termine: List<EventEntity>,
    kalender: List<EventCalendarEntity>,
    ausgeblendet: Set<String>,
    notizen: List<Notiztermin>,
    von: LocalDate,
    bis: LocalDate,
    zone: ZoneId,
): List<Eintrag> {
    val farben = kalender.filter { it.deletedAt == null }.associate { it.id to it.colorArgb }
    val ergebnis = ArrayList<Eintrag>()
    for (t in termine) {
        if (t.deletedAt != null || t.calendarId in ausgeblendet) continue
        val kalenderfarbe = farben[t.calendarId] ?: continue
        for (v in Terminregeln.vorkommen(t, von, bis, zone)) ergebnis += eintragVon(v, kalenderfarbe)
    }
    if (EBENE_NOTIZEN !in ausgeblendet) {
        for (n in notizen) {
            val tag = Instant.ofEpochMilli(n.triggerAt).atZone(zone).toLocalDate()
            if (tag.isBefore(von) || !tag.isBefore(bis)) continue
            ergebnis += Eintrag(
                schluessel = "n:${n.noteId}:${n.triggerAt}",
                titel = n.title.ifBlank { "Notiz ohne Titel" },
                beginn = n.triggerAt,
                ende = n.triggerAt + NOTIZDAUER,
                ersterTag = tag,
                letzterTag = tag,
                ganztaegig = false,
                farbe = FARBE_NOTIZEN,
                herkunft = Herkunft.Notiz(n.noteId),
                erledigt = n.isFired,
            )
        }
    }
    return ergebnis.sortedWith(compareBy<Eintrag>({ it.ersterTag }, { !it.oben }, { it.beginn }, { it.titel }))
}

/** Ein Vorkommen eines Termins als Eintrag. [kalenderfarbe] gilt, wenn der Termin keine eigene hat. */
fun eintragVon(v: Vorkommen, kalenderfarbe: Int): Eintrag {
    val t = v.termin
    return Eintrag(
        schluessel = "t:${t.id}:${v.schluessel}",
        titel = t.title.ifBlank { "Ohne Titel" },
        beginn = v.beginn,
        ende = v.ende,
        ersterTag = v.ersterTag,
        letzterTag = v.letzterTag,
        ganztaegig = t.allDay,
        farbe = t.colorArgb ?: kalenderfarbe,
        herkunft = Herkunft.Termin(v),
        art = t.art,
        wichtig = t.important,
        erledigt = t.done,
        symbol = t.symbol,
        ort = t.location,
    )
}

/**
 * Der Eintrag, den ein Treffer der Suche oder eine Zeile der Aufgabenliste
 * oeffnet: bei einer Serie das naechste Vorkommen ab [heute], sonst das erste.
 * Eine Aufgabe ohne Datum hat kein Vorkommen und steht dann auf [heute].
 */
fun eintragFuer(t: EventEntity, kalenderfarbe: Int, heute: LocalDate, zone: ZoneId): Eintrag {
    val v = if (t.undated) {
        null
    } else {
        val tag = if (t.allDay) Terminregeln.datumAus(t.startAt) else Terminregeln.tagVon(t.startAt, zone)
        val ab = if (t.recurrence == Wiederholung.KEINE) tag else maxOf(tag, heute)
        Terminregeln.vorkommen(t, ab, ab.plusDays(800), zone).firstOrNull()
            ?: Terminregeln.vorkommen(t, tag, tag.plusDays(800), zone).firstOrNull()
    }
    return eintragVon(v ?: Vorkommen(t, t.startAt, t.endAt, heute, heute, t.startAt), kalenderfarbe)
}

/** Eine Notiz-Erinnerung ist ein Zeitpunkt; im Raster steht sie als halbe Stunde. */
const val NOTIZDAUER: Long = 30 * 60_000L

/** Was an einem Tag steht. */
fun amTag(eintraege: List<Eintrag>, tag: LocalDate): List<Eintrag> =
    eintraege.filter { !tag.isBefore(it.ersterTag) && !tag.isAfter(it.letzterTag) }

/**
 * Ein Eintrag im Stundenraster: linker Rand und Breite als Anteil der
 * Tagesspalte, dazu die Ebene. Eine hoehere Ebene liegt oben.
 */
data class Platz(val eintrag: Eintrag, val links: Float, val breite: Float, val ebene: Int)

/** Termine, die so kurz nacheinander beginnen, stehen in einer Reihe nebeneinander. */
const val REIHE_MS: Long = 30 * 60_000L

/** Wie weit ein spaeter beginnender Termin eingerueckt ueber dem frueheren liegt. */
const val EINRUECKEN: Float = 0.12f

/** In einer Reihe greift jeder Block so weit unter seinen rechten Nachbarn. */
const val UNTERGREIFEN: Float = 1.7f

/** Bliebe beim Einruecken weniger Breite, geht der Termin stattdessen in die Reihe. */
const val MINDESTBREITE: Float = 0.5f

/**
 * Verteilt die Termine eines Tages auf die Breite der Spalte.
 *
 * Beginnen zwei Termine innerhalb einer halben Stunde, stehen sie in einer
 * Reihe nebeneinander; jeder greift dabei ein Stueck unter den naechsten, der
 * spaetere liegt oben. Beginnt ein Termin deutlich spaeter, waehrend ein
 * anderer noch laeuft, liegt er eingerueckt ueber diesem und laesst dessen
 * Anfang sichtbar: der lange Block hinten, der kurze davor. Bliebe dabei zu
 * wenig Breite, geht er doch in die Reihe.
 *
 * [tagBeginn] und [tagEnde] begrenzen Termine, die ueber Mitternacht gehen.
 * [mindestens] ist die kleinste Hoehe in Millisekunden, damit ein Termin von
 * fuenf Minuten nicht unter einem anderen verschwindet.
 */
fun kaskade(eintraege: List<Eintrag>, tagBeginn: Long, tagEnde: Long, mindestens: Long): List<Platz> {
    class Stueck(val e: Eintrag, val anfang: Long, val ende: Long)
    class Reihe(val links: Float, val glieder: MutableList<Int>)

    val stuecke = eintraege.map { e ->
        val anfang = maxOf(e.beginn, tagBeginn)
        Stueck(e, anfang, maxOf(minOf(e.ende, tagEnde), anfang + mindestens))
    }.sortedWith(compareBy<Stueck>({ it.anfang }, { -(it.ende - it.anfang) }, { it.e.schluessel }))

    val reihen = ArrayList<Reihe>()
    val reiheVon = IntArray(stuecke.size)

    fun linksVon(i: Int): Float {
        val r = reihen[reiheVon[i]]
        val anteil = (1f - r.links) / r.glieder.size
        return r.links + r.glieder.indexOf(i) * anteil
    }

    fun beitreten(i: Int, nachbar: Int) {
        reiheVon[i] = reiheVon[nachbar]
        reihen[reiheVon[i]].glieder += i
    }

    for (i in stuecke.indices) {
        val s = stuecke[i]
        val ueber = (0 until i).filter { j -> stuecke[j].anfang < s.ende && s.anfang < stuecke[j].ende }
        if (ueber.isEmpty()) {
            reiheVon[i] = reihen.size
            reihen += Reihe(0f, mutableListOf(i))
            continue
        }
        val nah = ueber.filter { s.anfang - stuecke[it].anfang < REIHE_MS }
        if (nah.isNotEmpty()) {
            beitreten(i, nah.max())
            continue
        }
        val oben = ueber.max()
        val links = linksVon(oben) + EINRUECKEN
        if (1f - links < MINDESTBREITE) {
            beitreten(i, oben)
        } else {
            reiheVon[i] = reihen.size
            reihen += Reihe(links, mutableListOf(i))
        }
    }

    return stuecke.indices.map { i ->
        val r = reihen[reiheVon[i]]
        val anzahl = r.glieder.size
        val anteil = (1f - r.links) / anzahl
        val stelle = r.glieder.indexOf(i)
        val links = r.links + stelle * anteil
        val breite = if (stelle == anzahl - 1) anteil else minOf(anteil * UNTERGREIFEN, 1f - links)
        Platz(stuecke[i].e, links, breite, i)
    }
}

/** Eine Aufgabe in der Liste der Aufgaben, mit dem Tag, unter dem sie steht. */
data class Aufgabe(val termin: EventEntity, val tag: LocalDate?, val zeit: Long?, val farbe: Int)

/** Eine Gruppe der Aufgabenliste. [tag] ist `null` fuer die Gruppen ohne eigenes Datum. */
data class Aufgabengruppe(val titel: String, val tag: LocalDate?, val aufgaben: List<Aufgabe>, val erledigt: Boolean = false)

/**
 * Die Aufgaben, wie die Liste oben rechts sie zeigt: Ueberfaellig, Heute,
 * Morgen, dann jeder weitere Tag, danach die ohne Datum und am Ende die
 * erledigten. Eine Serie steht mit ihrem naechsten Vorkommen ab heute.
 */
fun aufgabengruppen(
    termine: List<EventEntity>,
    kalender: List<EventCalendarEntity>,
    ausgeblendet: Set<String>,
    heute: LocalDate,
    zone: ZoneId,
): List<Aufgabengruppe> {
    val farben = kalender.filter { it.deletedAt == null }.associate { it.id to it.colorArgb }
    val aufgaben = termine
        .filter { it.art == Terminart.AUFGABE && it.deletedAt == null && it.calendarId !in ausgeblendet && it.calendarId in farben }
        .map { t ->
            val farbe = t.colorArgb ?: farben.getValue(t.calendarId)
            if (t.undated) return@map Aufgabe(t, null, null, farbe)
            val naechstes = if (t.recurrence == Wiederholung.KEINE) {
                null
            } else {
                Terminregeln.vorkommen(t, heute, heute.plusDays(800), zone).firstOrNull()
            }
            val tag = naechstes?.ersterTag ?: if (t.allDay) Terminregeln.datumAus(t.startAt) else Terminregeln.tagVon(t.startAt, zone)
            val zeit = if (t.allDay) null else naechstes?.beginn ?: t.startAt
            Aufgabe(t, tag, zeit, farbe)
        }
    val offen = aufgaben.filter { !it.termin.done }
    val gruppen = ArrayList<Aufgabengruppe>()
    val reihenfolge = compareBy<Aufgabe>({ it.tag }, { it.zeit ?: Long.MIN_VALUE }, { it.termin.title.lowercase() })
    offen.filter { it.tag != null && it.tag.isBefore(heute) }.sortedWith(reihenfolge).takeIf { it.isNotEmpty() }?.let {
        gruppen += Aufgabengruppe("Überfällig", null, it)
    }
    offen.filter { it.tag != null && !it.tag.isBefore(heute) }.groupBy { it.tag!! }.toSortedMap().forEach { (tag, liste) ->
        gruppen += Aufgabengruppe(Kalenderformat.tagNah(tag, heute), tag, liste.sortedWith(reihenfolge))
    }
    offen.filter { it.tag == null }.sortedBy { it.termin.createdAt }.takeIf { it.isNotEmpty() }?.let {
        gruppen += Aufgabengruppe("Ohne Datum", null, it)
    }
    aufgaben.filter { it.termin.done }.sortedByDescending { it.termin.updatedAt }.takeIf { it.isNotEmpty() }?.let {
        gruppen += Aufgabengruppe("Erledigt", null, it, erledigt = true)
    }
    return gruppen
}

/** Wie eine Erinnerung im Editor und in der Ansicht heisst. */
fun erinnerungstext(minuten: Int, ganztaegig: Boolean): String {
    if (ganztaegig) {
        // Minuten vor Mitternacht des Tages; negativ heisst nach Mitternacht.
        val tageVorher = Math.floorDiv(minuten + 1439, 1440)
        val uhr = tageVorher * 1440 - minuten
        val uhrzeit = "%d:%02d".format(uhr / 60, uhr % 60)
        return when (tageVorher) {
            0 -> "Am selben Tag um $uhrzeit"
            1 -> "Am Tag vorher um $uhrzeit"
            7 -> "Eine Woche vorher um $uhrzeit"
            else -> "$tageVorher Tage vorher um $uhrzeit"
        }
    }
    return when {
        minuten == 0 -> "Zum Zeitpunkt des Termins"
        minuten % 10080 == 0 -> mehrzahl(minuten / 10080, "Woche", "Wochen") + " vorher"
        minuten % 1440 == 0 -> mehrzahl(minuten / 1440, "Tag", "Tage") + " vorher"
        minuten % 60 == 0 -> mehrzahl(minuten / 60, "Stunde", "Stunden") + " vorher"
        else -> mehrzahl(minuten, "Minute", "Minuten") + " vorher"
    }
}

private fun mehrzahl(n: Int, eins: String, viele: String) = if (n == 1) "1 $eins" else "$n $viele"

/** Die Erinnerungen, die der Editor zur Wahl stellt. */
fun erinnerungsvorschlaege(ganztaegig: Boolean): List<Int> =
    if (ganztaegig) listOf(-540, 900, 2340, 9540) else listOf(0, 5, 10, 15, 30, 60, 1440)

/** Wie eine Wiederholung heisst. */
fun wiederholungstext(w: Wiederholung): String = when (w) {
    Wiederholung.KEINE -> "Einmalig"
    Wiederholung.TAEGLICH -> "Jeden Tag"
    Wiederholung.WERKTAGS -> "Jeden Werktag (Mo bis Fr)"
    Wiederholung.WOECHENTLICH -> "Jede Woche"
    Wiederholung.MONATLICH -> "Jeden Monat"
    Wiederholung.JAEHRLICH -> "Jedes Jahr"
}

/**
 * Eine Serie in Worten, etwa „Alle 2 Wochen am Mo, Mi“, „Jeden Tag · 5 Mal“
 * oder „Jeden Monat bis 30. Nov. 2026“.
 */
fun wiederholungstext(s: Serie): String {
    if (!s.benutzerdefiniert) return wiederholungstext(s.einheit)
    val kern = if (s.alle <= 1) wiederholungstext(s.einheit) else "Alle ${s.alle} " + einheitText(s.einheit, s.alle)
    val tage = when (s.tage.size) {
        0 -> ""
        1 -> " am " + s.tage.first().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.GERMANY)
        else -> " am " + s.tage.sorted().joinToString(", ") { wochentagKurz(it) }
    }
    val ende = s.bis?.let { " bis " + Kalenderformat.datum(it) } ?: s.anzahl?.let { " · $it Mal" } ?: ""
    return kern + tage + ende
}

/** „Tag“ oder „Tage“, je nach Anzahl. */
fun einheitText(w: Wiederholung, anzahl: Int): String = when (w) {
    Wiederholung.TAEGLICH, Wiederholung.WERKTAGS, Wiederholung.KEINE -> if (anzahl == 1) "Tag" else "Tage"
    Wiederholung.WOECHENTLICH -> if (anzahl == 1) "Woche" else "Wochen"
    Wiederholung.MONATLICH -> if (anzahl == 1) "Monat" else "Monate"
    Wiederholung.JAEHRLICH -> if (anzahl == 1) "Jahr" else "Jahre"
}

/** „Mo“, „Di“ und so weiter. */
fun wochentagKurz(d: java.time.DayOfWeek): String =
    d.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.GERMANY).trimEnd('.')

fun artText(art: Terminart): String = when (art) {
    Terminart.TERMIN -> "Termin"
    Terminart.AUFGABE -> "Aufgabe"
    Terminart.GEBURTSTAG -> "Geburtstag"
}

/** Treffer der Suche: Titel, Ort oder Beschreibung enthalten alle Woerter. */
fun passt(t: EventEntity, suche: String): Boolean {
    val woerter = suche.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (woerter.isEmpty()) return false
    val text = (t.title + " " + t.location + " " + t.description).lowercase()
    return woerter.all { it in text }
}
