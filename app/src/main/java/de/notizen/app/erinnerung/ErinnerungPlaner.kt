package de.notizen.app.erinnerung

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.core.data.db.entity.ReminderEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stellt und storniert die Wecker.
 *
 * Exakte Alarme sind ab Android 14 nicht mehr selbstverständlich. Die App
 * fragt `SCHEDULE_EXACT_ALARM` an, und der Nutzer muss es in den
 * Systemeinstellungen gewähren. Die bequeme Alternative `USE_EXACT_ALARM`
 * würde bei der Installation automatisch gewährt, ist laut Google aber
 * Kalender- und Weckerprogrammen vorbehalten, eine Notizen-App, die sie
 * benutzt, ist eine, die sich falsch anmeldet.
 *
 * Fehlt die Erlaubnis, wird nicht heimlich ungenau geweckt. Eine
 * Erinnerung, die irgendwann in der nächsten Stunde klingelt, ist keine
 * Erinnerung, sondern ein Zufall. Stattdessen sagt die Oberfläche, was fehlt,
 * und führt zur richtigen Einstellung.
 */
@Singleton
class ErinnerungPlaner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val manager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    /** Ob das System exakte Alarme gerade zulässt. Kann sich jederzeit ändern. */
    fun darfExaktWecken(): Boolean = manager.canScheduleExactAlarms()

    /** Führt in die Systemeinstellung, in der die Erlaubnis erteilt wird. */
    fun einstellungen(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Stellt den Wecker.
     *
     * `setExactAndAllowWhileIdle`, derselbe Weg, den auch Google Kalender geht.
     * Der Alarm ist exakt und wird auch dann durchgelassen, wenn das Gerät im
     * Doze-Modus liegt; ohne das `AllowWhileIdle` würde er nachts oder bei
     * liegengelassenem Gerät bis zum nächsten Wartungsfenster verschoben, also
     * genau dann, wenn eine Erinnerung am ehesten gebraucht wird.
     *
     * Warum nicht `setAlarmClock`, das noch eine Spur unantastbarer wäre:
     * Es trägt die Erinnerung in den Weckerplatz des Systems ein. Solange sie
     * aussteht, stünde ein Weckersymbol in der Statusleiste und der Termin auf
     * dem Sperrbildschirm, bei einer Erinnerung in drei Wochen drei Wochen
     * lang. Entschieden am 2026-08-21 gegen diesen Preis.
     *
     * Die eine Einschränkung, die bleibt: Android darf zwei Alarme dieser
     * Art aus derselben App, die weniger als etwa neun Minuten auseinander
     * liegen, auf diesen Abstand auseinanderziehen. Bei einer Erinnerung je
     * Notiz, von Hand gesetzt, tritt das praktisch nicht auf, es ist aber der
     * einzige verbleibende Fall, in dem die Uhrzeit nicht auf die Sekunde
     * stimmt, und deshalb steht er hier und nicht im Verborgenen.
     *
     * Gibt `false` zurück, wenn die Erlaubnis fehlt. Der Aufrufer muss das
     * sichtbar machen; stillschweigend nichts zu tun wäre der schlechteste Weg.
     */
    fun stellen(erinnerung: ReminderEntity, titel: String): Boolean {
        if (!darfExaktWecken()) return false

        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            erinnerung.triggerAt,
            absicht(erinnerung, titel, PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return true
    }

    fun stornieren(erinnerung: ReminderEntity) {
        manager.cancel(absicht(erinnerung, "", PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun absicht(erinnerung: ReminderEntity, titel: String, flags: Int): PendingIntent {
        val intent = Intent(context, ErinnerungEmpfaenger::class.java).apply {
            // Die Kennung gehoert in die Extras UND in den Request-Code: der
            // Request-Code allein entscheidet, welcher Alarm ueberschrieben
            // oder storniert wird, die Extras entscheiden, was beim Klingeln
            // passiert.
            putExtra(SCHLUESSEL_ID, erinnerung.id)
            putExtra(SCHLUESSEL_NOTIZ, erinnerung.noteId)
            putExtra(SCHLUESSEL_TITEL, titel)
        }
        return PendingIntent.getBroadcast(
            context,
            erinnerung.alarmId,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val SCHLUESSEL_ID = "erinnerungId"
        const val SCHLUESSEL_NOTIZ = "notizId"
        const val SCHLUESSEL_TITEL = "titel"
    }
}
