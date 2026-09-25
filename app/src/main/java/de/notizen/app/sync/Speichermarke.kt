package de.notizen.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkt sich die Notiz, die gerade gespeichert und verlassen wurde.
 *
 * Warum ein eigener Merker und kein Navigationsergebnis. Der Editor und die
 * Übersicht sind zwei Ziele im Navigationsbaum; ein Wert, der zwischen ihnen
 * hin und her gereicht wird, hängt an der Reihenfolge der Bildschirme. Hier
 * hängt er an der Sache: Der Editor sagt „diese hier ist durch", die Übersicht
 * fragt „ist eine durch?". Wer dazwischen liegt, ist beiden egal.
 *
 * Der Merker wird gelöscht, sobald die Animation gelaufen ist, nicht schon
 * beim Lesen. Sonst wäre er beim ersten Neuzeichnen weg, und das passiert,
 * bevor die Karte überhaupt sichtbar ist.
 *
 * Eine leere Notiz kommt hier nie an: Sie wird beim Verlassen verworfen, und
 * eine Linie um eine Karte, die es nicht mehr gibt, wäre eine Meldung über
 * nichts.
 */
@Singleton
class Speichermarke @Inject constructor() {

    private val _notiz = MutableStateFlow<String?>(null)
    val notiz: StateFlow<String?> = _notiz.asStateFlow()

    fun melde(noteId: String) {
        _notiz.value = noteId
    }

    fun quittieren() {
        _notiz.value = null
    }
}
