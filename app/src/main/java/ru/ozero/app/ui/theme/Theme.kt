package ru.ozero.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF006E6E),
        onPrimary = Color.White,
        secondary = Color(0xFF4A6363),
        background = Color(0xFFF5FAFA),
        surface = Color(0xFFF5FAFA),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF4CDADA),
        onPrimary = Color(0xFF003737),
        secondary = Color(0xFFB2CBCB),
        background = Color(0xFF191C1C),
        surface = Color(0xFF191C1C),
    )

@Composable
fun OzeroTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
