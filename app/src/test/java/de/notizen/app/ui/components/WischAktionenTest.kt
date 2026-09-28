package de.notizen.app.ui.components

import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.WischZiel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Wischgesten als Aktionen für TalkBack.
 *
 * Angeboten wird genau das, was auch der Finger kann, mit denselben Worten wie
 * in der Auswahlleiste.
 */
class WischAktionenTest {

    private fun namen(stufe: Stage, rechts: WischZiel, links: WischZiel) =
        wischAktionen(stufe, rechts, links) {}.map { it.label }

    @Test
    fun `im Eingang gibt es Weiterschieben und den Papierkorb`() {
        assertEquals(
            listOf("Nach Workspace verschieben", "In den Papierkorb"),
            namen(Stage.INBOX, WischZiel.NAECHSTE_STUFE, WischZiel.PAPIERKORB),
        )
    }

    @Test
    fun `was ins Leere liefe, wird nicht angeboten`() {
        // Aus dem Archiv geht es nicht weiter, und eine abgeschaltete Richtung ist keine Aktion.
        assertTrue(namen(Stage.ARCHIVE, WischZiel.NAECHSTE_STUFE, WischZiel.NICHTS).isEmpty())
    }

    @Test
    fun `im Papierkorb gibt es Endgueltig loeschen und Zurueckholen`() {
        assertEquals(
            listOf("Endgültig löschen", "Zurückholen"),
            namen(Stage.INBOX, WischZiel.ENDGUELTIG, WischZiel.WIEDERHERSTELLEN),
        )
    }

    @Test
    fun `dasselbe Ziel auf beiden Seiten steht nur einmal da`() {
        assertEquals(listOf("In den Papierkorb"), namen(Stage.WORKSPACE, WischZiel.PAPIERKORB, WischZiel.PAPIERKORB))
    }

    @Test
    fun `die Aktion tut dasselbe wie die Geste`() {
        var gewischt: WischZiel? = null
        val aktion = wischAktionen(Stage.WORKSPACE, WischZiel.NICHTS, WischZiel.VORHERIGE_STUFE) { gewischt = it }.single()

        assertEquals("Nach Eingang verschieben", aktion.label)
        assertTrue(aktion.action())
        assertEquals(WischZiel.VORHERIGE_STUFE, gewischt)
    }
}
