package de.notizen.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Name des Spiegelordners in Drive (SYNC.md 2). Seit Schema 4 `InNoteBox`;
 * der alte Ordner `Notizen-App` bleibt unangetastet liegen.
 */
const val ORDNERNAME = "InNoteBox"

/** Der Backup-Ordner mit den Snapshots (SYNC.md 2 und 10). */
const val BACKUP_ORDNERNAME = "InNoteBox-Backup"

private const val MIME_ORDNER = "application/vnd.google-apps.folder"
private const val API = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

/** Eine Datei in Drive, so viel davon wie uns angeht. */
@Serializable
data class Drivedatei(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val modifiedTime: String? = null,
    /** Ändert sich bei jeder Änderung. Grundlage der Konfliktprüfung beim Schreiben. */
    val headRevisionId: String? = null,
    val trashed: Boolean = false,
)

@Serializable
private data class Dateiliste(
    val files: List<Drivedatei> = emptyList(),
    val nextPageToken: String? = null,
)

/**
 * Ein Fehler, den der Aufrufer unterscheiden können muss.
 *
 * Drei Ausgänge, drei verschiedene Reaktionen, deshalb keine gemeinsame
 * Ausnahme: Nicht erlaubt heißt neu anmelden, kein Netz heißt später
 * nochmal, abgelehnt heißt hinsehen. Wer das zusammenwirft, baut einen
 * Sync, der bei fehlendem WLAN zur Neuanmeldung auffordert.
 */
sealed class Drivefehler(nachricht: String) : IOException(nachricht) {
    class NichtErlaubt(nachricht: String) : Drivefehler(nachricht)
    class KeinNetz(nachricht: String) : Drivefehler(nachricht)
    class Abgelehnt(val code: Int, nachricht: String) : Drivefehler(nachricht)
}

/**
 * Drive REST v3, nur die Handvoll Aufrufe, die dieses Projekt braucht.
 *
 * Kein `google-api-services-drive`. Die offizielle Java-Bibliothek zieht
 * einen ganzen Stapel Abhängigkeiten mit, ist auf Server zugeschnitten und
 * verlangt eine eigene HTTP-Schicht. Wir brauchen sechs Endpunkte; das ist mit
 * OkHttp weniger Code als die Konfiguration der Bibliothek.
 *
 * Das Token kommt von außen, bei jedem Aufruf. Dieses Modul kennt weder
 * Google Play Services noch Android, deshalb lässt es sich auf der JVM testen.
 * Wer es sich merkte, hätte irgendwann ein abgelaufenes.
 *
 * `drive.file` sieht nur, was diese App angelegt hat. Eine Suche nach dem
 * Ordner findet ihn also auch dann nicht, wenn er sichtbar in Drive liegt,
 * sofern ihn ein anderer Client erzeugt hat. Das ist der Kern der offenen
 * Frage aus SYNC.md 3 und der Grund für den Spike.
 */
/**
 * Was der Abgleich von Drive braucht, als Schnittstelle.
 *
 * Damit laesst sich der ganze Abgleich mit zwei simulierten Clients gegen
 * einen Speicher pruefen (`DriveImSpeicher` in den Tests), ohne Netz und ohne
 * Konto. Die echte Fassung ist [Drive].
 */
interface Drivezugang {
    suspend fun ordner(token: String, name: String = ORDNERNAME, elternId: String = "root"): String
    suspend fun inhalt(token: String, ordnerId: String): List<Drivedatei>
    suspend fun anlegen(token: String, ordnerId: String, name: String, inhalt: String): Drivedatei
    suspend fun ersetzen(token: String, dateiId: String, inhalt: String): Drivedatei
    suspend fun lesen(token: String, dateiId: String): String
    suspend fun anlegenBinaer(
        token: String,
        ordnerId: String,
        name: String,
        mimeType: String,
        datei: File,
    ): Drivedatei
    suspend fun herunterladen(token: String, dateiId: String, ziel: File)
    suspend fun loeschen(token: String, dateiId: String)
}

