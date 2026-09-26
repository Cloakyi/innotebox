package de.notizen.core.sync

import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SCHEMA.md` in Drive muss dieselben Werte nennen wie die App.
 *
 * Wer sich an die Beschreibung haelt, darf keine Datei schreiben, die die App
 * nicht lesen kann. Frueher stand dort `CHECKLIST`, die App kennt `LIST`.
 */
class SchemaTextTest {

    @Test
    fun `jeder Notiztyp der App steht in SCHEMA_md`() {
        val zeile = SCHEMA_TEXT.lines().first { it.contains("`type` (") }
        val genannt = zeile.substringAfter("`type` (").substringBefore(")")
            .split(",").map { it.trim() }.toSet()
        assertTrue(
            "SCHEMA.md nennt $genannt, die App kennt ${NoteType.entries}",
            genannt == NoteType.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `jede Stufe der App steht in SCHEMA_md`() {
        Stage.entries.forEach { stufe ->
            assertTrue("Stufe ${stufe.name} fehlt", SCHEMA_TEXT.contains(stufe.name))
        }
    }
}
