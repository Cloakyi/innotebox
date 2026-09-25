package de.notizen.core.sync.sicherung

import de.notizen.core.data.aussen.endungFuer
import de.notizen.core.sync.Anhangdokument
import de.notizen.core.data.model.Stage
import de.notizen.core.sync.Notizdokument
import de.notizen.core.sync.SCHEMA_VERSION
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Die Fassung des Sicherungsformats, die dieser Client schreibt.
 *
 * Nicht dasselbe wie [SCHEMA_VERSION]. Das Schema beschreibt, wie eine Notiz
 * aussieht; diese Zahl beschreibt, wie die Sicherungsdatei drumherum gebaut ist.
 * Die beiden koennen sich unabhaengig voneinander aendern.
 *
 * Fassung 4 (2026-09-19): Notiz- und Ordnerdokumente tragen die
 * Felder aus Schema 5 (SYNC.md 13). Am Aufbau der Datei aendert sich nichts;
 * die Fassung steigt trotzdem, damit eine aeltere App die Datei als neuer
 * erkennt und nicht halb versteht. Fassung 1 bis 3 lesen sich weiter, die
 * fehlenden Felder gelten als null bzw. ORDNER.
 */
const val SICHERUNG_VERSION = 4

/** Die Endung. Steht hier einmal, damit sie nicht an drei Stellen getippt wird. */
const val ENDUNG = "notesbak"

/** Die Eintraege in der Datei. */
const val EINTRAG_KOPF = "manifest.json"
const val EINTRAG_NOTIZEN = "notes.json"
const val EINTRAG_TAGS = "tags.json"
const val EINTRAG_ORDNER = "folders.json"

/** Seit Fassung 3: die Zaehlerstaende je Entitaet und die Grabsteine (SYNC.md 10). */
const val EINTRAG_ZAEHLER = "revs.json"
const val EINTRAG_GELOESCHT = "deleted.json"
const val ORDNER_ANHAENGE = "attachments/"

/**
 * Was oben in der Datei steht.
 *
 * Zuerst geschrieben, nicht zuletzt. Wer die Datei mit einem Werkzeug
 * oeffnet, das sie von vorn nach hinten liest, soll erfahren, wonach sie gebaut
 * ist, bevor er anfaengt, Notizen zu lesen, die er womoeglich falsch versteht.
 *
 * Die Zahlen sind der Bestand der Datenbank zum Zeitpunkt der Sicherung. Wie
 * viele Anhangsdateien tatsaechlich mitgingen, steht hier bewusst NICHT: Das
 * weiss man erst, wenn die letzte geschrieben ist, und eine Zahl im Kopf, die
 * nachtraeglich stimmen muesste, waere entweder gelogen oder ein Grund, den
 * Kopf ans Ende zu legen. Wer die Dateien zaehlen will, zaehlt die Eintraege
 * unter `attachments/`.
 */
@Serializable
data class Sicherungskopf(
    val formatVersion: Int = SICHERUNG_VERSION,
    val schemaVersion: Int = SCHEMA_VERSION,
    val erzeugtAm: Long,
    val erzeugtVon: String,
    val notizen: Int,
    val tags: Int,

    /**
     * Seit Fassung 2. Eine aeltere Datei hat das Feld nicht und liest sich
     * trotzdem: Sie stammt aus einer Zeit ohne Ordner, und null Ordner ist
     * dann die richtige Antwort.
     */
    val ordner: Int = 0,

    /** Seit Fassung 3: wie viele Grabsteine (DELETED) mitgehen. */
    val geloescht: Int = 0,
)

/**
 * Der Zaehlerstand einer Entitaet in der Sicherung (Fassung 3).
 *
 * Damit gewinnt ein wiederhergestellter Stand gegen den Spiegel statt
 * umgekehrt: Beim Einlesen wird `baseRev` gesetzt, und der naechste Abgleich
 * schreibt `rev + 1` (SYNC.md 10).
 */
@Serializable
data class Zaehlerstand(
    val type: String,
    val id: String,
    val rev: Long,
)

