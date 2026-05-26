package ru.ozero.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import ru.ozero.desktop.ui.MainViewModel
import ru.ozero.desktop.ui.RootNavigation
import ru.ozero.desktop.ui.theme.OzeroTheme
import ru.ozero.desktop.vpn.DesktopVpnManager

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Ozero",
        state = WindowState(size = DpSize(420.dp, 780.dp)),
    ) {
        val scope = rememberCoroutineScope()
        val vpnManager = remember { DesktopVpnManager(scope) }
        val viewModel = remember { MainViewModel(scope, vpnManager) }

        OzeroTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                RootNavigation(viewModel = viewModel)
            }
        }
    }
}
