package de.notizen.app.teilen

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import de.notizen.app.bild.Bildspeicher
import de.notizen.app.ui.editor.BILDER_HOECHSTENS
import de.notizen.core.data.repository.BildRepository
import de.notizen.core.data.repository.NoteContent
import de.notizen.core.data.repository.NoteRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Was eine andere App über „Teilen“ hereinreicht. Beides darf leer sein. */
data class Geteilt(val text: String, val bilder: List<Uri>)

/** Wie das Übernehmen ausging: die neue Notiz und, wo nötig, ein Satz dazu. */
data class Teilergebnis(val notizId: String?, val hinweis: String?)

/**
 * Liest aus einem Teilen-Intent, was InNoteBox übernimmt: Text und Bilder.
 *
 * Bilder nur von `content`-Adressen und nie aus dem eigenen Provider. Die
 * Adresse kommt von einer fremden App, und über sie ließen sich sonst Dateien
 * dieser App in eine Notiz holen. Höchstens so viele wie im Editor auf einmal.
 */
fun geteiltAus(intent: Intent, eigeneAutoritaet: String): Geteilt {
    val text = geteilterText(
        betreff = intent.getStringExtra(Intent.EXTRA_SUBJECT),
        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
    )
    val adressen = when {
        intent.type?.startsWith("image/") != true -> emptyList()
        intent.action == Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
    val bilder = adressen
        .filter { it.scheme == "content" && it.authority != eigeneAutoritaet }
        .take(BILDER_HOECHSTENS)
    return Geteilt(text, bilder)
}

/**
 * Der Text einer geteilten Sache: Betreff und Text, ohne Dopplung.
 *
 * Ein Browser schickt den Titel der Seite als Betreff und die Adresse als Text.
 * Beides gehört in die Notiz, der Betreff aber nicht in den Titel. Den vergibt
 * man in dieser App selbst, spätestens wenn die Notiz den Eingang verlässt.
 */
internal fun geteilterText(betreff: String?, text: String?): String {
    val oben = betreff?.trim().orEmpty()
    val unten = text?.trim().orEmpty()
    return when {
        oben.isEmpty() -> unten
        unten.isEmpty() -> oben
        oben in unten -> unten
        else -> oben + "\n\n" + unten
    }
}

/**
 * Macht aus Geteiltem eine neue Notiz im Eingang.
 *
 * Die Bilder gehen denselben Weg wie aus dem Editor: verkleinert, aufrecht und
 * neu geschrieben, nie das Original. Sie werden zuerst abgelegt. Lässt sich
 * keins lesen und gibt es auch keinen Text, entsteht gar keine Notiz statt
 * einer leeren.
 */
@Singleton
class Teilen @Inject constructor(
    private val notes: NoteRepository,
    private val bildspeicher: Bildspeicher,
    private val bilder: BildRepository,
) {
    suspend fun anlegen(geteilt: Geteilt): Teilergebnis {
        val abgelegt = geteilt.bilder.mapNotNull { adresse ->
            val anhangId = UUID.randomUUID().toString()
            bildspeicher.uebernehmen(adresse, anhangId)?.let { anhangId to it.datei }
        }
        val fehlend = geteilt.bilder.size - abgelegt.size

        if (geteilt.text.isEmpty() && abgelegt.isEmpty()) {
            return Teilergebnis(
                notizId = null,
                hinweis = if (fehlend > 0) {
                    "Das Bild ließ sich nicht lesen."
                } else {
                    "Damit kann InNoteBox nichts anfangen. Übernehmen lassen sich Text, Links und Bilder."
                },
            )
        }

        val id = notes.create()
        if (geteilt.text.isNotEmpty()) notes.updateContent(id, NoteContent(title = "", body = geteilt.text))
        abgelegt.forEach { (anhangId, datei) -> bilder.hinzufuegen(id, anhangId, datei) }

        return Teilergebnis(
            notizId = id,
            hinweis = when (fehlend) {
                0 -> null
                1 -> "Ein Bild ließ sich nicht lesen."
                else -> "$fehlend Bilder ließen sich nicht lesen."
            },
        )
    }
}
