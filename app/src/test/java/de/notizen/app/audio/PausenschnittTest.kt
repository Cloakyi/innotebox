package de.notizen.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Zerlegen an den Sprechpausen.
 *
 * Prüfbar ohne Mikrofon, ohne Modell und ohne Gerät — genau das ist der Vorteil
 * davon, diese Frage zu **rechnen** statt sie einer KI zu stellen: Die Antwort
 * ist jedes Mal dieselbe, und man kann sie hinschreiben.
 *
 * Die Tests bauen Pegelverläufe von Hand, ein Wert je 20 Millisekunden.
 */
class PausenschnittTest {

    private val proSekunde = 1000 / Pausenschnitt.RAHMEN_MS

    /** Baut einen Verlauf aus abwechselnd lauten und leisen Strecken. */
    private fun verlauf(vararg strecken: Pair<Float, Int>): FloatArray {
        val werte = mutableListOf<Float>()
        strecken.forEach { (pegel, ms) -> repeat(ms / Pausenschnitt.RAHMEN_MS) { werte += pegel } }
        return werte.toFloatArray()
    }

    private val LAUT = 0.30f
    private val LEISE = 0.002f

    @Test
    fun `durchgehendes Sprechen ergibt genau einen Abschnitt`() {
        val abschnitte = Pausenschnitt.zerlegen(verlauf(LAUT to 5_000))

        assertEquals(1, abschnitte.size)
        assertEquals(0, abschnitte.single().startMs)
    }

    @Test
    fun `eine lange Pause trennt`() {
        // Der Grundfall: zwei Saetze, dazwischen anderthalb Sekunden Ruhe.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LAUT to 2_000, LEISE to 1_500, LAUT to 2_000),
        )

