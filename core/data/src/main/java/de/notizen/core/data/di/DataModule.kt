package de.notizen.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.notizen.core.data.aussen.Anhangablage
import de.notizen.core.data.aussen.Dateiablage
import de.notizen.core.data.db.NotizenDatabase
import de.notizen.core.data.db.dao.ArchiveDao
import de.notizen.core.data.db.dao.AttachmentDao
import de.notizen.core.data.db.dao.FolderDao
import de.notizen.core.data.db.dao.NoteDao
import de.notizen.core.data.db.dao.ReminderDao
import de.notizen.core.data.db.dao.SearchDao
import de.notizen.core.data.db.dao.SyncDao
import de.notizen.core.data.db.dao.TagDao
import de.notizen.core.data.db.dao.TranscriptDao
import de.notizen.core.data.util.Clock
import de.notizen.core.data.util.SystemClock
import javax.inject.Singleton

/**
 * Der Einstellungs-Speicher. Das Delegate gehoert hierher und nicht in die
 * Klasse, die ihn benutzt: `preferencesDataStore` erzeugt einen versteckten,
 * prozessweiten Speicher pro Name -- als sichtbare Abhaengigkeit bleibt
 * `Einstellungen` testbar.
 */
private val Context.einstellungenStore: DataStore<Preferences> by
    preferencesDataStore(name = "einstellungen")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideEinstellungenStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.einstellungenStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotizenDatabase =
        Room.databaseBuilder(context, NotizenDatabase::class.java, NotizenDatabase.NAME)
            .addMigrations(*NotizenDatabase.MIGRATIONS)
            // BEWUSST KEIN fallbackToDestructiveMigration: die lokale Datenbank
            // ist auf jedem Client die Quelle der Wahrheit. Lieber ein harter
            // Fehler als stiller Datenverlust.
            .build()

    @Provides fun provideNoteDao(db: NotizenDatabase): NoteDao = db.noteDao()

    @Provides fun provideTagDao(db: NotizenDatabase): TagDao = db.tagDao()

    @Provides fun provideSearchDao(db: NotizenDatabase): SearchDao = db.searchDao()

    @Provides fun provideAttachmentDao(db: NotizenDatabase): AttachmentDao = db.attachmentDao()

    @Provides fun provideTranscriptDao(db: NotizenDatabase): TranscriptDao = db.transcriptDao()

    @Provides fun provideReminderDao(db: NotizenDatabase): ReminderDao = db.reminderDao()

    @Provides fun provideArchiveDao(db: NotizenDatabase): ArchiveDao = db.archiveDao()

    @Provides fun provideFolderDao(db: NotizenDatabase): FolderDao = db.folderDao()

    @Provides fun provideSyncDao(db: NotizenDatabase): SyncDao = db.syncDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindAnhangablage(impl: Dateiablage): Anhangablage
}
