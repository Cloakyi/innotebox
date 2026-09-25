package de.notizen.app.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.notizen.app.audio.Modellzustand
import de.notizen.app.audio.Transkription
import de.notizen.app.uebersetzung.Uebersetzungslage
import de.notizen.app.uebersetzung.Uebersetzungspruefung
import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.model.Geraetestand
import de.notizen.core.data.util.Clock
import kotlinx.coroutines.flow.last
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Misst, was dieses Geraet an KI kann.
 *
 * Drei Fragen mit den bekannten `checkStatus`-Aufrufen und der
 * `Uebersetzungspruefung`: Spracherkennung, Textgenerierung (Prompt API),
 * Uebersetzung des Systems. Messen laedt nichts. Das Nachladen ist ein
 * eigener Schritt, den nur der Nutzer ausloest; die App installiert nichts
 * von selbst.
 *
 * Die Uebersetzung kennt kein „ladbar" ueber diese App: Sprachpakete laedt man
 * in der Systemeinstellung. „Dienst da, keine Sprachen" zaehlt trotzdem als
 * `LADBAR`, denn genau dorthin fuehrt der Weg.
 */
@Singleton
class Geraetepruefung @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transkription: Transkription,
    private val titelKi: TitelKi,
    private val uebersetzungspruefung: Uebersetzungspruefung,
    private val clock: Clock,
) {

    suspend fun messen(): Geraetestand {
        val sprache = runCatching { transkription.zustand().first }.getOrNull()
        val textki = runCatching { titelKi.zustand() }.getOrNull()
        val uebersetzung = runCatching { uebersetzungspruefung.lage() }.getOrNull()

        return Geraetestand(
            spracherkennung = when (sprache) {
                is Modellzustand.Bereit -> Faehigkeit.VERFUEGBAR
                is Modellzustand.Ladbar, is Modellzustand.Laedt -> Faehigkeit.LADBAR
                else -> Faehigkeit.NICHT
            },
            textki = when (textki) {
                KiZustand.BEREIT -> Faehigkeit.VERFUEGBAR
                KiZustand.LADBAR, KiZustand.LAEDT -> Faehigkeit.LADBAR
                else -> Faehigkeit.NICHT
            },
            uebersetzung = when (uebersetzung) {
                is Uebersetzungslage.Bereit ->
                    if (uebersetzung.bereit.isNotEmpty()) Faehigkeit.VERFUEGBAR else Faehigkeit.LADBAR
                is Uebersetzungslage.KeineSprachen -> Faehigkeit.LADBAR
                else -> Faehigkeit.NICHT
            },
            geprueftAm = clock.now(),
            appVersion = appVersion(),
        )
    }

    /**
     * Laedt nach, was ladbar ist, und misst danach neu. Nur auf Knopfdruck.
     *
     * Die Spracherkennung wird ueber ihren Download-Fluss geholt, die Text-KI
     * ueber die Prompt API. Fuer die Uebersetzung gibt es hier nichts zu laden;
     * dafuer ist die Systemeinstellung da.
     */
    suspend fun nachladen(stand: Geraetestand): Geraetestand {
        if (stand.spracherkennung == Faehigkeit.LADBAR) {
            runCatching {
                val modus = transkription.zustand().second
                transkription.laden(modus).last()
            }
        }
        if (stand.textki == Faehigkeit.LADBAR) {
            runCatching { titelKi.laden() }
        }
        return messen()
    }

    private fun appVersion(): Int = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)
}
