package de.notizen.app.ui.daten

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.notizen.core.sync.Abgleichschritt
import de.notizen.core.sync.Pruefbericht
import de.notizen.app.sync.SyncViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.notizen.core.data.model.Stage
import de.notizen.core.data.repository.NoteRepository
import de.notizen.core.data.repository.TagRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Die Seite, auf der man Apps den Zugriff wieder entzieht.
 *
 * Steht hier und nicht in einer Anleitung: Wer „Trennen" drückt und wissen will,
 * ob wirklich nichts mehr offen ist, soll nicht suchen müssen.
 */
private const val KONTO_SEITE = "https://myaccount.google.com/connections"

/** Der Dateiwaehler filtert nicht. Warum, steht bei [BackupScreen]. */
private const val ALLE_DATEIEN = "*/" + "*"

/** Was gesichert bzw. synchronisiert würde. */
data class Bestand(
    val eingang: Int = 0,
    val workspace: Int = 0,
    val archiv: Int = 0,
    val tags: Int = 0,
) {
    val notizen: Int get() = eingang + workspace + archiv
}

@HiltViewModel
class DatenViewModel @Inject constructor(
    notes: NoteRepository,
    tags: TagRepository,
) : ViewModel() {

    val bestand: StateFlow<Bestand> = combine(
        notes.observeCount(Stage.INBOX),
        notes.observeCount(Stage.WORKSPACE),
        notes.observeCount(Stage.ARCHIVE),
        tags.observeAll(),
    ) { e, w, a, t -> Bestand(e, w, a, t.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Bestand())
}

/**
 * Sicherung und Wiederherstellung.
 *
 * **Die Datei waehlt das System.** Beide Knoepfe oeffnen den Dateiwaehler von
 * Android. Die App bekommt danach eine Adresse, die auf genau diese eine Datei
 * zeigt, und braucht keine Berechtigung auf den Speicher.
 *
 * **Der Typ ist `application/octet-stream` und nicht `application/zip`.** Der
 * Dateiwaehler haengt eine Endung an, die zum angegebenen Typ passt, sobald die
 * vorgeschlagene nicht dazu passt. Bei `application/zip` bekaeme die Datei
 * `.notesbak.zip`; zu `application/octet-stream` gehoert keine Endung, also
 * bleibt der vorgeschlagene Name stehen.
 *
 * **Gelesen wird ungefiltert.** `.notesbak` ist keine bei Android bekannte
 * Endung. Ein Filter auf `application/zip` blendete die eigene Sicherung aus,
 * und der Nutzer staende vor einem leeren Dateiwaehler.
 *
 * **[lieseSicherung] kommt von aussen**, aus einem Dateimanager. Der Weg fuehrt
 * ueber diesen Bildschirm und nicht am ihm vorbei: Hier steht der Fortschritt,
 * und hier steht hinterher, was eingelesen wurde. Im Verborgenen einzulesen
 * waere schneller und niemand wuesste danach, was geschehen ist.
 */
@Composable
fun BackupScreen(
    innerPadding: PaddingValues,
    zeigeHinweis: (String) -> Unit,
    lieseSicherung: Uri? = null,
    onSicherungGelesen: () -> Unit = {},
    viewModel: DatenViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val bestand by viewModel.bestand.collectAsStateWithLifecycle()
    val lage by backupViewModel.lage.collectAsStateWithLifecycle()

    // Aus dem Dateimanager hereingereicht. Sofort quittiert, sonst liefe es bei
    // jeder Drehung des Geraets erneut.
    LaunchedEffect(lieseSicherung) {
        lieseSicherung?.let {
            backupViewModel.wiederherstellen(it)
            onSicherungGelesen()
        }
    }

    val zielWaehlen = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { ziel -> ziel?.let(backupViewModel::sichern) }

    val quelleWaehlen = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { quelle -> quelle?.let(backupViewModel::wiederherstellen) }

    lage.meldung?.let { text ->
        LaunchedEffect(text) {
            zeigeHinweis(text)
            backupViewModel.meldungGelesen()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Bestandskarte(bestand)

        if (lage.laeuft) {
            Laufanzeige(
                text = if (lage.vorgang == Vorgang.SICHERN) {
                    "Die Sicherung wird geschrieben."
                } else {
                    "Die Sicherung wird eingelesen."
                },
            )
        }

        Abschnitt("Sichern")
        Text(
            text = "Schreibt alles in eine einzelne Datei im Format .notesbak. Das " +
                "ist ein gewöhnliches ZIP-Archiv mit lesbarem JSON darin. Du kommst " +
                "also auch ohne diese App und ohne Google an deine Daten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mit dabei sind Notizen mit Titel, Text und Checklisten, Farben, " +
                "Stufe, Favoriten und der Papierkorb. Dazu Tags samt ihren Farben, " +
                "der erkannte Text von Aufnahmen, offene Erinnerungen und die " +
                "Dateien selbst, also Bilder und Aufnahmen. Auch Notizen, die du " +
                "vom Abgleich ausgenommen hast, sind dabei.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = { zielWaehlen.launch(backupViewModel.vorschlag()) },
                enabled = !lage.laeuft,
            ) {
                Icon(Icons.Outlined.Upload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Jetzt sichern")
            }
        }

        Abschnitt("Wiederherstellen")
        Text(
            text = "Liest eine Datei im Format .notesbak wieder ein. Dabei wird nie " +
                "etwas gelöscht. Was es hier schon gibt, wird an seiner Kennung " +
                "erkannt, und es gilt der neuere Stand. Was nur hier liegt, bleibt " +
                "liegen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Eine Notiz, die du hier gelöscht hattest, kommt als neue Notiz " +
                "im Eingang zurück. Das ist Absicht. Für den Abgleich ist sie damit " +
                "eine neue Notiz und gerät nicht mit der Löschung in Streit, die in " +
                "deiner Cloud vermerkt ist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Erinnerungen, deren Termin schon vorbei ist, werden nicht neu " +
                "gestellt. Sonst käme nach dem Einlesen einer älteren Sicherung ein " +
                "Schwall Meldungen für Vergangenes herein.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedButton(
                onClick = { quelleWaehlen.launch(arrayOf(ALLE_DATEIEN)) },
                enabled = !lage.laeuft,
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Datei auswählen")
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Was gerade laeuft.
 *
 * Der Satz zum Dableiben steht dort mit Absicht: Die Arbeit haengt am
 * Lebenslauf dieses Bildschirms, und bei einer Sicherung mit vielen Aufnahmen
 * dauert sie lange genug, dass jemand auf die Idee kommt, zurueckzugehen.
 */
@Composable
private fun Laufanzeige(text: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$text Bei vielen Bildern und Aufnahmen dauert das einen Moment. " +
                "Bleib so lange auf dieser Seite.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Der Abgleich mit Google Drive.
 *
 * Der Verbindungszustand wird bei jedem Oeffnen erfragt, nicht gemerkt. Die
 * Begruendung steht bei [SyncViewModel].
 *
 * Seit dem 2026-08-23 geht ALLES mit: Notizen, Checklisten, Transkripte, Tags,
 * Erinnerungen und die Dateien von Bildern und Aufnahmen. Was der Screen dazu
 * sagt, muss deshalb auch stimmen -- ein Abgleich, von dem man faelschlich
 * annimmt, er sichere die Fotos mit, waere schlimmer als gar keiner.
 */
@Composable
fun SyncScreen(
    innerPadding: PaddingValues,
    zeigeHinweis: (String) -> Unit,
    viewModel: DatenViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel(),
) {
    val bestand by viewModel.bestand.collectAsStateWithLifecycle()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val schritt by syncViewModel.schritt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Googles Dialog laeuft ueber eine PendingIntent. Die kann nur eine
    // Activity starten -- deshalb wird sie hier gezuendet und nicht im
    // ViewModel.
    val anmeldedialog = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { ergebnis -> syncViewModel.ausDialog(ergebnis.data) }

    sync.meldung?.let { text ->
        LaunchedEffect(text) {
            zeigeHinweis(text)
            syncViewModel.meldungGelesen()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(18.dp),
            ) {
                Icon(
                    imageVector = if (sync.verbunden) {
                        Icons.Outlined.CloudDone
                    } else {
                        Icons.Outlined.CloudOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(
                        text = when {
                            sync.prueft -> "Wird geprüft …"
                            sync.verbunden -> "Mit Google Drive verbunden"
                            else -> "Nicht verbunden"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (sync.verbunden) {
                            "${bestand.notizen} Notizen · " + zuletztText(sync.zuletzt)
                        } else {
                            "${bestand.notizen} Notizen liegen nur auf diesem Gerät"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (sync.verbunden) {
                Button(
                    onClick = syncViewModel::jetztAbgleichen,
                    enabled = !sync.laeuft,
                ) { Text("Jetzt abgleichen") }

                OutlinedButton(
                    onClick = syncViewModel::jetztSichern,
                    enabled = !sync.laeuft,
                ) { Text("Jetzt sichern") }

                TextButton(
                    onClick = syncViewModel::trennen,
                    enabled = !sync.laeuft,
                ) { Text("Trennen") }
            } else {
                Button(
                    onClick = {
                        syncViewModel.verbinden { absicht ->
                            absicht?.let {
                                anmeldedialog.launch(IntentSenderRequest.Builder(it).build())
                            }
                        }
                    },
                    enabled = !sync.laeuft && !sync.prueft,
                ) { Text("Google-Konto verknüpfen") }
            }
        }

        schritt?.let { Abgleichanzeige(it) }

        sync.ordnerId?.let { id ->
            Text(
                text = "Kennung des Ordners: $id",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        sync.bericht?.let { Pruefberichtkarte(it) }

        Abschnitt("Wie synchronisiert wird")
        Zeile(Icons.Outlined.Folder, "Spiegel in Drive", "/InNoteBox/, eine Datei je Notiz, Ordner und Tag")
        Zeile(Icons.Outlined.Folder, "Sicherungen in Drive", "/InNoteBox-Backup/, ein Stand je Tag")

        Automatikschalter(
            an = sync.automatisch,
            onAendern = syncViewModel::setAutomatisch,
        )
        Text(
            text = if (sync.automatisch) {
                "Es gibt keinen Takt mehr. Abgeglichen wird kurz nach jeder Änderung, " +
                    "beim Öffnen und Verlassen der App und sobald das Netz zurück ist. " +
                    "Einmal am Tag, beim ersten Öffnen, prüft die App den ganzen " +
                    "Bestand und schreibt eine Sicherung in den Backup-Ordner."
            } else {
                "Es geht nichts von selbst zu Google. Auch nach dem Speichern nicht."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        WlanSchalter(
            an = sync.nurWlan,
            onAendern = syncViewModel::setNurWlan,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Abgelegt wird in einem ganz gewöhnlichen Ordner, nicht im " +
                "versteckten App-Speicher. So kommst du im Notfall auch ohne diese " +
                "App an deine Notizen, und die spätere Fassung für den Rechner liest " +
                "dieselben Dateien. Eine Datei je Notiz, unverschlüsselt und lesbar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Abschnitt("Was abgeglichen wird")
        Text(
            text = "Notizen mit Titel, Text und Checklisten. Farben, Stufe, Favoriten " +
                "und der Papierkorb. Tags samt ihren Farben. Erinnerungen, wobei jedes " +
                "Gerät für sich weckt. Der erkannte Text von Aufnahmen. Und die " +
                "Dateien selbst: Bilder und Aufnahmen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nur auf diesem Gerät bleiben drei Dinge. Wann du eine Notiz " +
                "zuletzt geöffnet hast, denn das steuert die automatische " +
                "Archivierung und ist auf jedem Gerät anders. Welche Erinnerung " +
                "hier schon geklingelt hat, denn jedes Gerät weckt für sich. Und " +
                "die Einstellungen der App selbst, also Farbschema, Wischgesten und " +
                "die Fristen der Archivierung.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Einzelne Notizen kannst du ausnehmen. Im Menü einer Notiz, hinter " +
                "den drei Punkten, steht „Nicht mehr synchronisieren\". Die Notiz " +
                "bleibt dann hier, und beim nächsten Abgleich verschwindet sie samt " +
                "ihren Bildern aus Google Drive. Auf einem anderen Gerät, das sie " +
                "schon hat, bleibt sie liegen: ausnehmen heißt nicht überall löschen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Abschnitt("Erlaubnis bei Google")
        Text(
            text = "„Trennen\" schaltet den Abgleich hier ab. Die Erlaubnis, die du " +
                "Google einmal erteilt hast, wird dabei zwar mit widerrufen, aber das " +
                "kann fehlschlagen. Nachsehen und aufräumen kannst du sie in deinem " +
                "Google-Konto unter „Verbindungen zu Apps von Drittanbietern\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedButton(
                onClick = {
                    // NICHT ueber `resolveActivity` pruefen. Seit Android 11
                    // sieht eine App fremde Activities nur, wenn sie sie im
                    // Manifest anmeldet -- die Pruefung sagt sonst immer "kein
                    // Browser da", auch wenn einer eingerichtet ist. Genau das
                    // ist am Geraet passiert. Der ehrliche Weg ist, es zu
                    // versuchen und den Fehlschlag abzufangen.
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, KONTO_SEITE.toUri()))
                    } catch (fehler: ActivityNotFoundException) {
                        zeigeHinweis("Auf diesem Gerät ist kein Browser eingerichtet.")
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Im Google-Konto verwalten")
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * Woran der Abgleich gerade arbeitet.
 *
 * **Ein Balken mit Anteil, wo es einen gibt, und ein unbestimmter, wo nicht.**
 * Beim Holen und Schicken ist bekannt, wie viele Notizen es sind; beim
 * Vorbereiten und Aufraeumen nicht. Einen Anteil zu erfinden, damit der Balken
 * huebscher aussieht, waere eine Auskunft, die niemand geprueft hat.
 */
@Composable
private fun Abgleichanzeige(schritt: Abgleichschritt) {
    val text = when (schritt) {
        Abgleichschritt.Vorbereiten -> "Der Abgleich wird vorbereitet."
        Abgleichschritt.Ordnung -> "Tags und Ordner werden abgeglichen."
        is Abgleichschritt.Notizen ->
            "Notizen werden abgeglichen. ${schritt.fertig} von ${schritt.gesamt}."
        Abgleichschritt.Aufraeumen -> "Es wird aufgeräumt."
    }

    val anteil = when (schritt) {
        is Abgleichschritt.Notizen -> anteilVon(schritt.fertig, schritt.gesamt)
        else -> null
    }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (anteil == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { anteil },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ohne Nenner gibt es keinen Anteil, und geteilt wird durch null gar nicht. */
private fun anteilVon(fertig: Int, gesamt: Int): Float? =
    if (gesamt <= 0) null else (fertig.toFloat() / gesamt).coerceIn(0f, 1f)

@Composable
private fun Bestandskarte(bestand: Bestand) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "${bestand.notizen} Notizen, ${bestand.tags} Tags",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Eingang ${bestand.eingang} · Workspace ${bestand.workspace} · " +
                    "Archiv ${bestand.archiv}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Abschnitt(titel: String) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = titel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Eine Zeile, die etwas anzeigt und sonst nichts.
 *
 * Ohne `clickable`: Vorher lag dort ein leerer Klickbereich. Er gab eine
 * Rueckmeldung und tat nichts, und das ist genau die Art Kleinigkeit, bei der
 * man anfaengt, der Oberflaeche nicht mehr zu glauben.
 */
@Composable
private fun Zeile(icon: ImageVector, titel: String, wert: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Column {
            Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Wann zuletzt abgeglichen wurde, in Worten.
 *
 * Eine Uhrzeit waere genauer und weniger nuetzlich. Interessant ist, ob es
 * gerade eben war oder gestern, nicht ob 14:03 oder 14:05.
 *
 * Die Einzahl ist ausgeschrieben. „vor 1 Minuten" liest sich wie ein Fehler,
 * und genau so einer war es auch.
 */
private fun zuletztText(millis: Long): String {
    if (millis <= 0) return "noch nie abgeglichen"

    val minuten = (System.currentTimeMillis() - millis) / 60_000
    val wann = when {
        minuten < 1 -> return "gerade abgeglichen"
        minuten == 1L -> "vor einer Minute"
        minuten < 60 -> "vor $minuten Minuten"
        minuten < 120 -> "vor einer Stunde"
        minuten < 1440 -> "vor ${minuten / 60} Stunden"
        minuten < 2880 -> "gestern"
        else -> "vor ${minuten / 1440} Tagen"
    }
    return "$wann abgeglichen"
}

/**
 * Ob der Abgleich auf WLAN warten soll.
 *
 * Steht hier, seit auch Dateien mitgehen: Eine Textnotiz sind ein paar Kilobyte,
 * eine zehnminütige Aufnahme rund zwanzig Megabyte.
 */
@Composable
private fun WlanSchalter(an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAendern(!an) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Nur im WLAN",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (an) {
                    "Der Abgleich wartet auf ein WLAN"
                } else {
                    "Auch mobil. Aufnahmen können dabei einiges an Datenvolumen kosten"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = an, onCheckedChange = onAendern)
    }
}

/** Ob der Abgleich von selbst laeuft. Ohne Takt: Ereignisse statt Zeitplan (SYNC.md 5). */
@Composable
private fun Automatikschalter(an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAendern(!an) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Abgleich von selbst",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (an) "Nach jeder Änderung und beim Öffnen der App" else "Nur von Hand",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = an, onCheckedChange = onAendern)
    }
}

/**
 * Der letzte Pruefbericht (SYNC.md 9), lesbar.
 *
 * Ohne Befund ein Satz; mit Befund die Punkte, damit man weiss, warum es
 * keine Sicherung gab.
 */
@Composable
private fun Pruefberichtkarte(bericht: Pruefbericht) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = if (bericht.ohneBefund) "Letzte Prüfung ohne Befund" else "Letzte Prüfung mit Befund",
            style = MaterialTheme.typography.titleSmall,
            color = if (bericht.ohneBefund) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
        Text(
            text = "${bericht.notizenLokal} Notizen hier, ${bericht.notizenDrueben} Dateien in Drive. " +
                "${bericht.ordnerLokal} Ordner, ${bericht.tagsLokal} Tags." +
                if (bericht.konfliktkopien > 0) " ${bericht.konfliktkopien} Konfliktkopien angelegt." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bericht.fehlendeDateien > 0) {
            Text(
                text = "${bericht.fehlendeDateien} Dateien fehlten in Drive und wurden neu hochgeladen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bericht.unlesbar.isNotEmpty()) {
            Text(
                text = "Nicht lesbar in Drive: " + bericht.unlesbar.joinToString(", ") + ". " +
                    "Diese Dateien wurden übergangen, es wurde nichts daraus gelöscht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (bericht.verweiseInsLeere > 0) {
            Text(
                text = "${bericht.verweiseInsLeere} Notizen zeigen auf einen Ordner, den es nicht gibt. " +
                    "Sie liegen im Hauptordner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
