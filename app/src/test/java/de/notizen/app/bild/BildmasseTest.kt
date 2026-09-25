package de.notizen.app.bild

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Das Verkleinern beim Import und beim Anzeigen.
 *
 * Beide Rechnungen sind bewusst aus dem Android-Teil herausgelöst: Ein
 * Seitenverhältnis, das sich beim Verkleinern verschiebt, sieht man einem
 * einzelnen Bild nicht an, man merkt es erst, wenn alle Fotos leicht gestaucht
 * wirken und niemand mehr weiß, woher das kommt.
 */
class BildmasseTest {

    // ------------------------------------------------- Zielgroesse (Import)

    @Test
    fun `ein grosses Querformat kommt auf die lange Kante`() {
        val (b, h) = Bildspeicher.zielgroesse(4000, 3000, maxKante = 2048)
        assertEquals(2048, b)
        assertEquals(1536, h)
    }

    @Test
    fun `ein grosses Hochformat ebenso`() {
        // Die lange Kante ist hier die Höhe. Wer nur die Breite begrenzt,
        // lässt ein Hochkantfoto in voller Auflösung durch.
        val (b, h) = Bildspeicher.zielgroesse(3000, 4000, maxKante = 2048)
        assertEquals(1536, b)
        assertEquals(2048, h)
    }

    @Test
    fun `ein kleines Bild wird nicht aufgeblasen`() {
        val (b, h) = Bildspeicher.zielgroesse(800, 600, maxKante = 2048)
        assertEquals(800, b)
        assertEquals(600, h)
    }

    @Test
    fun `genau auf der Grenze bleibt unveraendert`() {
        val (b, h) = Bildspeicher.zielgroesse(2048, 1000, maxKante = 2048)
        assertEquals(2048, b)
        assertEquals(1000, h)
    }

    @Test
    fun `das Seitenverhaeltnis bleibt erhalten`() {
        val faelle = listOf(
            4032 to 3024, 3024 to 4032, 5000 to 2500, 2500 to 5000, 4000 to 4000,
        )
        faelle.forEach { (breite, hoehe) ->
            val (b, h) = Bildspeicher.zielgroesse(breite, hoehe, maxKante = 2048)
            val vorher = breite.toDouble() / hoehe
            val nachher = b.toDouble() / h
            assertTrue(
                "%dx%d wurde zu %dx%d, Verhältnis %.4f statt %.4f"
                    .format(breite, hoehe, b, h, nachher, vorher),
                abs(vorher - nachher) < 0.01,
            )
        }
    }

    @Test
    fun `eine extrem schmale Kante wird nie null`() {
        // Ein Panorama von 8000x50 ergäbe bei naiver Rechnung eine Höhe von 12,
        // bei 8000x8 eine von 2, und irgendwo darunter null. Ein Bitmap mit
        // Höhe null ist kein kleines Bild, sondern ein Absturz.
        val (b, h) = Bildspeicher.zielgroesse(20000, 5, maxKante = 2048)
        assertTrue("Breite wurde $b", b >= 1)
        assertTrue("Höhe wurde $h", h >= 1)
    }

    @Test
    fun `unsinnige Masse stuerzen nicht ab`() {
        assertEquals(0 to 0, Bildspeicher.zielgroesse(0, 0, maxKante = 2048))
    }

    // ---------------------------------------------- Stichprobe (Anzeige)

    @Test
    fun `die Stichprobe ist immer eine Zweierpotenz`() {
        (1..40).forEach { faktor ->
            val ergebnis = Bildlader.stichprobe(quellBreite = 100 * faktor, zielBreite = 100)
            assertTrue(
                "$ergebnis ist keine Zweierpotenz",
                ergebnis > 0 && (ergebnis and (ergebnis - 1)) == 0,
            )
        }
    }

    @Test
    fun `das verkleinerte Bild bleibt mindestens so breit wie gewuenscht`() {
        // Der eigentliche Punkt: Eine Stufe zu weit, und das Bild wird auf dem
        // Bildschirm wieder hochgerechnet und ist sichtbar unscharf.
        (100..4000 step 137).forEach { quelle ->
            listOf(80, 200, 360, 720).forEach { ziel ->
                val faktor = Bildlader.stichprobe(quelle, ziel)
                assertTrue(
                    "$quelle px auf Ziel $ziel px: Faktor $faktor lässt nur " +
                        "${quelle / faktor} px übrig",
                    quelle / faktor >= ziel || quelle < ziel,
                )
            }
        }
    }

    @Test
    fun `ein Bild kleiner als das Ziel wird nicht verkleinert`() {
        assertEquals(1, Bildlader.stichprobe(quellBreite = 100, zielBreite = 400))
        assertEquals(1, Bildlader.stichprobe(quellBreite = 400, zielBreite = 400))
    }

    @Test
    fun `ein Ziel von null ergibt keinen Faktor null`() {
        assertEquals(1, Bildlader.stichprobe(quellBreite = 1000, zielBreite = 0))
    }
}
