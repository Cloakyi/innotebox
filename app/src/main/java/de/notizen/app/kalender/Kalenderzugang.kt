package de.notizen.app.kalender

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein Kalender auf dem Gerät, in den geschrieben werden darf.
 *
 * Ein Konto ist nicht ein Kalender. Hinter einer Adresse können mehrere
 * liegen: der eigene, dazu geteilte, abonnierte und die für Geburtstage. Genau
 * einer davon ist der Hauptkalender des Kontos, und das ist der, den man
 * meint, wenn man „mein Kalender" sagt. Er heißt beim Anbieter wie die Adresse
 * selbst, und deshalb sieht die Liste ohne [haupt] aus, als stünde dort ein
 * Konto zur Auswahl statt eines Kalenders.
 */
data class Kalenderwahl(
    val id: Long,
    val name: String,
    val konto: String,
    /** Der Hauptkalender dieses Kontos. */
    val haupt: Boolean = false,
    /** Ob dieser Kalender überhaupt mit dem Konto abgeglichen wird. */
    val synchronisiert: Boolean = true,
)

/**
 * Wie ein Kalender in der Auswahl heißt.
 *
 * Der Hauptkalender heißt beim Anbieter wie die Adresse des Kontos. Beides
 * untereinander zu schreiben ergäbe zweimal dasselbe; also bekommt er hier
 * einen Namen, der sagt, was er ist. Das Konto steht darunter.
 */
fun kalenderbeschriftung(wahl: Kalenderwahl): String =
    if (wahl.haupt) "Hauptkalender" else wahl.name

/**
 * Welcher Kalender beim Einschalten vorgeschlagen wird.
 *
 * Der Hauptkalender, nicht der erste der Liste. Vorher wurde einfach der
 * erste genommen, und der war der alphabetisch erste: Wer neben seinem Konto
 * noch einen zweiten Kalender hat, bekam den vorgeschlagen und musste erst
 * merken, dass er umstellen muss. Aufgefallen am 2026-08-23.
 *
 * Gibt es mehrere Konten, gewinnt das erste. Eine Reihenfolge zwischen zwei
 * gleichrangigen Konten kann diese App nicht kennen; die Auswahl steht daneben.
 */
fun vorschlag(auswahl: List<Kalenderwahl>): Kalenderwahl? =
    auswahl.firstOrNull { it.haupt } ?: auswahl.firstOrNull()

/**
 * Die Reihenfolge in der Auswahl.
 *
 * Hauptkalender nach oben, danach nach Konto und Name. Wer die Liste aufklappt,
 * sucht in neun von zehn Fällen genau den obersten.
 */
fun sortiert(auswahl: List<Kalenderwahl>): List<Kalenderwahl> =
    auswahl.sortedWith(
        compareByDescending<Kalenderwahl> { it.haupt }
            .thenBy { it.konto }
            .thenBy { it.name },
    )

/** Ein Termin, der nicht aus dieser App stammt. */
data class Fremdtermin(
    val eventId: Long,
    val titel: String,
    val beginn: Long,
    val ende: Long,
    val ganztaegig: Boolean,
)

/**
 * Der Kalender des Geräts.
 *
 * Nicht Googles Kalender-API über OAuth, sondern der Anbieter auf dem Gerät.
 * Der Unterschied ist keine Geschmacksfrage. Der Bereich `calendar.events` gilt
 * bei Google als sensibel; ihn anzufordern würde die Grundlage kosten, auf
 * der diese App steht. `drive.file` ist nicht sensibel, deshalb darf sie ohne
 * Googles Prüfung veröffentlicht werden (siehe docs/ENTSCHEIDUNGEN.md). Ein sensibler Bereich
 * daneben erzwingt die Verifizierung mit Datenschutzerklärung, Demo-Video und je
 * nach Einstufung einem Sicherheitsaudit.
 *
 * Über den Anbieter geht es ohne all das: Geschrieben wird in den Google-Kalender,
 * der ohnehin auf dem Gerät eingerichtet ist, und das System trägt ihn zu Google.
 * Es braucht nur die Berechtigung für den Kalender.
 *
 * Kein eigener Kalender. Über den Anbieter ließe sich zwar einer anlegen, aber
 * nur ein lokaler, und der ginge nie zu Google. Ein eigener Google-Kalender
 * bräuchte wieder die API und damit den sensiblen Bereich. Also wird in einen
 * bestehenden geschrieben, den der Nutzer auswählt.
 *
 * Alles hier läuft auf `Dispatchers.IO`. Ein `ContentProvider` ist eine Abfrage
 * über Prozessgrenzen hinweg, und die gehört nicht auf den Hauptstrang.
 */
