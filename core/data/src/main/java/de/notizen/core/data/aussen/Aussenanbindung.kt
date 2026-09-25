package de.notizen.core.data.aussen

import de.notizen.core.data.db.entity.ReminderEntity
import java.io.File

/**
 * Wo eine heruntergeladene Anhangsdatei hingehört.
 *
 * **Warum das eine Schnittstelle ist:** Der Abgleich liegt in `:core:sync` und
 * kennt kein Android. Er weiß, dass eine Datei aus Drive kommt und wohin ihr
 * Inhalt gehört, aber nicht, wo dieses Gerät seine Dateien führt. Das ist eine
 * Frage an die Plattform, keine an den Abgleich.
 */
interface Anhangablage {

    /**
     * Die Datei, in die ein Anhang geschrieben wird.
     *
     * Der Ordner existiert danach. Ob die Datei schon Inhalt hat, sagt sie
     * selbst — der Aufrufer prüft das, statt blind erneut herunterzuladen.
     */
    fun ziel(anhangId: String, mimeType: String): File

    /**
     * Dieselbe Ablage, aber der Name steht schon fest.
     *
     * Fuer das Wiederherstellen aus einer Sicherungsdatei: Dort steht der
     * Dateiname bereits im Archiv, und ihn aus Kennung und Format neu zu bilden
     * hiesse, sich darauf zu verlassen, dass beide Seiten dieselbe Endung
     * waehlen. Der Name aus dem Archiv ist geprueft, bevor er hier ankommt
     * (siehe `sichererAnhangname`) -- ein Eintrag in einem fremden Archiv darf
     * alles heissen, auch `../..`.
     */
    fun ziel(dateiname: String): File
}

/**
 * Was mit Erinnerungen geschieht, die vom anderen Gerät kommen.
 *
 * **Eine Erinnerungszeile ohne bestellten Wecker ist ein Versprechen, das nie
 * klingelt.** Genau deshalb wurden Erinnerungen beim Holen lange gar nicht erst
 * geschrieben. Das Bestellen selbst gehört in `:app` — `AlarmManager` ist
 * Android, und `:core:sync` soll auf der JVM prüfbar bleiben.
 *
 * `alarmId` und `isFired` bleiben gerätelokal (SYNC.md 14.8): **jedes Gerät
 * weckt für sich.** Die Umsetzung vergibt deshalb eine eigene `alarmId` und
 * übernimmt nicht die der Gegenseite.
 */
interface Weckdienst {

    /** Trägt die Erinnerungen einer Notiz ein und stellt die Wecker. */
    suspend fun uebernehmen(noteId: String, titel: String, termine: List<Long>)

    /** Bestellt ab, was zu einer Notiz gestellt war. */
    suspend fun abbestellen(erinnerungen: List<ReminderEntity>)
}
