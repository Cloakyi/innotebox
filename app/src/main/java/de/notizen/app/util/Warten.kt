package de.notizen.app.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Macht aus einem Play-Services-`Task` eine suspendierende Funktion.
 *
 * Von Hand statt über `kotlinx-coroutines-play-services`: Es sind zwölf Zeilen,
 * und sie ersparen eine weitere Abhängigkeit, deren Version niemand prüft.
 * `invokeOnCancellation` fehlt bewusst — ein `Task` lässt sich nicht abbrechen,
 * und so zu tun als ob wäre eine Zusage, die nicht gilt.
 *
 * Bis Phase 16 lag das privat in `Anmeldung`; ML Kit Translate liefert
 * dieselben `Task`s, deshalb steht es jetzt hier.
 */
internal suspend fun <T> Task<T>.warten(): T =
    suspendCancellableCoroutine { fortsetzung ->
        addOnCompleteListener { fertig ->
            val fehler = fertig.exception
            if (fehler != null) {
                fortsetzung.resumeWith(Result.failure(fehler))
            } else {
                @Suppress("UNCHECKED_CAST")
                fortsetzung.resume(fertig.result as T)
            }
        }
    }
