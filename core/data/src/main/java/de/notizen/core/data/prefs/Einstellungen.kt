package de.notizen.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import de.notizen.core.data.model.Darstellung
import de.notizen.core.data.model.Faehigkeit
import de.notizen.core.data.model.Geraetestand
import de.notizen.core.data.model.Ordnersortierung
import de.notizen.core.data.model.Sperrverzoegerung
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.ThemeWahl
import de.notizen.core.data.model.Transkriptsprache
import de.notizen.core.data.model.Uebersetzungsweg
import de.notizen.core.data.model.WischZiel
import de.notizen.core.data.repository.NoteSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dauerhafte Einstellungen der App.
 *
 * WARUM PRO STUFE: Der Eingang ist ein Ablagestapel, das Archiv eine
 * Nachschlagesammlung -- sie wollen nicht dieselbe Darstellung. Eine einzige
 * globale Einstellung waere die falsche Vereinfachung.
 *
 * Vorher lebten diese Werte nur im ViewModel. Weil beim Stufenwechsel ein neues
 * ViewModel entsteht, sprang die Ansicht bei jedem Wechsel auf den Standard
 * zurueck -- genau der Fehler, der am Geraet aufgefallen ist.
 *
 * WARUM DER STORE HEREINGEREICHT WIRD statt ueber `preferencesDataStore` am
 * Context zu haengen: jenes Delegate legt einen versteckten, prozessweiten
 * Speicher an. Der laesst sich in Tests nicht zuruecksetzen -- ein Test faerbt
 * damit den naechsten ein. Explizit hereingereicht ist die Abhaengigkeit
 * sichtbar und austauschbar.
 *
 * Unbekannte gespeicherte Werte (etwa aus einer neueren Version) fallen still
 * auf den Standard zurueck, statt die App beim Start abstuerzen zu lassen.
 */
