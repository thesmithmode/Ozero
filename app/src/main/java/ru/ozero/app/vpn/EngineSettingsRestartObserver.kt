package ru.ozero.app.vpn

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import ru.ozero.app.settings.SettingsModel
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.OrchestratorState

class EngineSettingsRestartObserver(
    settingsFlow: Flow<SettingsModel>,
    private val vpnStateProvider: () -> OrchestratorState,
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
        if (vpnStateProvider() is OrchestratorState.Connected) {
            onRestartConnected(snapshot)
        }
    }
}
