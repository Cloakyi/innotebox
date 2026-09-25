package de.notizen.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Das Siegel unter der Zustimmung zur KI.
 *
 * Auf dem Gerät liegt der Schlüssel im Android Keystore, den es auf der JVM
 * nicht gibt. Geprüft wird hier alles andere mit einem echten EC-Schlüssel
 * aus Java: Was ist gültig, und woran fällt eine veränderte, fremde oder
 * zurückgezogene Zustimmung durch.
 */
class ZustimmungssiegelTest {

    /** Wie der Keystore, nur im Speicher: ein EC-Schlüssel, SHA256withECDSA. */
    private class SoftwareSchluesselbund : Schluesselbund {
        var paar: KeyPair? = null

        override fun neuAnlegen() {
            paar = KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        }

        override fun signieren(daten: ByteArray): ByteArray =
            Signature.getInstance("SHA256withECDSA").run {
                initSign(paar!!.private)
                update(daten)
                sign()
            }

        override fun pruefen(daten: ByteArray, signatur: ByteArray): Boolean {
            val p = paar ?: return false
            return Signature.getInstance("SHA256withECDSA").run {
                initVerify(p.public)
                update(daten)
                verify(signatur)
            }
        }

        override fun loeschen() {
            paar = null
        }
    }

    private val schluesselbund = SoftwareSchluesselbund()
    private val siegel = Zustimmungssiegel(schluesselbund)

    private fun neueZustimmung(zeitpunkt: Long = 1_790_000_000_000L): Pair<String, String> {
        val aufzeichnung = siegel.aufzeichnung(zeitpunkt, appVersion = 9)
        return aufzeichnung to siegel.versiegeln(aufzeichnung)
    }

    @Test
    fun `eine frisch versiegelte Zustimmung gilt`() {
        val (aufzeichnung, s) = neueZustimmung()
        assertTrue(siegel.gueltig(aufzeichnung, s))
    }

    @Test
    fun `eine veraenderte Aufzeichnung faellt durch`() {
        val (aufzeichnung, s) = neueZustimmung()
        val veraendert = aufzeichnung.replace("zeitpunkt=1790000000000", "zeitpunkt=1790000000001")
        assertNotEquals(aufzeichnung, veraendert)
        assertFalse(siegel.gueltig(veraendert, s))
    }

    @Test
    fun `eine Zustimmung zu einem anderen Text gilt nicht, auch wenn sie echt signiert ist`() {
        // Ändert sich der Text des Dialogs, soll neu gefragt werden. Selbst eine
        // mit dem richtigen Schlüssel signierte alte Fassung zählt dann nicht.
        val (aufzeichnung, _) = neueZustimmung()
        val andererText = aufzeichnung.replace(KiZustimmungstext.pruefsumme, "0".repeat(64))
        val echtSigniert = java.util.Base64.getEncoder()
            .encodeToString(schluesselbund.signieren(andererText.toByteArray(Charsets.UTF_8)))
        assertFalse(siegel.gueltig(andererText, echtSigniert))
    }

    @Test
    fun `ohne die Angabe zur Volljaehrigkeit gilt nichts, auch echt signiert`() {
        val (aufzeichnung, _) = neueZustimmung()
        val ohneAlter = aufzeichnung.replace("volljaehrig=ja", "volljaehrig=nein")
        val echtSigniert = java.util.Base64.getEncoder()
            .encodeToString(schluesselbund.signieren(ohneAlter.toByteArray(Charsets.UTF_8)))
        assertFalse(siegel.gueltig(ohneAlter, echtSigniert))
    }

    @Test
    fun `nach dem Loeschen gilt keine Zustimmung mehr`() {
        val (aufzeichnung, s) = neueZustimmung()
        assertTrue(siegel.gueltig(aufzeichnung, s))
        siegel.loeschen()
        assertFalse(siegel.gueltig(aufzeichnung, s))
    }

    @Test
    fun `eine neue Zustimmung macht die alte ungueltig`() {
        // Jede Zustimmung bekommt einen neuen Schlüssel; die alte passt nicht mehr dazu.
        val (alt, altesSiegel) = neueZustimmung(1_790_000_000_000L)
        val (neu, neuesSiegel) = neueZustimmung(1_800_000_000_000L)
        assertFalse(siegel.gueltig(alt, altesSiegel))
        assertTrue(siegel.gueltig(neu, neuesSiegel))
    }

    @Test
    fun `eine Zustimmung von einem anderen Geraet gilt hier nicht`() {
        val fremd = Zustimmungssiegel(SoftwareSchluesselbund())
        val aufzeichnung = fremd.aufzeichnung(1_790_000_000_000L, appVersion = 9)
        val fremdesSiegel = fremd.versiegeln(aufzeichnung)
        neueZustimmung()
        assertFalse(siegel.gueltig(aufzeichnung, fremdesSiegel))
    }

    @Test
    fun `Unsinn als Siegel oder Aufzeichnung wirft nicht, sondern gilt nicht`() {
        neueZustimmung()
        assertFalse(siegel.gueltig("kein gleichheitszeichen", "AAAA"))
        assertFalse(siegel.gueltig(siegel.aufzeichnung(1L, 9), "%%% kein base64 %%%"))
        assertFalse(siegel.gueltig("", ""))
    }

    @Test
    fun `der Zeitpunkt steht in der Aufzeichnung`() {
        val (aufzeichnung, _) = neueZustimmung(1_790_000_000_000L)
        assertEquals(1_790_000_000_000L, siegel.zeitpunkt(aufzeichnung))
    }

    @Test
    fun `die Pruefsumme haengt am Text des Dialogs`() {
        assertEquals(64, KiZustimmungstext.pruefsumme.length)
        assertTrue(KiZustimmungstext.ABSAETZE.any { "Schlüssel" in it })
    }
}
