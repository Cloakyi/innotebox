package de.notizen.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.notizen.app.audio.Abspielstatus
import de.notizen.app.audio.Sprechabschnitt
import de.notizen.app.audio.Stillesprung
import de.notizen.app.ui.components.Abspielleiste
import de.notizen.app.ui.components.dauerText

/** Welche der beiden Seiten einer Audionotiz gerade offen ist. */
enum class Audioseite(val beschriftung: String) {
    AUFNAHME("Aufnahme"),
    TRANSKRIPT("Transkript"),
}

/**
 * Der Umschalter zwischen Aufnahme und Transkript.
 *
 * Zwei Seiten statt einer langen. Eine Audionotiz hat zwei Gesichter: das
 * Gehörte und das Gelesene. Beides untereinander auf einen Bildschirm zu
 * stapeln hieße, dass man für das eine immer am anderen vorbeiscrollt,
 * und das Transkript ist bei einer längeren Aufnahme lang.
 */
@Composable
fun Seitenumschalter(
    seite: Audioseite,
    hatTranskript: Boolean,
    farbe: Color,
    onWechsel: (Audioseite) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        Audioseite.entries.forEachIndexed { i, wert ->
            SegmentedButton(
                selected = wert == seite,
                onClick = { onWechsel(wert) },
                shape = SegmentedButtonDefaults.itemShape(i, Audioseite.entries.size),
                // Der Umschalter sitzt AUF der Notiz und traegt deshalb ihre
                // Farbe -- nicht die des Themes.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = farbe.copy(alpha = 0.18f),
                    activeContentColor = farbe,
                    activeBorderColor = farbe.copy(alpha = 0.5f),
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = farbe.copy(alpha = 0.7f),
                    inactiveBorderColor = farbe.copy(alpha = 0.3f),
                ),
                icon = {
                    Icon(
                        imageVector = when (wert) {
                            Audioseite.AUFNAHME -> Icons.Outlined.Mic
                            Audioseite.TRANSKRIPT -> Icons.Outlined.Subtitles
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = {
                    Text(
                        // Sagt schon vor dem Umschalten, ob es drüben etwas zu
                        // sehen gibt.
                        text = if (wert == Audioseite.TRANSKRIPT && !hatTranskript) {
                            "Transkript …"
                        } else {
                            wert.beschriftung
                        },
                    )
                },
            )
        }
    }
}

/**
 * Die Aufnahmeseite, nur die Aufnahme.
 *
 * Kein Textfeld, kein Transkript. Wer hier ist, will hören; das Gelesene liegt
 * einen Umschalter weiter. Eine Seite, die beides zeigt, ist für beides die
 * schlechtere.
 */
@Composable
fun Aufnahmeseite(
    wellenform: FloatArray,
    abschnitte: List<Sprechabschnitt>,
    status: Abspielstatus,
    notizId: String,
    dauerMs: Long,
    stilleUeberspringen: Boolean,
    farbe: Color,
    onAbspielen: () -> Unit,
    onSpringen: (Float) -> Unit,
    onSpringenUm: (Int) -> Unit,
    onStilleUmschalten: () -> Unit,
    onNeuAufnehmen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eigenerStand = status.notizId == notizId
    val gesamt = if (eigenerStand && status.dauerMs > 0) status.dauerMs.toLong() else dauerMs
    val ersparnis = Stillesprung.ersparnisMs(abschnitte, gesamt)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Abspielleiste(
            balken = wellenform,
            abschnitte = abschnitte,
            positionMs = if (eigenerStand) status.positionMs.toLong() else 0L,
            dauerMs = gesamt,
            laeuft = status.laeuftFuer(notizId),
            farbe = farbe,
            onAbspielen = onAbspielen,
            onSpringen = onSpringen,
            onZurueck = { onSpringenUm(-5) },
            onVor = { onSpringenUm(10) },
        )

        // Nur anbieten, wenn es wirklich etwas zu sparen gibt. Bei einem
        // durchgehenden Diktat waere der Schalter ein leeres Versprechen.
        if (ersparnis > 2_000) {
            FilterChip(
                selected = stilleUeberspringen,
                onClick = onStilleUmschalten,
                label = { Text("Pausen überspringen  −${dauerText(ersparnis)}") },
                leadingIcon = if (stilleUeberspringen) {
                    {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = farbe,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = farbe.copy(alpha = 0.8f),
                    selectedLabelColor = farbe,
                    selectedContainerColor = farbe.copy(alpha = 0.18f),
                ),
                // Ohne das steht der Rand auf FilterChipTokens
                // .FlatUnselectedOutlineColor -- im dunklen Theme ein
                // blaustichiges Grau, das auf einer gruenen Notiz auffaellt wie
                // ein Fremdkoerper. Der Rand ist bei einem NICHT gewaehlten Chip
                // der sichtbarste Teil ueberhaupt.
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = stilleUeberspringen,
                    borderColor = farbe.copy(alpha = 0.4f),
                    selectedBorderColor = farbe.copy(alpha = 0.6f),
                ),
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        TextButton(onClick = onNeuAufnehmen, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                tint = farbe,
                modifier = Modifier.size(18.dp),
            )
            Text("Neu aufnehmen", color = farbe, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
