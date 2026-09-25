package de.notizen.core.sync

/**
 * Die Regeln des Abgleichs nach Schema 4 (SYNC.md 6), als reine Funktionen.
 *
 * **Hier steht keine Datenbank und kein Netz.** Was hineingeht, sind zwei
 * kleine Bilder desselben Dings: wie es hier aussieht und wie es drueben
 * aussieht. Was herauskommt, ist genau eine Handlung. Der Abgleich fuehrt sie
 * nur aus. Deshalb laesst sich jede Zeile der Tabelle aus SYNC.md 6 ohne
 * Geraet pruefen.
 */
object Abgleichregeln {

    /** Wie die Entitaet hier steht. `null` = hier voellig unbekannt. */
    data class Lokal(
        /** `sync_state.baseRev`, 0 = noch nie oben. */
        val baseRev: Long,
        /** Lokal seit dem letzten Abgleich geaendert (Status DIRTY). */
        val geaendert: Boolean,
        val zustand: Zustand,
        val updatedAt: Long,
        /** Bei DELETED: der Zaehlerstand des Grabsteins. Sonst gleich baseRev. */
        val rev: Long = baseRev,
    )

    /** Wie die Entitaet drueben steht. `null` = keine Datei. */
    data class Fern(
        val rev: Long,
        val zustand: Zustand,
        val updatedAt: Long,
    )

    /** Die eine Handlung. */
    sealed interface Handlung {
        /** Nichts zu tun. */
        data object Nichts : Handlung

        /** Die eigene Fassung hochladen, mit diesem Zaehlerstand. */
        data class Hochladen(val rev: Long) : Handlung

        /** Die ferne Fassung uebernehmen, `baseRev` wird ihr `rev`. */
        data object Herunterladen : Handlung

        /**
         * Datei fehlt drueben, obwohl sie oben war (SYNC.md 6, letzte Zeile).
         * Kein Schluss aus der Abwesenheit, nur ein Befund plus Heilung:
         * erneut hochladen.
         */
        data class FehltDrueben(val rev: Long) : Handlung

        /** Beide Seiten geaendert. Wie es aufgeloest wird, sagt [Aufloesung]. */
        data class Konflikt(val aufloesung: Aufloesung, val rev: Long) : Handlung
    }

    /** Die drei Regeln aus SYNC.md 6.1, in ihrer Reihenfolge. */
    sealed interface Aufloesung {
        /** Eine Seite ist DELETED. DELETED gewinnt, nichts wird heruntergeladen. */
        data object GeloeschtGewinnt : Aufloesung

        /**
         * Eine Seite ist TRASHED, die andere hat nur bearbeitet. Der Zustand
         * TRASHED gewinnt, der Inhalt kommt von der Seite mit dem hoeheren
         * `updatedAt`.
         */
        data class PapierkorbGewinnt(val inhaltVonDrueben: Boolean) : Aufloesung

        /**
         * Beide haben den Inhalt bearbeitet: lokal bleibt, fern wird zur
         * Konfliktkopie (nur Notizen). Bei Ordnern und Tags gewinnt stattdessen
         * das hoehere `updatedAt`, bei Gleichstand drueben.
         */
        data class Kopie(val fernGewinnt: Boolean) : Aufloesung
    }

    /**
     * Die Tabelle aus SYNC.md 6, Zeile fuer Zeile.
     *
     * Ein lokaler Grabstein (DELETED) zaehlt als "geaendert" mit dem Zaehler
     * des Grabsteins: Er ist eine gewoehnliche, hoeherwertige Fassung.
     */
    fun entscheiden(lokal: Lokal?, fern: Fern?): Handlung {
        // Hier unbekannt, drueben vorhanden: echte Neuanlage. Ein DELETED, das
        // hier niemand kennt, ist kein Auftrag: Es gibt nichts zu loeschen.
        if (lokal == null) {
            return if (fern == null || fern.zustand == Zustand.DELETED) {
                Handlung.Nichts
            } else {
                Handlung.Herunterladen
            }
        }

        // DER GRABSTEIN. Er traegt seinen eigenen Zaehler und gewinnt gegen
        // alles, was nicht selbst DELETED ist. Steht drueben schon DELETED, ist
        // nichts mehr zu tun; sonst geht er hoch, notfalls ueber eine fremde
        // neuere Fassung hinweg (SYNC.md 6.1, Regel 1).
        if (lokal.zustand == Zustand.DELETED) {
            return when {
                fern == null -> Handlung.Hochladen(lokal.rev)
                fern.zustand == Zustand.DELETED -> Handlung.Nichts
                fern.rev >= lokal.rev ->
                    Handlung.Konflikt(Aufloesung.GeloeschtGewinnt, fern.rev + 1)
                else -> Handlung.Hochladen(lokal.rev)
            }
        }

        if (fern == null) {
            // Nie oben gewesen: hochladen. Oben gewesen und weg: Befund, kein
            // Schluss (Regel 1), aber heilen.
            return if (lokal.baseRev == 0L || lokal.geaendert) {
                Handlung.Hochladen(lokal.baseRev + 1)
            } else {
                Handlung.FehltDrueben(lokal.baseRev + 1)
            }
        }

        val fernNeuer = fern.rev > lokal.baseRev

        return when {
            !lokal.geaendert && !fernNeuer -> Handlung.Nichts
            lokal.geaendert && !fernNeuer -> Handlung.Hochladen(maxOf(lokal.baseRev, fern.rev) + 1)
            !lokal.geaendert -> Handlung.Herunterladen
            else -> Handlung.Konflikt(aufloesen(lokal, fern), maxOf(lokal.baseRev, fern.rev) + 1)
        }
    }

    private fun aufloesen(lokal: Lokal, fern: Fern): Aufloesung = when {
        fern.zustand == Zustand.DELETED -> Aufloesung.GeloeschtGewinnt

        lokal.zustand == Zustand.TRASHED || fern.zustand == Zustand.TRASHED ->
            Aufloesung.PapierkorbGewinnt(inhaltVonDrueben = fern.updatedAt > lokal.updatedAt)

        else -> Aufloesung.Kopie(fernGewinnt = fern.updatedAt >= lokal.updatedAt)
    }

    /**
     * Ob ein lokaler Grabstein vergessen werden darf (SYNC.md 7): erst, wenn
     * sein `deletedAt` aelter ist als das Wasserzeichen.
     */
    fun grabsteinVerfallen(deletedAt: Long, wasserzeichen: Long): Boolean =
        wasserzeichen > 0 && deletedAt < wasserzeichen
}
