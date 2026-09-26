package de.notizen.app.archiv

import de.notizen.app.ai.KiZustand
import de.notizen.app.ai.TitelKi
import de.notizen.app.ai.fallbackTitel
import de.notizen.core.data.db.entity.TagEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gibt Notizen einen Titel und einen Tag, bevor sie ins Archiv wandern.
 *
 * Warum überhaupt: Im Archiv wird gesucht, und gesucht wird über Titel. Eine
 * Notiz, die unbetitelt automatisch wegwandert, ist praktisch verloren, sie
 * steht dann in einer Liste aus lauter „(ohne Titel)".
 *
 * WARUM KEIN STRUCTURED OUTPUT: Naheliegend wäre „Structured Output" mit
 * KSP-Setup gewesen. Stattdessen läuft das über dieselbe Prompt-API-Anbindung,
 * die schon für die Titel auf dem Gerät funktioniert ([TitelKi]), plus strenge
 * Nachprüfung der Antwort. Gründe:
 *
 *  1. Der Tag muss ohnehin gegen die Liste der vorhandenen Tags geprüft werden:
 *     Ein erfundener Tag ist auch dann falsch, wenn er in einem sauberen
 *     JSON-Feld steht. Die Prüfung ist die eigentliche Sicherung, nicht das
 *     Format.
 *  2. Structured Output ist im Projekt nirgends erprobt. Eine zweite,
 *     ungeprüfte KI-Anbindung für einen Aufruf einzuführen, der nachts im
 *     Hintergrund läuft und dessen Fehler niemand sieht, wäre die schlechteste
 *     Stelle dafür.
 *
 * Nie erfundene Tags. Die KI darf nur aus den bereits vorhandenen wählen;
 * alles andere wird verworfen. Ein Tagsystem, in das eine Automatik nachts neue
 * Einträge schreibt, gehört nach kurzer Zeit niemandem mehr.
 */
@Singleton
class Beschriftung @Inject constructor(
    private val titelKi: TitelKi,
) {

    /**
     * Ein Titel für diese Notiz, oder `null`, wenn sie schon einen hat.
     *
     * Der Rückfall ohne KI ist kein Notnagel, sondern der Normalfall: Er
     * greift, wenn die KI abgeschaltet ist, das Modell fehlt, der Aufruf
     * scheitert oder die Antwort unbrauchbar ist. Er leitet den Titel aus
     * dem Inhalt ab, dieselbe Funktion, die auch der Editor beim Verlassen
     * benutzt.
     */
    suspend fun titelFuer(notiz: NoteWithRelations, kiErlaubt: Boolean): String? {
        if (notiz.note.title.isNotBlank()) return null

        val quelle = quelltext(notiz)
        val rueckfall = fallbackTitel(quelle, notiz.orderedItems.map { it.text })

        if (!kiErlaubt || quelle.isBlank()) return rueckfall.ifBlank { null }

        val vorschlag = runCatching {
            if (titelKi.zustand() != KiZustand.BEREIT) return@runCatching null
            titelKi.vorschlag(quelle)
        }.getOrNull()

        return brauchbar(vorschlag) ?: rueckfall.ifBlank { null }
    }

    /**
     * Ein passender Tag aus den vorhandenen, oder `null`.
     *
     * Gibt es keine Tags, wird gar nicht erst gefragt: Die KI hätte nichts zur
     * Auswahl und würde etwas erfinden.
     */
    suspend fun tagFuer(
        notiz: NoteWithRelations,
        vorhandene: List<TagEntity>,
        kiErlaubt: Boolean,
    ): TagEntity? {
        if (!kiErlaubt) return null
        if (vorhandene.isEmpty()) return null
        // Wer schon einen Tag hat, hat sich entschieden.
        if (notiz.tags.isNotEmpty()) return null

        val quelle = quelltext(notiz).take(TEXTGRENZE)
        if (quelle.isBlank()) return null

        val namen = vorhandene.joinToString(", ") { it.name }
        val antwort = runCatching {
            if (titelKi.zustand() != KiZustand.BEREIT) return@runCatching null
            titelKi.vorschlag(
                "Ordne die folgende Notiz genau einem dieser Schlagwörter zu: $namen. " +
                    "Antworte NUR mit dem Schlagwort, oder mit dem Wort KEINS, wenn " +
                    "keines passt.\n\n$quelle",
            )
        }.getOrNull()?.trim()?.trim('.', '"', '„', '"') ?: return null

        // DIE eigentliche Sicherung: Nur ein Name, der wirklich existiert, gilt.
        // Ohne Beachtung der Groß-/Kleinschreibung, weil Modelle gern
        // umschreiben, aber ohne jede Ähnlichkeitssuche, denn „fast wie Reise"
        // ist nicht „Reise".
        return vorhandene.firstOrNull { it.name.equals(antwort, ignoreCase = true) }
    }

    /** Was die KI zu lesen bekommt: Text, sonst Checkliste. */
    /** Was [nachholen] fuer eine Notiz gefunden hat. `null` heisst: nichts zu tun. */
    data class Nachgeholt(val titel: String?, val tag: TagEntity?)

    /** Ob die KI gerade arbeiten kann. */
    suspend fun kiZustand(): KiZustand =
        runCatching { titelKi.zustand() }.getOrDefault(KiZustand.FEHLER)

    /**
     * Holt fuer eine nachts archivierte Notiz nach, was die KI dort nicht durfte.
     *
     * Google laesst die KI nur arbeiten, solange die App im Vordergrund ist. Der
     * naechtliche Lauf gibt deshalb nur den Titel aus dem Text; beim naechsten
     * Oeffnen der App kommt die KI dazu.
     *
     * Der Titel wird nur ersetzt, solange er noch der aus dem Text abgeleitete
     * ist. Hat jemand ihn inzwischen geaendert, oder den Text, bleibt er. Einen
     * Tag bekommt nur, wer noch keinen hat.
     */
    suspend fun nachholen(notiz: NoteWithRelations, vorhandene: List<TagEntity>): Nachgeholt {
        val quelle = quelltext(notiz)
        val rueckfall = fallbackTitel(quelle, notiz.orderedItems.map { it.text })
        val nochAbgeleitet = quelle.isNotBlank() && rueckfall.isNotBlank() && notiz.note.title == rueckfall
        val titel = if (nochAbgeleitet) {
            brauchbar(runCatching { titelKi.vorschlag(quelle) }.getOrNull())
                ?.takeIf { it != notiz.note.title }
        } else {
            null
        }
        return Nachgeholt(titel, tagFuer(notiz, vorhandene, kiErlaubt = true))
    }

    private fun quelltext(notiz: NoteWithRelations): String =
        notiz.note.body.ifBlank {
            notiz.orderedItems.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        }.trim()

    /**
     * Verwirft, was als Titel nicht taugt.
     *
     * Sprachmodelle antworten gern mit einem ganzen Satz oder wiederholen die
     * Frage. Ein Titel, der über eine Zeile geht, ist in einer Liste genauso
     * unbrauchbar wie gar keiner.
     */
    private fun brauchbar(vorschlag: String?): String? {
        val t = vorschlag?.trim()?.trim('"', '„', '"')?.lines()?.firstOrNull()?.trim()
        return t?.takeIf { it.isNotBlank() && it.length <= TITELGRENZE }
    }

    private companion object {
        const val TITELGRENZE = 80
        const val TEXTGRENZE = 1500
    }
}
