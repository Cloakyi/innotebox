package de.notizen.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.notizen.app.bild.rememberBild
import de.notizen.app.ui.theme.BILD_ABDUNKLUNG
import de.notizen.core.data.db.entity.AttachmentEntity
import java.io.File

/**
 * Wie viele Bilder der Systemwaehler auf einmal herausgibt.
 *
 * Eine Grenze muss der Waehler haben. Zehn ist grosszuegig fuer eine Notiz und
 * klein genug, dass das Verkleinern nicht minutenlang laeuft, waehrend die
 * Oberflaeche nichts davon erzaehlt.
 */
const val BILDER_HOECHSTENS = 10

/**
 * Das Hintergrundbild einer Notiz, formatfuellend und abgedunkelt.
 *
 * Der Abdunkler ist keine Stilfrage, sondern die Bedingung dafuer, dass weisse
 * Schrift auf einem beliebigen Foto die geforderten 4,5:1 haelt. Siehe
 * [BILD_ABDUNKLUNG] und HintergrundScrimTest.
 */
@Composable
fun Hintergrundbild(anhang: AttachmentEntity) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val bild = rememberBild(File(anhang.localPath), maxWidth)
        bild?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Auch OHNE geladenes Bild gezeichnet: Waehrend des Ladens stuende die
        // weisse Schrift sonst kurz auf der hellen Themeflaeche und waere
        // unlesbar. Ein Flackern in Weiss-auf-Weiss ist genau die Art Fehler,
        // die man auf dem eigenen Geraet nie sieht und auf einem langsameren
        // immer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BILD_ABDUNKLUNG)),
        )
    }
}

/**
 * Was sich an eine Notiz anhaengen laesst.
 *
 * Zeichnungen fehlen hier bewusst und sind nicht ausgegraut: Ein abgeblendeter
 * Eintrag verspricht, dass es die Sache gibt und man sie nur gerade nicht
 * bekommt. Es gibt sie nicht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnhangBlatt(
    kameraVorhanden: Boolean,
    onFoto: () -> Unit,
    onGalerie: () -> Unit,
    onSchliessen: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onSchliessen,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Hinzufügen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )

            if (kameraVorhanden) {
                Blatteintrag(Icons.Outlined.PhotoCamera, "Foto aufnehmen", onFoto)
            }
            Blatteintrag(Icons.Outlined.PhotoLibrary, "Bild hinzufügen", onGalerie)
        }
    }
}

@Composable
private fun Blatteintrag(
    symbol: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onKlick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onKlick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Der leere Zustand einer Bildnotiz.
 *
 * Nach demselben Muster wie die uebrigen leeren Zustaende (siehe
 * `LeererZustand`): fette Ueberschrift, kleinerer Erklaertext, alles mittig.
 * Anders als dort steht hier ein Knopf darunter, weil es genau eine sinnvolle
 * naechste Handlung gibt.
 */
@Composable
fun BildEinladung(farbe: Color, onHinzufuegen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(farbe.copy(alpha = 0.07f))
            .clickable(onClick = onHinzufuegen)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AddPhotoAlternate,
            contentDescription = null,
            tint = farbe.copy(alpha = 0.85f),
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "Noch kein Bild",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = farbe,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "Nimm ein Foto auf oder wähle eines aus der Galerie.",
            style = MaterialTheme.typography.bodySmall,
            color = farbe.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onHinzufuegen, modifier = Modifier.padding(top = 6.dp)) {
            Text("Bild hinzufügen", color = farbe)
        }
    }
}
