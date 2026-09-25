package de.notizen.app.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.em

/**
 * Textauszeichnung im Notiztext.
 *
 * Gespeichert wird Markdown im `body`, angezeigt wird es ohne die
 * Markierungszeichen. Das war die entscheidende Wahl, und sie hat drei
 * Gründe:
 *
 *  1. Das Feld bleibt ein String. `notes.body` ändert sich nicht, SYNC.md
 *     bleibt einfach, und der Web-Client braucht einen Markdown-Renderer statt
 *     einer nachgebauten Spantabelle, die beide Seiten identisch umsetzen
 *     müssten.
 *  2. Die Suche merkt nichts davon. Der Tokenizer `unicode61` behandelt
 *     `*`, `#` und `~` als Trenner, `Küche` wird zu `küche` wie ohne
 *     Auszeichnung. Eine Spantabelle hätte den Index unberührt gelassen, eine
 *     HTML-Fassung hätte ihn vermüllt.
 *  3. Es überlebt das Teilen. Wer eine Notiz in eine fremde App schickt,
 *     bekommt lesbaren Text mit erkennbarer Struktur, nicht rohes Markup.
 *
 * Die Markierungen sind im Editor unsichtbar. Sichtbar gelassen wäre die
 * ehrlichere, aber schlechtere Lösung: Wer „fett" antippt, will fett sehen und
 * keine Sternchen. Der Preis ist eine echte [OffsetMapping], die Stelle, an
 * der so etwas gern schiefgeht, wenn der Cursor plötzlich woanders steht als
 * er sollte. Deshalb sind beide Richtungen als Tabellen gebaut und nicht
 * gerechnet, und [AuszeichnungTest] prüft sie über jede Position.
 *
 * Bekannte Grenze: Kursiv lässt sich nicht in Fett schachteln. Beide
 * benutzen dasselbe Zeichen, und `*x*` sauber aufzulösen bräuchte einen
 * echten Parser statt vier unabhängiger Muster. Für Unterstreichen und
 * Durchstreichen gilt die Einschränkung nicht, die haben eigene Zeichen und
 * lassen sich beliebig mit Fett kombinieren.
 *
 * Unvollständige Markierungen bleiben stehen. `**fe` zeigt die Sternchen,
 * `fett` nicht. Anders herum verschwänden beim Tippen Zeichen unter dem
 * Cursor, sobald man das erste Sternchen setzt.
 *
 * Umgeschaltet wird auf der erkannten Auszeichnung, nicht auf dem Zeichenpaar
 * neben der Auswahl. Das ist der Unterschied zwischen einer Leiste, die man
 * benutzen mag, und einer, die stört: Wer den Cursor in ein fettes Wort setzt
 * und „fett" antippt, will es normal haben, und bekam in der ersten Fassung
 * ein zweites Paar mitten hinein. Ohne Auswahl gilt das Wort unter dem Cursor;
 * leer gewordene Paare räumt [Auszeichnung.aufraeumen] bei der nächsten Eingabe
 * weg.
 *
 * Ein Leerzeichen trennt Wörter, die Auszeichnung tut es nicht. Wer ein
 * fettes Wort schreibt, Leertaste drückt und weiterschreibt, hat beide Wörter in
 * derselben Auszeichnung, der Cursor stand ja zwischen den Zeichen. Beim
 * Aufheben zählt deshalb das Wort, nicht die ganze Fundstelle: Der Rest
 * links und rechts wird neu eingefasst, und der Leerraum an der Bruchstelle
 * wandert nach draußen.
 *
 * Nach dem Umschalten bleibt der Cursor, nicht die Auswahl. Bliebe der Text
 * ausgewählt, ersetzte ihn der nächste Tastendruck, man drückt „fett", tippt
 * weiter, und das fette Wort ist weg. Der Cursor landet am Ende des betroffenen
 * Textes und innerhalb der Auszeichnung, so wie in jeder Textverarbeitung.
 */
object Auszeichnung {

    /**
     * Ab dieser Länge wird nicht mehr ausgezeichnet.
     *
     * Die Umsetzung läuft bei jedem Neuzeichnen über den ganzen Text. Für eine
     * Notiz ist das nichts; für ein eingefügtes Buch wäre es spürbar, und ein
     * hakendes Textfeld ist schlimmer als fehlende Fettschrift.
     */
    const val GRENZE = 20_000

