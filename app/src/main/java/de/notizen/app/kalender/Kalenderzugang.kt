package de.notizen.app.kalender

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Kalender des Geräts, nur noch zum Aufräumen.
 *
 * Bis 2026-10-30 konnte die App Erinnerungen als Termine in einen Kalender des
 * Geräts schreiben und dessen Termine anzeigen. Seit sie einen eigenen
 * Kalender hat, gibt es keine Verbindung mehr in eine der beiden Richtungen
 * (docs/ENTSCHEIDUNGEN.md 15). Übrig ist, was es braucht, um die früher
 * eingetragenen Termine wieder zu entfernen.
 */
@Singleton
class Kalenderzugang @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Ob das System das Schreiben gerade erlaubt. Ohne Erlaubnis bleibt nur liegen lassen. */
    fun erlaubt(): Boolean = erlaubt(Manifest.permission.READ_CALENDAR) &&
        erlaubt(Manifest.permission.WRITE_CALENDAR)

    private fun erlaubt(recht: String): Boolean =
        ContextCompat.checkSelfPermission(context, recht) == PackageManager.PERMISSION_GRANTED

    /**
     * Entfernt einen Termin.
     *
     * Gibt auch dann `true` zurück, wenn es ihn gar nicht mehr gab. Jemand kann
     * ihn in der Kalender-App gelöscht haben, und dann ist das Ziel ja erreicht.
     */
    suspend fun entfernen(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!erlaubt()) return@withContext false

        runCatching {
            val ziel = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(ziel, null, null)
            true
        }.getOrDefault(false)
    }

    companion object {
        /** Das Schema der Adressen, die früher in den Terminen standen. */
        const val SCHEMA = "innotebox"
    }
}