/** Ein Grabstein in der Sicherung (Fassung 3). */
@Serializable
data class Grabsteineintrag(
    val type: String,
    val id: String,
    val deletedAt: Long,
    val rev: Long,
)

/**
 * Eine Notiz in der Sicherung.
 *
 * Das Notizdokument ist dasselbe wie in Google Drive, Feld fuer Feld. Es
 * ist bereits der Vertrag mit dem spaeteren Rechner-Client, es ist geprueft, und
 * es hat eine Abbildung in beide Richtungen. Ein zweites Format daneben waere
 * eine zweite Stelle, an der dieselbe Wahrheit steht.
 *
 * Drumherum liegt, was der Abgleich absichtlich weglaesst. Eine Sicherung
 * hat einen anderen Zweck als ein Abgleich: Sie stellt *dieses* Geraet wieder
 * her, nicht ein zweites daneben. Deshalb gehoert hier das Geraetelokale dazu,
 * das drueben niemanden etwas angeht:
 *
 *  * `lastOpenedAt` steuert die automatische Archivierung. Ginge er verloren,
 *    saehe nach dem Wiederherstellen jede Notiz aus, als sei sie seit ihrer
 *    Erstellung nicht mehr angesehen worden, und die naechste Archivierung
 *    raeumte den halben Workspace weg.
 *  * `syncEnabled` ist die Notiz, die der Nutzer ausdruecklich NICHT in der
 *    Cloud haben will. Sie beim Wiederherstellen stillschweigend wieder
 *    einzuschalten waere die eine Sorte Fehler, die man nicht bemerkt.
 */
@Serializable
data class Sicherungsnotiz(
    val notiz: Notizdokument,
    val lastOpenedAt: Long,
    val syncEnabled: Boolean = true,
)

/** Wie mit einer Notiz aus der Datei verfahren wird. */
enum class Uebernahme { NEU, ERSETZEN, BEHALTEN }

/**
 * Ob eine Notiz aus der Datei die hiesige ersetzt.
 *
 * Der neuere Stand gewinnt, und geloescht wird nie. Das ist dieselbe Regel,
 * nach der auch der Abgleich entscheidet, und sie ist die einzige, die in beiden
 * Faellen richtig ist: Nach einem Datenverlust ist die Datenbank leer, da
 * gewinnt ohnehin alles aus der Datei. Wird eine Sicherung dagegen in einen
 * bestehenden Bestand gelesen, darf ein alter Stand aus der Datei nicht das
 * ueberschreiben, was seither geschrieben wurde.
 *
 * Gleichstand heisst behalten. Zwei Fassungen mit derselben Zeit sind
 * dieselbe Fassung, und Schreiben waere Arbeit ohne Wirkung.
 */
fun entscheiden(lokalUpdatedAt: Long?, ausDatei: Long): Uebernahme = when {
    lokalUpdatedAt == null -> Uebernahme.NEU
    ausDatei > lokalUpdatedAt -> Uebernahme.ERSETZEN
    else -> Uebernahme.BEHALTEN
}

/**
 * Der vorgeschlagene Dateiname.
 *
 * Datum statt Uhrzeit: Wer zweimal am Tag sichert, bekommt vom Dateiwaehler ein
 * `(1)` angehaengt, und das ist verstaendlicher als eine Minutenangabe im Namen.
 */
fun dateiname(zeitpunkt: Long): String {
    val tag = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(java.util.Date(zeitpunkt))
    return "innotebox-$tag.$ENDUNG"
}

/**
 * Der Name, unter dem eine Anhangsdatei aus der Sicherung abgelegt wird.
 *
 * Der Rueckweg aus einer fremden Datei muss geprueft werden. Ein Eintrag in
 * einem ZIP-Archiv darf alles heissen, auch `../../shared_prefs/etwas.xml`. Wer
 * so einen Namen ungeprueft an einen Dateipfad haengt, schreibt dorthin, wohin
 * das Archiv zeigt, und nicht dorthin, wohin er wollte.
 *
 * Erlaubt ist deshalb genau eine Form: ein Name direkt unter `attachments/`,
 * ohne weitere Ordner und ohne `..`. Alles andere gibt `null` und wird
 * uebergangen.
 */
