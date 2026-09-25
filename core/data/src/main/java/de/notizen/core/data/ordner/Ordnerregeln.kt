package de.notizen.core.data.ordner

import de.notizen.core.data.db.entity.FolderEntity
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.Ordnersortierung

/**
 * Die Rechenregeln des Ordnerbaums, ohne Datenbank und ohne Oberflaeche.
 *
 * Hier steht alles, was schiefgehen kann, wenn ein Baum aus Zeilen mit
 * Elternverweisen gebaut wird: Ordner, die sich selbst enthalten, Ordner ohne
 * Eltern, und Zaehlungen, die die Unterordner vergessen. Reine Funktionen,
 * damit jede dieser Regeln einzeln pruefbar ist.
 *
 * **Der Baum ist nie zu trauen.** Die Zeilen kommen zum Teil vom anderen
 * Geraet, und dort kann jemand einen Ordner verschoben haben, waehrend hier
 * sein Elternteil geloescht wurde. Jede Funktion hier muss mit einem Kreis und
 * mit fehlenden Eltern fertigwerden, statt sich aufzuhaengen.
 */

/** Ein Ordner mit seiner Tiefe im Baum. Die Wurzel hat die Tiefe null. */
data class Ordnerzeile(
    val ordner: FolderEntity,
    val tiefe: Int,
)

/**
 * Die Reihenfolge unter Geschwistern, nach [sortierung] (Phase 14e).
 *
 * Bei der eigenen Reihenfolge erst `sortIndex`, dann der Name. Ohne den Namen
 * als zweites Merkmal springen gleich sortierte Ordner bei jedem Neuladen
 * umeinander; ohne die Kennung als drittes tun es zwei gleichnamige. Nach
 * Datum stehen die zuletzt angelegten oben, wie bei den Notizen.
 */
fun ordnerReihenfolge(
    ordner: List<FolderEntity>,
    sortierung: Ordnersortierung = Ordnersortierung.EIGENE,
): List<FolderEntity> = when (sortierung) {
    Ordnersortierung.EIGENE ->
        ordner.sortedWith(compareBy({ it.sortIndex }, { it.name.lowercase() }, { it.id }))
    Ordnersortierung.NAME ->
        ordner.sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
    Ordnersortierung.DATUM ->
        ordner.sortedWith(compareByDescending<FolderEntity> { it.createdAt }.thenBy { it.id })
}

/**
 * Die Kinder eines Ordners, oder die oberste Ebene fuer `null`.
 *
 * **Waisen haengen an der Wurzel.** Ein Ordner, dessen Elternteil es nicht
 * mehr gibt, waere sonst unsichtbar und mit ihm alles, was darin liegt. Er ist
 * dann zwar nicht dort, wo er einmal war, aber er ist da.
 */
fun kinder(
    alle: List<FolderEntity>,
    elternId: String?,
    sortierung: Ordnersortierung = Ordnersortierung.EIGENE,
): List<FolderEntity> {
    val vorhanden = alle.mapTo(HashSet()) { it.id }
    return ordnerReihenfolge(
        alle.filter { ordner ->
            val eltern = ordner.parentId?.takeIf { it in vorhanden }
            eltern == elternId
        },
        sortierung,
    )
}

/**
 * Alle Ordner unterhalb von [id], beliebig tief.
 *
 * Der Merker der besuchten Kennungen ist kein Beiwerk: Zeigen zwei Ordner
 * ueber Kreuz aufeinander, laeuft die Suche ohne ihn ewig.
 */
fun nachkommen(alle: List<FolderEntity>, id: String): Set<String> {
    val nachEltern = alle.groupBy { it.parentId }
    val gefunden = LinkedHashSet<String>()
    val offen = ArrayDeque<String>()
    offen += id

    while (offen.isNotEmpty()) {
        val jetzt = offen.removeFirst()
        for (kind in nachEltern[jetzt].orEmpty()) {
            if (gefunden.add(kind.id)) offen += kind.id
        }
    }
    return gefunden
}

/**
 * Der Weg von der Wurzel bis zu [id], fuer die Zeile ueber dem Inhalt.
 *
 * Leer, wenn es den Ordner nicht gibt. Bricht ab, sobald ein Ordner ein
 * zweites Mal auftaucht -- dann liegt ein Kreis vor, und ein halber Pfad ist
 * immer noch besser als eine haengende App.
 */
