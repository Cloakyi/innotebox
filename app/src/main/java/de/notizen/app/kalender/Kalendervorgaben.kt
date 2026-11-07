package de.notizen.app.kalender

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.notizen.core.data.repository.KalenderRepository

/**
 * Was ein Kalender fuer neue Termine vorgibt. Leer heisst: wie in den
 * allgemeinen Einstellungen. Die Vorgaben bleiben auf dem Geraet.
 */
data class Kalendervorgabe(
    /** Minuten vor dem Beginn, [KEINE_ERINNERUNG] fuer keine, null fuer allgemein. */
    val erinnerung: Int? = null,
    /** Dauer in Minuten, null fuer allgemein. */
    val dauer: Int? = null,
    /** Ob ein neuer Termin ohne Uhrzeit ganztaegig beginnt. */
    val ganztaegig: Boolean = false,
) {
    fun kodiert(): String = "e=${erinnerung ?: ""};d=${dauer ?: ""};g=${if (ganztaegig) 1 else 0}"

    /** Die Erinnerungen, die ein neuer Termin dieses Kalenders bekommt. */
    fun erinnerungen(ganz: Boolean, allgemein: Int): List<Int> = when {
        ganz -> listOf(-540)
        erinnerung == KEINE_ERINNERUNG -> emptyList()
        else -> listOf(erinnerung ?: allgemein)
    }

    companion object {
        const val KEINE_ERINNERUNG = Int.MIN_VALUE

        fun lesen(text: String?): Kalendervorgabe {
            if (text.isNullOrBlank()) return Kalendervorgabe()
            val teile = text.split(';').mapNotNull { t ->
                val i = t.indexOf('=')
                if (i < 0) null else t.substring(0, i) to t.substring(i + 1)
            }.toMap()
            return Kalendervorgabe(
                erinnerung = teile["e"]?.toIntOrNull(),
                dauer = teile["d"]?.toIntOrNull()?.takeIf { it > 0 },
                ganztaegig = teile["g"] == "1",
            )
        }
    }
}

/** Ob ein Kalender eigene Vorgaben haben kann. Aufgaben und Geburtstage haben feste Regeln. */
fun hatVorgaben(kalenderId: String): Boolean =
    kalenderId != KalenderRepository.AUFGABEN && kalenderId != KalenderRepository.GEBURTSTAGE

/** Die Dauern, die man fuer neue Termine waehlen kann, in Minuten. */
val DAUERN = listOf(15, 30, 45, 60, 90, 120)

fun dauertext(minuten: Int): String = when {
    minuten < 60 -> "$minuten Minuten"
    minuten == 60 -> "1 Stunde"
    minuten % 60 == 0 -> "${minuten / 60} Stunden"
    else -> "${minuten / 60},${minuten % 60 * 10 / 60} Stunden"
}

private fun vorgabeErinnerungstext(e: Int?): String = when (e) {
    null -> "Wie allgemein"
    Kalendervorgabe.KEINE_ERINNERUNG -> "Keine Benachrichtigung"
    else -> erinnerungstext(e, false)
}

/** Die Vorgaben eines Kalenders im Dialog unter Name und Farbe. */
@Composable
fun VorgabenTeil(vorgabe: Kalendervorgabe, onAendern: (Kalendervorgabe) -> Unit) {
    Column {
        Text(
            "Für neue Termine in diesem Kalender",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Wahlzeile(
            titel = "Benachrichtigung",
            wert = vorgabeErinnerungstext(vorgabe.erinnerung),
            eintraege = listOf<Int?>(null, Kalendervorgabe.KEINE_ERINNERUNG) + erinnerungsvorschlaege(false),
            text = ::vorgabeErinnerungstext,
        ) { onAendern(vorgabe.copy(erinnerung = it)) }
        Wahlzeile(
            titel = "Dauer",
            wert = vorgabe.dauer?.let(::dauertext) ?: "Wie allgemein",
            eintraege = listOf<Int?>(null) + DAUERN,
            text = { it?.let(::dauertext) ?: "Wie allgemein" },
        ) { onAendern(vorgabe.copy(dauer = it)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAendern(vorgabe.copy(ganztaegig = !vorgabe.ganztaegig)) }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ganztägig", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Gilt für Termine über das Plus. Ein Tipp ins Raster legt weiter einen Termin mit Uhrzeit an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = vorgabe.ganztaegig, onCheckedChange = { onAendern(vorgabe.copy(ganztaegig = it)) })
        }
    }
}

@Composable
private fun <T> Wahlzeile(titel: String, wert: String, eintraege: List<T>, text: (T) -> String, onWahl: (T) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { offen = true }
                .padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            eintraege.forEach { e ->
                DropdownMenuItem(
                    text = { Text(text(e)) },
                    onClick = {
                        offen = false
                        onWahl(e)
                    },
                )
            }
        }
    }
}
