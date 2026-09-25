package de.notizen.core.sync.sicherung

import de.notizen.core.sync.Anhangdokument
import de.notizen.core.sync.Ordnerdokument
import de.notizen.core.sync.Sync
import de.notizen.core.sync.Tagdokument
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Was beim Packen tatsaechlich in der Datei gelandet ist. */
data class Packbericht(
    val notizen: Int,
    val tags: Int,
    val ordner: Int,
    val dateien: Int,
)

/** Was beim Auspacken herauskam. */
data class Sicherungsinhalt(
    val kopf: Sicherungskopf?,
    val tags: List<Tagdokument>,

    /**
     * Die Ordner. Bei einer Datei aus Fassung 1 leer, und das ist kein Fehler:
     * Damals gab es keine. Die Notizen darin tragen dann eine `folderId`, zu
     * der es keinen Ordner gibt, und haengen sichtbar im Hauptordner.
     */
    val ordner: List<Ordnerdokument>,
    val notizen: List<Sicherungsnotiz>,
    /** Kennung des Anhangs auf die Datei, in der seine Bytes jetzt liegen. */
    val dateien: Map<String, File>,
    /** Eintraege, die uebergangen wurden, weil ihr Name nicht in Ordnung war. */
    val abgewiesen: Int,
    /** Seit Fassung 3. Bei aelteren Dateien leer. */
    val zaehler: List<Zaehlerstand> = emptyList(),
    val geloescht: List<Grabsteineintrag> = emptyList(),
)

/**
 * Die Sicherungsdatei als solche: ein ZIP-Archiv mit lesbarem JSON darin.
 *
 * Diese Klasse kennt keine Datenbank. Sie nimmt entgegen, was hineinsoll,
 * und gibt heraus, was drinstand. Genau deshalb laesst sich das Format auf der
 * JVM pruefen, ohne dass ein Geraet startet oder eine Datenbank existiert, und
 * genau deshalb steht die eigentliche Uebernahme in [Sicherung] daneben statt
 * hier drin.
 *
 * Warum ZIP und nicht ein einziges grosses JSON. Ein Foto in einer
 * JSON-Datei muesste als Base64 hineingeschrieben werden. Das macht es ein
 * Drittel groesser, es zwingt jeden Leser, die ganze Datei im Speicher zu
 * halten, und ein Bild, das man sich ansehen will, waere ohne Werkzeug nicht
 * herauszubekommen. So liegt jedes Bild als Bild in einem Ordner, den jedes
 * Betriebssystem oeffnet.
 */
class Sicherungspaket {

    /**
     * Schreibt die Sicherung.
     *
     * Die Notizen werden einzeln geholt, nicht alle auf einmal. Bei ein
     * paar hundert Notizen mit Transkripten waeren das schnell einige Dutzend
     * Megabyte, die es gleichzeitig im Speicher gaebe, obwohl immer nur eine
     * gebraucht wird. [notizFuer] laedt deshalb genau die, die als naechste
     * geschrieben wird.
     *
     * [dateiFuer] sagt, wo die Datei eines Anhangs auf diesem Geraet liegt, oder
     * `null`, wenn es sie nicht gibt. Eine fehlende Datei bricht die Sicherung
     * NICHT ab: Der Anhang steht dann in der Notiz und seine Datei fehlt, und das
     * ist genau der Zustand, in dem die App ohnehin schon war.
     */
    suspend fun packen(
        ziel: OutputStream,
        kopf: Sicherungskopf,
        tags: List<Tagdokument>,
        ordner: List<Ordnerdokument>,
        notizIds: List<String>,
        notizFuer: suspend (String) -> Sicherungsnotiz?,
        dateiFuer: suspend (Anhangdokument) -> File?,
        zaehler: List<Zaehlerstand> = emptyList(),
        geloescht: List<Grabsteineintrag> = emptyList(),
    ): Packbericht {
        var geschriebeneNotizen = 0
        var geschriebeneDateien = 0
        val schonDrin = HashSet<String>()

        ZipOutputStream(ziel.buffered()).use { zip ->
            zip.eintrag(EINTRAG_KOPF) { it.write(Sync.encodeToString(kopf).toByteArray()) }
            zip.eintrag(EINTRAG_TAGS) { it.write(Sync.encodeToString(tags).toByteArray()) }
            zip.eintrag(EINTRAG_ORDNER) { it.write(Sync.encodeToString(ordner).toByteArray()) }
            zip.eintrag(EINTRAG_ZAEHLER) { it.write(Sync.encodeToString(zaehler).toByteArray()) }
            zip.eintrag(EINTRAG_GELOESCHT) { it.write(Sync.encodeToString(geloescht).toByteArray()) }

            // Die Anhangsdateien werden waehrend des Notizdurchlaufs
            // gesammelt, aber erst NACH `notes.json` geschrieben: In ein
            // ZIP-Archiv passt immer nur ein Eintrag zur Zeit.
            val anhaenge = ArrayList<Pair<String, File>>()

            zip.eintrag(EINTRAG_NOTIZEN) { strom ->
                strom.write("[\n".toByteArray())
                for (id in notizIds) {
                    val notiz = notizFuer(id) ?: continue
                    if (geschriebeneNotizen > 0) strom.write(",\n".toByteArray())
                    strom.write(Sync.encodeToString(notiz).toByteArray())
                    geschriebeneNotizen++

                    for (anhang in notiz.notiz.attachments) {
                        if (!schonDrin.add(anhang.id)) continue
                        val datei = dateiFuer(anhang) ?: continue
                        if (!datei.isFile || datei.length() == 0L) continue
                        anhaenge += anhangpfad(anhang) to datei
                    }
                }
                strom.write("\n]\n".toByteArray())
            }

            for ((pfad, datei) in anhaenge) {
                // Zwischen dem Sammeln und jetzt kann die Datei verschwunden
                // sein. Das ist kein Grund abzubrechen, wohl aber einer, sie
                // nicht mitzuzaehlen.
                val geschafft = runCatching {
                    zip.eintrag(pfad) { strom -> datei.inputStream().use { it.copyTo(strom) } }
                }.isSuccess
                if (geschafft) geschriebeneDateien++
            }
        }

        return Packbericht(geschriebeneNotizen, tags.size, ordner.size, geschriebeneDateien)
    }

