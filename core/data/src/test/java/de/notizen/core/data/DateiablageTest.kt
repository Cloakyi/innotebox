package de.notizen.core.data

import de.notizen.core.data.aussen.endungFuer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Dateiendung heruntergeladener Anhänge.
 *
 * **Warum das geprüft wird, obwohl es nach Kosmetik aussieht:** Die Endung
 * entscheidet, ob ein Bildbetrachter oder ein Musikprogramm die Datei anfassen
 * kann, wenn der Nutzer sie aus der App heraus teilt. Und eine geratene Endung
 * ist schlimmer als gar keine — eine Datei, die `jpg` heißt und keines ist,
 * führt jedes Programm in die Irre.
 */
class DateiablageTest {

    @Test
    fun `die Typen, die diese App wirklich erzeugt`() {
        assertEquals("jpg", endungFuer("image/jpeg"))
        assertEquals("wav", endungFuer("audio/wav"))
    }

    @Test
    fun `Parameter hinter dem Semikolon zaehlen nicht mit`() {
        // Drive und HTTP haengen gern ein Charset an. Ohne das Abschneiden
        // faende die Tabelle nichts und jede Datei hiesse `bin`.
        assertEquals("jpg", endungFuer("image/jpeg; charset=binary"))
        assertEquals("wav", endungFuer("audio/wav ; q=1"))
    }

    @Test
    fun `Grossschreibung aendert nichts`() {
        assertEquals("png", endungFuer("IMAGE/PNG"))
    }

    @Test
    fun `Unbekanntes wird bin, nicht geraten`() {
        assertEquals("bin", endungFuer("application/x-irgendwas"))
        assertEquals("bin", endungFuer(""))
    }

    @Test
    fun `die Schreibweisen, die andere Clients schicken koennten`() {
        // Ein spaeterer Web-Client liest dieselben Dateien und schreibt
        // vielleicht andere, ebenso gueltige Namen fuer denselben Typ.
        assertEquals("jpg", endungFuer("image/jpg"))
        assertEquals("wav", endungFuer("audio/x-wav"))
        assertEquals("heic", endungFuer("image/heif"))
    }
}
