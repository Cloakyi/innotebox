package de.notizen.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.notizen.core.data.prefs.Einstellungen
import de.notizen.core.data.prefs.KiSiegel
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Text, dem man zustimmt. Der Dialog zeigt genau diese Sätze, und das
 * Siegel hält ihre Prüfsumme fest. Ändert sich ein Wort, passt keine alte
 * Zustimmung mehr, und die App fragt neu.
 */
object KiZustimmungstext {
    const val TITEL = "KI auf dem Gerät"

    val ABSAETZE = listOf(
        "InNoteBox kann dir Titel vorschlagen, Aufnahmen in Text umwandeln und Transkripte " +
            "aufbereiten. Beim nächtlichen Archivieren vergibt sie außerdem Titel und einen " +
            "passenden Tag aus deinen vorhandenen. Das rechnet die KI deines Geräts; deine " +
            "Notizen verlassen es dabei nicht.",
        "Die Schnittstelle dafür heißt ML Kit und stammt von Google. Sie schickt Google Angaben " +
            "über ihre Nutzung: Gerät und App, Leistung, Fehlercodes und die eingestellten " +
            "Sprachen. Inhalte deiner Notizen sind nicht dabei.",
        "Vorschläge der KI können falsch sein. Nach den Bedingungen von Google ist die KI nur " +
            "für Volljährige.",
        "Die App versiegelt deine Zustimmung mit einem Schlüssel, den es nur auf diesem Gerät " +
            "gibt. Schaltest du die KI in den Einstellungen aus, löscht sie Zustimmung und " +
            "Schlüssel.",
    )

    const val ALTER = "Ich bin mindestens 18 Jahre alt."

