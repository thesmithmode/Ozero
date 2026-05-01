package ru.ozero.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors =
    lightColorScheme(
        primary = OzeroPalette.Teal,
        onPrimary = OzeroPalette.Text,
        secondary = OzeroPalette.Aqua,
        background = OzeroPalette.Bg0,
        surface = OzeroPalette.Bg1,
        onSurface = OzeroPalette.Text,
        error = OzeroPalette.StateDanger,
    )

private val DarkColors =
    darkColorScheme(
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
fun OzeroTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
