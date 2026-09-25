package de.notizen.core.sync.di

import dagger.Module
import de.notizen.core.sync.Drive
import de.notizen.core.sync.Drivezugang
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    /** Die echte Drive-Anbindung hinter der Schnittstelle, die der Abgleich benutzt. */
    @Provides
    @Singleton
    fun drivezugang(drive: Drive): Drivezugang = drive

    /**
     * Ein HTTP-Client fuer die ganze App.
     *
     * OkHttp haelt Verbindungen und Threads in einem Pool; jede Instanz baut
     * ihren eigenen auf. Mehrere davon sind nicht falsch, aber verschwenderisch
     * -- und zwar genau dort, wo es auffaellt: auf dem Telefon.
     *
     * Die Zeitgrenzen sind bewusst grosszuegig. Ein Abgleich laeuft im
     * Hintergrund, auf niemanden wartet dabei jemand, und ein Abbruch nach zehn
     * Sekunden im schlechten Netz waere ein Fehlschlag, der keiner ist.
     */
    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
