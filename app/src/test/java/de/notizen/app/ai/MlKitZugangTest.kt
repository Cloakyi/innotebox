package de.notizen.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wo ML Kit im Quelltext vorkommen darf.
 *
 * ML Kit schickt Google Nutzungsangaben und darf deshalb erst nach einer
 * Zustimmung laufen. Die Zustimmung fragen die Stellen ab, die eine der Dateien
 * unten benutzen, und jede dieser Dateien startet ML Kit nur über
 * [MlKitStart.sicherstellen]. Dieser Test hält die Liste fest. Braucht eine neue
 * Datei ML Kit, schlägt er an; aufgenommen wird sie erst, wenn feststeht, dass
 * ihr Weg an der Zustimmung hängt. Dass ML Kit nicht beim Start der App von
 * selbst anläuft, prüft `manifestPruefen` bei jedem Bauen.
 */
class MlKitZugangTest {

    private val erlaubt = setOf(
        "ai/MlKitStart.kt",
        "ai/Aufraeumen.kt",
        "ai/TitelKi.kt",
        "audio/Transkription.kt",
        "audio/Transkriptor.kt",
        "uebersetzung/KiUebersetzer.kt",
        "uebersetzung/MlKitUebersetzer.kt",
    )

    /** Der Quelltext der App, gleich ob der Test im Modul oder im Projektstamm startet. */
    private val quelltext: File = listOf("src/main/java/de/notizen/app", "app/src/main/java/de/notizen/app")
        .map(::File)
        .first { it.isDirectory }

    private fun dateienMit(text: String): Set<String> = quelltext.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && text in it.readText() }
        .map { it.relativeTo(quelltext).invariantSeparatorsPath }
        .toSet()

    @Test
    fun `ML Kit kommt nur in den bekannten Dateien vor`() {
        val neu = dateienMit("com.google.mlkit") - erlaubt
        assertTrue(
            "ML Kit in neuen Dateien: $neu. Erst eintragen, wenn ihr Weg an der Zustimmung hängt.",
            neu.isEmpty(),
        )
    }

    @Test
    fun `die Liste nennt keine Datei, die ML Kit nicht mehr braucht`() {
        val weg = erlaubt - dateienMit("com.google.mlkit")
        assertTrue("Diese Dateien benutzen ML Kit nicht mehr: $weg. Aus der Liste nehmen.", weg.isEmpty())
    }

    @Test
    fun `jede Datei mit ML Kit startet es ueber MlKitStart`() {
        (dateienMit("com.google.mlkit") - "ai/MlKitStart.kt").forEach { pfad ->
            assertTrue(
                "$pfad ruft MlKitStart.sicherstellen() nicht auf.",
                "MlKitStart.sicherstellen()" in File(quelltext, pfad).readText(),
            )
        }
    }

    @Test
    fun `nur MlKitStart startet ML Kit`() {
        assertEquals(setOf("ai/MlKitStart.kt"), dateienMit("MlKit.initialize"))
    }
}
