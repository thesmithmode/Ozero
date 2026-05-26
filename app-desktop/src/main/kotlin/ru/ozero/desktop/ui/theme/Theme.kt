package ru.ozero.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = OzeroPalette.StateConnected,
    onPrimary = OzeroPalette.Ink,
    secondary = OzeroPalette.Aqua,
    background = OzeroPalette.Bg0,
    surface = OzeroPalette.Bg1,
    surfaceVariant = OzeroPalette.Ink2,
    onSurface = OzeroPalette.Text,
    onSurfaceVariant = OzeroPalette.Text2,
    error = OzeroPalette.StateDanger,
)

@Composable
fun OzeroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
