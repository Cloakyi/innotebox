package de.notizen.app.kalender

import de.notizen.core.data.db.relation.Kalenderzeile

/** Was mit dem Termin einer Notiz geschehen soll. */
sealed interface Kalendertat {

    data class Anlegen(val noteId: String, val titel: String, val beginn: Long) : Kalendertat

    data class Aendern(
        val noteId: String,
        val eventId: Long,
        val titel: String,
        val beginn: Long,
    ) : Kalendertat

    data class Entfernen(val noteId: String, val eventId: Long) : Kalendertat
}

/**
 * Welche Notiz einen Termin im Kalender haben soll.
 *
 * Vier Bedingungen, und jede einzelne hat einen Grund:
 *
 *  * **Der Hauptschalter steht an.** Ist er aus, verschwindet alles, was diese
 *    App je eingetragen hat. Ein Schalter, der nur neue Termine verhindert und
 *    die alten stehen lässt, ist kein Ausschalter.
 *  * **Die Notiz ist nicht ausgenommen** (`calendarEnabled`).
 *  * **Sie liegt nicht im Papierkorb.** Etwas Weggeworfenes gehört nicht in den
 *    Kalender. Kommt es zurück, kommt der Termin mit.
 *  * **Es gibt eine Erinnerung, die noch aussteht.** Ohne Zeitpunkt gibt es
 *    nichts einzutragen, und eine bereits ausgelöste ist Vergangenheit.
 */
fun sollImKalender(zeile: Kalenderzeile, hauptschalterAn: Boolean): Boolean =
    hauptschalterAn &&
        zeile.calendarEnabled &&
        zeile.deletedAt == null &&
        zeile.triggerAt != null

/**
 * Der Abgleich zwischen den Notizen und dem Kalender.
 *
 * **Reine Funktion, ohne Kalender und ohne Datenbank.** Ein Fehler hier trägt
 * entweder Termine ein, die niemand wollte, oder lässt welche stehen, die längst
 * weg sein sollten. Beides fällt erst Wochen später auf, wenn es klingelt oder
 * eben nicht.
 *
 * Ein bestehender Termin wird **immer** neu geschrieben, statt vorher zu
 * vergleichen. Der Titel der Notiz und die Uhrzeit stehen nicht in dieser Zeile,
 * ein Vergleich bräuchte also erst eine Abfrage beim Kalender. Der Fluss, der
 * diese Liste liefert, meldet sich ohnehin nur, wenn sich an genau diesen
 * Feldern etwas geändert hat.
 */
fun taten(zeilen: List<Kalenderzeile>, hauptschalterAn: Boolean): List<Kalendertat> =
    zeilen.mapNotNull { zeile ->
        val soll = sollImKalender(zeile, hauptschalterAn)
        val termin = zeile.calendarEventId

        when {
            soll && termin == null ->
                Kalendertat.Anlegen(zeile.noteId, beschriftung(zeile.title), zeile.triggerAt!!)

            soll ->
                Kalendertat.Aendern(
                    noteId = zeile.noteId,
                    eventId = termin!!,
                    titel = beschriftung(zeile.title),
                    beginn = zeile.triggerAt!!,
                )

            termin != null -> Kalendertat.Entfernen(zeile.noteId, termin)

            else -> null
        }
    }

/**
 * Wie der Termin im Kalender heißt.
 *
 * Eine Notiz darf im Eingang titellos sein. Ein Termin ohne Titel wäre im
 * Kalender ein leerer Balken, bei dem man raten muss, wofür er steht.
 */
fun beschriftung(titel: String): String = titel.ifBlank { "Notiz ohne Titel" }
