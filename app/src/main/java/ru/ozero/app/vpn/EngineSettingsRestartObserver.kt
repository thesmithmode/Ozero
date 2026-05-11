package ru.ozero.app.vpn

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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
        val engineAutoPriority: List<EngineId>?,
    )

    private val snapshots: Flow<Snapshot> = settingsFlow
        .map {
            Snapshot(
                manualEngine = it.manualEngine,
                byedpiWinningArgs = it.byedpiWinningArgs?.trim(),
                ipv6Enabled = it.ipv6Enabled,
                customDnsServers = it.customDnsServers,
                engineAutoPriority = if (it.manualEngine == null) it.engineAutoPriority else null,
            )
        }
        .distinctUntilChanged()

    private val manualEngineFastPath: Flow<Snapshot> = flow {
        var prev: Snapshot? = null
        snapshots.collect { current ->
            val previous = prev
            if (previous != null && previous.manualEngine != current.manualEngine) {
                emit(current)
            }
            prev = current
        }
    }

    @OptIn(FlowPreview::class)
    private val otherChangesDebounced: Flow<Snapshot> = flow {
        var prev: Snapshot? = null
        snapshots.collect { current ->
            val previous = prev
            if (previous != null &&
                previous.copy(manualEngine = null) != current.copy(manualEngine = null)
            ) {
                emit(current)
            }
            prev = current
        }
    }.debounce(RESTART_DEBOUNCE_MS)

    val triggers: Flow<Snapshot> = merge(manualEngineFastPath, otherChangesDebounced)

    suspend fun handle(snapshot: Snapshot) {
        if (vpnStateProvider() is TunnelState.Connected) {
            onRestartConnected(snapshot)
        }
    }

    private companion object {
        const val RESTART_DEBOUNCE_MS = 4000L
    }
}
