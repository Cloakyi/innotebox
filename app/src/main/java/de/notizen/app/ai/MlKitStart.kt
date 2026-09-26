package de.notizen.app.ai

import android.content.Context
import com.google.mlkit.common.MlKit

/**
 * Startet ML Kit, und zwar erst, wenn es gebraucht wird.
 *
 * Von sich aus startet ML Kit beim Start jeder App über einen eigenen
 * ContentProvider. Den nimmt InNoteBox im Manifest heraus: ML Kit soll erst
 * laufen, wenn der Nutzer der KI zugestimmt oder „Verarbeitung im Netz"
 * eingeschaltet hat. Jede Stelle, die einen ML-Kit-Client holt, ruft vorher
 * [sicherstellen], und alle diese Stellen hängen schon an der Zustimmung.
 */
object MlKitStart {

    @Volatile private var anwendung: Context? = null

    @Volatile private var gestartet = false

    /** Aus `NotizenApplication.onCreate`. Startet noch nichts. */
    fun anmelden(context: Context) {
        anwendung = context.applicationContext
    }

    fun sicherstellen() {
        if (gestartet) return
        synchronized(this) {
            if (gestartet) return
            val context = anwendung ?: return
            // Ein zweiter Start wirft; der erste zählt.
            runCatching { MlKit.initialize(context) }
            gestartet = true
        }
    }
}