fun pfad(alle: List<FolderEntity>, id: String?): List<FolderEntity> {
    if (id == null) return emptyList()
    val nachId = alle.associateBy { it.id }

    val rueckwaerts = ArrayList<FolderEntity>()
    val gesehen = HashSet<String>()
    var jetzt = nachId[id]

    while (jetzt != null && gesehen.add(jetzt.id)) {
        rueckwaerts += jetzt
        jetzt = jetzt.parentId?.let { nachId[it] }
    }
    return rueckwaerts.reversed()
}

/**
 * Der ganze Baum als flache Liste, jede Zeile mit ihrer Tiefe.
 *
 * Fuer die Ordnerauswahl, die alles auf einmal zeigt. Tiefensuche, damit ein
 * Ordner unmittelbar unter seinem Elternteil steht und nicht am Ende.
 */
fun baum(
    alle: List<FolderEntity>,
    sortierung: Ordnersortierung = Ordnersortierung.EIGENE,
): List<Ordnerzeile> {
    val zeilen = ArrayList<Ordnerzeile>(alle.size)
    val gesehen = HashSet<String>()

    fun absteigen(elternId: String?, tiefe: Int) {
        for (ordner in kinder(alle, elternId, sortierung)) {
            // Derselbe Schutz wie oben: Ein Kreis wuerde hier sonst endlos
            // tiefer werden, bis der Stapel reisst.
            if (!gesehen.add(ordner.id)) continue
            zeilen += Ordnerzeile(ordner, tiefe)
            absteigen(ordner.id, tiefe + 1)
        }
    }

    absteigen(null, 0)
    return zeilen
}

/**
 * Welche Zeilen des Baums gerade zu sehen sind.
 *
 * Fuer den aufklappbaren Baum in der Seitenspalte. Sichtbar ist eine Zeile
 * genau dann, wenn jeder ihrer Vorfahren aufgeklappt ist.
 *
 * **Baut darauf, dass [baum] in Tiefensuche liefert**, also jeder Ast
 * unmittelbar unter seinem Elternteil steht. Dann genuegt ein Durchgang: Trifft
 * man auf einen zugeklappten Ordner, faellt alles weg, was tiefer liegt, bis
 * wieder eine Zeile auf seiner Ebene oder darueber kommt. Eine Suche nach den
 * Vorfahren je Zeile waere dieselbe Antwort mit mehr Arbeit.
 */
fun sichtbar(zeilen: List<Ordnerzeile>, aufgeklappt: Set<String>): List<Ordnerzeile> {
    val ergebnis = ArrayList<Ordnerzeile>(zeilen.size)
    var zugeklapptAb = -1

    for (zeile in zeilen) {
        if (zugeklapptAb >= 0 && zeile.tiefe > zugeklapptAb) continue

        zugeklapptAb = -1
        ergebnis += zeile
        if (zeile.ordner.id !in aufgeklappt) zugeklapptAb = zeile.tiefe
    }
    return ergebnis
}

/**
 * Die Kennungen aller Ordner, die ueberhaupt Kinder haben.
 *
 * Nur sie bekommen in der Seitenspalte ein Zeichen zum Aufklappen. Ein Pfeil an
 * einem leeren Ordner waere ein Versprechen auf etwas, das nicht kommt.
 */
fun mitKindern(alle: List<FolderEntity>): Set<String> {
    val vorhanden = alle.mapTo(HashSet()) { it.id }
    return alle.mapNotNullTo(HashSet()) { it.parentId?.takeIf { eltern -> eltern in vorhanden } }
}

/**
 * Ob [id] nach [zielId] verschoben werden darf. `null` ist die oberste Ebene.
 *
 * Drei Faelle sind verboten. Zwei davon zerschneiden den Baum: ein Ordner in
 * sich selbst, und ein Ordner in einen seiner eigenen Unterordner. Danach
 * haengt der ganze Ast an nichts mehr und ist nirgends mehr zu erreichen.
 * Der dritte ist der Wechsel des Bereichs (SYNC.md 13): Ein Archivordner
 * kommt nie in den normalen Baum und umgekehrt, sonst laege ein Ordner in
 * einem Baum, in dem seine Notizen nicht zu sehen sind. Ein Ziel, das es
 * nicht gibt, gilt als fremder Bereich.
 */
fun darfHinein(alle: List<FolderEntity>, id: String, zielId: String?): Boolean {
    if (zielId == null) return true
    if (zielId == id) return false
    val eigener = alle.firstOrNull { it.id == id }?.bereich
    val ziel = alle.firstOrNull { it.id == zielId }?.bereich
    if (eigener != null && ziel != eigener) return false
    return zielId !in nachkommen(alle, id)
}

