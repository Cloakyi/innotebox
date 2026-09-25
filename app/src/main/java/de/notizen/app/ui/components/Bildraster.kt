package de.notizen.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.notizen.app.bild.rememberBild
import de.notizen.core.data.db.entity.AttachmentEntity
import java.io.File

private val ECKE = 12.dp

/** Hoechster Anteil der Bildschirmhoehe, den ein einzelnes Bild einnehmen darf. */
private const val BILDANTEIL = 0.38f
private val LUECKE = 4.dp

/**
 * Die Bilder einer Notiz.
 *
 * Bewusst KEIN `LazyVerticalGrid`: Diese Ansicht steht in einer Spalte, die
 * bereits senkrecht scrollt, und zwei ineinandergeschachtelte Scroller
 * derselben Richtung sind in Compose ein Absturz, kein Schoenheitsfehler. Die
 * Bilderzahl einer Notiz ist ueberschaubar -- eine Spalte aus Zeilen genuegt
 * und kostet nichts.
 *
 * Ein einzelnes Bild bekommt die volle Breite und darf sein eigenes
 * Seitenverhaeltnis behalten. Ab zwei wird es ein Quadratraster: gemischte
 * Hoehen nebeneinander sehen aus wie ein Fehler, auch wenn sie keiner sind.
 */
@Composable
fun Bildraster(
    bilder: List<AttachmentEntity>,
    hintergrundId: String?,
    onBild: (AttachmentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bilder.isEmpty()) return

    // Ein Hochkantfoto in voller Hoehe schiebt das Textfeld aus dem Bild --
    // und mit offener Tastatur ist es dann gar nicht mehr erreichbar. Deshalb
    // bekommt ein Bild hoechstens diesen Anteil des Bildschirms; der Rest wird
    // beschnitten. Wer das ganze Bild sehen will, tippt es an.
    val hoechstHoehe = LocalConfiguration.current.screenHeightDp.dp * BILDANTEIL

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LUECKE),
    ) {
        if (bilder.size == 1) {
            Bildkachel(
                anhang = bilder.single(),
                istHintergrund = bilder.single().id == hintergrundId,
                hoechstHoehe = hoechstHoehe,
                onKlick = { onBild(bilder.single()) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            bilder.chunked(2).forEach { reihe ->
                Row(horizontalArrangement = Arrangement.spacedBy(LUECKE)) {
                    reihe.forEach { anhang ->
                        Bildkachel(
                            anhang = anhang,
                            istHintergrund = anhang.id == hintergrundId,
                            // Im Raster quadratisch: gemischte Hoehen
                            // nebeneinander sehen aus wie ein Fehler.
                            hoechstHoehe = null,
                            onKlick = { onBild(anhang) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Haelt die letzte, unvollstaendige Zeile in der Spur.
                    // Ohne das zoege ein einzelnes Bild sich auf die volle
                    // Breite und spraenge aus dem Raster.
                    repeat(2 - reihe.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Bildkachel(
    anhang: AttachmentEntity,
    istHintergrund: Boolean,
    hoechstHoehe: Dp?,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val bild = rememberBild(File(anhang.localPath), maxWidth)

        // Vor dem Laden steht noch kein Seitenverhaeltnis fest. 4:3 als
        // Annahme haelt die Hoehe stabil, damit der Text darunter nicht
        // springt, sobald das Bild eintrifft.
        val format = when {
            hoechstHoehe == null -> 1f
            bild != null -> {
                // Die Deckelung laeuft ueber das Seitenverhaeltnis, nicht ueber
                // `heightIn`: `aspectRatio` und eine Hoechsthoehe zusammen
                // widersprechen sich, und Compose loest den Widerspruch nicht
                // in die Richtung auf, die man erwartet.
                val natuerlich = bild.width.toFloat() / bild.height
                natuerlich.coerceAtLeast(maxWidth / hoechstHoehe).coerceAtMost(1.9f)
            }
            else -> 4f / 3f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(format)
                .clip(RoundedCornerShape(ECKE))
                .background(Color.Black.copy(alpha = 0.12f))
                .clickable(onClick = onKlick),
        ) {
            bild?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Bild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (istHintergrund) {
                // Sagt, welches der Bilder gerade die Flaeche der Notiz ist.
                // Ohne die Marke muesste man raten, sobald mehr als eines da
                // ist -- der Hintergrund selbst ist ja abgedunkelt und
                // beschnitten kaum wiederzuerkennen.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(5.dp),
                ) {
                    Icon(
                        Icons.Filled.Wallpaper,
                        contentDescription = "Wird als Hintergrund verwendet",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Vorschaubild fuer die Notizkarte.
 *
 * Misst seine Breite selbst, statt sie sich sagen zu lassen: Karten stehen in
 * einem gestaffelten Raster, dessen Spaltenbreite von der Bildschirmbreite
 * abhaengt. Ein von aussen geratener Wert waere auf dem einen Geraet zu klein
 * (unscharf) und auf dem anderen zu gross (verschwendeter Speicher).
 */
@Composable
fun Bildvorschau(anhang: AttachmentEntity, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bild = rememberBild(File(anhang.localPath), maxWidth)
        val format = bild
            ?.let { (it.width.toFloat() / it.height).coerceIn(0.75f, 1.9f) }
            ?: (4f / 3f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(format)
                .clip(RoundedCornerShape(ECKE))
                .background(Color.Black.copy(alpha = 0.12f)),
        ) {
            bild?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
