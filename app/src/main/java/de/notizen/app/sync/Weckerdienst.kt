package de.notizen.app.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.notizen.core.data.aussen.Weckdienst
import de.notizen.core.data.db.entity.ReminderEntity
import de.notizen.core.data.repository.ReminderRepository
import de.notizen.app.erinnerung.ErinnerungPlaner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erinnerungen, die vom anderen Gerät kommen, eintragen und wecken.
 *
 * **Warum das hier liegt und nicht im Abgleich:** `AlarmManager` ist Android.
 * `:core:sync` soll auf der JVM prüfbar bleiben, und eine Erinnerung, die nur
 * als Datenbankzeile ankommt, ist keine Erinnerung, sondern ein Eintrag, der nie
 * klingelt. Genau deshalb wurden Erinnerungen beim Holen lange gar nicht erst
 * geschrieben.
 *
 * **Die `alarmId` wird neu vergeben, nicht übernommen.** Sie ist gerätelokal
 * (SYNC.md 14.8): Jedes Gerät weckt für sich. Zwei Erinnerungen mit derselben
 * Kennung bestellen sich gegenseitig ab, und das fände man im Nachhinein nie.
 *
 * **Fehlt die Erlaubnis für exakte Alarme, wird die Zeile trotzdem
 * geschrieben.** Der Termin gehört zur Notiz und ist Teil dessen, was
 * abgeglichen wird. Die Oberfläche zeigt ihn dann an und sagt an ihrer Stelle,
 * dass die Erlaubnis fehlt. Ihn wegzulassen hieße, eine Änderung des anderen
 * Geräts stillschweigend zu verwerfen.
 */
@Singleton
class Weckerdienst @Inject constructor(
    private val erinnerungen: ReminderRepository,
    private val planer: ErinnerungPlaner,
) : Weckdienst {

    override suspend fun uebernehmen(noteId: String, titel: String, termine: List<Long>) {
        // Eine Notiz trägt höchstens eine Erinnerung; kommen mehrere, gilt die
        // nächste. Alles andere wäre eine Aufgabenverwaltung, und die ist diese
        // App nicht.
        val naechster = termine.minOrNull() ?: return

        val erinnerung = erinnerungen.setzen(noteId, naechster)
        planer.stellen(erinnerung, titel.ifBlank { "Erinnerung" })
    }

    override suspend fun abbestellen(erinnerungen: List<ReminderEntity>) {
        erinnerungen.forEach { planer.stornieren(it) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeckdienstModule {

    @Binds
    @Singleton
    abstract fun bindWeckdienst(impl: Weckerdienst): Weckdienst
}
