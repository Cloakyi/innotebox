package de.notizen.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.notizen.app.ai.Aufbereitung
import de.notizen.app.audio.AufnahmeStatus
import de.notizen.app.ui.components.LiveWellenform
import de.notizen.app.audio.Erkennungsmodus
import de.notizen.app.audio.Modellzustand
import de.notizen.app.audio.TranskriptStatus

/**
 * Der Aufnahmebereich im Editor.
 *
 * Zwei Schritte, bewusst getrennt: erst aufnehmen, dann transkribieren.
 * Aufnehmen kann immer, auch ohne Sprachmodell und ohne Netz, es ist der
 * Schritt, der sich nicht wiederholen lässt. Das Transkript ist ein zweiter,
 * beliebig oft wiederholbarer Vorgang; misslingt er, ist nichts verloren.
 */
@Composable
fun AufnahmeBereich(
    aufnahme: AufnahmeStatus,
    transkript: TranskriptStatus,
    hatAufnahme: Boolean,
    modell: Modellzustand,
    kiVerfuegbar: Boolean,
    /** Ob die Umwandlung in Text ueberhaupt angeboten wird. */
    transkriptErlaubt: Boolean,
    laeuftAufbereitung: Boolean,
    farbe: Color,
    onStarten: () -> Unit,
    onStoppen: () -> Unit,
    onTranskript: () -> Unit,
    onLaden: () -> Unit,
    onAufbereiten: (Aufbereitung) -> Unit,
    onTitel: (() -> Unit)? = null,
    /** Auf der Transkriptseite wird nicht noch einmal zum Aufnehmen eingeladen. */
    nurTranskript: Boolean = false,
    /** „Einstellungen oeffnen" am ausgegrauten Transkript. */
    onEinstellungen: () -> Unit = {},
) {
    Surface(
        color = farbe.copy(alpha = 0.10f),
        contentColor = farbe,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                aufnahme.laeuft -> Laufend(aufnahme, farbe, onStoppen)
                transkript.laeuft -> Transkribiert(transkript, farbe)
                else -> Ruhend(
                    nurTranskript = nurTranskript,
                    onTitel = onTitel,
                    farbe = farbe,
                    aufnahme = aufnahme,
                    transkript = transkript,
                    hatAufnahme = hatAufnahme,
                    modell = modell,
                    kiVerfuegbar = kiVerfuegbar,
                    transkriptErlaubt = transkriptErlaubt,
                    laeuftAufbereitung = laeuftAufbereitung,
                    onStarten = onStarten,
                    onTranskript = onTranskript,
                    onLaden = onLaden,
                    onAufbereiten = onAufbereiten,
                    onEinstellungen = onEinstellungen,
                )
            }
        }
    }
}

@Composable
private fun Laufend(status: AufnahmeStatus, farbe: Color, onStoppen: () -> Unit) {
    // Die mitlaufende Welle statt eines einzelnen Ausschlags: Man sieht nicht
    // nur, dass gerade etwas ankommt, sondern auch, was in den letzten Sekunden
    // war -- ob man zu leise wurde, wo eine Pause lag.
    LiveWellenform(
        werte = status.verlauf,
        gesamt = status.verlaufGesamt,
        farbe = farbe,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Text(
            text = dauer(status.dauerMs),
            style = MaterialTheme.typography.headlineSmall,
            color = farbe,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onStoppen,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, Modifier.size(18.dp))
            Text("Fertig", modifier = Modifier.padding(start = 6.dp))
        }
    }

    if (status.dauerMs > STILLE_NACH_MS && !status.hatTon) {
        Hinweis(
            "Es kommt kein Ton an. Ist das Mikrofon abgedeckt oder von einer anderen " +
                "App belegt?",
            MaterialTheme.colorScheme.error,
        )
    }
    status.fehler?.let { Hinweis(it, MaterialTheme.colorScheme.error) }
}

