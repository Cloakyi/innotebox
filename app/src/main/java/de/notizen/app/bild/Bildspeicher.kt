package de.notizen.app.bild

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.util.pruefsummeVon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Ein fertig abgelegtes Bild. */
data class Bildablage(val datei: File, val breite: Int, val hoehe: Int, val hash: String)

/**
 * Legt Bilder ab und normalisiert sie dabei.
 *
 * Es wird nie die Originaldatei gespeichert. Jedes Bild wird einmal
 * dekodiert, auf hoechstens [MAX_KANTE] Pixel lange Kante verkleinert und als
 * JPEG neu geschrieben. Das ist in SYNC.md 14.6 als Vertrag festgehalten und hat
 * zwei Gruende:
 *
 *  1. Die EXIF-Drehung ist danach erledigt. `ImageDecoder` wendet sie beim
 *     Dekodieren an (fuer JPEG und HEIC -- fuer RAW nicht, siehe unten), und
 *     das neu geschriebene JPEG steht aufrecht ohne Orientierungs-Tag. Ab da
 *     darf jede Anzeigestelle die Datei einfach zeichnen. Wer stattdessen das
 *     Original behaelt, muss die Drehung an JEDER Anzeigestelle erneut
 *     beruecksichtigen -- und vergisst sie an einer.
 *  2. Groesse. Ein Handyfoto sind 4000x3000 Pixel und mehrere Megabyte. In
 *     einer Notiz, die als Karte in einem Raster steht, ist das Verschwendung
 *     auf Platte, im Speicher und spaeter in der Drive-Uebertragung.
 *
 * BEKANNTE GRENZE: Fuer RAW-Dateien (DNG) wendet `ImageDecoder` die
 * EXIF-Drehung NICHT an -- ein so importiertes Bild kann quer liegen. Der
 * Systembildwaehler liefert auf einem Telefon praktisch immer JPEG oder HEIC;
 * gegen RAW wird hier bewusst nichts unternommen, statt eine Drehungslogik zu
 * bauen, die im Normalfall nie laeuft und deshalb auch nie auffaellt, wenn sie
 * falsch ist.
 */
@Singleton
class Bildspeicher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun ordner(): File = File(context.filesDir, ORDNER).apply { mkdirs() }

    fun datei(anhangId: String): File = File(ordner(), "$anhangId.jpg")

    /** Ob ueberhaupt eine Kamera-App bereitsteht. */
    fun kameraVorhanden(): Boolean {
        val absicht = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        return absicht.resolveActivity(context.packageManager) != null ||
            context.packageManager.queryIntentActivities(absicht, 0).isNotEmpty() ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * Ziel fuer die Kamera-App.
     *
     * Liegt im Cache, nicht bei den Bildern: Bricht der Nutzer das Fotografieren
     * ab, bleibt eine leere Datei zurueck, und die soll nicht zwischen echten
     * Anhaengen stehen. Aus dem Cache raeumt sie notfalls das System weg.
     *
     * Schreibrechte fuer die Kamera-App muessen NICHT gesetzt werden: Android
     * ueberfuehrt `EXTRA_OUTPUT` beim Start selbst in ClipData und setzt die
     * Rechte dabei (`Intent.migrateExtraStreamToClipData`). Wer hier von Hand
     * nachhilft, doppelt nur.
     */
    fun kameraZiel(): Pair<File, Uri> {
        val ordner = File(context.cacheDir, KAMERA).apply { mkdirs() }
        val datei = File(ordner, "kamera_${System.currentTimeMillis()}.jpg")
        val adresse = FileProvider.getUriForFile(context, "${context.packageName}.dateien", datei)
        return datei to adresse
    }

    /**
     * Uebernimmt ein Bild von irgendwoher (Galerie, Kamera, fremde App).
     *
     * Gibt `null` zurueck, wenn die Quelle nicht lesbar oder kein Bild ist --
     * ein Absturz waere hier die schlechteste Antwort, denn die Adresse kommt
     * von aussen und die App hat keine Zusage darueber, was dahintersteckt.
     */
    suspend fun uebernehmen(quelle: Uri, anhangId: String): Bildablage? =
        ablegen(anhangId) { ImageDecoder.createSource(context.contentResolver, quelle) }

    /**
     * Dieselbe Uebernahme fuer eine Datei -- der Weg der Kamera.
     *
     * Eigener Einstieg statt `Uri.fromFile`, weil die Datei im eigenen Cache
     * liegt: Sie ueber den ContentResolver zu lesen hiesse, den Umweg ueber
     * einen Provider zu nehmen, um an die eigene Platte zu kommen.
     */
    suspend fun uebernehmen(quelle: File, anhangId: String): Bildablage? =
        ablegen(anhangId) { ImageDecoder.createSource(quelle) }

    private suspend fun ablegen(
        anhangId: String,
        quelle: () -> ImageDecoder.Source,
    ): Bildablage? = withContext(Dispatchers.IO) {
        runCatching {
            val bild = dekodieren(quelle())
            try {
                schreiben(bild, anhangId)
            } finally {
                bild.recycle()
            }
        }.getOrNull()
    }

    /** Raeumt liegen gebliebene Kamera-Zwischendateien weg. */
    fun kameraAufraeumen() {
        runCatching { File(context.cacheDir, KAMERA).listFiles()?.forEach { it.delete() } }
    }

    private fun dekodieren(quellstueck: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(quellstueck) { decoder, info, _ ->
            // Software, weil das Bild gleich wieder ausgelesen und als JPEG
            // geschrieben wird. Ein Hardware-Bitmap liegt im Grafikspeicher und
            // ist dafuer der falsche Ort.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (breite, hoehe) = zielgroesse(info.size.width, info.size.height)
            decoder.setTargetSize(breite, hoehe)
        }

    private fun schreiben(bild: Bitmap, anhangId: String): Bildablage {
        val ziel = datei(anhangId)
        ziel.outputStream().use { bild.compress(Bitmap.CompressFormat.JPEG, QUALITAET, it) }
        return Bildablage(ziel, bild.width, bild.height, pruefsummeVon(ziel))
    }

    companion object {
        const val ORDNER = "bilder"
        private const val KAMERA = "kamera"

        /**
         * Laengste Kante nach dem Verkleinern.
         *
         * 2048 ist gross genug, dass ein Bild formatfuellend auf einem heutigen
         * Telefon noch scharf ist, und klein genug, dass eine Notiz mit zehn
         * Fotos nicht fuenfzig Megabyte belegt.
         */
        const val MAX_KANTE = 2048

        /** JPEG-Qualitaet. Unter 80 werden Flaechen sichtbar fleckig. */
        private const val QUALITAET = 88

        /**
         * Zielmasse unter Beibehaltung des Seitenverhaeltnisses.
         *
         * Kleinere Bilder werden NICHT vergroessert -- das brauchte Rechenzeit
         * und Platz, ohne ein einziges Detail hinzuzufuegen.
         *
         * Rein und ohne Android-Bezug, damit es sich testen laesst.
         */
        internal fun zielgroesse(
            breite: Int,
            hoehe: Int,
            maxKante: Int = MAX_KANTE,
        ): Pair<Int, Int> {
            val laengste = max(breite, hoehe)
            if (laengste <= maxKante || laengste <= 0) return breite to hoehe
            val faktor = maxKante.toDouble() / laengste
            return max(1, (breite * faktor).roundToInt()) to
                max(1, (hoehe * faktor).roundToInt())
        }
    }
}
