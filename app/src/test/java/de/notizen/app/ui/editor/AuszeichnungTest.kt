package de.notizen.app.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Textauszeichnung.
 *
 * Der Schwerpunkt liegt auf der Zuordnung zwischen Quelle und Anzeige. Die
 * gestaltete Darstellung sieht man sofort, wenn sie falsch ist; eine kaputte
 * Zuordnung merkt man erst daran, dass der Cursor an der falschen Stelle steht
 * oder die App mitten im Tippen abstürzt, und dann ist unklar, warum.
 */
class AuszeichnungTest {

    private fun sichtbar(quelle: String) = Auszeichnung.aufloesen(quelle).text.text

    // ------------------------------------------------------ Was verschwindet

    @Test
    fun `vollstaendige Markierungen verschwinden`() {
        assertEquals("fett", sichtbar("**fett**"))
        assertEquals("kursiv", sichtbar("*kursiv*"))
        assertEquals("unterstrichen", sichtbar("__unterstrichen__"))
        assertEquals("durch", sichtbar("~~durch~~"))
        assertEquals("Titel", sichtbar("# Titel"))
        assertEquals("Zwischen", sichtbar("## Zwischen"))
    }

    @Test
    fun `unvollstaendige Markierungen bleiben stehen`() {
        // Sonst verschwänden beim Tippen Zeichen unter dem Cursor, sobald man
        // das erste Sternchen setzt.
        assertEquals("**fe", sichtbar("**fe"))
        assertEquals("~~offen", sichtbar("~~offen"))
        assertEquals("__halb", sichtbar("__halb"))
    }

    @Test
    fun `Fettschrift wird nicht als zweimal kursiv gelesen`() {
        // Genau daran scheitert der naive Ausdruck: `fett` zerfiele in zwei
        // kursive Stücke, und übrig bliebe „fett" mit falscher Gestalt.
        val aufgeloest = Auszeichnung.aufloesen("**fett**")
        assertEquals("fett", aufgeloest.text.text)
        assertEquals(1, aufgeloest.text.spanStyles.size)
    }

    @Test
    fun `Auszeichnungen ueberlagern sich`() {
        val aufgeloest = Auszeichnung.aufloesen("**__beides__**")
        assertEquals("beides", aufgeloest.text.text)
        assertEquals(2, aufgeloest.text.spanStyles.size)
    }

    @Test
    fun `Auszeichnung geht nicht ueber Zeilengrenzen`() {
        // Ein einzelnes Sternchen in einer Notiz darf nicht alles darunter
        // kursiv machen, bis irgendwo ein zweites auftaucht.
        assertEquals("*eins\nzwei*", sichtbar("*eins\nzwei*"))
    }

    @Test
    fun `eine Raute mitten in der Zeile ist keine Ueberschrift`() {
        assertEquals("Preis # 3", sichtbar("Preis # 3"))
    }

    @Test
    fun `Ueberschriften gelten je Zeile`() {
        assertEquals("Titel\nnormal\nZwischen", sichtbar("# Titel\nnormal\n## Zwischen"))
    }

    // ------------------------------------------------------------- Zuordnung

    @Test
    fun `die Zuordnung bleibt fuer jede Position im gueltigen Bereich`() {
        // Der eigentliche Absturzschutz. Compose fragt beide Richtungen bei
        // jeder Änderung ab, auch mit Werten aus dem vorherigen Stand.
        val proben = listOf(
            "", "kein Markup", "**fett**", "*a* und **b**",
            "# Titel\n\nAbsatz mit __Unterstreichung__ und ~~Streichung~~.",
            "***", "____", "~~~~", "**", "#", "# ",
            "Ein **sehr *verschachtelter* Fall** hier.",
        )
        proben.forEach { quelle ->
            val a = Auszeichnung.aufloesen(quelle)
            val sichtbareLaenge = a.text.text.length

            for (i in 0..quelle.length) {
                val t = a.zuordnung.originalToTransformed(i)
                assertTrue("„$quelle" + "\": $i -> $t liegt außerhalb", t in 0..sichtbareLaenge)
            }
            for (i in 0..sichtbareLaenge) {
                val o = a.zuordnung.transformedToOriginal(i)
                assertTrue("„$quelle" + "\": $i <- $o liegt außerhalb", o in 0..quelle.length)
            }
        }
    }

