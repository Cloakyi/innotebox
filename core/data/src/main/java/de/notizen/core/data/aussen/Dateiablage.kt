package de.notizen.core.data.aussen

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die Endung, unter der ein Anhang abgelegt wird.
 *
 * Reine Funktion, damit sie prüfbar ist. Die Endung ist nicht Kosmetik: Sie
 * entscheidet, ob ein Bildbetrachter oder ein Musikprogramm die Datei anfassen
 * kann, wenn der Nutzer sie aus der App heraus teilt.
 *
 * Unbekanntes wird `bin`. Eine geratene Endung wäre schlimmer als gar keine,
 * eine Datei, die `jpg` heißt und keines ist, führt jedes Programm in die Irre.
 */
fun endungFuer(mimeType: String): String = when (mimeType.substringBefore(';').trim().lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/heic", "image/heif" -> "heic"
    "image/gif" -> "gif"
    "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
    "audio/mpeg" -> "mp3"
    "audio/mp4", "audio/aac" -> "m4a"
    "audio/ogg" -> "ogg"
    else -> "bin"
}

/**
 * Legt heruntergeladene Anhänge im privaten Speicher der App ab.
 *
 * Ein eigener Ordner, nicht der von Bildern oder Aufnahmen. Die beiden
 * bestehenden Ordner gehören dem, was auf diesem Gerät entstanden ist; hier
 * liegt, was von woanders kam. Getrennt zu halten kostet nichts und erspart die
 * Frage, wem eine Datei gehört, wenn einmal aufgeräumt werden muss.
 *
 * Der Pfad landet als `localPath` an der Anhangszeile, und die Oberfläche liest
 * ausschließlich den, sie muss also nicht wissen, woher eine Datei stammt.
 */
@Singleton
class Dateiablage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : Anhangablage {

    override fun ziel(anhangId: String, mimeType: String): File =
        ziel("$anhangId.${endungFuer(mimeType)}")

    override fun ziel(dateiname: String): File {
        val ordner = File(context.filesDir, ORDNER).apply { mkdirs() }
        return File(ordner, dateiname)
    }

    private companion object {
        const val ORDNER = "anhaenge"
    }
}
