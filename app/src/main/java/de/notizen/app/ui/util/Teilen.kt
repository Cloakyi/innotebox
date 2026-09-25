package de.notizen.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.notizen.core.data.db.relation.NoteWithRelations
import java.io.File

/**
 * Notizen über das System-Teilen-Blatt weitergeben.
 *
 * Die App wählt bewusst **kein** Ziel aus: Sie übergibt alles an Android, und
 * der Nutzer entscheidet im Systemdialog, wohin es geht. Alles andere wäre eine
 * Datenweitergabe hinter seinem Rücken.
 *
 * **Anhänge gehen mit.** Eine Audionotiz zu teilen und nur ihr Transkript zu
 * verschicken wäre die halbe Sache — die Aufnahme ist der eigentliche Inhalt.
 * Wo sowohl Datei als auch Text vorhanden sind, bekommt die Gegenstelle beides:
 * die Datei zum Anhören, den Text zum Lesen.
 */
fun teileNotizen(context: Context, notizen: List<NoteWithRelations>) {
    if (notizen.isEmpty()) return

    val text = notizen.joinToString("\n\n---\n\n") { alsText(it) }
    val betreff = notizen.singleOrNull()?.note?.title?.takeIf { it.isNotBlank() }
    val dateien = notizen.flatMap { dateienVon(context, it) }

    val absicht = when {
        dateien.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        // Genau eine Datei: ACTION_SEND. Viele Ziele nehmen nur diese Form an,
        // und mit ACTION_SEND_MULTIPLE bei einer einzigen Datei faellt man bei
        // manchen durch.
        dateien.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = typVon(notizen)
            putExtra(Intent.EXTRA_STREAM, dateien.single())
            putExtra(Intent.EXTRA_TEXT, text)
        }

        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = typVon(notizen)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(dateien))
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    absicht.apply {
        betreff?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        // Ohne diese Erlaubnis bekommt die Gegenstelle eine Adresse, die sie
        // nicht oeffnen darf -- und das Teilen scheitert erst DORT, wo niemand
        // mehr versteht, warum.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(
            absicht,
            if (notizen.size == 1) "Notiz teilen" else "${notizen.size} Notizen teilen",
        ),
    )
}

/**
 * Die teilbaren Dateien einer Notiz.
 *
 * Über einen `FileProvider`, nicht als `file://`-Adresse: Seit Android 7 löst
 * eine solche Adresse in einer fremden App eine `FileUriExposedException` aus.
 */
private fun dateienVon(context: Context, notiz: NoteWithRelations): List<Uri> =
    notiz.attachments.mapNotNull { anhang ->
        val datei = File(anhang.localPath)
        if (!datei.exists()) return@mapNotNull null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.dateien", datei)
        }.getOrNull()
    }

/**
 * Der gemeinsame Typ aller mitgeschickten Dateien.
 *
 * Sind es verschiedene, bleibt nur `* / *`. Ein erfundener gemeinsamer Typ
 * würde Ziele anbieten, die mit der Hälfte der Dateien nichts anfangen können.
 */
private fun typVon(notizen: List<NoteWithRelations>): String {
    val typen = notizen.flatMap { it.attachments }.map { it.mimeType }.toSet()
    return typen.singleOrNull() ?: "*/*"
}

/**
 * Klartext einer Notiz.
 *
 * Checklisten behalten ihre Haken als Zeichen — das überlebt jede App, in die
 * man den Text einfügt, während echte Kontrollkästchen unterwegs verloren
 * gingen.
 */
private fun alsText(notiz: NoteWithRelations): String = buildString {
    if (notiz.note.title.isNotBlank()) {
        appendLine(notiz.note.title)
        appendLine()
    }

    val eintraege = notiz.orderedItems.filter { it.text.isNotBlank() }
    if (eintraege.isNotEmpty()) {
        eintraege.forEach { appendLine((if (it.isChecked) "[x] " else "[ ] ") + it.text) }
    }

    if (notiz.note.body.isNotBlank()) {
        if (eintraege.isNotEmpty()) appendLine()
        // Sagt der Gegenstelle, woher der Text kommt. Ohne diese Zeile stünde
        // neben einer Audiodatei ein Text ohne erkennbaren Zusammenhang.
        if (notiz.attachments.any { it.mimeType.startsWith("audio/") }) {
            appendLine("Transkript zur Aufnahme:")
        }
        append(notiz.note.body)
    }

    val tags = notiz.tags.map { it.name }
    if (tags.isNotEmpty()) {
        appendLine()
        appendLine()
        append(tags.joinToString(" ") { "#$it" })
    }
}.trim()
