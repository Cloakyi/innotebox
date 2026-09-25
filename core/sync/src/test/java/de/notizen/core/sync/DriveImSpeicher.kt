package de.notizen.core.sync

import java.io.File

/**
 * Ein Drive im Speicher, fuer die Szenarien mit zwei simulierten Clients
 * (SYNC.md 12).
 *
 * Benimmt sich in dem, was der Abgleich sieht, wie das echte: Ordner haben
 * Kennungen, Dateien haben Kennungen und eine `headRevisionId`, die bei jedem
 * Schreiben wechselt, und wer eine Datei ueber ihren Namen neu anlegt, bekommt
 * eine NEUE Kennung (so arbeitet ein Assistent, SYNC.md 3).
 */
class DriveImSpeicher : Drivezugang {

    class Eintrag(
        val id: String,
        var name: String,
        val eltern: String,
        val ordner: Boolean,
        var text: String? = null,
        var bytes: ByteArray? = null,
        var revision: Int = 1,
    )

    val eintraege = LinkedHashMap<String, Eintrag>()
    private var zaehler = 0

    /** Wie oft was aufgerufen wurde, fuer Tests, die Anfragen zaehlen. */
    var schreibvorgaenge = 0
        private set

    private fun neueId(): String = "d" + (++zaehler)

    private fun datei(e: Eintrag) = Drivedatei(
        id = e.id,
        name = e.name,
        mimeType = if (e.ordner) "application/vnd.google-apps.folder" else null,
        headRevisionId = "r" + e.revision,
    )

    override suspend fun ordner(token: String, name: String, elternId: String): String {
        eintraege.values.firstOrNull { it.ordner && it.name == name && it.eltern == elternId }
            ?.let { return it.id }
        val id = neueId()
        eintraege[id] = Eintrag(id, name, elternId, ordner = true)
        return id
    }

    override suspend fun inhalt(token: String, ordnerId: String): List<Drivedatei> =
        eintraege.values.filter { it.eltern == ordnerId }.map { datei(it) }

    override suspend fun anlegen(token: String, ordnerId: String, name: String, inhalt: String): Drivedatei {
        schreibvorgaenge++
        val id = neueId()
        val e = Eintrag(id, name, ordnerId, ordner = false, text = inhalt)
        eintraege[id] = e
        return datei(e)
    }

    override suspend fun ersetzen(token: String, dateiId: String, inhalt: String): Drivedatei {
        schreibvorgaenge++
        val e = eintraege[dateiId] ?: throw Drivefehler.Abgelehnt(404, "Datei $dateiId gibt es nicht.")
        e.text = inhalt
        e.revision++
        return datei(e)
    }

    override suspend fun lesen(token: String, dateiId: String): String =
        eintraege[dateiId]?.text ?: throw Drivefehler.Abgelehnt(404, "Datei $dateiId gibt es nicht.")

    override suspend fun anlegenBinaer(
        token: String,
        ordnerId: String,
        name: String,
        mimeType: String,
        datei: File,
    ): Drivedatei {
        schreibvorgaenge++
        val id = neueId()
        val e = Eintrag(id, name, ordnerId, ordner = false, bytes = datei.readBytes())
        eintraege[id] = e
        return datei(e)
    }

    override suspend fun herunterladen(token: String, dateiId: String, ziel: File) {
        val e = eintraege[dateiId] ?: throw Drivefehler.Abgelehnt(404, "Datei $dateiId gibt es nicht.")
        ziel.parentFile?.mkdirs()
        ziel.writeBytes(e.bytes ?: e.text.orEmpty().toByteArray())
    }

    override suspend fun loeschen(token: String, dateiId: String) {
        eintraege.remove(dateiId)
    }

    // ------------------------------------------------ Helfer fuer Tests

    /** Der Text einer Datei ueber Ordnername und Dateiname, wie ein Mensch sucht. */
    fun text(pfad: String): String? {
        val teile = pfad.split("/")
        var eltern = "root"
        for (teil in teile.dropLast(1)) {
            eltern = eintraege.values.firstOrNull { it.ordner && it.name == teil && it.eltern == eltern }?.id
                ?: return null
        }
        return eintraege.values.firstOrNull { !it.ordner && it.name == teile.last() && it.eltern == eltern }?.text
    }

    /**
     * Schreibt eine Datei so, wie es ein Assistent tut: alte Datei weg, neue
     * mit neuer Kennung unter demselben Namen.
     */
    fun vonAussenSchreiben(pfad: String, inhalt: String) {
        val teile = pfad.split("/")
        var eltern = "root"
        for (teil in teile.dropLast(1)) {
            eltern = eintraege.values.firstOrNull { it.ordner && it.name == teil && it.eltern == eltern }?.id
                ?: run {
                    val id = neueId()
                    eintraege[id] = Eintrag(id, teil, eltern, ordner = true)
                    id
                }
        }
        eintraege.values.filter { !it.ordner && it.name == teile.last() && it.eltern == eltern }
            .forEach { eintraege.remove(it.id) }
        val id = neueId()
        eintraege[id] = Eintrag(id, teile.last(), eltern, ordner = false, text = inhalt)
    }

    fun dateinamen(ordnerpfad: String): List<String> {
        var eltern = "root"
        for (teil in ordnerpfad.split("/")) {
            eltern = eintraege.values.firstOrNull { it.ordner && it.name == teil && it.eltern == eltern }?.id
                ?: return emptyList()
        }
        return eintraege.values.filter { !it.ordner && it.eltern == eltern }.map { it.name }
    }
}
