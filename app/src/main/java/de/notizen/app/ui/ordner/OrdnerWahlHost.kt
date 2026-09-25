package de.notizen.app.ui.ordner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notizen.app.ui.stage.StageViewModel

/**
 * Die Ordnerauswahl fuer eine Mehrfachauswahl von Notizen.
 *
 * **Steht auch im Fluss zur Verfuegung**, nicht nur in der Ordneransicht. Wer
 * seine Ordner gerade aufbaut, will Notizen einsortieren, ohne dafuer die
 * ganze App umstellen zu muessen. Umgekehrt bleibt die Einteilung erhalten,
 * wenn er wieder in den Fluss zurueckschaltet.
 */
@Composable
fun OrdnerWahlHost(
    stage: StageViewModel,
    ordner: OrdnerViewModel = hiltViewModel(),
) {
    val offen by stage.ordnerBlattOffen.collectAsStateWithLifecycle()
    val auswahl by stage.auswahl.collectAsStateWithLifecycle()
    if (!offen) return

    OrdnerAuswahlDialog(
        ueberschrift = if (auswahl.size == 1) {
            "Notiz in einen Ordner legen"
        } else {
            auswahl.size.toString() + " Notizen in einen Ordner legen"
        },
        erklaerung = "Eine Notiz liegt in höchstens einem Ordner. " +
            "Der Hauptordner nimmt sie aus jedem Ordner wieder heraus.",
        zeilen = ordner.baumzeilen(),
        ausgeschlossen = null,
        zeigeHauptordner = true,
        onAbbrechen = stage::schliesseOrdnerAuswahl,
        onWahl = { zielId, name -> stage.setOrdner(zielId, name) },
    )
}
