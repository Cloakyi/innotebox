package de.notizen.core.data.prefs

/**
 * Prüft das Siegel unter der Zustimmung zur KI (seit Alpha 9).
 *
 * Die Zustimmung ist eine kurze Aufzeichnung (was, wann, welcher Text, die
 * Angabe zur Volljährigkeit), signiert mit einem Schlüssel, der nur auf diesem
 * Gerät existiert. Die Umsetzung sitzt in `:app` und arbeitet mit dem Android
 * Keystore; die Datenschicht kennt nur die Frage, ob beides zusammenpasst.
 *
 * Gilt das Siegel nicht (Schlüssel fehlt, Aufzeichnung verändert, Text der
 * Zustimmung inzwischen ein anderer), ist die KI aus und die App fragt neu.
 */
fun interface KiSiegel {

    /** Gilt diese Aufzeichnung mit diesem Siegel auf diesem Gerät und für den heutigen Text? */
    fun gueltig(aufzeichnung: String, siegel: String): Boolean

    companion object {
        /** Kennt keinen Schlüssel und lässt nichts gelten. Für Tests, die mit der KI nichts zu tun haben. */
        val KEINES = KiSiegel { _, _ -> false }
    }
}