    /** SHA-256 über Titel, Absätze und die Angabe zum Alter, in dieser Reihenfolge. */
    val pruefsumme: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest((listOf(TITEL) + ABSAETZE + ALTER).joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Ein Schlüssel zum Signieren, der dieses Gerät nicht verlassen kann.
 * Getrennt vom Siegel, damit sich das Siegel ohne Android prüfen lässt.
 */
interface Schluesselbund {
    /** Verwirft einen vorhandenen Schlüssel und legt einen neuen an. */
    fun neuAnlegen()
    fun signieren(daten: ByteArray): ByteArray
    fun pruefen(daten: ByteArray, signatur: ByteArray): Boolean
    fun loeschen()
}

/**
 * Der Schlüssel im Android Keystore: EC P-256, nicht exportierbar, nach
 * Möglichkeit im eigenen Sicherheitschip (StrongBox), sonst in der
 * vertrauenswürdigen Umgebung des Prozessors. Android löscht ihn beim
 * Deinstallieren der App; auf ein anderes Gerät lässt er sich nicht mitnehmen.
 */
class AndroidSchluesselbund @Inject constructor() : Schluesselbund {

    private fun speicher(): KeyStore = KeyStore.getInstance(ANBIETER).apply { load(null) }

    override fun neuAnlegen() {
        loeschen()
        try {
            erzeugen(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            erzeugen(strongBox = false)
        }
    }

    private fun erzeugen(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANBIETER)
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    override fun signieren(daten: ByteArray): ByteArray {
        val schluessel = speicher().getKey(ALIAS, null) as PrivateKey
        return Signature.getInstance(VERFAHREN).run {
            initSign(schluessel)
            update(daten)
            sign()
        }
    }

    override fun pruefen(daten: ByteArray, signatur: ByteArray): Boolean {
        val zertifikat = speicher().getCertificate(ALIAS) ?: return false
        return Signature.getInstance(VERFAHREN).run {
            initVerify(zertifikat.publicKey)
            update(daten)
            verify(signatur)
        }
    }

    override fun loeschen() {
        val s = speicher()
        if (s.containsAlias(ALIAS)) s.deleteEntry(ALIAS)
    }

    private companion object {
        const val ANBIETER = "AndroidKeyStore"
        const val ALIAS = "ki_zustimmung"
        const val VERFAHREN = "SHA256withECDSA"
    }
}

/**
 * Aufzeichnung und Siegel der Zustimmung zur KI (seit Alpha 9).
 *
 * Die Aufzeichnung ist schlichter Text, eine Angabe je Zeile (`zweck`,
 * `text`, `volljaehrig`, `zeitpunkt`, `app`), das Siegel ihre Signatur mit dem
 * Schlüssel aus [Schluesselbund], als Base64. Gültig ist eine Zustimmung nur,
 * wenn die Signatur mit dem Schlüssel dieses Geräts aufgeht, die Prüfsumme zum
 * heutigen Text passt und die Volljährigkeit darin steht. Eine veränderte,
 * kopierte oder erfundene Zustimmung fällt damit durch.
 */
@Singleton
class Zustimmungssiegel @Inject constructor(
    private val schluesselbund: Schluesselbund,
) : KiSiegel {

    // Das Ergebnis der letzten Prüfung. Der Schlüsselspeicher ist ein Dienst
    // des Systems; ihn bei jeder Einstellung neu zu fragen, wäre unnötig.
    @Volatile
    private var zuletzt: Triple<String, String, Boolean>? = null

    fun aufzeichnung(zeitpunkt: Long, appVersion: Int): String = listOf(
        "zweck=$ZWECK",
        "text=${KiZustimmungstext.pruefsumme}",
        "volljaehrig=ja",
        "zeitpunkt=$zeitpunkt",
        "app=$appVersion",
    ).joinToString("\n")

    /** Legt einen neuen Schlüssel an und gibt das Siegel über [aufzeichnung] zurück. */
    fun versiegeln(aufzeichnung: String): String {
        zuletzt = null
        schluesselbund.neuAnlegen()
        return Base64.getEncoder().encodeToString(schluesselbund.signieren(aufzeichnung.toByteArray(Charsets.UTF_8)))
    }

    override fun gueltig(aufzeichnung: String, siegel: String): Boolean {
        zuletzt?.let { (a, s, ergebnis) -> if (a == aufzeichnung && s == siegel) return ergebnis }
        val ergebnis = pruefen(aufzeichnung, siegel)
        zuletzt = Triple(aufzeichnung, siegel, ergebnis)
        return ergebnis
    }

    private fun pruefen(aufzeichnung: String, siegel: String): Boolean {
        val felder = felder(aufzeichnung) ?: return false
        if (felder["zweck"] != ZWECK) return false
        if (felder["text"] != KiZustimmungstext.pruefsumme) return false
        if (felder["volljaehrig"] != "ja") return false
        if (felder["zeitpunkt"]?.toLongOrNull() == null) return false
        val signatur = runCatching { Base64.getDecoder().decode(siegel) }.getOrNull() ?: return false
        return runCatching {
            schluesselbund.pruefen(aufzeichnung.toByteArray(Charsets.UTF_8), signatur)
        }.getOrDefault(false)
    }

    /** Löscht den Schlüssel. Danach gilt keine Zustimmung mehr, auch keine aufbewahrte. */
    fun loeschen() {
        zuletzt = null
        schluesselbund.loeschen()
    }

    /** Wann zugestimmt wurde, gelesen aus der Aufzeichnung. */
    fun zeitpunkt(aufzeichnung: String): Long? = felder(aufzeichnung)?.get("zeitpunkt")?.toLongOrNull()

    private fun felder(aufzeichnung: String): Map<String, String>? {
        val paare = aufzeichnung.split("\n").map { zeile ->
            val i = zeile.indexOf('=')
            if (i <= 0) return null
            zeile.substring(0, i) to zeile.substring(i + 1)
        }
        val karte = paare.toMap()
        return if (karte.size == paare.size) karte else null
    }

    private companion object {
        const val ZWECK = "ki-auf-dem-geraet"
    }
}

/**
 * Erteilen und Widerrufen der Zustimmung, an einer Stelle für Startdialog und
 * Einstellungen.
 */
@Singleton
class KiZustimmung @Inject constructor(
    @ApplicationContext private val context: Context,
    private val einstellungen: Einstellungen,
    private val siegel: Zustimmungssiegel,
    private val clock: Clock,
) {

    /**
     * Legt eine neue Zustimmung mit neuem Schlüssel an und schaltet die KI ein.
     * `false`, wenn der Schlüsselspeicher streikt; die KI bleibt dann aus, und
     * die App fragt beim nächsten Start wieder.
     */
    suspend fun erteilen(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val aufzeichnung = siegel.aufzeichnung(clock.now(), appVersion())
            einstellungen.kiEinschalten(aufzeichnung, siegel.versiegeln(aufzeichnung))
        }.isSuccess
    }

    /** Schaltet die KI aus und löscht Zustimmung und Schlüssel restlos. */
    suspend fun widerrufen() = withContext(Dispatchers.IO) {
        einstellungen.kiAusschalten()
        runCatching { siegel.loeschen() }
        Unit
    }

    /** Wann der gültigen Zustimmung zugestimmt wurde. */
    fun zeitpunkt(aufzeichnung: String?): Long? = aufzeichnung?.let(siegel::zeitpunkt)

    private fun appVersion(): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class KiSiegelModule {

    @Binds
    @Singleton
    abstract fun bindSchluesselbund(impl: AndroidSchluesselbund): Schluesselbund

    @Binds
    abstract fun bindKiSiegel(impl: Zustimmungssiegel): KiSiegel
}