@Singleton
class Einstellungen @Inject constructor(
    private val store: DataStore<Preferences>,
    private val kiSiegel: KiSiegel = KiSiegel.KEINES,
) {

    // ------------------------------------------------------------ Darstellung

    fun darstellung(stage: Stage): Flow<Darstellung> =
        store.data.map { p ->
            p[darstellungKey(stage)]
                ?.let { runCatching { Darstellung.valueOf(it) }.getOrNull() }
                ?: Darstellung.RASTER
        }

    suspend fun setDarstellung(stage: Stage, wert: Darstellung) {
        store.edit { it[darstellungKey(stage)] = wert.name }
    }

    // ------------------------------------------------------------- Sortierung

    fun sortierung(stage: Stage): Flow<NoteSort> =
        store.data.map { p ->
            p[sortierungKey(stage)]
                ?.let { runCatching { NoteSort.valueOf(it) }.getOrNull() }
                ?: NoteSort.CREATED
        }

    suspend fun setSortierung(stage: Stage, wert: NoteSort) {
        store.edit { it[sortierungKey(stage)] = wert.name }
    }

    // ----------------------------------------------------------- Darstellung

    fun themeWahl(): Flow<ThemeWahl> =
        store.data.map { p ->
            p[THEME]?.let { runCatching { ThemeWahl.valueOf(it) }.getOrNull() } ?: ThemeWahl.SYSTEM
        }

    suspend fun setThemeWahl(wert: ThemeWahl) {
        store.edit { it[THEME] = wert.name }
    }

    /**
     * Dynamic Color ist der Standard. Ausschalten
     * bringt das Fallback-Schema aus dem Seed #001835 zurueck.
     */
    fun dynamicColor(): Flow<Boolean> = store.data.map { it[DYNAMIC_COLOR] ?: true }

    suspend fun setDynamicColor(an: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = an }
    }

    // --------------------------------------------------------- Wischgesten

    /**
     * Voreinstellung ist der Fluss selbst: nach rechts vorwaerts, nach links
     * zurueck. Wer lieber nach links loescht, stellt es hier um.
     */
    fun wischRechts(): Flow<WischZiel> = wisch(WISCH_RECHTS, WischZiel.NAECHSTE_STUFE)

    fun wischLinks(): Flow<WischZiel> = wisch(WISCH_LINKS, WischZiel.VORHERIGE_STUFE)

    suspend fun setWischRechts(wert: WischZiel) {
        store.edit { it[WISCH_RECHTS] = wert.name }
    }

    suspend fun setWischLinks(wert: WischZiel) {
        store.edit { it[WISCH_LINKS] = wert.name }
    }

    /**
     * Die Gesten im Papierkorb, getrennt von denen im Fluss (seit
     * 2026-09-15). Voreinstellung: nach rechts endgueltig loeschen, nach links
     * zurueckholen. Beides rastet ein und verlangt den zweiten Tipp.
     */
    fun wischPapierkorbRechts(): Flow<WischZiel> =
        wisch(WISCH_PAPIERKORB_RECHTS, WischZiel.ENDGUELTIG, WischZiel.imPapierkorbWaehlbar)

    fun wischPapierkorbLinks(): Flow<WischZiel> =
        wisch(WISCH_PAPIERKORB_LINKS, WischZiel.WIEDERHERSTELLEN, WischZiel.imPapierkorbWaehlbar)

    suspend fun setWischPapierkorbRechts(wert: WischZiel) {
        store.edit { it[WISCH_PAPIERKORB_RECHTS] = wert.name }
    }

    suspend fun setWischPapierkorbLinks(wert: WischZiel) {
        store.edit { it[WISCH_PAPIERKORB_LINKS] = wert.name }
    }

    private fun wisch(
        key: Preferences.Key<String>,
        standard: WischZiel,
        erlaubt: List<WischZiel> = WischZiel.waehlbar,
    ): Flow<WischZiel> =
        store.data.map { p ->
            p[key]?.let { runCatching { WischZiel.valueOf(it) }.getOrNull() }
                ?.takeIf { it in erlaubt }
                ?: standard
        }

    // ----------------------------------------------------- Sprache und KI

    /**
     * Sprache der Spracherkennung.
     *
     * Gilt für Aufnahme UND nachträgliches Transkribieren, sonst nähme man in
     * einer Sprache auf und ließe in einer anderen erkennen, und das Ergebnis
     * wäre unerklärlich schlecht.
     */
    fun transkriptsprache(): Flow<Transkriptsprache> =
        store.data.map { Transkriptsprache.ausName(it[TRANSKRIPTSPRACHE]) }

    suspend fun setTranskriptsprache(wert: Transkriptsprache) {
        store.edit { it[TRANSKRIPTSPRACHE] = wert.name }
    }

    /**
     * Ob die KI auf dem Gerät arbeiten darf: Titelvorschläge, das Umwandeln in
     * Text und das Aufbereiten von Transkripten.
     *
     * Schaltet die Bedienelemente ab, nicht bloß den Aufruf: Ein Knopf, der da
     * ist und nichts tut, wäre schlechter als keiner.
     *
     * Wirksam nur mit gültiger, versiegelter Zustimmung (seit Alpha 9). Die
     * KI läuft über ML Kit, und ML Kit schickt Google Kennzahlen über die
     * Nutzung. Das Auslesen dafür braucht nach § 25 TDDDG eine Einwilligung.
     * Die Zustimmung ist eine Aufzeichnung mit Siegel ([KiSiegel]); fehlt sie,
     * ist sie verändert oder gilt sie für einen anderen Text, ist die KI aus,
     * und die App ruft ML Kit gar nicht erst auf, auch kein `checkStatus`.
     */
    fun kiAktiv(): Flow<Boolean> = kiLage().map { it == KiLage.AN }

    /**
     * Ob beim Start nach der KI gefragt wird: nicht ausgeschaltet, aber ohne
     * gültige Zustimmung. Trifft den ersten Start, Installationen aus der Zeit
     * vor der Zustimmung und jede Zustimmung, deren Siegel nicht mehr passt.
     * Wer mit „Ohne KI" ablehnt, wird nicht wieder gefragt.
     */
    fun kiFrageOffen(): Flow<Boolean> = kiLage().map { it == KiLage.OFFEN }

    /** Die gespeicherte Aufzeichnung der Zustimmung, oder `null`. Gilt nur zusammen mit [kiAktiv]. */
    fun kiZustimmung(): Flow<String?> = store.data.map { it[KI_ZUSTIMMUNG] }

    /**
     * Einschalten gibt es nur mit einer versiegelten Zustimmung. Beides wird
     * zusammen geschrieben; eine halbe Zustimmung kann nicht entstehen.
     */
    suspend fun kiEinschalten(aufzeichnung: String, siegel: String) {
        store.edit {
            it[KI_AKTIV] = true
            it[KI_ZUSTIMMUNG] = aufzeichnung
            it[KI_SIEGEL] = siegel
        }
    }

    /**
     * Ausschalten nimmt die Zustimmung restlos zurück: Aufzeichnung und Siegel
     * verschwinden. Den Schlüssel dazu löscht die App-Schicht. Wer wieder
     * einschaltet, stimmt neu zu, und dabei entsteht ein neuer Schlüssel.
     */
    suspend fun kiAusschalten() {
        store.edit {
            it[KI_AKTIV] = false
            it.remove(KI_ZUSTIMMUNG)
            it.remove(KI_SIEGEL)
            it.remove(BESCHRIFTUNG_OFFEN)
        }
    }

    /**
     * Notizen, deren Beschriftung durch die KI noch aussteht.
     *
     * Der naechtliche Lauf darf die KI nicht benutzen: Google laesst sie nur
     * arbeiten, solange die App im Vordergrund ist. Er merkt die Notizen deshalb
     * hier vor, und beim naechsten Oeffnen holt die App Titel und Tag nach.
     */
    fun beschriftungOffen(): Flow<Set<String>> = store.data.map { it[BESCHRIFTUNG_OFFEN] ?: emptySet() }

    suspend fun beschriftungVormerken(ids: Collection<String>) {
        if (ids.isEmpty()) return
        store.edit {
            val alle = (it[BESCHRIFTUNG_OFFEN] ?: emptySet()) + ids
            // Eine Obergrenze, falls die KI lange nicht bereit ist. Was darueber
            // hinausgeht, behaelt den Titel aus dem Text, und das ist kein Verlust.
            it[BESCHRIFTUNG_OFFEN] = if (alle.size > MAX_BESCHRIFTUNG_OFFEN) {
                alle.take(MAX_BESCHRIFTUNG_OFFEN).toSet()
            } else {
                alle
            }
        }
    }

    suspend fun beschriftungErledigt(id: String) {
        store.edit { it[BESCHRIFTUNG_OFFEN] = (it[BESCHRIFTUNG_OFFEN] ?: emptySet()) - id }
    }

    suspend fun beschriftungLeeren() {
        store.edit { it.remove(BESCHRIFTUNG_OFFEN) }
    }

    /** Welche Fassung von `SCHEMA.md` zuletzt nach Drive geschrieben wurde. */
    fun schemaTextStand(): Flow<String> = store.data.map { it[SCHEMA_TEXT_STAND] ?: "" }

    suspend fun setSchemaTextStand(stand: String) {
        store.edit { it[SCHEMA_TEXT_STAND] = stand }
    }

    private enum class KiLage { AN, AUS, OFFEN }

    // Geprüft wird nur, wenn sich eines der drei Felder ändert, nicht bei jeder
    // anderen Einstellung. Die Prüfung spricht mit dem Schlüsselspeicher des
    // Systems und läuft deshalb nicht auf dem Hauptthread.
    private fun kiLage(): Flow<KiLage> = store.data
        .map { Triple(it[KI_AKTIV] ?: true, it[KI_ZUSTIMMUNG], it[KI_SIEGEL]) }
        .distinctUntilChanged()
        .map { (an, aufzeichnung, siegel) ->
            when {
                !an -> KiLage.AUS
                aufzeichnung != null && siegel != null &&
                    runCatching { kiSiegel.gueltig(aufzeichnung, siegel) }.getOrDefault(false) -> KiLage.AN
                else -> KiLage.OFFEN
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Ob beim Verschieben ab dem Workspace ein Titel verlangt wird.
     * Standard an; wer es abschaltet, bekommt vorher die Rueckfrage mit dem
     * Grund: Im Archiv wird ueber Titel gesucht.
     */
    fun titelpflicht(): Flow<Boolean> = store.data.map { it[TITELPFLICHT] ?: true }

    suspend fun setTitelpflicht(an: Boolean) {
        store.edit { it[TITELPFLICHT] = an }
    }

    // --------------------------------------------------------- Sicherheit

    /**
     * Ob die App beim Oeffnen entsperrt werden muss. Standard aus;
     * Einschalten geht nur nach einer erfolgreichen Entsperrung, damit sich
     * niemand aussperrt.
     */
    fun sperreAn(): Flow<Boolean> = store.data.map { it[SPERRE_AN] ?: false }

    suspend fun setSperreAn(an: Boolean) {
        store.edit { it[SPERRE_AN] = an }
    }

    /** Wie lange die App im Hintergrund sein darf, bevor sie sich sperrt. */
    fun sperrverzoegerung(): Flow<Sperrverzoegerung> =
        store.data.map { Sperrverzoegerung.ausName(it[SPERRVERZOEGERUNG]) }

    suspend fun setSperrverzoegerung(wert: Sperrverzoegerung) {
        store.edit { it[SPERRVERZOEGERUNG] = wert.name }
    }

    /** Ob Bildschirmfotos und Bildschirmaufnahmen der App unterbunden werden (`FLAG_SECURE`). */
    fun aufnahmeschutzAn(): Flow<Boolean> = store.data.map { it[AUFNAHMESCHUTZ_AN] ?: false }

    suspend fun setAufnahmeschutzAn(an: Boolean) {
        store.edit { it[AUFNAHMESCHUTZ_AN] = an }
    }

    // ------------------------------------------------------- Uebersetzung

    /** Ueber welchen Weg uebersetzt wird. */
    fun uebersetzungsweg(): Flow<Uebersetzungsweg> =
        store.data.map { Uebersetzungsweg.ausName(it[UEBERSETZUNGSWEG]) }

    suspend fun setUebersetzungsweg(weg: Uebersetzungsweg) {
        store.edit { it[UEBERSETZUNGSWEG] = weg.name }
    }

    /**
     * Die zuletzt gewaehlten Sprachen, als Sprachcodes (`de`, `en`). Der Dialog
     * im Editor faengt damit an, damit man sie nicht jedes Mal neu waehlt.
     */
    fun uebersetzungSprachen(): Flow<Pair<String, String>> =
        store.data.map { (it[UEBERSETZUNG_VON] ?: "de") to (it[UEBERSETZUNG_NACH] ?: "en") }

    suspend fun setUebersetzungSprachen(von: String, nach: String) {
        store.edit {
            it[UEBERSETZUNG_VON] = von
            it[UEBERSETZUNG_NACH] = nach
        }
    }

    /**
     * Ob die App ins Netz darf (Opt-in). Standard aus. Eingeschaltet
     * wird nur ueber den Dialog mit den Bedingungen; hier steht auch, wann.
     * Heute haengt daran nur das Nachladen der Sprachpakete von ML Kit; die
     * Online-KI kommt dazu, sobald ein Anbieter gewaehlt ist.
     */
    fun netzErlaubt(): Flow<Boolean> = store.data.map { it[NETZ_ERLAUBT] ?: false }

    fun netzZugestimmtAm(): Flow<Long> = store.data.map { it[NETZ_ZUGESTIMMT_AM] ?: 0L }

    suspend fun setNetzErlaubt(an: Boolean, wann: Long) {
        store.edit {
            it[NETZ_ERLAUBT] = an
            if (an) it[NETZ_ZUGESTIMMT_AM] = wann
        }
    }

    /**
     * Der gemerkte Geraetestand, oder `null`, wenn noch nie
     * gemessen wurde (erster Start, Datenloeschung).
     */
    fun geraetestand(): Flow<Geraetestand?> = store.data.map { p ->
        val sprache = p[GERAET_SPRACHE]?.let { runCatching { Faehigkeit.valueOf(it) }.getOrNull() }
        val textki = p[GERAET_TEXTKI]?.let { runCatching { Faehigkeit.valueOf(it) }.getOrNull() }
        val uebersetzung = p[GERAET_UEBERSETZUNG]?.let { runCatching { Faehigkeit.valueOf(it) }.getOrNull() }
        if (sprache == null || textki == null || uebersetzung == null) {
            null
        } else {
            Geraetestand(
                spracherkennung = sprache,
                textki = textki,
                uebersetzung = uebersetzung,
                geprueftAm = p[GERAET_GEPRUEFT_AM] ?: 0L,
                appVersion = p[GERAET_APP_VERSION] ?: 0,
            )
        }
    }

    suspend fun setGeraetestand(stand: Geraetestand) {
        store.edit {
            it[GERAET_SPRACHE] = stand.spracherkennung.name
            it[GERAET_TEXTKI] = stand.textki.name
            it[GERAET_UEBERSETZUNG] = stand.uebersetzung.name
            it[GERAET_GEPRUEFT_AM] = stand.geprueftAm
            it[GERAET_APP_VERSION] = stand.appVersion
        }
    }

    // ------------------------------------------------------- Auto-Archiv

    /**
     * Hauptschalter der automatischen Archivierung.
     *
     * Standardmäßig aus. Eine Automatik, die
     * ungefragt Notizen verschiebt, muss man einschalten wollen, sonst wirkt
     * die erste Nacht wie ein Datenverlust.
     */
    fun autoArchivAn(): Flow<Boolean> = store.data.map { it[AUTO_ARCHIV] ?: false }

    suspend fun setAutoArchivAn(an: Boolean) {
        store.edit { it[AUTO_ARCHIV] = an }
    }

    /**
     * Nach wie vielen Tagen ohne Öffnen eine Notiz dieser Stufe wegwandert.
     *
     * `0` heißt aus. Ein eigener Schalter je Stufe wäre ein zweiter Wert,
     * der dasselbe sagt, und zwei Werte, die dasselbe sagen, laufen
     * irgendwann auseinander.
     */
    fun altersgrenze(stage: Stage): Flow<Int> =
        store.data.map { it[altersgrenzeKey(stage)] ?: standardAlter(stage) }

    suspend fun setAltersgrenze(stage: Stage, tage: Int) {
        store.edit { it[altersgrenzeKey(stage)] = tage.coerceAtLeast(0) }
    }

    /**
     * Wie viele Notizen eine Stufe höchstens hält. `0` heißt aus.
     *
     * Greift unabhängig vom Alter: Ein Eingang mit zweihundert Notizen ist auch
     * dann kein Eingang mehr, wenn jede davon von gestern ist.
     */
    fun mengengrenze(stage: Stage): Flow<Int> =
        store.data.map { it[mengengrenzeKey(stage)] ?: standardMenge(stage) }

    suspend fun setMengengrenze(stage: Stage, anzahl: Int) {
        store.edit { it[mengengrenzeKey(stage)] = anzahl.coerceAtLeast(0) }
    }

    /**
     * Nach wie vielen Tagen der Papierkorb sich selbst leert. `0` heißt aus.
     *
     * Steht auf `0`, und das ist keine Bequemlichkeit: Hier wird endgültig
     * gelöscht. Wer das will, soll es sagen.
     */
    fun papierkorbFrist(): Flow<Int> = store.data.map { it[PAPIERKORB_FRIST] ?: 0 }

    suspend fun setPapierkorbFrist(tage: Int) {
        store.edit { it[PAPIERKORB_FRIST] = tage.coerceAtLeast(0) }
    }

    // ------------------------------------------------------------- Sync

    /**
     * Wie oft im Hintergrund abgeglichen wird, in Minuten. `0` heißt aus.
     *
     * Untergrenze ist 15, weniger lässt WorkManager für wiederkehrende Arbeit
     * nicht zu, und ein Wert darunter wäre ein Versprechen, das das System
     * nicht hält.
     *
     * Steuert beides: den regelmäßigen Lauf und den Anstoß nach dem
     * Speichern. Wer „aus" wählt, will nicht, dass im Hintergrund etwas zu
     * Google geht, dann darf auch das Speichern nichts auslösen.
     */
    fun syncIntervall(): Flow<Int> = store.data.map { it[SYNC_INTERVALL] ?: 15 }

    suspend fun setSyncIntervall(minuten: Int) {
        store.edit { it[SYNC_INTERVALL] = if (minuten <= 0) 0 else maxOf(15, minuten) }
    }

    /**
     * Ob der Nutzer die Verbindung selbst gelöst hat.
     *
     * Das ist der eigentliche Ausschalter, und er liegt bewusst hier. Der
     * Widerruf bei Google kann fehlschlagen: kein Netz, ein Konto, das gerade
     * nichts sagt, ein Play-Dienst, der klemmt. „Trennen" darf davon nicht
     * abhängen, wer seine Sachen trennt, muss das auch können.
     *
     * Es ist keine zweite Wahrheit neben Googles: Es sind zwei verschiedene
     * Fragen. Google beantwortet, ob die App auf Drive zugreifen darf;
     * dieser Wert beantwortet, ob sie es soll. Verbunden ist nur, wo beides
     * ja ist.
     */
    fun syncGetrennt(): Flow<Boolean> = store.data.map { it[SYNC_GETRENNT] ?: false }

    /**
     * Ob Drive auf diesem Geraet je verbunden war.
     *
     * Solange nicht, fragt die App gar nicht bei Google nach, auch nicht
     * lautlos (siehe `Anmeldung`). Ein frueherer erfolgreicher Abgleich zaehlt
     * mit, damit Geraete verbunden bleiben, die es schon vor diesem Wert waren.
     */
    fun syncJeVerbunden(): Flow<Boolean> = store.data.map {
        (it[SYNC_JE_VERBUNDEN] ?: false) || (it[LETZTER_ABGLEICH] ?: 0L) > 0L
    }

    suspend fun setSyncJeVerbunden(verbunden: Boolean) {
        store.edit { it[SYNC_JE_VERBUNDEN] = verbunden }
    }

    suspend fun setSyncGetrennt(getrennt: Boolean) {
        store.edit { it[SYNC_GETRENNT] = getrennt }
    }

    /**
     * Ob der Abgleich auf WLAN warten soll.
     *
     * Steht hier, seit auch Dateien mitgehen. Eine Textnotiz sind ein paar
     * Kilobyte; eine zehnminuetige Aufnahme sind rund zwanzig Megabyte, und die
     * gingen sonst ungefragt ueber das Mobilfunkvolumen.
     *
     * Voreinstellung ist aus, also auch mobil. Wer den Abgleich einschaltet,
     * will seine Sachen gesichert haben, und ein Sync, der drei Tage wartet,
     * weil man nicht zu Hause war, sichert nichts.
     */
    fun syncNurWlan(): Flow<Boolean> = store.data.map { it[SYNC_NUR_WLAN] ?: false }

    suspend fun setSyncNurWlan(nurWlan: Boolean) {
        store.edit { it[SYNC_NUR_WLAN] = nurWlan }
    }

    /**
     * Ob Erinnerungen als Termine im Kalender des Geraets erscheinen.
     *
     * Voreinstellung aus. Eine App, die ungefragt in den Kalender schreibt,
     * hat sich das Recht dazu nicht abgeholt, auch wenn Android es ihr gegeben
     * haette.
     */
    fun kalenderAn(): Flow<Boolean> = store.data.map { it[KALENDER_AN] ?: false }

    suspend fun setKalenderAn(an: Boolean) {
        store.edit { it[KALENDER_AN] = an }
    }

    /**
     * In welchen Kalender geschrieben wird. `-1` heisst: noch keiner gewaehlt.
     *
     * Die Kennung kommt vom Kalender-Anbieter des Geraets und gilt nur hier.
     * Ohne Wahl wird nichts eingetragen, auch wenn der Schalter an ist: Einen
     * Kalender zu raten hiesse, Termine irgendwo abzulegen.
     */
    fun kalenderId(): Flow<Long> = store.data.map { it[KALENDER_ID] ?: -1L }

    suspend fun setKalenderId(id: Long) {
        store.edit { it[KALENDER_ID] = id }
    }

    // -------------------------------------------------------------- Ordner

    /**
     * Ob die App im Ordnermodus laeuft statt im Fluss.
     *
     * Ein Schalter, der die ganze App umstellt. Im Ordnermodus
     * verschwinden Eingang, Workspace und Archiv aus der Seitenspalte, und das
     * automatische Aufraeumen ruht -- es raeumt zwischen Stufen, die dann
     * niemand mehr sieht. Die Stufe jeder Notiz bleibt trotzdem gefuehrt, also
     * verliert das Zurueckschalten nichts.
     *
     * Voreingestellt auf aus. Der Fluss ist der Entwurf dieser App; die Ordner
     * sind die Antwort fuer den, der lieber selbst einteilt.
     */
    fun ordnermodus(): Flow<Boolean> = store.data.map { it[ORDNERMODUS] ?: false }

    suspend fun setOrdnermodus(an: Boolean) {
        store.edit { it[ORDNERMODUS] = an }
    }

    /**
     * Darstellung und Sortierung der Ordneransicht.
     *
     * Eigene Werte und nicht die einer Stufe. Der Grund ist derselbe, aus dem
     * die Stufen sie einzeln fuehren: Ein Ordner ist eine Sammlung, die man
     * durchsieht, kein Stapel, den man abarbeitet, und er will nicht
     * zwangslaeufig dieselbe Ansicht wie der Eingang.
     */
    fun ordnerDarstellung(): Flow<Darstellung> =
        store.data.map { p ->
            p[ORDNER_DARSTELLUNG]
                ?.let { runCatching { Darstellung.valueOf(it) }.getOrNull() }
                ?: Darstellung.RASTER
        }

    suspend fun setOrdnerDarstellung(wert: Darstellung) {
        store.edit { it[ORDNER_DARSTELLUNG] = wert.name }
    }

    fun ordnerSortierung(): Flow<NoteSort> =
        store.data.map { p ->
            p[ORDNER_SORTIERUNG]
                ?.let { runCatching { NoteSort.valueOf(it) }.getOrNull() }
                ?: NoteSort.CREATED
        }

    suspend fun setOrdnerSortierung(wert: NoteSort) {
        store.edit { it[ORDNER_SORTIERUNG] = wert.name }
    }

    /** Wonach die Ordner geordnet stehen, fuer die ganze App. */
    fun ordnerReihenfolge(): Flow<Ordnersortierung> =
        store.data.map { p ->
            p[ORDNER_REIHENFOLGE]
                ?.let { runCatching { Ordnersortierung.valueOf(it) }.getOrNull() }
                ?: Ordnersortierung.EIGENE
        }

    suspend fun setOrdnerReihenfolge(wert: Ordnersortierung) {
        store.edit { it[ORDNER_REIHENFOLGE] = wert.name }
    }

    /** Wann zuletzt erfolgreich abgeglichen wurde. `0` = noch nie. */
    fun letzterAbgleich(): Flow<Long> = store.data.map { it[LETZTER_ABGLEICH] ?: 0L }

    suspend fun setLetzterAbgleich(wann: Long) {
        store.edit { it[LETZTER_ABGLEICH] = wann }
    }

    // ------------------------------------------------ Schema 4 (SYNC.md)

    /**
     * Die Kennung dieses Geraets in `index.json` (SYNC.md 3.4).
     *
     * Wird beim ersten Aufruf erzeugt und danach nie mehr geaendert. Kurz,
     * damit sie in `lastEditor` lesbar bleibt; acht Hexzeichen reichen, denn
     * es geht um eine Handvoll Geraete eines Menschen, nicht um die Welt.
     */
    suspend fun geraeteId(): String {
        val vorhanden = store.data.map { it[GERAETE_ID] }.first()
        if (vorhanden != null) return vorhanden
        val neu = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        store.edit { it[GERAETE_ID] = neu }
        return neu
    }

    /** Der Gerätename zur Anzeige in `index.json`. Setzt die App beim Start. */
    fun geraeteLabel(): Flow<String> = store.data.map { it[GERAETE_LABEL] ?: "Gerät" }

    suspend fun setGeraeteLabel(label: String) {
        store.edit { it[GERAETE_LABEL] = label }
    }

    /**
     * An welchem Kalendertag der taegliche Durchlauf (SYNC.md 9) zuletzt lief,
     * als `YYYY-MM-DD`. Leer = noch nie.
     */
    fun letzterTagesdurchlauf(): Flow<String> =
        store.data.map { it[LETZTER_TAGESDURCHLAUF] ?: "" }

    suspend fun setLetzterTagesdurchlauf(tag: String) {
        store.edit { it[LETZTER_TAGESDURCHLAUF] = tag }
    }

    /**
     * Der letzte Pruefbericht (SYNC.md 9), als JSON. Leer = noch keiner.
     *
     * Der Bericht gehoert nicht in die Datenbank: Er ist kein Bestand, sondern
     * die Aussage eines Laufs, und die naechste ersetzt ihn ganz.
     */
    fun letzterPruefbericht(): Flow<String> = store.data.map { it[LETZTER_PRUEFBERICHT] ?: "" }

    suspend fun setLetzterPruefbericht(json: String) {
        store.edit { it[LETZTER_PRUEFBERICHT] = json }
    }

    /**
     * Das lokale Abbild von `purgeWatermark` (SYNC.md 7). Ein Grabstein, der
     * aelter ist, darf vergessen werden.
     */
    fun purgeWasserzeichen(): Flow<Long> = store.data.map { it[PURGE_WASSERZEICHEN] ?: 0L }

    suspend fun setPurgeWasserzeichen(wann: Long) {
        store.edit { it[PURGE_WASSERZEICHEN] = wann }
    }

    /**
     * Ob der Abgleich von selbst laeuft (SYNC.md 5). Seit Schema 4 gibt es
     * keinen Takt mehr; der fruehere Wert `syncIntervall` lebt nur noch als
     * Schalter weiter: null hiess aus, alles andere an.
     */
    fun syncAutomatisch(): Flow<Boolean> = store.data.map { (it[SYNC_INTERVALL] ?: 15) > 0 }

    suspend fun setSyncAutomatisch(an: Boolean) {
        store.edit { it[SYNC_INTERVALL] = if (an) 15 else 0 }
    }

    private fun altersgrenzeKey(stage: Stage) = intPreferencesKey("alter_${stage.name}")

    private fun mengengrenzeKey(stage: Stage) = intPreferencesKey("menge_${stage.name}")

    /**
     * Vorschläge, keine Zwangswerte, der Hauptschalter steht ja auf aus.
     *
     * Der Eingang ist ein Durchgangsraum und darf schnell räumen; der Workspace
     * ist der Ort, an dem gearbeitet wird, und bekommt deutlich mehr Zeit. Das
     * Archiv taucht hier gar nicht auf: Dorthin wird archiviert, es ist das
     * Ziel und nie die Quelle.
     */
    private fun standardAlter(stage: Stage): Int = when (stage) {
        Stage.INBOX -> 14
        Stage.WORKSPACE -> 60
        Stage.ARCHIVE -> 0
    }

    private fun standardMenge(stage: Stage): Int = when (stage) {
        Stage.INBOX -> 50
        Stage.WORKSPACE -> 0
        Stage.ARCHIVE -> 0
    }

    private fun darstellungKey(stage: Stage) = stringPreferencesKey("darstellung_${stage.name}")

    private fun sortierungKey(stage: Stage) = stringPreferencesKey("sortierung_${stage.name}")

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_ARCHIV = booleanPreferencesKey("auto_archiv")
        val PAPIERKORB_FRIST = intPreferencesKey("papierkorb_frist")
        val SYNC_INTERVALL = intPreferencesKey("sync_intervall")
        val LETZTER_ABGLEICH = longPreferencesKey("letzter_abgleich")
        val SYNC_GETRENNT = booleanPreferencesKey("sync_getrennt")
        val SYNC_JE_VERBUNDEN = booleanPreferencesKey("sync_je_verbunden")
        val BESCHRIFTUNG_OFFEN = stringSetPreferencesKey("beschriftung_offen")
        val SCHEMA_TEXT_STAND = stringPreferencesKey("schema_text_stand")
        const val MAX_BESCHRIFTUNG_OFFEN = 500
        val SYNC_NUR_WLAN = booleanPreferencesKey("sync_nur_wlan")
        val WISCH_RECHTS = stringPreferencesKey("wisch_rechts")
        val WISCH_LINKS = stringPreferencesKey("wisch_links")
        val WISCH_PAPIERKORB_RECHTS = stringPreferencesKey("wisch_papierkorb_rechts")
        val WISCH_PAPIERKORB_LINKS = stringPreferencesKey("wisch_papierkorb_links")
        val TRANSKRIPTSPRACHE = stringPreferencesKey("transkriptsprache")
        val KI_AKTIV = booleanPreferencesKey("ki_aktiv")
        val KI_ZUSTIMMUNG = stringPreferencesKey("ki_zustimmung")
        val KI_SIEGEL = stringPreferencesKey("ki_siegel")
        val TITELPFLICHT = booleanPreferencesKey("titelpflicht")
        val GERAET_SPRACHE = stringPreferencesKey("geraet_sprache")
        val GERAET_TEXTKI = stringPreferencesKey("geraet_textki")
        val GERAET_UEBERSETZUNG = stringPreferencesKey("geraet_uebersetzung")
        val GERAET_GEPRUEFT_AM = longPreferencesKey("geraet_geprueft_am")
        val GERAET_APP_VERSION = intPreferencesKey("geraet_app_version")
        val UEBERSETZUNGSWEG = stringPreferencesKey("uebersetzungsweg")
        val SPERRE_AN = booleanPreferencesKey("sperre_an")
        val SPERRVERZOEGERUNG = stringPreferencesKey("sperrverzoegerung")
        val AUFNAHMESCHUTZ_AN = booleanPreferencesKey("aufnahmeschutz_an")
        val UEBERSETZUNG_VON = stringPreferencesKey("uebersetzung_von")
        val UEBERSETZUNG_NACH = stringPreferencesKey("uebersetzung_nach")
        val NETZ_ERLAUBT = booleanPreferencesKey("netz_erlaubt")
        val NETZ_ZUGESTIMMT_AM = longPreferencesKey("netz_zugestimmt_am")
        val KALENDER_AN = booleanPreferencesKey("kalender_an")
        val ORDNERMODUS = booleanPreferencesKey("ordnermodus")
        val ORDNER_DARSTELLUNG = stringPreferencesKey("ordner_darstellung")
        val ORDNER_SORTIERUNG = stringPreferencesKey("ordner_sortierung")
        val ORDNER_REIHENFOLGE = stringPreferencesKey("ordner_reihenfolge")
        val KALENDER_ID = longPreferencesKey("kalender_id")
        val GERAETE_ID = stringPreferencesKey("geraete_id")
        val GERAETE_LABEL = stringPreferencesKey("geraete_label")
        val LETZTER_TAGESDURCHLAUF = stringPreferencesKey("letzter_tagesdurchlauf")
        val LETZTER_PRUEFBERICHT = stringPreferencesKey("letzter_pruefbericht")
        val PURGE_WASSERZEICHEN = longPreferencesKey("purge_wasserzeichen")
    }
}
