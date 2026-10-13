package de.notizen.app.audio

import android.util.Log
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.prefs.Einstellungen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Transkription"

/** Zustand des Sprachpakets, bevor überhaupt eine Aufnahme-UI erscheint. */
sealed interface Modellzustand {
    data object Bereit : Modellzustand
    data object Ladbar : Modellzustand
    data class Laedt(val prozent: Int) : Modellzustand
    data object NichtVerfuegbar : Modellzustand
    data class Fehler(val ursache: Throwable) : Modellzustand
}

/**
 * Die Spracherkennung, soweit sie VOR dem ersten Wort geklärt sein muss.
 *
 * Gefragt wird, bevor der Transkript-Knopf erscheint. Ein Knopf, der erst nach
 * dem Drücken zugibt, dass das Sprachpaket fehlt, ist schlechter als einer,
 * der von vornherein sagt, was zu tun ist. Die AUFNAHME hängt davon
 * ausdrücklich nicht ab, sie läuft auch ohne Sprachpaket.
 *
 * Das eigentliche Erkennen steht in [Transkriptor], der Weg ins System in
 * [Systemerkennung].
 */
@Singleton
class Transkription @Inject constructor(
    private val einstellungen: Einstellungen,
    private val systemerkennung: Systemerkennung,
) {

    /** Die eingestellte Sprache, einmal gelesen statt bei jedem Aufruf. */
    suspend fun sprache(): Transkriptsprache = einstellungen.transkriptsprache().first()

    /** Prüft, ob das Sprachpaket für die eingestellte Sprache bereitliegt. */
    suspend fun zustand(): Modellzustand {
        val sprache = sprache()
        val zustand = runCatching { systemerkennung.sprachpaket(sprache) }.fold(
            onSuccess = { paket ->
                when (paket) {
                    Sprachpaket.INSTALLIERT -> Modellzustand.Bereit
                    Sprachpaket.LAEDT -> Modellzustand.Laedt(0)
                    Sprachpaket.LADBAR -> Modellzustand.Ladbar
                    Sprachpaket.NICHT -> Modellzustand.NichtVerfuegbar
                }
            },
            onFailure = { Modellzustand.Fehler(it) },
        )
        Log.i(TAG, "Spracherkennung ${sprache.name}: $zustand")
        return zustand
    }

    /**
     * Lädt das Sprachpaket über den Erkennungsdienst des Systems und meldet
     * danach den neuen Zustand. Der Dienst darf das Laden auch auf später
     * verschieben, etwa bis WLAN da ist; dann bleibt es bei „ladbar“.
     */
    fun laden(): Flow<Modellzustand> = flow {
        val sprache = sprache()
        emit(Modellzustand.Laedt(0))
        // Ein Fehler beim Laden steht danach ohnehin im Zustand: Das Paket fehlt dann noch.
        systemerkennung.laden(sprache)
            .catch { Log.w(TAG, "Sprachpaket laden", it) }
            .collect { prozent -> emit(Modellzustand.Laedt(prozent)) }
        emit(zustand())
    }
}
