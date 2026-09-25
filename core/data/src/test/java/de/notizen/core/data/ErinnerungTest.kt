package de.notizen.core.data

import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.SyncStatus
import de.notizen.core.data.repository.NoteContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Regeln rund um Erinnerungen -- ohne einen einzigen echten Alarm.
 *
 * Genau dafür liegt der Datenteil in `:core:data` und der `AlarmManager` in
 * `:app`: dass eine Erinnerung existiert, ersetzt wird oder eine Notiz vor der
 * Auto-Archivierung schützt, lässt sich hier prüfen, ohne je etwas klingeln zu
 * lassen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErinnerungTest : DatenbankTestbasis() {

    private val gleich get() = clock.now() + EIN_TAG

    @Test
    fun `eine Notiz traegt hoechstens eine Erinnerung`() = runTest {
        // Mehrere Wecker fuer denselben Zettel waeren eine Aufgabenverwaltung,
        // und die ist diese App nicht. Ein neuer Termin ERSETZT den alten.
        val id = notes.create()
        erinnerungen.setzen(id, gleich)
        erinnerungen.setzen(id, gleich + EIN_TAG)

        val vorhanden = erinnerungen.zuNotiz(id)
        assertEquals(1, vorhanden.size)
        assertEquals(gleich + EIN_TAG, vorhanden.single().triggerAt)
    }

    @Test
    fun `jede Erinnerung bekommt eine eigene Alarmkennung`() = runTest {
        // Zwei gleiche Kennungen wuerden sich gegenseitig abbestellen, und das
        // faende man im Nachhinein nie.
        val kennungen = (1..5).map { erinnerungen.setzen(notes.create(), gleich).alarmId }
        assertEquals(kennungen.size, kennungen.toSet().size)
    }

    @Test
    fun `eine geloeschte Kennung darf wieder vergeben werden`() = runTest {
        // Festgehalten, WEIL es zuerst wie ein Fehler aussah: nach dem Loeschen
        // der letzten Erinnerung faengt die Zaehlung wieder bei eins an. Das ist
        // ungefaehrlich, denn der Aufrufer bestellt den alten Wecker ab, bevor
        // ein neuer mit derselben Kennung gestellt wird -- und selbst wenn nicht,
        // ueberschreibt FLAG_UPDATE_CURRENT den alten Alarm durch den richtigen.
        //
        // Wichtig ist allein, dass GLEICHZEITIG bestehende Erinnerungen sich
        // nicht in die Quere kommen; das prueft der Test darueber.
        val a = notes.create()
        val erste = erinnerungen.setzen(a, gleich)
        erinnerungen.entfernen(a)
        val zweite = erinnerungen.setzen(notes.create(), gleich)

        assertEquals(erste.alarmId, zweite.alarmId)
    }

    @Test
    fun `abhaken loescht die Erinnerung nicht`() = runTest {
        // triggerAt wird synchronisiert. Ein Loeschen kaeme auf dem anderen
        // Geraet als "gibt es nicht mehr" an, BEVOR es dort geklingelt hat.
        val id = notes.create()
        val erinnerung = erinnerungen.setzen(id, gleich)
        erinnerungen.abhaken(erinnerung.id)

        val danach = erinnerungen.zuNotiz(id).single()
        assertTrue("Sie ist abgehakt", danach.isFired)
        assertEquals("Aber noch da", erinnerung.triggerAt, danach.triggerAt)
    }

    @Test
    fun `abgehakte Erinnerungen zaehlen nicht mehr als offen`() = runTest {
        val id = notes.create()
        val erinnerung = erinnerungen.setzen(id, gleich)
        assertEquals(1, erinnerungen.offene().size)

        erinnerungen.abhaken(erinnerung.id)
        assertTrue(erinnerungen.offene().isEmpty())
    }

    @Test
    fun `die Notiz kennt ihre offene Erinnerung`() = runTest {
        // Darueber zeichnet die Karte ihr Glockensymbol.
        val id = notes.create()
        val erinnerung = erinnerungen.setzen(id, gleich)

        assertEquals(erinnerung.id, notes.get(id)!!.offeneErinnerung?.id)

        erinnerungen.abhaken(erinnerung.id)
        assertNull(
            "Eine ausgeloeste Erinnerung ist keine offene mehr",
            notes.get(id)!!.offeneErinnerung,
        )
    }

    @Test
    fun `eine geloeschte Notiz nimmt ihre Erinnerung mit`() = runTest {
        // ON DELETE CASCADE. Ohne das bliebe ein Wecker fuer eine Notiz stehen,
        // die es nicht mehr gibt.
        val id = notes.create()
        erinnerungen.setzen(id, gleich)
        notes.purge(listOf(id))

        assertTrue(erinnerungen.offene().isEmpty())
    }

    @Test
    fun `eine Erinnerung wird zum Abgleich vorgemerkt`() = runTest {
        // triggerAt gehoert zum Sync (SYNC.md 14.8). Ohne die Marke wuerde der
        // Termin nie beim zweiten Client ankommen.
        val id = notes.create()
        val erinnerung = erinnerungen.setzen(id, gleich)

        val marke = db.syncDao().stateOf(EntityType.REMINDER, erinnerung.id)
        assertNotNull("Ohne Marke wird die Erinnerung nie hochgeladen", marke)
        assertEquals(SyncStatus.DIRTY, marke!!.syncStatus)
    }

    @Test
    fun `das Setzen einer Erinnerung aendert die Notiz nicht`() = runTest {
        // updatedAt steuert die Konfliktaufloesung. Ein Wecker ist keine
        // INHALTS-Aenderung -- wer das verwechselt, laesst die Notiz beim
        // naechsten Abgleich gegen die Fassung des anderen Geraets gewinnen,
        // obwohl sich am Text nichts geaendert hat.
        val id = notes.create()
        notes.updateContent(id, NoteContent("Zahnarzt", "Termin bestaetigen"))
        val vorher = notes.get(id)!!.note.updatedAt

        clock.advanceBy(EIN_TAG)
        erinnerungen.setzen(id, gleich)

        assertEquals(vorher, notes.get(id)!!.note.updatedAt)
    }

    @Test
    fun `entfernen gibt zurueck was abzubestellen ist`() = runTest {
        // Der Aufrufer in :app braucht die alarmId, um den Wecker wirklich zu
        // stornieren. Gaebe entfernen() nur Unit zurueck, bliebe der Alarm im
        // System stehen und klingelte fuer eine Erinnerung, die es nicht mehr
        // gibt.
        val id = notes.create()
        val gesetzt = erinnerungen.setzen(id, gleich)

        val entfernt = erinnerungen.entfernen(id)
        assertEquals(listOf(gesetzt.alarmId), entfernt.map { it.alarmId })
        assertTrue(erinnerungen.zuNotiz(id).isEmpty())
    }

    @Test
    fun `Favoriten stehen in der Liste vorne`() = runTest {
        val zuerstErstellt = notes.create()
        notes.updateContent(zuerstErstellt, NoteContent("Alt", "x"))
        clock.advanceBy(1000)
        val spaeter = notes.create()
        notes.updateContent(spaeter, NoteContent("Neu", "y"))

        notes.setFavorite(listOf(zuerstErstellt), true)

        val reihenfolge = notes.observe(Stage.INBOX).first().map { it.note.id }

        assertEquals(
            "Der Favorit muss vor der neueren Notiz stehen",
            zuerstErstellt,
            reihenfolge.first(),
        )
        assertFalse(reihenfolge.isEmpty())
    }
}
