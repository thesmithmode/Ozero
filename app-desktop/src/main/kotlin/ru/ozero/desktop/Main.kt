package ru.ozero.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import ru.ozero.desktop.logging.DesktopLogStore
import ru.ozero.desktop.ui.MainViewModel
import ru.ozero.desktop.ui.OzeroSystemTray
import ru.ozero.desktop.ui.RootNavigation
import ru.ozero.desktop.ui.theme.OzeroTheme
import ru.ozero.desktop.vpn.DesktopSettingsStore
import ru.ozero.desktop.vpn.DesktopVpnManager

fun main() {
    DesktopLogStore.installJulHandler()
    application {
        val settingsStore = remember { DesktopSettingsStore() }
        var isWindowVisible by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        val vpnManager = remember { DesktopVpnManager(scope) }
        val viewModel = remember { MainViewModel(scope, vpnManager, settingsStore) }

        OzeroSystemTray(
            vpnManager = vpnManager,
            scope = scope,
            onShowWindow = { isWindowVisible = true },
        )

        val windowIcon = remember {
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("icon.png")
                ?.let { BitmapPainter(loadImageBitmap(it)) }
        }

        if (isWindowVisible) {
            Window(
                onCloseRequest = { isWindowVisible = false },
                title = "Ozero",
                state = WindowState(size = DpSize(420.dp, 780.dp)),
                icon = windowIcon,
            ) {
                OzeroTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        RootNavigation(viewModel = viewModel, settingsStore = settingsStore)
                    }
                }
            }
        }
    }
}
