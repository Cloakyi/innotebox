package de.notizen.core.data.repository

import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.SearchDao
import de.notizen.core.data.db.relation.NoteWithRelations
import de.notizen.core.data.ordner.nachkommen
import de.notizen.core.data.search.Suchanfrage
import de.notizen.core.data.search.Suchausdruck
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die Markierungen, mit denen `snippet()` die Fundstellen einfasst: STX und
 * ETX, zwei Steuerzeichen, die in keinem Notiztext vorkommen. Als Escape
 * geschrieben, nicht als rohes Zeichen: Ein rohes Steuerzeichen im Quelltext
 * geht beim ersten Werkzeug verloren, das die Datei neu schreibt.
 */
const val TREFFER_AUF = '\u0002'
const val TREFFER_ZU = '\u0003'

/**
 * Eine gefundene Notiz samt Textausschnitt.
 *
 * Der Ausschnitt ist `null`, wenn ohne Suchtext nur gefiltert wurde. Dann gibt
 * es keine Fundstelle, die man zeigen könnte, und ein erfundener Ausschnitt
 * wäre schlimmer als keiner. Für Notizen aus dem Papierkorb gibt es ebenfalls
 * keinen: Sie stehen nicht im Index, und `snippet()` kennt sie nicht.
 */
data class Suchtreffer(
    val notiz: NoteWithRelations,
    val ausschnitt: String?,
) {
    val imPapierkorb: Boolean get() = notiz.note.deletedAt != null
}

/**
 * Die Volltextsuche über alle Stufen hinweg.
 *
 * Text sucht SQLite, gefiltert wird in Kotlin. Das ist eine bewusste
 * Entscheidung und keine Abkürzung: die sechs Filter aus Abschnitt 6 in eine
 * einzige Abfrage zu gießen, hieße dynamisches SQL mit `IN`-Listen, die leer
 * sein können, Room erzeugt daraus `IN ()`, und das ist in SQLite ein
 * Syntaxfehler. Der Ausweg wären Wächter-Parameter und Ersatzwerte, also genau
 * die Sorte Abfrage, die man später nicht mehr gefahrlos anfasst.
 *
 * Die Datenmenge trägt das: es sind die Notizen eines einzelnen Menschen, und
 * die teure Arbeit, das Finden im Volltext, macht weiterhin der Index. Sollte
 * es je um Größenordnungen mehr werden, wandern die Filter in die `WHERE`,
 * ohne dass sich die Schnittstelle ändert.
 *
 * Reihenfolge: finden, dann filtern, dann anreichern. Genau in dieser
 * Ordnung, weil die zweite Ranking-Quelle aus Abschnitt 6 später zwischen den
 * ersten beiden Schritt einsetzt.
 *
 * Der Papierkorb ist ein zweiter Weg. Der Index kennt nur
 * lebende Notizen (`trash` nimmt sie heraus, sonst fände man Weggeworfenes
 * ohne es zu wollen). Ist der Filter an, werden die weggeworfenen Notizen
 * über ihren Text gesucht, so wie es die alte Suchleiste des Papierkorbs tat,
 * und hinten angehängt. Sie stehen ohnehin nicht in der Reihenfolge des
 * Index, und im Papierkorb zählt das Wegwerfdatum.
 */
@Singleton
class SucheRepository @Inject constructor(
    private val searchDao: SearchDao,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val clock: Clock,
) {

    fun suche(anfrage: Suchanfrage): Flow<List<Suchtreffer>> {
        val ausdruck = Suchausdruck.bauen(anfrage.text)
        val woerter = Suchausdruck.woerter(anfrage.text)

        // Ohne Suchtext gibt es keine Treffer, sondern alle Notizen -- die
        // Filter allein sind eine gueltige Anfrage.
        val lebende = if (ausdruck == null) {
            noteDao.observeAlle()
        } else {
            searchDao.beobachteTreffer(ausdruck)
        }

        val weggeworfene = if (anfrage.mitPapierkorb) {
            noteDao.observeTrash().map { alle -> alle.filter { enthaeltAlle(it, woerter) } }
        } else {
            flowOf(emptyList())
        }

        return combine(lebende, weggeworfene) { gefunden, papierkorb ->
            // Die Unterordner der gewaehlten Ordner, einmal je Ergebnis: Der
            // Baum aendert sich selten, die Trefferliste oft.
            val ordner = erweitert(anfrage.ordnerIds)
            val passend = (gefunden + papierkorb).filter { passt(it, anfrage, ordner) }
            if (ausdruck == null) {
                passend.map { Suchtreffer(it, null) }
            } else {
                val ausschnitte = searchDao.searchSnippets(
                    ausdruck = ausdruck,
                    vorne = TREFFER_AUF.toString(),
                    hinten = TREFFER_ZU.toString(),
                ).associate { it.noteId to it.snippet }
                passend.map { Suchtreffer(it, ausschnitte[it.note.id]) }
            }
        }
    }

    /**
     * Die gewaehlten Ordner samt allen Unterordnern, oder `null`, wenn kein
     * Ordner gewaehlt ist. Ein gewaehlter Ordner schliesst ein, was unter ihm
     * liegt: Wer „Arbeit" waehlt, meint auch „Arbeit › Projekte".
     */
    private suspend fun erweitert(ordnerIds: Set<String>): Set<String>? {
        if (ordnerIds.isEmpty()) return null
        val alle = folderDao.getAlleLebenden()
        return ordnerIds.flatMapTo(HashSet(ordnerIds)) { nachkommen(alle, it) }
    }

    /**
     * Ob eine Notiz durch alle gesetzten Filter kommt.
     *
     * Innerhalb einer Kategorie ODER, zwischen den Kategorien UND. Eine leere
     * Menge schränkt nicht ein.
     */
    private fun passt(notiz: NoteWithRelations, anfrage: Suchanfrage, ordner: Set<String>?): Boolean {
        val n = notiz.note

        if (anfrage.stufen.isNotEmpty() && n.stage !in anfrage.stufen) return false
        if (anfrage.farben.isNotEmpty() && n.colorId !in anfrage.farben) return false
        if (anfrage.typen.isNotEmpty() && n.type !in anfrage.typen) return false
        if (anfrage.nurFavoriten && !n.isFavorite) return false
        if (ordner != null && n.folderId !in ordner) return false

        if (anfrage.tagIds.isNotEmpty()) {
            val eigene = notiz.tags.map { it.id }.toSet()
            if (anfrage.tagIds.none { it in eigene }) return false
        }

        val grenze = anfrage.zeitraum.grenze(clock.now())
        if (grenze != null && n.updatedAt < grenze) return false

        return true
    }

    /**
     * Der zweite Weg fuer den Papierkorb: jedes gesuchte Wort muss in Titel,
     * Text oder einem Listeneintrag vorkommen, ohne Ruecksicht auf Gross- und
     * Kleinschreibung. Ohne Suchtext kommt alles durch.
     */
    private fun enthaeltAlle(notiz: NoteWithRelations, woerter: List<String>): Boolean {
        if (woerter.isEmpty()) return true
        val text = buildString {
            append(notiz.note.title).append('\n')
            append(notiz.note.body).append('\n')
            notiz.items.forEach { append(it.text).append('\n') }
        }
        return woerter.all { text.contains(it, ignoreCase = true) }
    }
}
