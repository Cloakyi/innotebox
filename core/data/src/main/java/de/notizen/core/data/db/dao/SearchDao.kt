package de.notizen.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.notizen.core.data.db.entity.NoteFtsEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import kotlinx.coroutines.flow.Flow

/**
 * Ein Treffer mit Kontext-Ausschnitt.
 *
 * Der Ausschnitt kommt von SQLites `snippet()` (FTS4). Die Fundstellen sind
 * darin mit den uebergebenen Markierungen eingefasst; die UI ersetzt sie durch
 * Formatierung. `highlight()` waere bequemer, ist aber FTS5-only und steht uns
 * mit Room nicht zur Verfuegung.
 */
data class SearchSnippet(
    val noteId: String,
    val snippet: String,
)

/**
 * Volltextsuche.
 *
 * Der Index ist eine eigenstaendige FTS4-Tabelle, die das Repository bei jeder
 * Schreiboperation mitpflegt -- siehe SYNC.md 14.14. Er wird NIE synchronisiert.
 *
 * Zum Suchbegriff: FTS4 erwartet MATCH-Syntax. Praefix-Suche schreibt sich
 * `foo*`. Das Zusammenbauen des Ausdrucks macht das Repository, nicht die UI.
 */
@Dao
interface SearchDao {

    // ------------------------------------------------------------ Indexpflege

    @Insert
    suspend fun insertIndex(entry: NoteFtsEntity)

    @Query("DELETE FROM note_fts WHERE noteId = :noteId")
    suspend fun deleteIndex(noteId: String)

    @Query("DELETE FROM note_fts")
    suspend fun clearIndex()

    /** Index einer Notiz neu setzen. Loeschen und Einfuegen in einem Rutsch. */
    @Transaction
    suspend fun reindex(entry: NoteFtsEntity) {
        deleteIndex(entry.noteId)
        insertIndex(entry)
    }

    // ---------------------------------------------------------------- suchen

    @Transaction
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_fts ON note_fts.noteId = notes.id
        WHERE note_fts MATCH :ausdruck AND notes.deletedAt IS NULL
        ORDER BY notes.isFavorite DESC, notes.updatedAt DESC
        """,
    )
    suspend fun search(ausdruck: String): List<NoteWithRelations>

    /**
     * Wie [search], aber als Fluss.
     *
     * Damit aktualisiert sich eine offene Trefferliste, wenn sich eine der
     * gezeigten Notizen aendert -- Room haengt die Benachrichtigung an die
     * beteiligten Tabellen, `note_fts` eingeschlossen.
     *
     * Die Reihenfolge ist bewusst dieselbe wie in [search] und ausdruecklich
     * KEINE Relevanzsortierung. FTS4 liefert keine, und eine erfundene waere
     * schlechter als eine ehrliche: Favoriten zuerst, dann das zuletzt
     * Geaenderte. Hier dockt spaeter die zweite Ranking-Quelle an.
     */
    @Transaction
    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_fts ON note_fts.noteId = notes.id
        WHERE note_fts MATCH :ausdruck AND notes.deletedAt IS NULL
        ORDER BY notes.isFavorite DESC, notes.updatedAt DESC
        """,
    )
    fun beobachteTreffer(ausdruck: String): Flow<List<NoteWithRelations>>

    /**
     * Wie [search], aber mit Kontext-Ausschnitt statt der ganzen Notiz.
     *
     * Die -1 bei `snippet()` heisst "beste passende Spalte automatisch waehlen",
     * die 32 begrenzt den Ausschnitt auf ungefaehr 32 Token.
     */
    @Query(
        """
        SELECT note_fts.noteId AS noteId,
               snippet(note_fts, :vorne, :hinten, :auslassung, -1, 32) AS snippet
        FROM note_fts
        JOIN notes ON notes.id = note_fts.noteId
        WHERE note_fts MATCH :ausdruck AND notes.deletedAt IS NULL
        """,
    )
    suspend fun searchSnippets(
        ausdruck: String,
        vorne: String = "",
        hinten: String = "",
        auslassung: String = "…",
    ): List<SearchSnippet>
}
