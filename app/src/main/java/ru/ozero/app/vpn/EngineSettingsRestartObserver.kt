package ru.ozero.app.vpn

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineId

class EngineSettingsRestartObserver(
    settingsFlow: Flow<SettingsModel>,
    private val vpnStateProvider: () -> TunnelState,
    private val onRestartConnected: suspend (Snapshot) -> Unit,
) {
    data class Snapshot(
        val manualEngine: EngineId?,
        val byedpiWinningArgs: String?,
        val splitMode: SplitTunnelMode,
        val ipv6Enabled: Boolean,
    )

    val triggers: Flow<Snapshot> = settingsFlow
        .map { Snapshot(it.manualEngine, it.byedpiWinningArgs?.trim(), it.splitMode, it.ipv6Enabled) }
        .distinctUntilChanged()
        .drop(1)

    suspend fun handle(snapshot: Snapshot) {
        if (vpnStateProvider() is TunnelState.Connected) {
            onRestartConnected(snapshot)
        }
    }
}