fun sichererAnhangname(eintrag: String): String? {
    if (!eintrag.startsWith(ORDNER_ANHAENGE)) return null

    val name = eintrag.removePrefix(ORDNER_ANHAENGE)
    if (name.isEmpty()) return null
    if (name.contains('/') || name.contains('\\')) return null
    if (name == "." || name == "..") return null
    return name
}

/** Die Kennung des Anhangs steckt im Dateinamen, die Endung sagt nur das Format. */
fun anhangIdAus(name: String): String = name.substringBeforeLast('.')

/**
 * Wo die Datei eines Anhangs in der Sicherung liegt.
 *
 * Der Name ist die Kennung des Anhangs plus die Endung, die zu seinem Format
 * gehoert. Die Kennung, weil die Notiz sie nennt und die Datei so wiederfindbar
 * ist; die Endung, weil `attachments/` sonst ein Ordner voller Dateien waere,
 * die kein Bildbetrachter anfasst.
 */
fun anhangpfad(anhang: Anhangdokument): String =
    ORDNER_ANHAENGE + anhang.id + "." + endungFuer(anhang.mimeType)

/** Eine Notiz aus der Sicherung, neu aufgelegt unter eigener Kennung. */
data class Neuauflage(
    val eintrag: Sicherungsnotiz,
    /** Alte Anhangskennung auf neue. Danach muessen die Dateien umziehen. */
    val anhangKennungen: Map<String, String>,
)

/**
 * Legt eine Notiz aus der Sicherung als NEUE Notiz an.
 *
 * Wozu das gut ist. Eine Notiz, die hier geloescht wurde, unter ihrer alten
 * Kennung wiederzubeleben hiesse, gegen ihren Grabstein anzutreten. Der ist
 * keine Kleinigkeit, sondern die Nachricht an alle Geraete, dass es diese Notiz
 * nicht mehr gibt. Wer sie ueberstimmt, muss sie ueberall zurueckziehen, und in
 * dem Moment, in dem das irgendwo nicht ankommt, loescht das andere Geraet die
 * Notiz wieder. Eine neue Kennung hat gar keinen Grabstein: Es gibt nichts zu
 * streiten, statt den Streit gewinnen zu muessen.
 *
 * Alles bekommt eine neue Kennung, nicht nur die Notiz. Eintraege, Anhaenge,
 * Transkripte und Erinnerungen haengen an ihr, und eine alte Anhangskennung
 * wieder zu benutzen hiesse, den Grabstein des Anhangs gegen sich zu haben.
 * Genau dem soll die neue Kennung ja aus dem Weg gehen.
 *
 * Sie landet im Eingang. Etwas, das zurueckkommt, gehoert dorthin, wo man
 * hinsieht. Der Papierkorb bleibt unberuehrt: Lag die Notiz in der Sicherung im
 * Papierkorb, liegt sie danach wieder dort.
 *
 * `lastOpenedAt` faengt bei [jetzt] an. Die Notiz ist auf diesem Geraet neu, und
 * der alte Wert wuerde sie beim naechsten Aufraeumlauf sofort ins Archiv
 * schieben.
 *
 * [kennung] kommt von aussen, damit sich das Ergebnis pruefen laesst.
 */
fun neuAuflegen(eintrag: Sicherungsnotiz, jetzt: Long, kennung: () -> String): Neuauflage {
    val doku = eintrag.notiz
    val anhangKennungen = doku.attachments.associate { it.id to kennung() }

    val neu = doku.copy(
        id = kennung(),
        stage = Stage.INBOX,
        stageChangedAt = jetzt,
        attachments = doku.attachments.map { it.copy(id = anhangKennungen.getValue(it.id)) },
        backgroundAttachmentId = doku.backgroundAttachmentId?.let { anhangKennungen[it] },
        items = doku.items.map { it.copy(id = kennung()) },
        transcripts = doku.transcripts.map { it.copy(id = kennung()) },
        reminders = doku.reminders.map { it.copy(id = kennung()) },
    )

    return Neuauflage(eintrag.copy(notiz = neu, lastOpenedAt = jetzt), anhangKennungen)
}
