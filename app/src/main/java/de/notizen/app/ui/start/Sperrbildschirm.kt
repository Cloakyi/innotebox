package de.notizen.app.ui.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.notizen.app.R
import de.notizen.app.sicherheit.Entsperrung
import de.notizen.app.sicherheit.alsActivity

/**
 * Die Abdeckung, solange noch nicht feststeht, ob gesperrt wird.
 *
 * Nur das Logo auf der Grundfarbe, ohne Frage nach der Entsperrung. Sie liegt
 * für die paar Millisekunden über der App, die das Lesen der Einstellung
 * dauert, beim Start und nach jeder Rückkehr aus dem Hintergrund. Ein eigenes
 * Fenster aus demselben Grund wie beim Sperrbildschirm: Offene Dialoge der
 * App lägen sonst darüber.
 */
@Composable
fun Abdeckung() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_grafik),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(26.dp)),
                )
            }
        }
    }
}

/**
 * Der Sperrbildschirm: Logo, ein Satz, ein Knopf.
 *
 * Ein bildschirmfüllender Dialog, kein Overlay im Inhalt. Dialoge und
 * Blätter der App (Notizmenü, Farbwahl) sind eigene Fenster und lägen über
 * einem Overlay; ein Dialog, der nach ihnen entsteht, liegt über allen. Die
 * App darunter bleibt zusammengesetzt, damit nach dem Entsperren alles dort
 * steht, wo es war.
 *
 * Beim Erscheinen fragt er von selbst nach der Entsperrung. Wer den Dialog
 * wegklickt, bekommt den Knopf, und dabei bleibt es auch nach einem Drehen des
 * Geräts: Die Activity entsteht dabei neu, und ohne den gemerkten Stand kam
 * jedes Mal ungefragt der nächste Dialog. Den Satz des Systems zeigt er nur bei
 * echten Fehlern, etwa nach zu vielen Versuchen. Zurück verlässt die App,
 * statt den Bildschirm zu schließen.
 */
@Composable
fun Sperrbildschirm(onEntsperrt: () -> Unit) {
    val context = LocalContext.current
    var hinweis by rememberSaveable { mutableStateOf<String?>(null) }
    // Ob der Dialog schon einmal weggeklickt wurde oder gescheitert ist. Dann
    // wartet der Bildschirm auf den Knopf, statt nach einem Drehen neu zu fragen.
    var abgewiesen by rememberSaveable { mutableStateOf(false) }

    val anfordern = {
        val activity = context.alsActivity()
        if (activity == null) {
            hinweis = "Die Entsperrung ließ sich nicht öffnen."
        } else {
            hinweis = null
            abgewiesen = false
            Entsperrung.anfordern(
                activity = activity,
                titel = "InNoteBox entsperren",
                untertitel = "Mit Fingerabdruck, Gesicht oder der Bildschirmsperre des Geräts",
                onErfolg = onEntsperrt,
                onAbgewiesen = { abgewiesen = true },
                onAbbruch = {
                    abgewiesen = true
                    hinweis = it
                },
            )
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Zurueck heisst hier: die App verlassen, nicht die Sperre umgehen.
        BackHandler { context.alsActivity()?.moveTaskToBack(true) }

        LaunchedEffect(Unit) { if (!abgewiesen) anfordern() }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(32.dp),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_grafik),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(26.dp)),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "InNoteBox ist gesperrt",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = hinweis ?: "Deine Notizen bleiben verborgen, bis du dich ausweist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(28.dp))
                Button(onClick = anfordern) {
                    Icon(Icons.Outlined.LockOpen, contentDescription = null, Modifier.size(18.dp))
                    Text("Entsperren", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
