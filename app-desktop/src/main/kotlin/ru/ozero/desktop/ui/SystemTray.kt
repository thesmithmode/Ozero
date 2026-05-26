package ru.ozero.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.strings.Strings
import ru.ozero.desktop.ui.theme.OzeroPalette
import ru.ozero.desktop.vpn.DesktopVpnManager

@Composable
fun ApplicationScope.OzeroSystemTray(
    vpnManager: DesktopVpnManager,
    scope: CoroutineScope,
    onShowWindow: () -> Unit,
) {
    val state by vpnManager.state.collectAsState()
    val isConnected = state is TunnelState.Connected
    val iconColor = if (isConnected) OzeroPalette.StateConnected else OzeroPalette.StateOff
    val icon = remember(iconColor) { OzeroTrayIcon(iconColor) }

    Tray(
        icon = icon,
        tooltip = if (isConnected) "Ozero — ${Strings.MAIN_STATUS_CONNECTED}" else "Ozero",
        menu = {
            Item(Strings.TRAY_SHOW, onClick = onShowWindow)
            Separator()
            if (isConnected) {
                Item(Strings.TRAY_DISCONNECT, onClick = { vpnManager.disconnect() })
            } else {
                Item(Strings.TRAY_CONNECT, onClick = { scope.launch { vpnManager.toggle() } })
            }
            Separator()
            Item(Strings.TRAY_QUIT, onClick = ::exitApplication)
        },
    )
}

private class OzeroTrayIcon(private val color: Color) : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