@Singleton
class Kalenderzugang @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Ob das System den Zugriff gerade erlaubt.
     *
     * Vor jedem Zugriff gefragt, nicht einmal gemerkt. Die Erlaubnis lässt
     * sich in den Systemeinstellungen jederzeit entziehen, und dann fliegt jeder
     * Aufruf. Eine App, die einen einmal geholten Zustand für dauerhaft hält,
     * stürzt genau dann ab, wenn jemand aufräumt.
     */
    fun erlaubt(): Boolean = erlaubt(Manifest.permission.READ_CALENDAR) &&
        erlaubt(Manifest.permission.WRITE_CALENDAR)

    private fun erlaubt(recht: String): Boolean =
        ContextCompat.checkSelfPermission(context, recht) == PackageManager.PERMISSION_GRANTED

    /**
     * Die Kalender, in die geschrieben werden darf.
     *
     * Gefiltert auf `CAL_ACCESS_CONTRIBUTOR` und höher. Ein Kalender, den man
     * nur lesen darf, gehört nicht in die Auswahl: Er stünde da, ließe sich
     * antippen, und das Eintragen scheiterte hinterher lautlos. Abonnierte
     * Kalender wie Feiertage sind genau dieser Fall.
     */
    suspend fun kalender(): List<Kalenderwahl> = withContext(Dispatchers.IO) {
        if (!erlaubt()) return@withContext emptyList()

        val spalten = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        val bedingung = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val werte = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val gefunden = mutableListOf<Kalenderwahl>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                spalten,
                bedingung,
                werte,
                // Die eigentliche Reihenfolge macht `sortiert`. Hier steht
                // nur, dass sie ueberhaupt eine hat.
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { zeiger ->
                while (zeiger.moveToNext()) {
                    val konto = zeiger.getString(2) ?: ""
                    val besitzer = zeiger.getString(3) ?: ""

                    // `isPrimary` ist eine berechnete Spalte und darf leer sein.
                    // Dann wird dieselbe Rechnung selbst gemacht: Der
                    // Hauptkalender eines Kontos gehoert dem Konto selbst.
                    val haupt = when {
                        zeiger.isNull(4) -> besitzer.isNotEmpty() && besitzer == konto
                        else -> zeiger.getInt(4) != 0
                    }

                    gefunden += Kalenderwahl(
                        id = zeiger.getLong(0),
                        name = zeiger.getString(1) ?: "Ohne Namen",
                        konto = konto,
                        haupt = haupt,
                        synchronisiert = zeiger.isNull(5) || zeiger.getInt(5) != 0,
                    )
                }
            }
        }
        sortiert(gefunden)
    }

    /**
     * Trägt einen Termin ein und gibt seine Kennung zurück.
     *
     * Eine halbe Stunde lang, nicht ein Zeitpunkt. Der Kalender-Anbieter
     * verlangt für einen Termin ohne Wiederholung entweder `DTEND` oder
     * `DURATION`; ein Termin ohne Ende wird abgewiesen. Eine Erinnerung ist
     * eigentlich punktförmig, aber ein Balken, den man im Tagesplan sieht, ist
     * nützlicher als ein Strich, den man übersieht.
     *
     * `CUSTOM_APP_PACKAGE` zeigt zurück auf diese App. Tippt man den Termin
     * in Google Kalender an, führt er zur Notiz statt in eine Sackgasse.
     */
    suspend fun eintragen(
        kalenderId: Long,
        notizId: String,
        titel: String,
        beginn: Long,
    ): Long? = withContext(Dispatchers.IO) {
        if (!erlaubt()) return@withContext null

        val werte = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, kalenderId)
            put(CalendarContract.Events.TITLE, titel)
            put(CalendarContract.Events.DESCRIPTION, BESCHREIBUNG)
            put(CalendarContract.Events.DTSTART, beginn)
            put(CalendarContract.Events.DTEND, beginn + DAUER_MS)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, notizadresse(notizId))
        }

        runCatching {
            context.contentResolver
                .insert(CalendarContract.Events.CONTENT_URI, werte)
                ?.lastPathSegment
                ?.toLongOrNull()
        }.getOrNull()
    }

    /** Schreibt Titel und Zeit eines bestehenden Termins neu. */
    suspend fun aendern(eventId: Long, titel: String, beginn: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!erlaubt()) return@withContext false

            val werte = ContentValues().apply {
                put(CalendarContract.Events.TITLE, titel)
                put(CalendarContract.Events.DTSTART, beginn)
                put(CalendarContract.Events.DTEND, beginn + DAUER_MS)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            runCatching {
                val ziel = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId,
                )
                context.contentResolver.update(ziel, werte, null, null) > 0
            }.getOrDefault(false)
        }

    /**
     * Entfernt einen Termin.
     *
     * Gibt auch dann `true` zurück, wenn es ihn gar nicht mehr gab. Jemand kann
     * ihn in der Kalender-App gelöscht haben, und dann ist das Ziel ja erreicht.
     * Ein `false` hieße „noch einmal versuchen", und das liefe ewig.
     */
    suspend fun entfernen(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!erlaubt()) return@withContext false

        runCatching {
            val ziel = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(ziel, null, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Die Termine eines Zeitraums, ohne die aus dieser App.
     *
     * Über `Instances`, nicht über `Events`. `Events` führt die Regel einer
     * Terminserie, nicht ihre einzelnen Termine. Ein wöchentliches Treffen stünde
     * dort einmal und erschiene im Monat genau einmal statt viermal.
     *
     * Die eigenen Termine fallen heraus: Sie stehen schon als Notiz in der
     * Liste, und zweimal dasselbe ist keine Auskunft.
     */
    suspend fun termine(von: Long, bis: Long, eigene: Set<Long>): List<Fremdtermin> =
        withContext(Dispatchers.IO) {
            if (!erlaubt()) return@withContext emptyList()

            val spalten = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            )

            val gefunden = mutableListOf<Fremdtermin>()
            runCatching {
                CalendarContract.Instances.query(
                    context.contentResolver,
                    spalten,
                    von,
                    bis,
                )?.use { zeiger ->
                    while (zeiger.moveToNext()) {
                        val id = zeiger.getLong(0)
                        if (id in eigene) continue
                        gefunden += Fremdtermin(
                            eventId = id,
                            titel = zeiger.getString(1) ?: "Ohne Titel",
                            beginn = zeiger.getLong(2),
                            ende = zeiger.getLong(3),
                            ganztaegig = zeiger.getInt(4) != 0,
                        )
                    }
                }
            }
            gefunden.sortedBy { it.beginn }
        }

    /**
     * Die Adresse, unter der Google Kalender diese Notiz wieder findet.
     *
     * Dieselbe Form, die auch der Dateimanager benutzt, gibt es hier nicht: Ein
     * Termin verweist auf eine Notiz, nicht auf eine Datei. Angemeldet ist die
     * Adresse in `AndroidManifest.xml` bei [MainActivity].
     */
    private fun notizadresse(notizId: String) = "$SCHEMA://notiz/$notizId"

    companion object {
        /** Eigenes Schema, damit nichts anderes es beansprucht. */
        const val SCHEMA = "innotebox"

        /** Wie lange ein eingetragener Termin dauert. */
        private const val DAUER_MS = 30 * 60 * 1000L

        private const val BESCHREIBUNG = "Erinnerung aus InNoteBox"
    }
}
