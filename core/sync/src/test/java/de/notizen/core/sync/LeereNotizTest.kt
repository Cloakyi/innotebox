package de.notizen.core.sync

import de.notizen.core.data.db.entity.AttachmentEntity
import de.notizen.core.data.db.entity.NoteEntity
import de.notizen.core.data.db.entity.NoteItemEntity
import de.notizen.core.data.db.entity.TranscriptEntity
import de.notizen.core.data.db.relation.NoteWithRelations
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wann eine Notiz als leer gilt.
 *
 * Zwei Stellen hängen daran, und sie müssen dasselbe meinen. Der Editor
 * verwirft eine leere Notiz beim Verlassen, der Abgleich lädt sie gar nicht
 * erst hoch. Liefen die beiden Regeln auseinander, läge in Drive eine Datei,
 * die es auf dem Gerät nicht mehr gibt, und der nächste Abgleich holte sie
 * zurück.
 */
class LeereNotizTest {

    private fun notiz(
        titel: String = "",
        text: String = "",
        eintraege: List<String> = emptyList(),
        anhaenge: Int = 0,
        transkripte: List<String> = emptyList(),
    ) = NoteWithRelations(
        note = NoteEntity(
            id = "n1",
            title = titel,
            body = text,
            createdAt = 0,
            updatedAt = 0,
            lastOpenedAt = 0,
            stageChangedAt = 0,
        ),
        items = eintraege.mapIndexed { i, t -> NoteItemEntity("i$i", "n1", t, false, i) },
        attachments = (0 until anhaenge).map {
            AttachmentEntity("a$it", "n1", "/pfad", "image/jpeg", 1, "h")
        },
        transcripts = transkripte.mapIndexed { i, t ->
            TranscriptEntity("t$i", "n1", 0, 0, t, null, null, true)
        },
    )

    @Test
    fun `ohne alles ist sie leer`() {
        assertTrue(notiz().istLeer)
    }

    @Test
    fun `Leerzeichen sind kein Inhalt`() {
        assertTrue(notiz(titel = "   ", text = "\n\n").istLeer)
    }

    @Test
    fun `ein Titel allein genuegt`() {
        assertFalse(notiz(titel = "Einkauf").istLeer)
    }

    @Test
    fun `Text allein genuegt`() {
        assertFalse(notiz(text = "Milch").istLeer)
    }

    @Test
    fun `leere Checklisteneintraege zaehlen nicht`() {
        // Eine frische Checkliste hat eine leere Zeile. Die ist noch kein Inhalt.
        assertTrue(notiz(eintraege = listOf("", "  ")).istLeer)
        assertFalse(notiz(eintraege = listOf("", "Brot")).istLeer)
    }

    @Test
    fun `ein Bild allein genuegt`() {
        // Eine Bildnotiz hat oft weder Titel noch Text. Sie trotzdem als leer
        // zu verwerfen waere der Verlust des Fotos.
        assertFalse(notiz(anhaenge = 1).istLeer)
    }

    @Test
    fun `ein Transkript allein genuegt`() {
        // Dasselbe fuer eine Aufnahme: Der gesprochene Text steht im Transkript,
        // der Rumpf der Notiz kann leer sein.
        assertFalse(notiz(transkripte = listOf("guten Abend")).istLeer)
    }
}