    // `*?` und nicht `+?`: Ein LEERES Paar (`****`) muss ebenfalls verschwinden.
    // Es entsteht, wenn man die Auszeichnung setzt, bevor man tippt -- und
    // sichtbare Sternchen, die auf Eingabe warten, sind genau das, was beim
    // schnellen Schreiben stoert. Fuer kursiv geht das NICHT: `**` waere dort
    // ein leeres Paar und zugleich der Anfang einer Fettschrift.
    private val FETT = Regex("""\*\*([^\n]*?)\*\*""")
    private val UNTERSTRICHEN = Regex("""__([^\n]*?)__""")
    private val DURCHGESTRICHEN = Regex("""~~([^\n]*?)~~""")

    /**
     * Kursiv: ein Sternchen, das weder von einem Sternchen umgeben ist noch
     * eines direkt daneben hat.
     *
     * Ohne die beiden Umschauungen zerlegte dieser Ausdruck `fett` in zwei
     * kursive Stücke und machte aus der Fettschrift Unsinn.
     */
    private val KURSIV = Regex("""(?<!\*)\*(?!\*)([^*\n]+?)\*(?!\*)""")

    private val UEBERSCHRIFT = Regex("""^(#{1,2}) """, RegexOption.MULTILINE)

    /**
     * Die Auszeichnungen, die die Leiste anbietet.
     *
     * `__` für unterstrichen ist kein Standard-Markdown, dort gibt es
     * dafür nichts, und `__x__` bedeutet dasselbe wie `x`. Wir vergeben es
     * neu, weil beide Clients dieses Projekts denselben Vertrag lesen (SYNC.md)
     * und Fettschrift bereits `**` hat. Ein importierter fremder Markdown-Text
     * würde an dieser einen Stelle anders aussehen als gedacht; das ist der
     * Preis, und er ist klein.
     */
    enum class Art(val zeichen: String) {
        FETT("**"),
        KURSIV("*"),
        UNTERSTRICHEN("__"),
        DURCHGESTRICHEN("~~"),
    }

    enum class Stufe(val vorsatz: String) {
        UEBERSCHRIFT("# "),
        ZWISCHENUEBERSCHRIFT("## "),
        NORMAL(""),
    }

    /** Ergebnis der Auflösung: sichtbarer Text plus beide Richtungen der Zuordnung. */
    class Aufgeloest(
        val text: AnnotatedString,
        private val quellLaenge: Int,
        private val nachTransformiert: IntArray,
        private val nachOriginal: IntArray,
    ) {
        val zuordnung: OffsetMapping = object : OffsetMapping {
            // Beide Richtungen begrenzen die Eingabe. Compose reicht während
            // einer Änderung kurzzeitig Werte herein, die zum ALTEN Text
            // gehören; ohne die Begrenzung wäre das ein Absturz mitten im
            // Tippen, und zwar ein sehr schwer nachstellbarer.
            override fun originalToTransformed(offset: Int): Int =
                nachTransformiert[offset.coerceIn(0, quellLaenge)]

            override fun transformedToOriginal(offset: Int): Int =
                nachOriginal[offset.coerceIn(0, nachOriginal.size - 1)]
        }
    }