    @Test
    fun `die Zuordnung waechst monoton`() {
        // Eine Zuordnung, die zurückspringt, lässt den Cursor rückwärts laufen.
        val quelle = "# Titel\n**fett** und *kursiv* und ~~weg~~"
        val a = Auszeichnung.aufloesen(quelle)
        var vorher = 0
        for (i in 0..quelle.length) {
            val t = a.zuordnung.originalToTransformed(i)
            assertTrue("Rückwärts bei $i: $vorher -> $t", t >= vorher)
            vorher = t
        }
    }

    @Test
    fun `Anfang und Ende passen zusammen`() {
        val quelle = "**fett** und mehr"
        val a = Auszeichnung.aufloesen(quelle)
        assertEquals(0, a.zuordnung.originalToTransformed(0))
        assertEquals(a.text.text.length, a.zuordnung.originalToTransformed(quelle.length))
        assertEquals(quelle.length, a.zuordnung.transformedToOriginal(a.text.text.length))
    }

    @Test
    fun `ausserhalb liegende Anfragen werden auf den Rand gezogen`() {
        // Compose reicht während einer Änderung kurz Werte herein, die zum
        // ALTEN Text gehören. Ohne Begrenzung wäre das ein Absturz mitten im
        // Tippen, und ein sehr schwer nachstellbarer.
        //
        // Geprüft wird das Abschneiden, nicht ein bestimmter Wert: Welche
        // Quellposition zur sichtbaren Null gehört, ist eine Entscheidung
        // (bei `fett` die 2, also direkt vor dem „f"), und die gehört
        // in den Test darunter statt hierher.
        val a = Auszeichnung.aufloesen("**fett**")
        assertEquals(a.zuordnung.originalToTransformed(0), a.zuordnung.originalToTransformed(-5))
        assertEquals(a.zuordnung.originalToTransformed(8), a.zuordnung.originalToTransformed(999))
        assertEquals(a.zuordnung.transformedToOriginal(0), a.zuordnung.transformedToOriginal(-5))
        assertEquals(a.zuordnung.transformedToOriginal(4), a.zuordnung.transformedToOriginal(999))
    }

    @Test
    fun `eine sichtbare Stelle zeigt auf ihr eigenes Zeichen`() {
        // Die Regel, nach der die Rückrichtung gebaut ist: Sichtbare Position n
        // zeigt auf die Quellstelle des n-ten sichtbaren Zeichens; das Ende der
        // Anzeige zeigt aufs Ende der Quelle.
        //
        // Für den Cursor heißt das: vorn im fetten Wort steht er INNERHALB der
        // Auszeichnung (Quelle 2, direkt vor dem „f"), wer dort tippt, schreibt
        // fett weiter. Am Ende steht er hinter den schließenden Zeichen, tippt
        // also außerhalb weiter. Diese Asymmetrie ist gewollt: Sie ist genau
        // das, was man von einer Textverarbeitung kennt.
        val a = Auszeichnung.aufloesen("**fett**")
        assertEquals(2, a.zuordnung.transformedToOriginal(0))
        assertEquals(5, a.zuordnung.transformedToOriginal(3))
        assertEquals(8, a.zuordnung.transformedToOriginal(4))
    }

    @Test
    fun `hinter einer Auszeichnung zeigt die Stelle auf das naechste sichtbare Zeichen`() {
        // Derselbe Fall, aber mitten im Text statt am Ende: Nach „fett" folgt
        // ein Leerzeichen, und genau darauf muss die Stelle zeigen, nicht auf
        // eines der schließenden Sternchen.
        val quelle = "**fett** und mehr"
        val a = Auszeichnung.aufloesen(quelle)
        assertEquals("fett und mehr", a.text.text)
        assertEquals(' ', quelle[a.zuordnung.transformedToOriginal(4)])
    }

    // ------------------------------------------------------------ Umschalten

