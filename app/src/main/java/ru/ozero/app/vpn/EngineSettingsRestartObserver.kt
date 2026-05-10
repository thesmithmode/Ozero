package ru.ozero.app.vpn

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId

class EngineSettingsRestartObserver(
    settingsFlow: Flow<SettingsModel>,
    private val vpnStateProvider: () -> TunnelState,
    private val onRestartConnected: suspend (Snapshot) -> Unit,
) {
    data class Snapshot(
        val manualEngine: EngineId?,
        val byedpiWinningArgs: String?,
        val ipv6Enabled: Boolean,
        val customDnsServers: List<String>,
    )

    @OptIn(FlowPreview::class)
    val triggers: Flow<Snapshot> = settingsFlow
        .map {
            Snapshot(
                manualEngine = it.manualEngine,
                byedpiWinningArgs = it.byedpiWinningArgs?.trim(),
                ipv6Enabled = it.ipv6Enabled,
                customDnsServers = it.customDnsServers,
            )
        }
        .distinctUntilChanged()
        .drop(1)
        .debounce(RESTART_DEBOUNCE_MS)

    suspend fun handle(snapshot: Snapshot) {
        if (vpnStateProvider() is TunnelState.Connected) {
            onRestartConnected(snapshot)
        }
    }

    private companion object {
        const val RESTART_DEBOUNCE_MS = 4000L
    }
}