@Composable
private fun Transkribiert(status: TranskriptStatus, farbe: Color) {
    Text("Transkript wird erstellt", style = MaterialTheme.typography.titleSmall, color = farbe)

    // Ein echter Balken, kein unbestimmter Kringel: die Zahl der Abschnitte
    // steht nach dem Zerlegen fest, also ist der Anteil bekannt und darf auch
    // gezeigt werden.
    if (status.abschnitte > 0) {
        LinearProgressIndicator(
            progress = { status.anteil },
            // Auch der Fortschritt sitzt auf der Notiz und traegt ihre Farbe.
            color = farbe,
            trackColor = farbe.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Hinweis("Abschnitt ${status.fertig} von ${status.abschnitte}", farbe)
    } else {
        Hinweis("Sucht die Sprechpausen …", farbe)
    }

    if (status.teile.isNotEmpty()) {
        Text(
            text = status.text,
            style = MaterialTheme.typography.bodyMedium,
            color = farbe.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun Ruhend(
    nurTranskript: Boolean,
    onTitel: (() -> Unit)?,
    farbe: Color,
    aufnahme: AufnahmeStatus,
    transkript: TranskriptStatus,
    hatAufnahme: Boolean,
    modell: Modellzustand,
    kiVerfuegbar: Boolean,
    transkriptErlaubt: Boolean,
    laeuftAufbereitung: Boolean,
    onStarten: () -> Unit,
    onTranskript: () -> Unit,
    onLaden: () -> Unit,
    onAufbereiten: (Aufbereitung) -> Unit,
    /** „Einstellungen oeffnen" am ausgegrauten Transkript. */
    onEinstellungen: () -> Unit = {},
) {
    if (!nurTranskript) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStarten) {
                Icon(Icons.Outlined.Mic, contentDescription = null, Modifier.size(18.dp))
                Text(
                    text = if (hatAufnahme) "Neu aufnehmen" else "Aufnehmen",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (aufnahme.dauerMs > 0) {
                Text(
                    text = dauer(aufnahme.dauerMs),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }

    if (hatAufnahme) {
        if (transkriptErlaubt) {
            TranskriptKnopf(modell, transkript, farbe, onTranskript, onLaden)
        } else {
            // AUSGEGRAUT UND NICHT WEG. Ein Knopf, der verschwindet, sieht aus
            // wie ein Fehler; einer, der blass dasteht und sagt warum, ist eine
            // Auskunft. Die Aufnahme darueber bleibt voll bedienbar. Und der
            // Weg zum Schalter ist gleich daneben.
            Gesperrt(
                titel = "Transkript erstellen",
                symbol = Icons.Outlined.Subtitles,
                grund = "Die KI ist in den Einstellungen abgeschaltet. " +
                    "Die Aufnahme bleibt erhalten und lässt sich abspielen.",
                farbe = farbe,
                onEinstellungen = onEinstellungen,
            )
        }
    }

    transkript.fehler?.let {
        Hinweis(it, MaterialTheme.colorScheme.error)
        // Der Fehlertext der Erkennung bleibt im Original stehen -- er hat beim
        // Suchen schon einmal genau gesagt, was los war. Dazu gehoert aber der
        // Satz, der die Aufregung wegnimmt: die Aufnahme ist nicht betroffen.
        if (hatAufnahme) {
            Hinweis(
                "Die Aufnahme ist davon nicht betroffen und liegt weiter auf dem Gerät.",
                farbe,
            )
        }
    }

    // Der Modus gehoert sichtbar gemacht: die Erkennungsqualitaet unterscheidet
    // sich hoerbar, und wer den Rueckfall nicht kennt, haelt das Ergebnis fuer
    // einen Fehler der App.
    // Mit einem Satz dazu, was das Fachwort heisst. Der Hinweis stimmte
    // vorher schon, sagte aber niemandem, was er bedeutet.
    transkript.modus?.let {
        Hinweis(
            text = when (it) {
                Erkennungsmodus.ADVANCED ->
                    "Erkannt mit der erweiterten Erkennung. Das ist das genauere " +
                        "Sprachmodell auf dem Gerät; es versteht Namen und Satzzeichen besser."
                Erkennungsmodus.BASIC ->
                    "Erkannt mit der einfachen Erkennung. Die erweiterte, die Namen und " +
                        "Satzzeichen besser versteht, gibt es auf diesem Gerät nicht."
            },
            farbe = farbe,
        )
    }

    if (transkript.teile.isNotEmpty()) {
        when {
            kiVerfuegbar -> AufbereitenKnopf(laeuftAufbereitung, farbe, onAufbereiten)
            // KI abgeschaltet: ausgegraut wie das Transkript, nicht weg.
            // Kann das Geraet gar nicht, steht hier nichts (docs/ENTSCHEIDUNGEN.md, AICore).
            !transkriptErlaubt -> Gesperrt(
                titel = "Text aufbereiten",
                symbol = Icons.Outlined.AutoAwesome,
                grund = "Die KI ist in den Einstellungen abgeschaltet.",
                farbe = farbe,
                onEinstellungen = onEinstellungen,
            )
        }
    }

    // Erst wenn es ein Transkript gibt, gibt es auch etwas, woraus sich ein
    // Titel ableiten laesst. Vorher waere der Knopf eine leere Zusage.
    if (transkript.teile.isNotEmpty() && onTitel != null) {
        TextButton(onClick = onTitel) {
            Icon(
                Icons.Outlined.Title,
                contentDescription = null,
                tint = farbe,
                modifier = Modifier.size(18.dp),
            )
            Text("Titel vorschlagen", color = farbe, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * Der abgeschaltete Transkriptbereich.
 *
 * Dieselbe Form wie der Knopf daneben, nur ohne Wirkung und in der gedaempften
 * Farbe fuer Abgeschaltetes. Wer die Zeile darunter liest, weiss sofort, wo er
 * es wieder einschaltet.
 */
@Composable
private fun Gesperrt(
    titel: String,
    symbol: androidx.compose.ui.graphics.vector.ImageVector,
    grund: String,
    farbe: Color,
    onEinstellungen: () -> Unit,
) {
    val blass = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = symbol,
                contentDescription = null,
                tint = blass,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = titel,
                color = blass,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = grund,
            style = MaterialTheme.typography.bodySmall,
            color = blass,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Der eine Knopf hier ist nicht blass: Er tut etwas, naemlich zum
        // Schalter springen, und der blitzt dort kurz auf.
        TextButton(onClick = onEinstellungen, contentPadding = PaddingValues(0.dp)) {
            Text("Einstellungen öffnen", color = farbe)
        }
    }
}

@Composable
private fun TranskriptKnopf(
    modell: Modellzustand,
    transkript: TranskriptStatus,
    farbe: Color,
    onTranskript: () -> Unit,
    onLaden: () -> Unit,
) {
    when (modell) {
        is Modellzustand.Bereit -> TextButton(onClick = onTranskript) {
            Icon(
                Icons.Outlined.Subtitles,
                contentDescription = null,
                tint = farbe,
                modifier = Modifier.size(18.dp),
            )
            Text(
                color = farbe,
                text = if (transkript.teile.isEmpty()) {
                    "Transkript erstellen"
                } else {
                    "Transkript neu erstellen"
                },
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        is Modellzustand.Ladbar -> Column {
            Text(
                "Für das Transkript fehlt noch das Sprachmodell. " +
                    "Die Aufnahme ist gesichert.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onLaden) { Text("Modell laden", color = farbe) }
        }

        is Modellzustand.Laedt -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            CircularProgressIndicator(
                color = farbe,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Text(
                // Kein Prozentbalken: die API liefert nur die geladenen Bytes,
                // keine Gesamtgroesse.
                text = if (modell.bytes > 0) "Lädt … ${modell.bytes / 1_000_000} MB" else "Lädt …",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        is Modellzustand.NichtVerfuegbar -> Hinweis(
            "Auf diesem Gerät steht keine Spracherkennung für Deutsch bereit " +
                "(Code ${modell.code}). Die Aufnahme bleibt trotzdem erhalten.",
            farbe,
        )

        is Modellzustand.Fehler -> Hinweis(
            "Die Spracherkennung meldet: ${modell.ursache.message}",
            MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Die Nachbearbeitung des Transkripts.
 *
 * Erscheint erst, wenn es etwas aufzubereiten gibt, ein Knopf, den man auf
 * leeren Text loslässt, kann nur enttäuschen.
 */
@Composable
private fun AufbereitenKnopf(laeuft: Boolean, farbe: Color, onWahl: (Aufbereitung) -> Unit) {
    var offen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        TextButton(onClick = { offen = true }, enabled = !laeuft) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = farbe,
                modifier = Modifier.size(18.dp),
            )
            Text("Text aufbereiten", color = farbe, modifier = Modifier.padding(start = 6.dp))
        }
        if (laeuft) {
            CircularProgressIndicator(
                color = farbe,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        }

        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            Aufbereitung.entries.forEach { art ->
                DropdownMenuItem(
                    text = { Text(art.beschriftung) },
                    onClick = {
                        offen = false
                        onWahl(art)
                    },
                )
            }
        }
    }
}

@Composable
private fun Hinweis(text: String, farbe: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = farbe.copy(alpha = 0.85f),
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun dauer(ms: Long): String {
    val sekunden = ms / 1000
    return "%d:%02d".format(sekunden / 60, sekunden % 60)
}

/** Ab wann Stille als Fehler gemeldet wird statt als Sprechpause. */
private const val STILLE_NACH_MS = 4_000L