        assertEquals(2, abschnitte.size)
        assertTrue("Erster Abschnitt vor dem zweiten", abschnitte[0].endeMs < abschnitte[1].startMs)
    }

    @Test
    fun `eine kurze Luecke trennt nicht`() {
        // Zwischen zwei Woertern liegen leicht 200 ms Ruhe. Wer da schneidet,
        // zerlegt jeden Satz in Einzelteile und verschickt Dutzende winziger
        // Haeppchen an die Erkennung.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LAUT to 2_000, LEISE to 200, LAUT to 2_000),
        )

        assertEquals("Das ist ein Satz, kein Absatz", 1, abschnitte.size)
    }

    @Test
    fun `Stille am Anfang und Ende faellt weg`() {
        // Genau der Punkt der Uebung: Was nicht gesprochen ist, wird der
        // Erkennung gar nicht erst hingeschickt.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LEISE to 3_000, LAUT to 2_000, LEISE to 3_000),
        )

        val einziger = abschnitte.single()
        assertTrue("Der Vorlauf ist weg", einziger.startMs >= 2_500)
        assertTrue("Der Nachlauf ist weg", einziger.endeMs <= 5_500)
    }

    @Test
    fun `eine vollkommen stille Aufnahme ergibt nichts`() {
        // Ohne die absolute Untergrenze waere das Dreifache eines Grundrauschens
        // nahe null immer noch nahe null -- und der Schnitt faende ueberall
        // Sprache.
        assertTrue(Pausenschnitt.zerlegen(verlauf(LEISE to 10_000)).isEmpty())
    }

    @Test
    fun `ein einzelnes Knacken ist kein Abschnitt`() {
        // Ein Stuhlruecken oder ein Klicken dauert keine 300 ms. Daraus einen
        // Abschnitt zu machen hiesse, die Erkennung auf ein Geraeusch
        // anzusetzen.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LEISE to 2_000, LAUT to 60, LEISE to 2_000),
        )

        assertTrue(abschnitte.isEmpty())
    }

    @Test
    fun `ein Knacken NEBEN echter Sprache faellt trotzdem weg`() {
        // Aufgefallen beim Mutationstest: Der Test darueber lief auch ohne den
        // Laengenfilter durch, weil dort die ganze Aufnahme als Stille gilt.
        // Hier gibt es echte Sprache UND ein kurzes Geraeusch -- nur so ist der
        // Filter wirklich geprueft.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LAUT to 3_000, LEISE to 3_000, LAUT to 60, LEISE to 3_000),
        )

        assertEquals("Nur der gesprochene Teil zaehlt", 1, abschnitte.size)
        assertTrue("Und zwar der erste", abschnitte.single().startMs < 1_000)
    }

    @Test
    fun `laute Umgebung verschiebt die Schwelle mit`() {
        // Im Zug liegt das Grundrauschen hoch. Eine feste Schwelle faende dort
        // entweder ueberall Sprache oder nirgends. Gemessen wird deshalb
        // relativ zum ruhigsten Teil der Aufnahme.
        val laut = 0.05f
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(laut to 3_000, 0.40f to 2_000, laut to 3_000),
        )

        assertEquals(1, abschnitte.size)
    }

    @Test
    fun `die Schwelle liegt ueber dem Grundrauschen`() {
        val pegel = verlauf(0.01f to 4_000, 0.50f to 2_000)
        val schwelle = Pausenschnitt.schwelleFuer(pegel)

        assertTrue("Ueber dem Ruhepegel", schwelle > 0.01f)
        assertTrue("Unter dem Sprechpegel", schwelle < 0.50f)
    }

    @Test
    fun `Abschnitte ueberlappen sich nicht`() {
        // Sie erben ihre Zeitstempel an den Transkripttext. Ueberlappten sie
        // sich, zeigten zwei Textstellen auf dieselbe Sekunde der Aufnahme.
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(
                LAUT to 1_000, LEISE to 800,
                LAUT to 1_000, LEISE to 800,
                LAUT to 1_000,
            ),
        )

        assertEquals(3, abschnitte.size)
        abschnitte.zipWithNext { a, b ->
            assertTrue("$a und $b ueberlappen", a.endeMs <= b.startMs)
        }
    }

    @Test
    fun `ein sehr langer Abschnitt wird geteilt`() {
        // Wer minutenlang ohne Atempause redet, erzeugt sonst ein einziges
        // Riesenstueck. Die Erkennung beendet sich in solchen Faellen von
        // selbst, und alles danach waere verloren.
        val abschnitte = Pausenschnitt.zerlegen(verlauf(LAUT to 150_000))

        assertTrue("Muss geteilt werden", abschnitte.size > 1)
        abschnitte.forEach {
            assertTrue("Stueck zu lang: ${'$'}{it.dauerMs} ms", it.dauerMs <= 15_000)
        }
    }

    @Test
    fun `beim Teilen geht keine Sekunde verloren`() {
        // Die Stuecke muessen luekenlos aneinander anschliessen und zusammen
        // genau das Original ergeben -- sonst fehlt Text, und man merkt es
        // nicht.
        val abschnitte = Pausenschnitt.zerlegen(verlauf(LAUT to 150_000))

        assertEquals(0, abschnitte.first().startMs)
        assertEquals(150_000, abschnitte.last().endeMs)
        abschnitte.zipWithNext { a, b ->
            assertEquals("Luecke zwischen $a und $b", a.endeMs, b.startMs)
        }
    }

    @Test
    fun `ein normal langer Abschnitt bleibt ganz`() {
        // Unterhalb der Hoechstlaenge wird nicht geteilt. Der Wert selbst ist
        // eine Erfahrungszahl vom Geraet und wurde schon einmal gesenkt --
        // deshalb prueft der Test nur die Regel, nicht die Zahl.
        val abschnitte = Pausenschnitt.zerlegen(verlauf(LAUT to 10_000))
        assertEquals(1, abschnitte.size)
    }

    @Test
    fun `eine leere Aufnahme stuerzt nicht ab`() {
        assertTrue(Pausenschnitt.zerlegen(FloatArray(0)).isEmpty())
    }

    @Test
    fun `der Rand laesst den ersten Laut nicht abschneiden`() {
        // Sprache beginnt leiser, als sie weitergeht. Setzte der Abschnitt
        // genau dort an, wo die Schwelle erreicht wird, fehlte der Wortanfang
        // -- und die Erkennung liest "aus" statt "Haus".
        val abschnitte = Pausenschnitt.zerlegen(
            verlauf(LEISE to 2_000, LAUT to 2_000, LEISE to 2_000),
        )

        val einziger = abschnitte.single()
        assertTrue("Etwas Vorlauf gehoert dazu", einziger.startMs < 2_000)
        assertTrue("Und etwas Nachlauf", einziger.endeMs > 4_000)
    }

    @Test
    fun `ein Abschnitt weiss, wie lange er dauert`() {
        val abschnitt = Sprechabschnitt(startMs = 1_500, endeMs = 4_000)
        assertEquals(2_500, abschnitt.dauerMs)
        assertEquals(proSekunde, 50)
    }
}