    @Test
    fun `nach dem Auszeichnen bleibt der Cursor, nicht die Auswahl`() {
        // Bliebe der Text ausgewählt, ersetzte ihn der nächste Tastendruck:
        // Man drückt „fett", tippt weiter, und das fette Wort ist weg. Genau
        // das ist am Gerät passiert.
        val vorher = TextFieldValue("Hallo Welt", TextRange(6, 10))
        val nachher = Auszeichnung.umschalten(vorher, Auszeichnung.Art.FETT)

        assertEquals("Hallo **Welt**", nachher.text)
        assertTrue("Es ist noch etwas ausgewählt", nachher.selection.collapsed)
        // Am Ende des ausgezeichneten Textes und INNERHALB der Auszeichnung:
        // Wer gerade fett gewählt hat, schreibt fett weiter.
        assertEquals("Hallo **Welt", nachher.text.substring(0, nachher.selection.start))
    }

    @Test
    fun `getipptes nach dem Auszeichnen ersetzt nichts`() {
        // Der Fall aus der Rückmeldung, einmal durchgespielt: markieren,
        // fett drücken, weitertippen.
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("Hallo Welt", TextRange(6, 10)),
            Auszeichnung.Art.FETT,
        )
        val getippt = nachher.text.replaceRange(
            nachher.selection.start,
            nachher.selection.end,
            "!",
        )
        assertEquals("Hallo **Welt!**", getippt)
    }

    @Test
    fun `auch nach dem Aufheben bleibt nur der Cursor`() {
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("Hallo **Welt**", TextRange(11)),
            Auszeichnung.Art.FETT,
        )
        assertEquals("Hallo Welt", nachher.text)
        assertTrue(nachher.selection.collapsed)
        assertEquals(10, nachher.selection.start)
    }

    @Test
    fun `zweimal dasselbe hebt sich auf`() {
        val vorher = TextFieldValue("Hallo Welt", TextRange(6, 10))
        val einmal = Auszeichnung.umschalten(vorher, Auszeichnung.Art.FETT)
        val zweimal = Auszeichnung.umschalten(einmal, Auszeichnung.Art.FETT)

        assertEquals("Hallo Welt", zweimal.text)
        // Der Cursor steht wieder hinter dem Wort, an derselben Stelle wie vor
        // dem ersten Druck das Auswahlende.
        assertEquals(10, zweimal.selection.start)
    }

    @Test
    fun `der Cursor am sichtbaren Ende eines fetten Wortes hebt es auf`() {
        // Der zweite Punkt aus der Rückmeldung. Tippt man ans sichtbare Ende
        // von „Welt", rechnet die Anzeige das auf die Quellstelle HINTER den
        // schließenden Sternchen zurück, Position 14, nicht 11. Ohne die
        // erweiterte Erkennung müsste man den Cursor erst von Hand ins Wort
        // hineinschieben, um die Fettschrift loszuwerden.
        val quelle = "Hallo **Welt**"
        val a = Auszeichnung.aufloesen(quelle)
        val amEnde = a.zuordnung.transformedToOriginal(a.text.text.length)
        assertEquals(14, amEnde)

        val nachher = Auszeichnung.umschalten(
            TextFieldValue(quelle, TextRange(amEnde)),
            Auszeichnung.Art.FETT,
        )
        assertEquals("Hallo Welt", nachher.text)
    }

    @Test
    fun `an jeder sichtbaren Stelle des Wortes wird die Fettschrift erkannt`() {
        // Kein Loch: Egal, wo im Wort man hintippt, der Knopf muss greifen.
        val quelle = "Hallo **Welt**"
        val a = Auszeichnung.aufloesen(quelle)
        // Sichtbar ist "Hallo Welt"; das Wort beginnt bei 6.
        (6..a.text.text.length).forEach { sichtbar ->
            val stelle = a.zuordnung.transformedToOriginal(sichtbar)
            val wert = TextFieldValue(quelle, TextRange(stelle))
            assertTrue(
                "Bei sichtbarer Stelle " + sichtbar + " (Quelle " + stelle + ") nicht erkannt",
                Auszeichnung.Art.FETT in Auszeichnung.aktiv(wert),
            )
        }
    }

    @Test
    fun `ohne Auswahl gilt das Wort unter dem Cursor`() {
        // Sonst müsste man erst markieren, um ein einzelnes Wort auszuzeichnen,
        // und genau dieser Umweg macht das Schreiben zäh.
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("Hallo Welt", TextRange(8)),
            Auszeichnung.Art.FETT,
        )
        assertEquals("Hallo **Welt**", nachher.text)
    }

    @Test
    fun `der Cursor IN einem fetten Wort hebt die Fettschrift auf`() {
        // Der Punkt, an dem die erste Fassung falsch lag: Sie schrieb ein
        // zweites Zeichenpaar mitten hinein, statt das vorhandene zu entfernen.
        val wert = TextFieldValue("Hallo **Welt**", TextRange(11))
        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT)

        assertEquals("Hallo Welt", nachher.text)
    }

    @Test
    fun `eine Auswahl SAMT versteckter Zeichen hebt die Auszeichnung auf`() {
        // Genau diese Auswahl liefert das Textfeld, wenn man das sichtbare Wort
        // markiert: Das Ende der Anzeige bildet auf das Ende der Quelle ab, die
        // schließenden Zeichen liegen also mit drin. Ohne diesen Fall entstünde
        // eine Auszeichnung in der Auszeichnung.
        val wert = TextFieldValue("**fett**", TextRange(2, 8))
        assertEquals("fett", Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT).text)
    }

    @Test
    fun `eine Auswahl mitten im Wort befreit genau diese Auswahl`() {
        // Ausgewählt ist „et" in „fett". Übrig bleiben zwei fette Stücke mit
        // einem normalen dazwischen, genau das, was jede Textverarbeitung
        // täte.
        val wert = TextFieldValue("**fett**", TextRange(3, 5))
        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT)

        assertEquals("fett", sichtbar(nachher.text))
        val stile = Auszeichnung.aufloesen(nachher.text).text.spanStyles
        assertEquals("Es müssten zwei fette Stücke sein: " + nachher.text, 2, stile.size)
    }

    // ------------------------------------- Ein Leerzeichen trennt Wörter

    @Test
    fun `nur das gemeinte Wort verliert die Fettschrift`() {
        // Der Fall aus der Rückmeldung: Wer ein fettes Wort schreibt,
        // Leertaste drückt und weiterschreibt, hat BEIDE Wörter in derselben
        // Auszeichnung, der Cursor stand ja zwischen den Zeichen. Wer dann
        // „fett" antippt, meint das zweite Wort und nicht beide.
        val wert = TextFieldValue("**Hallo Welt**", TextRange(11))
        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT)

        assertEquals("**Hallo** Welt", nachher.text)
        assertEquals("Hallo Welt", sichtbar(nachher.text))
    }

    @Test
    fun `auch das erste Wort laesst sich einzeln befreien`() {
        val wert = TextFieldValue("**Hallo Welt**", TextRange(4))
        assertEquals("Hallo **Welt**", Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT).text)
    }

    @Test
    fun `das Leerzeichen an der Bruchstelle bleibt draussen`() {
        // `**Hallo ** Welt` wäre eine Auszeichnung, die ein Leerzeichen
        // umfasst: unsichtbar, aber im gespeicherten Text, und die wandert in
        // den Sync.
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("**Hallo Welt**", TextRange(11)),
            Auszeichnung.Art.FETT,
        )
        // Geprüft wird das, was gemeint ist: Kein ausgezeichnetes Stück darf mit
        // Leerraum anfangen oder aufhören. Über die Zeichenfolge `** ` allein
        // ginge das nicht, die steht in `Hallo Welt` völlig zu Recht.
        val aufgeloest = Auszeichnung.aufloesen(nachher.text)
        aufgeloest.text.spanStyles.forEach { bereich ->
            val stueck = aufgeloest.text.text.substring(bereich.start, bereich.end)
            assertEquals(
                "Ausgezeichneter Leerraum in: " + nachher.text,
                stueck.trim(),
                stueck,
            )
        }
    }

    @Test
    fun `aus der Mitte von drei Woertern faellt nur das mittlere heraus`() {
        val wert = TextFieldValue("**eins zwei drei**", TextRange(10))
        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.FETT)

        assertEquals("**eins** zwei **drei**", nachher.text)
        assertEquals("eins zwei drei", sichtbar(nachher.text))
    }

    @Test
    fun `der Cursor steht danach hinter dem befreiten Wort`() {
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("**Hallo Welt**", TextRange(11)),
            Auszeichnung.Art.FETT,
        )
        assertTrue(nachher.selection.collapsed)
        assertEquals("**Hallo** Welt", nachher.text.substring(0, nachher.selection.start))
    }

    @Test
    fun `am sichtbaren Ende zaehlt das letzte Wort, nicht die ganze Auszeichnung`() {
        // Dieselbe Stelle wie oben, aber angetippt statt hineingesetzt: Das
        // sichtbare Ende rechnet auf die Quellstelle hinter den schließenden
        // Zeichen zurück.
        val quelle = "**Hallo Welt**"
        val a = Auszeichnung.aufloesen(quelle)
        val amEnde = a.zuordnung.transformedToOriginal(a.text.text.length)

        val nachher = Auszeichnung.umschalten(
            TextFieldValue(quelle, TextRange(amEnde)),
            Auszeichnung.Art.FETT,
        )
        assertEquals("**Hallo** Welt", nachher.text)
    }

    @Test
    fun `ein einzelnes Wort verliert seine Zeichen ganz`() {
        // Die Aufteilung darf den einfachen Fall nicht verkomplizieren: Wo es
        // nichts zu teilen gibt, bleibt nichts stehen.
        val nachher = Auszeichnung.umschalten(
            TextFieldValue("**fett**", TextRange(4)),
            Auszeichnung.Art.FETT,
        )
        assertEquals("fett", nachher.text)
    }

    @Test
    fun `aktiv meldet fett, wenn der Cursor im fetten Wort steht`() {
        // Der Knopf muss anzeigen, was man sieht, sonst tut er beim Antippen
        // etwas anderes, als er verspricht.
        val wert = TextFieldValue("Hallo **Welt**", TextRange(11))
        assertTrue(Auszeichnung.Art.FETT in Auszeichnung.aktiv(wert))

        val daneben = TextFieldValue("Hallo **Welt**", TextRange(2))
        assertFalse(Auszeichnung.Art.FETT in Auszeichnung.aktiv(daneben))
    }

    @Test
    fun `ein leeres Zeichenpaar ist unsichtbar`() {
        // Es entsteht, wenn man die Auszeichnung setzt, bevor man tippt.
        // Sichtbare Sternchen, die auf Eingabe warten, stören beim schnellen
        // Schreiben.
        assertEquals("", sichtbar("****"))
        assertEquals("ab", sichtbar("a****b"))
    }

    @Test
    fun `leer gewordene Paare werden bei der naechsten Eingabe weggeraeumt`() {
        // Wer den Text eines fetten Wortes wieder wegnimmt, bleibt sonst auf
        // einem `****` sitzen, das er einzeln löschen müsste.
        val nachher = Auszeichnung.aufraeumen(TextFieldValue("Hallo ****", TextRange(8)))
        assertEquals("Hallo ", nachher.text)
        assertEquals(6, nachher.selection.start)
    }

    @Test
    fun `das Aufraeumen laesst gefuellte Paare in Ruhe`() {
        val wert = TextFieldValue("**fett** und __was__", TextRange(4))
        assertEquals(wert.text, Auszeichnung.aufraeumen(wert).text)
        assertEquals(wert.selection, Auszeichnung.aufraeumen(wert).selection)
    }

    @Test
    fun `das Aufraeumen verschiebt den Cursor nur um das, was davor wegfaellt`() {
        // Text: "____xy____", Cursor hinter dem x (Stelle 5).
        val nachher = Auszeichnung.aufraeumen(TextFieldValue("____xy____", TextRange(5)))
        assertEquals("xy", nachher.text)
        assertEquals(1, nachher.selection.start)
    }

    @Test
    fun `das Aufraeumen fasst kursive Paare NICHT an`() {
        // `**` wäre zugleich ein leeres kursives Paar und der Anfang einer
        // Fettschrift. Wer Markdown von Hand tippt, bekäme sie sonst unter den
        // Fingern weggeräumt.
        val wert = TextFieldValue("**", TextRange(2))
        assertEquals("**", Auszeichnung.aufraeumen(wert).text)
    }

    @Test
    fun `aktiv erkennt eine eingefasste Auswahl`() {
        val wert = TextFieldValue("**fett**", TextRange(2, 6))
        assertTrue(Auszeichnung.Art.FETT in Auszeichnung.aktiv(wert))
    }

    @Test
    fun `fett wird nicht faelschlich als kursiv gemeldet`() {
        // Bei `fett` liegt links und rechts der Auswahl je ein `*`. Die
        // naive Prüfung meldete deshalb auch kursiv, und der Kursiv-Knopf
        // hätte je ein Sternchen entfernt und aus fett kursiv gemacht.
        val wert = TextFieldValue("**fett**", TextRange(2, 6))
        assertFalse(Auszeichnung.Art.KURSIV in Auszeichnung.aktiv(wert))

        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.KURSIV)
        assertTrue(
            "Die Fettschrift darf dabei nicht verloren gehen: " + nachher.text,
            nachher.text.startsWith("**") && nachher.text.endsWith("**"),
        )
    }

    @Test
    fun `am Textrand wird nichts ausserhalb gelesen`() {
        assertTrue(Auszeichnung.aktiv(TextFieldValue("a", TextRange(0, 1))).isEmpty())
        assertEquals(
            "**a**",
            Auszeichnung.umschalten(TextFieldValue("a", TextRange(0, 1)), Auszeichnung.Art.FETT).text,
        )
    }

    @Test
    fun `Markierungszeichen am Rand der Auswahl werden nicht mit eingepackt`() {
        // Eine Auswahl, die ein `*` mitnimmt, erzeugte sonst Zeichenfolgen, die
        // niemand mehr auflösen kann.
        val wert = TextFieldValue("__x__ y", TextRange(3, 7))
        val nachher = Auszeichnung.umschalten(wert, Auszeichnung.Art.DURCHGESTRICHEN)
        assertTrue("Ergebnis: " + nachher.text, "~~" !in nachher.text.substring(0, 3))
    }

    // ------------------------------------------------------------- Stufen

    @Test
    fun `eine Ueberschrift wird gesetzt und wieder aufgehoben`() {
        val wert = TextFieldValue("Einkauf", TextRange(3))
        val gross = Auszeichnung.setzeStufe(wert, Auszeichnung.Stufe.UEBERSCHRIFT)
        assertEquals("# Einkauf", gross.text)
        assertEquals(Auszeichnung.Stufe.UEBERSCHRIFT, Auszeichnung.stufeAn(gross))

        val zurueck = Auszeichnung.setzeStufe(gross, Auszeichnung.Stufe.NORMAL)
        assertEquals("Einkauf", zurueck.text)
        assertEquals(Auszeichnung.Stufe.NORMAL, Auszeichnung.stufeAn(zurueck))
    }

    @Test
    fun `die Stufe wird ersetzt, nicht gestapelt`() {
        val eins = Auszeichnung.setzeStufe(TextFieldValue("Text", TextRange(0)), Auszeichnung.Stufe.UEBERSCHRIFT)
        val zwei = Auszeichnung.setzeStufe(eins, Auszeichnung.Stufe.ZWISCHENUEBERSCHRIFT)
        assertEquals("## Text", zwei.text)
    }

    @Test
    fun `nur die Zeile unter dem Cursor wird angefasst`() {
        val wert = TextFieldValue("erste\nzweite\ndritte", TextRange(8))
        val nachher = Auszeichnung.setzeStufe(wert, Auszeichnung.Stufe.UEBERSCHRIFT)
        assertEquals("erste\n# zweite\ndritte", nachher.text)
    }

    @Test
    fun `der Cursor wandert mit dem Vorsatz mit`() {
        val wert = TextFieldValue("Text", TextRange(2))
        val nachher = Auszeichnung.setzeStufe(wert, Auszeichnung.Stufe.UEBERSCHRIFT)
        // Vorher stand er hinter „Te", danach muss er es immer noch tun.
        assertEquals("# Te", nachher.text.substring(0, nachher.selection.start))
    }

    // -------------------------------------------------------------- Klartext

    @Test
    fun `Klartext ist das, was auch angezeigt wird`() {
        assertEquals("Einkauf", Auszeichnung.klartext("# **Einkauf**"))
        assertEquals("nichts", Auszeichnung.klartext("nichts"))
    }

    @Test
    fun `sehr langer Text wird unveraendert durchgereicht`() {
        // Die Umsetzung läuft bei jedem Neuzeichnen über den ganzen Text. Ein
        // hakendes Textfeld wäre schlimmer als fehlende Fettschrift.
        val lang = "**a** ".repeat(Auszeichnung.GRENZE / 3)
        assertTrue(lang.length > Auszeichnung.GRENZE)
        assertEquals(lang, Auszeichnung.klartext(lang))
    }
}
