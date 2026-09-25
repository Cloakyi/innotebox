package de.notizen.app.ui.start

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Eine Berechtigung, wie der Dialog sie fuehrt.
 *
 * Fuenf Stueck, und sie sind bewusst nicht alle gleich: Drei sind gewoehnliche
 * Laufzeitberechtigungen (Systemdialog), zwei fuehren in eine Systemeinstellung
 * (exakte Alarme, Energiesparen), weil Android dafuer keinen Dialog anbietet.
 */
enum class Berechtigung(val titel: String, val wozu: String) {
    BENACHRICHTIGUNGEN(
        "Benachrichtigungen",
        "Für Erinnerungen und die Meldung des nächtlichen Aufräumens.",
    ),
    MIKROFON(
        "Mikrofon",
        "Für Sprachnotizen. Die Aufnahme bleibt auf dem Gerät.",
    ),
    KALENDER(
        "Kalender",
        "Damit Erinnerungen auch im Kalender des Geräts stehen.",
    ),
    EXAKTE_ALARME(
        "Erinnerungen auf die Minute",
        "Ohne diese Einstellung darf Android eine Erinnerung um bis zu eine Stunde verschieben.",
    ),
    ENERGIESPAREN(
        "Ohne Energiesparen",
        "Damit Abgleich und Sicherung im Hintergrund durchlaufen und nicht vom Energiesparen angehalten werden.",
    ),
}

/** Ob eine Berechtigung gerade erteilt ist. Wird bei jedem Wiederkehren neu gefragt. */
fun erteilt(context: Context, was: Berechtigung): Boolean = when (was) {
    Berechtigung.BENACHRICHTIGUNGEN -> gewaehrt(context, Manifest.permission.POST_NOTIFICATIONS)
    Berechtigung.MIKROFON -> gewaehrt(context, Manifest.permission.RECORD_AUDIO)
    Berechtigung.KALENDER ->
        gewaehrt(context, Manifest.permission.READ_CALENDAR) &&
            gewaehrt(context, Manifest.permission.WRITE_CALENDAR)
    Berechtigung.EXAKTE_ALARME ->
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    Berechtigung.ENERGIESPAREN ->
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
}

private fun gewaehrt(context: Context, erlaubnis: String): Boolean =
    ContextCompat.checkSelfPermission(context, erlaubnis) == PackageManager.PERMISSION_GRANTED

/** Was beim Start noch fehlt. */
fun fehlendeBerechtigungen(context: Context): List<Berechtigung> =
    Berechtigung.entries.filterNot { erteilt(context, it) }

/**
 * Der Dialog beim Start, der alle Nachfragen auf einmal stellt.
 *
 * **Beim Start und nicht erst an der Stelle, an der es gebraucht wird.** Vom
 * Nutzer am 2026-09-16 so gewuenscht: Die Systemabfrage erschien sonst mitten
 * in einer Handlung (ueber dem Zeitwaehler, beim ersten Antippen der Aufnahme),
 * und eine Ablehnung im falschen Moment liess sich nur noch ueber die
 * Systemeinstellungen zurueckholen. Hier ist jede Frage eine unter anderen, mit
 * einem Satz dazu, wozu sie gut ist.
 *
 * Erscheint, solange etwas fehlt. „Später" schliesst ihn fuer diesen Lauf der
 * App; beim naechsten Start steht er wieder da, wenn noch etwas fehlt. Kein
 * „nie wieder fragen": Wer eine Berechtigung nicht geben will, sieht eine
 * kurze Liste beim Start, und das ist der Preis dafuer, nichts zu uebersehen.
 *
 * Die Zustaende werden bei jedem Wiederkehren in die App neu gelesen: Aus der
 * Systemeinstellung kommt keine Rueckmeldung, nur die Rueckkehr.
 */
@Composable
fun Berechtigungsdialog(): Boolean {
    val context = LocalContext.current
    val lebenslauf = LocalLifecycleOwner.current

    // Ein Zaehler, der bei jeder Rueckkehr steigt und damit alles neu lesen laesst.
    var stand by remember { mutableIntStateOf(0) }
    DisposableEffect(lebenslauf) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            if (ereignis == Lifecycle.Event.ON_RESUME) stand++
        }
        lebenslauf.lifecycle.addObserver(beobachter)
        onDispose { lebenslauf.lifecycle.removeObserver(beobachter) }
    }

    val fehlend = remember(stand) { fehlendeBerechtigungen(context) }
    var offen by rememberSaveable { mutableStateOf(true) }
    if (!offen || fehlend.isEmpty()) return false

    val einzeln = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { stand++ }
    val mehrere = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { stand++ }

    AlertDialog(
        onDismissRequest = { offen = false },
        title = { Text("Was die App braucht") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Jede Berechtigung hat einen Zweck, und keine ist Pflicht. Was du " +
                        "nicht erlaubst, fällt nur an dieser einen Stelle aus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Berechtigung.entries.forEach { was ->
                    val da = was !in fehlend
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(was.titel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = was.wozu,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (da) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Erteilt",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(22.dp),
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    when (was) {
                                        Berechtigung.BENACHRICHTIGUNGEN ->
                                            einzeln.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        Berechtigung.MIKROFON -> einzeln.launch(Manifest.permission.RECORD_AUDIO)
                                        Berechtigung.KALENDER -> mehrere.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR,
                                            ),
                                        )
                                        // Systemeinstellungen: versuchen und den Fehlschlag
                                        // abfangen, nicht vorab pruefen (docs/ENTSCHEIDUNGEN.md, resolveActivity).
                                        Berechtigung.EXAKTE_ALARME -> runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                    .setData(Uri.fromParts("package", context.packageName, null)),
                                            )
                                        }
                                        Berechtigung.ENERGIESPAREN -> runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                    .setData(Uri.fromParts("package", context.packageName, null)),
                                            )
                                        }
                                    }
                                },
                            ) { Text("Erlauben") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { offen = false }) { Text("Später") }
        },
    )
    return true
}