    /**
     * Liest die Sicherung.
     *
     * In einem Durchgang, aber ohne auf die Reihenfolge zu bauen. Ein
     * ZIP-Archiv laesst sich aus einem Datenstrom nur von vorn nach hinten
     * lesen, und in welcher Reihenfolge die Eintraege darin stehen, entscheidet,
     * wer die Datei geschrieben hat. Deshalb wird alles eingesammelt und erst
     * danach zusammengesetzt. Der JSON-Teil ist klein genug fuer den Speicher,
     * die Anhangsdateien gehen sofort auf die Platte.
     *
     * [zielFuer] bekommt den geprueften Dateinamen und sagt, wohin die Bytes
     * gehoeren, oder `null`, wenn dieser Eintrag uebersprungen werden soll.
     */
    fun entpacken(quelle: InputStream, zielFuer: (String) -> File?): Sicherungsinhalt {
        var kopf: Sicherungskopf? = null
        var tags: List<Tagdokument> = emptyList()
        var ordner: List<Ordnerdokument> = emptyList()
        var notizen: List<Sicherungsnotiz> = emptyList()
        var zaehler: List<Zaehlerstand> = emptyList()
        var geloescht: List<Grabsteineintrag> = emptyList()
        val dateien = LinkedHashMap<String, File>()
        var abgewiesen = 0

        ZipInputStream(quelle.buffered()).use { zip ->
            while (true) {
                val eintrag = zip.nextEntry ?: break
                if (eintrag.isDirectory) continue

                when (eintrag.name) {
                    EINTRAG_KOPF -> kopf = runCatching {
                        Sync.decodeFromString<Sicherungskopf>(zip.readBytes().decodeToString())
                    }.getOrNull()

                    EINTRAG_TAGS -> tags = runCatching {
                        Sync.decodeFromString<List<Tagdokument>>(zip.readBytes().decodeToString())
                    }.getOrDefault(emptyList())

                    EINTRAG_ORDNER -> ordner = runCatching {
                        Sync.decodeFromString<List<Ordnerdokument>>(
                            zip.readBytes().decodeToString(),
                        )
                    }.getOrDefault(emptyList())

                    EINTRAG_NOTIZEN -> notizen = runCatching {
                        Sync.decodeFromString<List<Sicherungsnotiz>>(zip.readBytes().decodeToString())
                    }.getOrDefault(emptyList())

                    EINTRAG_ZAEHLER -> zaehler = runCatching {
                        Sync.decodeFromString<List<Zaehlerstand>>(zip.readBytes().decodeToString())
                    }.getOrDefault(emptyList())

                    EINTRAG_GELOESCHT -> geloescht = runCatching {
                        Sync.decodeFromString<List<Grabsteineintrag>>(zip.readBytes().decodeToString())
                    }.getOrDefault(emptyList())

                    else -> {
                        val name = sichererAnhangname(eintrag.name)
                        if (name == null) {
                            abgewiesen++
                            continue
                        }
                        val ziel = zielFuer(name) ?: continue
                        ziel.outputStream().use { zip.copyTo(it) }
                        dateien[anhangIdAus(name)] = ziel
                    }
                }
            }
        }

        return Sicherungsinhalt(kopf, tags, ordner, notizen, dateien, abgewiesen, zaehler, geloescht)
    }

    private inline fun ZipOutputStream.eintrag(name: String, schreiben: (ZipOutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        schreiben(this)
        closeEntry()
    }
}