@Singleton
class Drive @Inject constructor(
    private val http: OkHttpClient,
) : Drivezugang {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Findet den Notizen-Ordner oder legt ihn an.
     *
     * Gesucht wird nicht im Papierkorb, sonst liefert die Suche einen
     * Ordner, in den nichts mehr geschrieben werden kann, und jeder Upload
     * scheitert mit einer Meldung über die Datei statt über den Ordner.
     */
    override suspend fun ordner(token: String, name: String, elternId: String): String {
        val abfrage = "mimeType='$MIME_ORDNER' and name='$name' " +
            "and trashed=false and '$elternId' in parents"

        val vorhanden = liste(token, abfrage).firstOrNull()
        if (vorhanden != null) return vorhanden.id

        val koerper = """{"name":"$name","mimeType":"$MIME_ORDNER","parents":["$elternId"]}"""
        val antwort = ruf(
            token,
            Request.Builder()
                .url(API)
                .post(koerper.toRequestBody(JSON_TYP)),
        )
        return json.decodeFromString<Drivedatei>(antwort).id
    }

    /** Alle Dateien in einem Ordner. Blättert selbstständig durch. */
    override suspend fun inhalt(token: String, ordnerId: String): List<Drivedatei> =
        liste(token, "'$ordnerId' in parents and trashed=false")

    /** Legt eine Textdatei an und gibt ihre Drive-Kennung zurück. */
    override suspend fun anlegen(
        token: String,
        ordnerId: String,
        name: String,
        inhalt: String,
    ): Drivedatei {
        val grenze = "notizen" + System.nanoTime()
        val kopf = """{"name":"$name","parents":["$ordnerId"]}"""
        val koerper = mehrteilig(grenze, kopf, inhalt)

        val antwort = ruf(
            token,
            Request.Builder()
                .url("$UPLOAD?uploadType=multipart&fields=id,name,headRevisionId")
                .post(koerper.toRequestBody("multipart/related; boundary=$grenze".toMediaType())),
        )
        return json.decodeFromString(antwort)
    }

    /**
     * Überschreibt eine vorhandene Datei.
     *
     * Kein `If-Match`: Drive kennt für Dateiinhalte kein Bedingungsschreiben.
     * Wer sicher sein will, dass zwischendurch niemand geschrieben hat, muss
     * die `headRevisionId` vorher lesen und danach vergleichen, genau das
     * macht die Konfliktauflösung.
     */
    override suspend fun ersetzen(token: String, dateiId: String, inhalt: String): Drivedatei {
        val antwort = ruf(
            token,
            Request.Builder()
                .url("$UPLOAD/$dateiId?uploadType=media&fields=id,name,headRevisionId")
                .patch(inhalt.toRequestBody(JSON_TYP)),
        )
        return json.decodeFromString(antwort)
    }

    override suspend fun lesen(token: String, dateiId: String): String =
        ruf(token, Request.Builder().url("$API/$dateiId?alt=media"))

    /**
     * Legt eine Binärdatei an: Bild oder Aufnahme.
     *
     * Getrennt von [anlegen], weil der Körper nicht in eine Zeichenkette
     * passt. Eine zehnminütige WAV-Aufnahme sind rund zwanzig Megabyte; sie
     * erst in einen String und dann in ein ByteArray zu heben, hieße dreimal
     * dasselbe im Speicher zu halten. [DateiKoerper] schreibt stattdessen
     * direkt aus der Datei in die Verbindung.
     */
    override suspend fun anlegenBinaer(
        token: String,
        ordnerId: String,
        name: String,
        mimeType: String,
        datei: File,
    ): Drivedatei {
        val grenze = "notizen" + System.nanoTime()
        val kopf = """{"name":"$name","parents":["$ordnerId"]}"""

        val antwort = ruf(
            token,
            Request.Builder()
                .url("$UPLOAD?uploadType=multipart&fields=id,name,headRevisionId")
                .post(DateiKoerper(grenze, kopf, mimeType, datei)),
        )
        return json.decodeFromString(antwort)
    }

    /**
     * Lädt eine Datei herunter.
     *
     * Erst daneben, dann umbenennen. Bricht die Übertragung mittendrin ab,
     * läge sonst eine halbe Datei am richtigen Platz, und die sieht für jeden
     * späteren Lauf aus wie eine fertige. Ein halbes Bild wäre dann für immer
     * ein halbes Bild.
     */
    override suspend fun herunterladen(token: String, dateiId: String, ziel: File): Unit =
        withContext(Dispatchers.IO) {
            val anfrage = Request.Builder()
                .url("$API/$dateiId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()

            val antwort = try {
                http.newCall(anfrage).execute()
            } catch (fehler: IOException) {
                throw Drivefehler.KeinNetz(fehler.message ?: "Keine Verbindung zu Google Drive.")
            }

            antwort.use {
                if (!it.isSuccessful) throw fehlerZu(it.code, it.body.string())

                ziel.parentFile?.mkdirs()
                val halb = File(ziel.parentFile, ziel.name + ".teil")
                it.body.byteStream().use { quelle ->
                    halb.outputStream().use { senke -> quelle.copyTo(senke) }
                }
                if (!halb.renameTo(ziel)) {
                    halb.delete()
                    throw Drivefehler.Abgelehnt(0, "Die Datei ließ sich nicht ablegen.")
                }
            }
        }

    /**
     * Löscht endgültig, nicht in den Papierkorb.
     *
     * Eine Notiz, die hier verschwindet, hat den Papierkorb der App schon
     * durchlaufen, sie ein zweites Mal in einen Papierkorb zu legen, wäre
     * eine Rücknahmemöglichkeit, die niemand erwartet und die den Ordner
     * zumüllt.
     */
    override suspend fun loeschen(token: String, dateiId: String) {
        ruf(token, Request.Builder().url("$API/$dateiId").delete())
    }

    // ------------------------------------------------------------- intern

    private suspend fun liste(token: String, abfrage: String): List<Drivedatei> {
        val gefunden = mutableListOf<Drivedatei>()
        var seite: String? = null

        do {
            val url = API.toHttpUrl().newBuilder()
                .addQueryParameter("q", abfrage)
                .addQueryParameter("fields", "files(id,name,mimeType,modifiedTime,headRevisionId),nextPageToken")
                .addQueryParameter("pageSize", "1000")
                .apply { seite?.let { addQueryParameter("pageToken", it) } }
                .build()

            val antwort = json.decodeFromString<Dateiliste>(
                ruf(token, Request.Builder().url(url)),
            )
            gefunden += antwort.files
            seite = antwort.nextPageToken
        } while (seite != null)

        return gefunden
    }

    /**
     * Ein Aufruf gegen Drive.
     *
     * `withContext(Dispatchers.IO)`, weil OkHttps `execute()` blockiert. Ohne
     * das blockierte ein Abgleich den Aufrufer-Thread -- im Hintergrundlauf
     * unauffaellig, im Vordergrund waere es eine haengende Oberflaeche.
     */
    private suspend fun ruf(token: String, bau: Request.Builder): String =
        withContext(Dispatchers.IO) {
        val antwort: Response = try {
            http.newCall(bau.header("Authorization", "Bearer $token").build()).execute()
        } catch (fehler: IOException) {
            throw Drivefehler.KeinNetz(fehler.message ?: "Keine Verbindung zu Google Drive.")
        }

        antwort.use {
            val text = it.body.string()
            if (it.isSuccessful) return@withContext text

            throw fehlerZu(it.code, text)
        }
    }

    /**
     * Was ein Fehlercode bedeutet.
     *
     * 401 heißt immer: Token abgelaufen oder widerrufen. 403 kann beides
     * heißen. Früher galt es pauschal als fehlende Erlaubnis, dann forderte eine
     * überschrittene Quote zur Neuanmeldung auf, und die hätte nichts geholfen.
     * Der Grund steht in der Antwort, also wird er gelesen.
     */
    private fun fehlerZu(code: Int, text: String): Drivefehler = when {
        code == 401 -> Drivefehler.NichtErlaubt("Google verweigert den Zugriff (401).")

        code == 403 && KONTINGENT.any { it in text } ->
            Drivefehler.Abgelehnt(403, "Google drosselt gerade. Später noch einmal.")

        code == 403 -> Drivefehler.NichtErlaubt("Google verweigert den Zugriff (403).")

        else -> Drivefehler.Abgelehnt(code, "Drive antwortete mit $code: $text")
    }

    /**
     * Der mehrteilige Körper für einen Upload mit Metadaten.
     *
     * Von Hand zusammengesetzt: Drive verlangt `multipart/related` mit genau
     * zwei Teilen in dieser Reihenfolge. OkHttps `MultipartBody` erzeugt
     * `multipart/form-data`, und das nimmt Drive nicht an.
     */
    private fun mehrteilig(grenze: String, kopf: String, inhalt: String): String = buildString {
        append("--").append(grenze).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(kopf).append("\r\n")
        append("--").append(grenze).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(inhalt).append("\r\n")
        append("--").append(grenze).append("--")
    }

    private companion object {
        val JSON_TYP = "application/json; charset=utf-8".toMediaType()

        /** Gruende, bei denen ein 403 nur „zu viel auf einmal" bedeutet. */
        val KONTINGENT = listOf(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "quotaExceeded",
            "sharingRateLimitExceeded",
        )
    }
}

/**
 * Ein `multipart/related`-Körper aus JSON-Kopf und einer Datei.
 *
 * Von Hand, aus zwei Gründen. Erstens nimmt Drive nur `multipart/related` mit
 * genau zwei Teilen in dieser Reihenfolge, und OkHttps `MultipartBody` erzeugt
 * `multipart/form-data`. Zweitens wird die Datei gestreamt: `writeTo` bekommt
 * die Verbindung als Senke und schiebt die Bytes durch, ohne sie vorher in den
 * Speicher zu holen.
 *
 * `contentLength` ist bekannt und wird angegeben. Ohne das ginge der Upload als
 * `chunked` hinaus, und Drive antwortet darauf ungnädig.
 */
private class DateiKoerper(
    private val grenze: String,
    private val kopf: String,
    private val mimeType: String,
    private val datei: File,
) : RequestBody() {

    private val vorspann = (
        "--$grenze\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            kopf + "\r\n" +
            "--$grenze\r\n" +
            "Content-Type: $mimeType\r\n\r\n"
        ).toByteArray()

    private val nachspann = "\r\n--$grenze--".toByteArray()

    override fun contentType() = "multipart/related; boundary=$grenze".toMediaType()

    override fun contentLength(): Long = vorspann.size + datei.length() + nachspann.size

    override fun writeTo(sink: BufferedSink) {
        sink.write(vorspann)
        datei.source().use { sink.writeAll(it) }
        sink.write(nachspann)
    }
}
