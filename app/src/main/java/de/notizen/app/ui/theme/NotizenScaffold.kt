package de.notizen.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Scaffold-Wrapper der App.
 *
 * WARUM ES DEN GIBT -- bitte nicht wegoptimieren:
 *
 * Die Token-Zuordnung will
 * `surfaceContainerLow` als App-Hintergrund und `surface` als Notiz-Karte.
 * Compose' `Scaffold` nimmt als containerColor aber `background`, und in
 * unserem Fallback-Schema ist `background` identisch mit `surface` (#000C2C),
 * also die KARTENFARBE. Ohne diese Ueberschreibung waeren Karte und
 * Hintergrund gleich eingefaerbt und die Karten unsichtbar.
 *
 * Deshalb: jeder Screen benutzt NotizenScaffold, nie Scaffold direkt.
 */
@Composable
fun NotizenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}