    /**
     * Löst Markdown in gestalteten Text plus Zuordnung auf.
     *
     * Die Muster werden unabhängig voneinander angewandt, nicht
     * geschachtelt geparst. Dadurch überlagern sich Auszeichnungen von selbst
     * (`__x__` ist fett und unterstrichen), ohne dass es dafür eine
     * Grammatik bräuchte. Wer die Zeichen wild durcheinanderwirft, bekommt ein
     * merkwürdiges, aber harmloses Ergebnis, und keinen Absturz.
     */
    fun aufloesen(quelle: String): Aufgeloest {
        val versteckt = BooleanArray(quelle.length)
        val stile = mutableListOf<Triple<Int, Int, SpanStyle>>()

        fun paar(muster: Regex, stil: SpanStyle) {
            muster.findAll(quelle).forEach { treffer ->
                val inhalt = treffer.groups[1]?.range ?: return@forEach
                for (i in treffer.range.first until inhalt.first) versteckt[i] = true
                for (i in inhalt.last + 1..treffer.range.last) versteckt[i] = true
                stile += Triple(inhalt.first, inhalt.last + 1, stil)
            }
        }

        // Reihenfolge zählt nur für die Lesbarkeit; die Muster schließen sich
        // gegenseitig über ihre Umschauungen aus.
        paar(FETT, SpanStyle(fontWeight = FontWeight.Bold))
        paar(UNTERSTRICHEN, SpanStyle(textDecoration = TextDecoration.Underline))
        paar(DURCHGESTRICHEN, SpanStyle(textDecoration = TextDecoration.LineThrough))
        paar(KURSIV, SpanStyle(fontStyle = FontStyle.Italic))

        UEBERSCHRIFT.findAll(quelle).forEach { treffer ->
            for (i in treffer.range) versteckt[i] = true
            val zeilenende = quelle.indexOf('\n', treffer.range.last + 1)
                .let { if (it < 0) quelle.length else it }
            // `em` statt `sp`: relativ zur Grundschrift des Feldes. Eine feste
            // Punktgröße wäre auf einer Karte richtig und im Editor daneben.
            val stil = if (treffer.groupValues[1].length == 1) {
                SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold)
            } else {
                SpanStyle(fontSize = 1.22.em, fontWeight = FontWeight.SemiBold)
            }
            stile += Triple(treffer.range.last + 1, zeilenende, stil)
        }

        // ------------------------------------------------ Zuordnung aufbauen
        val nachTransformiert = IntArray(quelle.length + 1)
        val sichtbar = StringBuilder(quelle.length)
        var t = 0
        for (i in quelle.indices) {
            nachTransformiert[i] = t
            if (!versteckt[i]) {
                sichtbar.append(quelle[i])
                t++
            }
        }
        nachTransformiert[quelle.length] = t

        val nachOriginal = IntArray(t + 1)
        nachOriginal[t] = quelle.length
        for (i in quelle.indices) {
            if (!versteckt[i]) nachOriginal[nachTransformiert[i]] = i
        }

        val gestaltet = buildAnnotatedString {
            append(sichtbar.toString())
            stile.forEach { (von, bis, stil) ->
                val a = nachTransformiert[von.coerceIn(0, quelle.length)]
                val b = nachTransformiert[bis.coerceIn(0, quelle.length)]
                if (b > a) addStyle(stil, a, b)
            }
        }