/** Die Ordner eines Bereichs. Die Baumfunktionen oben rechnen je Bereich, nie ueber beide. */
fun imBereich(alle: List<FolderEntity>, bereich: Bereich): List<FolderEntity> =
    alle.filter { it.bereich == bereich }

/**
 * Wohin eine Notiz aus dem Archiv des Ordnermodus zurueckkann (Phase 14b).
 *
 * Der Vorschlag ist immer nur ein Vorschlag; die freie Wahl steht daneben.
 */
sealed interface Rueckkehr {
    /** Sie kam aus dem Hauptordner. */
    data object Hauptordner : Rueckkehr

    /** Der Herkunftsordner lebt. [pfad] von der Wurzel bis zu ihm, fuer die Anzeige. */
    data class Vorhanden(val ordner: FolderEntity, val pfad: List<FolderEntity>) : Rueckkehr

    /**
     * Der Herkunftsordner liegt im Papierkorb oder ist weg.
     *
     * [pfad] nennt den fehlenden Weg, soweit er noch bekannt ist (leer, wenn
     * die Zeile endgueltig entfernt wurde). [wiederherstellbar] sagt, ob
     * „Ordner neu anlegen" ueberhaupt geht: Nur eine weich geloeschte Kette
     * laesst sich zurueckholen. [naechster] ist der erste lebende Vorfahr,
     * `null` heisst Hauptordner.
     */
    data class Fehlt(
        val pfad: List<FolderEntity>,
        val wiederherstellbar: Boolean,
        val naechster: FolderEntity?,
    ) : Rueckkehr
}

/**
 * Rechnet die [Rueckkehr] fuer einen Herkunftsordner aus, ueber ALLE Zeilen
 * einschliesslich der geloeschten; sonst liesse sich der fehlende Pfad nicht
 * nennen.
 */
fun rueckkehr(alleInklusiveGeloescht: List<FolderEntity>, herkunftId: String?): Rueckkehr {
    if (herkunftId == null) return Rueckkehr.Hauptordner
    val nachId = alleInklusiveGeloescht.associateBy { it.id }
    val herkunft = nachId[herkunftId]
        ?: return Rueckkehr.Fehlt(pfad = emptyList(), wiederherstellbar = false, naechster = null)

    val pfad = pfad(alleInklusiveGeloescht, herkunftId)
    if (herkunft.deletedAt == null) return Rueckkehr.Vorhanden(herkunft, pfad)

    // Der erste lebende Vorfahr, von unten nach oben gesucht. Der Pfad ist
    // von der Wurzel her gebaut, deshalb rueckwaerts.
    val naechster = pfad.dropLast(1).lastOrNull { it.deletedAt == null }
    return Rueckkehr.Fehlt(pfad = pfad, wiederherstellbar = true, naechster = naechster)
}

/**
 * Die Ordner, die als Ziel fuer [id] in Frage kommen.
 *
 * Was nicht gehen kann, steht gar nicht erst zur Wahl. Ein ausgegrauter
 * Eintrag waere die zweitbeste Loesung: Er erklaert zwar, dass es nicht geht,
 * aber niemand fragt sich beim Verschieben, warum ein Ordner in sich selbst
 * nicht funktioniert.
 */
fun moeglicheZiele(
    alle: List<FolderEntity>,
    id: String,
    sortierung: Ordnersortierung = Ordnersortierung.EIGENE,
): List<Ordnerzeile> =
    baum(alle, sortierung).filter { darfHinein(alle, id, it.ordner.id) }

/**
 * Die Anzahl der Notizen je Ordner, einschliesslich aller Unterordner.
 *
 * [direkt] zaehlt nur, was unmittelbar in einem Ordner liegt. Ein Ordner, der
 * selbst nichts enthaelt, aber drei volle Unterordner hat, stuende damit mit
 * einer Null da -- und sahe leer aus, obwohl er es nicht ist.
 */
fun gesamtzahlen(alle: List<FolderEntity>, direkt: Map<String, Int>): Map<String, Int> {
    val ergebnis = HashMap<String, Int>(alle.size)
    for (ordner in alle) {
        val eigene = direkt[ordner.id] ?: 0
        val unten = nachkommen(alle, ordner.id).sumOf { direkt[it] ?: 0 }
        ergebnis[ordner.id] = eigene + unten
    }
    return ergebnis
}
