package de.notizen.app.bild

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Laedt abgelegte Bilder zum Anzeigen.
 *
 * Bewusst selbst gebaut statt einer Bildbibliothek: Alle Dateien sind lokal,
 * liegen bereits verkleinert und aufrecht (siehe [Bildspeicher]) und haben ein
 * bekanntes Format. Damit faellt genau das weg, wofuer man sonst eine solche
 * Bibliothek nimmt -- Netzwerk, Plattencache, Formaterkennung, Drehung. Uebrig
 * bleiben Herunterrechnen und ein Speichercache, und das sind die dreissig
 * Zeilen hier.
 *
 * Ein `object` und keine Hilt-Abhaengigkeit, weil nichts davon einen Context
 * braucht: sonst muesste jede Anzeigestelle ein ViewModel bemuehen, nur um ein
 * Bild zu zeichnen.
 */
object Bildlader {

    /**
     * Ein Achtel des Heaps. Bilder sind das Erste, was bei Speicherdruck
     * wegfliegen darf -- sie lassen sich jederzeit neu von Platte lesen.
     */
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun laden(datei: File, zielBreitePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            if (!datei.exists() || zielBreitePx <= 0) return@withContext null

            // Laenge und Zeitstempel gehoeren in den Schluessel: Ein Anhang
            // bekommt zwar nie eine neue Datei unter derselben Adresse, aber ein
            // Cache, der das stillschweigend voraussetzt, zeigt im Zweifel
            // monatelang das falsche Bild an.
            val schluessel = "${datei.path}|${datei.length()}|${datei.lastModified()}|$zielBreitePx"
            cache.get(schluessel)?.let { return@withContext it.asImageBitmap() }

            runCatching {
                val kopf = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(datei.path, kopf)
                if (kopf.outWidth <= 0) return@runCatching null

                val optionen = BitmapFactory.Options().apply {
                    inSampleSize = stichprobe(kopf.outWidth, zielBreitePx)
                }
                BitmapFactory.decodeFile(datei.path, optionen)?.also {
                    cache.put(schluessel, it)
                }
            }.getOrNull()?.asImageBitmap()
        }

    /**
     * Groesster Verkleinerungsfaktor, bei dem das Bild noch mindestens so breit
     * ist wie gewuenscht.
     *
     * `inSampleSize` verarbeitet nur Zweierpotenzen; alles andere rundet
     * BitmapFactory selbst ab, und dann weiss niemand mehr, was herauskommt.
     * Rein und deshalb testbar.
     */
    internal fun stichprobe(quellBreite: Int, zielBreite: Int): Int {
        if (zielBreite <= 0 || quellBreite <= zielBreite) return 1
        var faktor = 1
        while (quellBreite / (faktor * 2) >= zielBreite) faktor *= 2
        return faktor
    }
}

/**
 * Laedt ein Bild passend zur angegebenen Anzeigebreite.
 *
 * Gibt `null` zurueck, solange geladen wird oder wenn die Datei fehlt. Die
 * Anzeigestelle entscheidet, was sie in der Zwischenzeit zeigt -- ein
 * eingebauter Platzhalter waere hier die falsche Entscheidung, weil eine Karte
 * im Raster und ein Vollbild sehr verschiedene brauchen.
 */
@Composable
fun rememberBild(datei: File?, zielBreite: Dp): ImageBitmap? {
    val zielBreitePx = with(LocalDensity.current) { zielBreite.roundToPx() }
    var bild by remember(datei?.path, zielBreitePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(datei?.path, zielBreitePx) {
        bild = datei?.let { Bildlader.laden(it, zielBreitePx) }
    }
    return bild
}
