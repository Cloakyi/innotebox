package de.notizen.app.kalender

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import de.notizen.app.ui.theme.NotizenTheme
import de.notizen.core.data.model.Terminart

/**
 * Der Wecker eines wichtigen Termins auf dem ganzen Bildschirm, auch ueber
 * dem Sperrbildschirm.
 *
 * Er zeigt nur, was ihm mitgegeben wurde, und liest keine Datenbank: Er soll
 * sofort dastehen, auch wenn die App gerade nicht laeuft. Der Ton haengt an
 * der Benachrichtigung; „Ausschalten“ nimmt sie weg und damit den Ton.
 */
class WeckerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        anzeigen(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        anzeigen(intent)
    }

    private fun anzeigen(intent: Intent) {
        val meldung = intent.getIntExtra(Terminwecker.EXTRA_MELDUNG, 0)
        val art = runCatching { Terminart.valueOf(intent.getStringExtra(ART).orEmpty()) }
            .getOrDefault(Terminart.TERMIN)
        setContent {
            NotizenTheme {
                Wecker(
                    titel = intent.getStringExtra(TITEL).orEmpty(),
                    zeit = intent.getStringExtra(ZEIT).orEmpty(),
                    ort = intent.getStringExtra(ORT).orEmpty(),
                    symbol = intent.getStringExtra(SYMBOL),
                    art = art,
                    farbe = Color(intent.getIntExtra(FARBE, FARBE_STANDARD)),
                    onAus = {
                        NotificationManagerCompat.from(this).cancel(meldung)
                        finish()
                    },
                    onSchlummern = {
                        sendBroadcast(
                            Intent(this, TerminEmpfaenger::class.java)
                                .setAction(Terminwecker.AKTION_SCHLUMMERN)
                                .putExtra(Terminwecker.EXTRA_MELDUNG, meldung)
                                .putExtra(Terminwecker.EXTRA_TERMIN, intent.getStringExtra(Terminwecker.EXTRA_TERMIN))
                                .putExtra(Terminwecker.EXTRA_SCHLUESSEL, intent.getLongExtra(Terminwecker.EXTRA_SCHLUESSEL, 0L)),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val TITEL = "titel"
        const val ZEIT = "zeit"
        const val ORT = "ort"
        const val SYMBOL = "symbol"
        const val ART = "art"
        const val FARBE = "farbe"
    }
}

@Composable
private fun Wecker(
    titel: String,
    zeit: String,
    ort: String,
    symbol: String?,
    art: Terminart,
    farbe: Color,
    onAus: () -> Unit,
    onSchlummern: () -> Unit,
) {
    val aufFarbe = if (farbe.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Wichtiger Termin",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(160.dp).clip(CircleShape).background(farbe),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Terminsymbole.zeichen(symbol, art),
                    contentDescription = null,
                    tint = aufFarbe,
                    modifier = Modifier.size(88.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = titel,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = zeit,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (ort.isNotBlank()) {
                Text(
                    text = ort,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onSchlummern,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Schlummern · ${Terminwecker.SCHLUMMERZEIT} Minuten") }
            Button(
                onClick = onAus,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) { Text("Ausschalten", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
