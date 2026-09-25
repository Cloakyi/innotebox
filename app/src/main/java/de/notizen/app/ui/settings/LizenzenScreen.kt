package de.notizen.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Die Lizenzen der App und alles, was in ihr steckt.
 *
 * **Pflicht, nicht Zierde.** Die Apache-Lizenz verlangt, dass jeder, der die
 * App bekommt, auch den Lizenztext bekommt; die Pakete von ML Kit und den
 * Play-Diensten bringen dazu eine eigene Liste mit Drittsoftware mit, deren
 * Hinweise ebenfalls weitergegeben werden muessen. Beim Packen der APK fallen
 * diese Dateien sonst weg.
 *
 * Die Daten stehen in `assets/lizenzen.json`, erzeugt von `./gradlew
 * :app:lizenzen` (gradle/lizenzen.gradle.kts) aus dem tatsaechlichen
 * Release-Build. Wer eine Abhaengigkeit aendert, laesst die Aufgabe neu laufen.
 */
@Composable
fun LizenzenScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val daten by produceState<Lizenzdaten?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { lizenzdatenLesen(context) }.getOrNull() }
    }
    var offen by remember { mutableStateOf<Lizenzanzeige?>(null) }

    val oeffnen: (String?, Int?, String?) -> Unit = { titel, text, url ->
        val d = daten
        when {
            text != null && d != null -> offen = Lizenzanzeige(titel ?: "", d.texte[text])
            url != null -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        // Die Hinweise, die Abschnitt 5 d der GPL fuer eine Oberflaeche
        // verlangt: Copyright, keine Gewaehrleistung, Weitergabe unter der
        // Lizenz, und wo man sie lesen kann (hier, ohne Netz).
        item {
            Absatz(
                "InNoteBox ist freie Software, Copyright 2026 Cloak Studio. Du darfst sie unter " +
                    "den Bedingungen der GNU General Public License, Version 3, weitergeben und " +
                    "verändern. Dazu kommt eine zusätzliche Erlaubnis für die Bibliotheken von " +
                    "Google, die selbst nicht frei sind.",
            )
            Absatz(
                "InNoteBox kommt ohne jede Gewährleistung, soweit das Gesetz das zulässt. " +
                    "Einzelheiten stehen in der Lizenz.",
            )
        }

        val d = daten
        if (d == null) return@LazyColumn

        item {
            Zeile(
                titel = "GNU General Public License, Version 3",
                wert = "Die Lizenz von InNoteBox",
                onClick = { oeffnen("GNU General Public License", d.app.lizenz, null) },
            )
            Zeile(
                titel = "Zusätzliche Erlaubnis",
                wert = "Für die Bibliotheken von Google",
                onClick = { oeffnen("Zusätzliche Erlaubnis", d.app.zusatz, null) },
            )
            Zeile(
                titel = "Quelltext",
                wert = QUELLTEXT.removePrefix("https://"),
                onClick = { oeffnen(null, null, QUELLTEXT) },
            )
        }

        item { Ueberschrift("Bibliotheken") }
        items(d.bibliotheken, key = { "b:" + it.name }) { b ->
            Zeile(
                titel = b.name,
                wert = "${b.version}, ${b.lizenz}",
                onClick = {
                    // Eine NOTICE-Datei gehoert nach Apache 2.0 zum Lizenztext dazu.
                    val hinweis = b.hinweis
                    if (b.text != null && hinweis != null) {
                        offen = Lizenzanzeige(b.name, d.texte[b.text] + "\n\n\n" + d.texte[hinweis])
                    } else {
                        oeffnen(b.name, b.text, b.url)
                    }
                },
            )
        }

        item {
            Ueberschrift("In den Bibliotheken von Google enthalten")
            Absatz(
                "ML Kit und die Play-Dienste bringen selbst weitere Software mit. Die " +
                    "Hinweise dazu stammen aus diesen Paketen.",
            )
        }
        items(d.enthalten, key = { "e:" + it.name }) { e ->
            Zeile(
                titel = e.name,
                wert = null,
                onClick = {
                    offen = Lizenzanzeige(e.name, e.texte.joinToString("\n\n\n") { d.texte[it] })
                },
            )
        }

        item { Ueberschrift("Schrift") }
        items(d.schriften, key = { "s:" + it.name }) { s ->
            Zeile(
                titel = s.name,
                wert = s.lizenz,
                onClick = { oeffnen(s.name, s.text, null) },
            )
        }
    }

    offen?.let { anzeige ->
        AlertDialog(
            onDismissRequest = { offen = null },
            confirmButton = { TextButton(onClick = { offen = null }) { Text("Schließen") } },
            title = { Text(anzeige.titel) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = anzeige.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
        )
    }
}

private const val QUELLTEXT = "https://github.com/Cloakyi/innotebox"

private data class Lizenzanzeige(val titel: String, val text: String)

internal data class Bibliothek(
    val name: String,
    val version: String,
    val lizenz: String,
    /** Stelle in [Lizenzdaten.texte], oder `null`, wenn die Bedingungen beim Anbieter stehen. */
    val text: Int?,
    val url: String?,
    /** Die NOTICE-Datei der Bibliothek, falls sie eine mitbringt. */
    val hinweis: Int? = null,
)

/** Die eigene Lizenz der App: die GPL und die zusätzliche Erlaubnis. */
internal data class Eigenlizenz(val lizenz: Int, val zusatz: Int)

internal data class Enthalten(val name: String, val texte: List<Int>)

internal data class Schrift(val name: String, val lizenz: String, val text: Int)

internal data class Lizenzdaten(
    val app: Eigenlizenz,
    val bibliotheken: List<Bibliothek>,
    val enthalten: List<Enthalten>,
    val schriften: List<Schrift>,
    val texte: List<String>,
)

private fun lizenzdatenLesen(context: Context): Lizenzdaten {
    val json = context.assets.open("lizenzen.json").bufferedReader().use { it.readText() }
    return lizenzdatenAus(JSONObject(json))
}

internal fun lizenzdatenAus(wurzel: JSONObject): Lizenzdaten {
    fun <T> JSONArray.liste(umwandeln: (JSONObject) -> T): List<T> =
        (0 until length()).map { umwandeln(getJSONObject(it)) }

    return Lizenzdaten(
        app = wurzel.getJSONObject("app").let { Eigenlizenz(it.getInt("lizenz"), it.getInt("zusatz")) },
        bibliotheken = wurzel.getJSONArray("bibliotheken").liste {
            Bibliothek(
                name = it.getString("name"),
                version = it.getString("version"),
                lizenz = it.getString("lizenz"),
                text = if (it.has("text")) it.getInt("text") else null,
                url = if (it.has("url")) it.getString("url") else null,
                hinweis = if (it.has("hinweis")) it.getInt("hinweis") else null,
            )
        },
        enthalten = wurzel.getJSONArray("enthalten").liste { e ->
            val texte = e.getJSONArray("texte")
            Enthalten(e.getString("name"), (0 until texte.length()).map { texte.getInt(it) })
        },
        schriften = wurzel.getJSONArray("schriften").liste {
            Schrift(it.getString("name"), it.getString("lizenz"), it.getInt("text"))
        },
        texte = wurzel.getJSONArray("texte").let { t -> (0 until t.length()).map { t.getString(it) } },
    )
}

@Composable
private fun Ueberschrift(titel: String) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = titel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Absatz(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun Zeile(titel: String, wert: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(titel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (wert != null) {
            Text(wert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