        return Aufgeloest(gestaltet, quelle.length, nachTransformiert, nachOriginal)
    }

    /** Der Text ohne Markierungszeichen. Für Titel, Vorschauen und Ähnliches. */
    fun klartext(quelle: String): String =
        if (quelle.length > GRENZE) quelle else aufloesen(quelle).text.text

    /** Gestalteter Text zum Anzeigen, ohne Editor. */
    fun alsAnnotiert(quelle: String): AnnotatedString =
        if (quelle.length > GRENZE) AnnotatedString(quelle) else aufloesen(quelle).text

    // ------------------------------------------------------------- Bearbeiten

    /**
     * Schaltet eine Auszeichnung für die Auswahl an oder aus.
     *
     * Ohne Auswahl werden beide Zeichen gesetzt und der Cursor dazwischen
     * gestellt, dann tippt man einfach weiter und schreibt fett. Das ist
     * dasselbe Verhalten wie in jeder Textverarbeitung.
     */
    fun umschalten(wert: TextFieldValue, art: Art): TextFieldValue {
        val text = wert.text
        var von = minOf(wert.selection.start, wert.selection.end).coerceIn(0, text.length)
        var bis = maxOf(wert.selection.start, wert.selection.end).coerceIn(0, text.length)

        // 1. Ist hier schon so ausgezeichnet? Dann will man es LOS.
        //
        // Gesucht wird die tatsächliche Auszeichnung, nicht ein Zeichenpaar
        // direkt neben der Auswahl. Das war der Fehler der ersten Fassung: Wer
        // ein fettes Wort markiert, bekommt vom Textfeld eine Auswahl, die die
        // versteckten Zeichen mit einschließt, sie lag also nie exakt
        // zwischen ihnen, und statt zu entfernen wurde ein zweites Paar
        // hineingeschrieben.
        spanneAn(text, art, von, bis)?.let { treffer ->
            return befreien(text, treffer, art, von, bis)
        }

        // 2. Ohne Auswahl gilt das Wort unter dem Cursor.
        //
        // Sonst müsste man erst markieren, um ein einzelnes Wort auszuzeichnen,
        // und genau dieser Umweg macht das Schreiben zäh.
        if (von == bis) {
            wortRund(text, von)?.let { (a, b) ->
                von = a
                bis = b
            }
        }

        // 3. Markierungszeichen an den Rändern nicht mit einpacken. Eine
        // Auswahl, die ein `*` mitnimmt, erzeugt sonst Zeichenfolgen, die
        // niemand mehr auflösen kann.
        while (von < bis && text[von].istMarke()) von++
        while (bis > von && text[bis - 1].istMarke()) bis--

        // 4. Einfassen.
        val zeichen = art.zeichen
        val neu = buildString {
            append(text, 0, von)
            append(zeichen)
            append(text, von, bis)
            append(zeichen)
            append(text, bis, text.length)
        }
        // Der Cursor landet am ENDE des ausgezeichneten Textes, innerhalb der
        // Auszeichnung: Wer gerade "fett" gewählt hat, schreibt fett weiter --
        // so verhält sich jede Textverarbeitung. Eine stehen bleibende Auswahl
        // wäre hier fatal, siehe oben.
        return TextFieldValue(neu, TextRange(bis + zeichen.length))
    }

    /**
     * Nimmt die Auszeichnung von dem Teil, den man gemeint hat, und nur von
     * dem.
     *
     * Ein Leerzeichen trennt Wörter, die Auszeichnung tut es nicht. Schreibt
     * man ein fettes Wort, drückt Leertaste und schreibt weiter, steht das
     * zweite Wort in derselben Auszeichnung wie das erste, der Cursor blieb ja
     * zwischen den Zeichen. Wer dann „fett" antippt, meint das zweite Wort und
     * nicht beide. Die erste Fassung entfernte die Zeichen der ganzen
     * Fundstelle und machte damit auch das erste Wort wieder dünn.
     *
     * Der Rest links und rechts wird deshalb neu eingefasst. Der Leerraum an der
     * Bruchstelle wandert dabei nach draußen: `Hallo Welt` und nicht
     * `**Hallo ** Welt`, ein ausgezeichnetes Leerzeichen sieht man nicht, aber
     * es steht im gespeicherten Text und wandert in den Sync.
     */
    private fun befreien(
        text: String,
        treffer: MatchResult,
        art: Art,
        von: Int,
        bis: Int,
    ): TextFieldValue {
        val inhalt = treffer.groups[1]!!.range
        val c0 = inhalt.first
        val c1 = inhalt.last + 1

        var w0: Int
        var w1: Int
        if (von != bis) {
            w0 = maxOf(von, c0)
            w1 = minOf(bis, c1)
        } else {
            val wort = wortRund(text, von)
            w0 = maxOf(wort?.first ?: c0, c0)
            w1 = minOf(wort?.second ?: c1, c1)
        }
        // Kein brauchbares Ziel: dann gilt die ganze Fundstelle. Das ist der
        // Fall, wenn der Cursor auf einem Zeichen steht, das zu keinem Wort
        // gehoert.
        if (w1 <= w0) {
            w0 = c0
            w1 = c1
        }

        val zeichen = art.zeichen
        val neu = StringBuilder(text.length)
        neu.append(text, 0, treffer.range.first)
        neu.append(eingefasst(text.substring(c0, w0), zeichen))
        neu.append(text, w0, w1)
        val cursor = neu.length
        neu.append(eingefasst(text.substring(w1, c1), zeichen))
        neu.append(text, treffer.range.last + 1, text.length)

        // NUR der Cursor, keine Auswahl -- und zwar hinter dem betroffenen
        // Text. Bliebe er ausgewaehlt, ersetzte der naechste Tastendruck genau
        // das, was man gerade ausgezeichnet hat.
        return TextFieldValue(neu.toString(), TextRange(cursor))
    }

    /**
     * Fasst ein Reststueck wieder ein, mit dem Leerraum an den Raendern
     * ausserhalb der Zeichen.
     *
     * Besteht das Stueck nur aus Leerraum, bleibt es unangetastet: `** **`
     * waere eine Auszeichnung ohne Inhalt, die kein Mensch sieht und die beim
     * naechsten Bearbeiten im Weg steht.
     */
    private fun eingefasst(teil: String, zeichen: String): String {
        val kern = teil.trim()
        if (kern.isEmpty()) return teil
        val vorn = teil.substring(0, teil.indexOfFirst { !it.isWhitespace() })
        val hinten = teil.substring(teil.indexOfLast { !it.isWhitespace() } + 1)
        return vorn + zeichen + kern + zeichen + hinten
    }

    /**
     * Entfernt leer gewordene Zeichenpaare.
     *
     * Läuft bei jeder Eingabe des Nutzers, nicht beim Auszeichnen selbst.
     * Wer den Text eines fetten Wortes wieder wegnimmt, bleibt sonst auf einem
     * `****` sitzen, das er einzeln löschen müsste, und das ist genau der
     * Punkt, an dem eine solche Leiste lästig wird.
     *
     * Kursiv fehlt hier bewusst: Sein leeres Paar wäre `**`, und das ist nicht
     * von einer beginnenden Fettschrift zu unterscheiden. Wer Markdown von Hand
     * tippt, bekäme sie unter den Fingern weggeräumt.
     */
    fun aufraeumen(wert: TextFieldValue): TextFieldValue {
        val text = wert.text
        if (!LEERES_PAAR.containsMatchIn(text)) return wert

        val cursor = wert.selection.start
        val neu = StringBuilder(text.length)
        var verschiebung = 0
        var gelesen = 0

        LEERES_PAAR.findAll(text).forEach { treffer ->
            neu.append(text, gelesen, treffer.range.first)
            // Nur was VOR dem Cursor wegfällt, verschiebt ihn.
            if (cursor > treffer.range.first) {
                verschiebung += minOf(cursor, treffer.range.last + 1) - treffer.range.first
            }
            gelesen = treffer.range.last + 1
        }
        neu.append(text, gelesen, text.length)

        return TextFieldValue(
            neu.toString(),
            TextRange((cursor - verschiebung).coerceIn(0, neu.length)),
        )
    }

    private val LEERES_PAAR = Regex("""\*\*\*\*|____|~~~~""")

    private fun Char.istMarke() = this == '*' || this == '_' || this == '~'

    private fun muster(art: Art): Regex = when (art) {
        Art.FETT -> FETT
        Art.KURSIV -> KURSIV
        Art.UNTERSTRICHEN -> UNTERSTRICHEN
        Art.DURCHGESTRICHEN -> DURCHGESTRICHEN
    }

    /**
     * Die Auszeichnung dieser Art, die die Auswahl berührt, oder `null`.
     *
     * Gesucht wird über dasselbe Muster, das auch die Anzeige benutzt. Damit
     * kann der Knopf nichts anderes melden, als man sieht: Was fett dargestellt
     * wird, gilt hier als fett, und nichts sonst.
     */
    private fun spanneAn(text: String, art: Art, von: Int, bis: Int): MatchResult? {
        if (text.length > GRENZE) return null
        return muster(art).findAll(text).firstOrNull { treffer ->
            val inhalt = treffer.groups[1]?.range ?: return@firstOrNull false
            if (von != bis) {
                // Mit Auswahl: Sie muss den Inhalt berühren.
                return@firstOrNull von < inhalt.last + 1 && bis > inhalt.first
            }

            // Ohne Auswahl gilt die GANZE Fundstelle samt Zeichen, und
            // zusätzlich die Stelle direkt dahinter.
            //
            // Das ist keine Großzügigkeit, sondern nötig: Tippt man ans
            // sichtbare Ende eines fetten Wortes, rechnet die Anzeige diese
            // Stelle auf die Quellposition HINTER den schließenden Zeichen
            // zurück -- man steht also nach dem Tippen dort, wo man nach dem
            // Wort steht. Ohne diese Zeile müsste man den Cursor erst von Hand
            // ins Wort hineinschieben, um die Fettschrift wieder loszuwerden.
            von in treffer.range.first..(treffer.range.last + 1)
        }
    }

    /**
     * Das Wort um diese Stelle, als halboffener Bereich, oder `null`.
     *
     * Buchstaben und Ziffern zählen, sonst nichts. Ein Satzzeichen mit fett zu
     * setzen sieht in jeder Schrift falsch aus.
     */
    private fun wortRund(text: String, stelle: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        var a = stelle.coerceIn(0, text.length)
        var b = a

        // Ueber Markierungszeichen hinweg suchen. Sie sind unsichtbar, und wer
        // ans sichtbare Ende eines fetten Wortes tippt, landet in der Quelle
        // HINTER den schliessenden Zeichen -- er meint aber selbstverstaendlich
        // dieses Wort.
        while (a > 0 && text[a - 1].istMarke()) a--
        while (a > 0 && text[a - 1].isLetterOrDigit()) a--
        while (b < text.length && text[b].istMarke()) b++
        while (b < text.length && text[b].isLetterOrDigit()) b++

        // Die uebersprungenen Zeichen gehoeren nicht zum Wort.
        while (a < b && !text[a].isLetterOrDigit()) a++
        while (b > a && !text[b - 1].isLetterOrDigit()) b--
        return if (b > a) a to b else null
    }

    /**
     * Setzt die Größe der Zeile, in der der Cursor steht.
     *
     * Zeilenweise und nicht auswahlweise, weil eine Überschrift eine Aussage
     * über eine ganze Zeile ist, eine halb überschriftliche Zeile gibt es
     * nicht.
     */
    fun setzeStufe(wert: TextFieldValue, stufe: Stufe): TextFieldValue {
        val text = wert.text
        val cursor = wert.selection.start.coerceIn(0, text.length)
        val zeilenstart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0 || cursor == 0) 0 else it + 1 }

        val alterVorsatz = Stufe.entries
            .filter { it.vorsatz.isNotEmpty() }
            .firstOrNull { text.startsWith(it.vorsatz, zeilenstart) }
            ?.vorsatz
            .orEmpty()

        val ohneAlten = text.removeRange(zeilenstart, zeilenstart + alterVorsatz.length)
        val neu = ohneAlten.substring(0, zeilenstart) +
            stufe.vorsatz +
            ohneAlten.substring(zeilenstart)

        val verschiebung = stufe.vorsatz.length - alterVorsatz.length
        val neuerCursor = (cursor + verschiebung).coerceIn(zeilenstart, neu.length)
        return TextFieldValue(neu, TextRange(neuerCursor))
    }

    /** Welche Stufe die Zeile unter dem Cursor gerade hat. */
    fun stufeAn(wert: TextFieldValue): Stufe {
        val text = wert.text
        val cursor = wert.selection.start.coerceIn(0, text.length)
        val zeilenstart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it < 0 || cursor == 0) 0 else it + 1 }
        return Stufe.entries
            .filter { it.vorsatz.isNotEmpty() }
            .firstOrNull { text.startsWith(it.vorsatz, zeilenstart) }
            ?: Stufe.NORMAL
    }

    /**
     * Welche Auszeichnungen für die Auswahl gerade gelten.
     *
     * Nur exakte Umschließung zählt. Eine Auswahl, die mitten in einem fetten
     * Stück liegt, gilt als nicht ausgezeichnet, der Knopf würde sonst
     * gedrückt aussehen, aber beim Antippen etwas anderes tun, als er anzeigt.
     */
    fun aktiv(wert: TextFieldValue): Set<Art> {
        val text = wert.text
        val von = minOf(wert.selection.start, wert.selection.end).coerceIn(0, text.length)
        val bis = maxOf(wert.selection.start, wert.selection.end).coerceIn(0, text.length)
        return Art.entries.filterTo(mutableSetOf()) { spanneAn(text, it, von, bis) != null }
    }
}

/**
 * Zeigt Markdown gestaltet und ohne seine Markierungszeichen.
 *
 * Ein `object` wäre hier falsch: [VisualTransformation] wird von Compose bei
 * jeder Änderung neu befragt, und das Ergebnis hängt allein vom übergebenen
 * Text ab, Zustand darf es keinen geben.
 */
class Auszeichnungsanzeige : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.length > Auszeichnung.GRENZE) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val aufgeloest = Auszeichnung.aufloesen(text.text)
        return TransformedText(aufgeloest.text, aufgeloest.zuordnung)
    }
}
