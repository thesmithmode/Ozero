package ru.ozero.app.vpn

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode

class EngineSettingsRestartObserver(
    settingsFlow: Flow<SettingsModel>,
    private val vpnStateProvider: () -> TunnelState,
    private val onRestartConnected: suspend (Snapshot) -> Unit,
) {
    private var startupAcceptedSnapshot: Snapshot? = null

    data class Snapshot(
        val manualEngine: EngineId?,
        val byedpiWinningArgs: String?,
        val ipv6Enabled: Boolean,
        val trafficMode: TrafficMode,
        val customDnsServers: List<String>,
        val engineAutoPriority: List<EngineId>?,
    )

    data class Trigger(val previous: Snapshot, val snapshot: Snapshot)

    val triggers: Flow<Trigger> = channelFlow {
        var baseline: Snapshot? = null
        var pending: Job? = null
        settingsFlow
            .map {
                Snapshot(
                    manualEngine = it.manualEngine,
                    byedpiWinningArgs = it.byedpiWinningArgs?.trim(),
                    ipv6Enabled = it.ipv6Enabled,
                    trafficMode = it.trafficMode,
                    customDnsServers = it.customDnsServers,
                    engineAutoPriority = if (it.manualEngine == null) it.engineAutoPriority else null,
                )
            }
            .distinctUntilChanged()
            .collect { snapshot ->
                val previous = baseline
                if (previous == null) {
                    baseline = snapshot
                    return@collect
                }
                pending?.cancel()
                pending = launch {
                    delay(RESTART_DEBOUNCE_MS)
                    if (previous != snapshot) {
                        send(Trigger(previous = previous, snapshot = snapshot))
                    }
                    baseline = snapshot
                }
            }
    }

    suspend fun handle(trigger: Trigger) {
        val snapshot = trigger.snapshot
        val state = vpnStateProvider()
        if (state is TunnelState.Connected) {
            if (
                snapshot.targetEngine() == state.engineId &&
                snapshot.sameRuntimeFor(state.engineId, trigger.previous)
            ) {
                PersistentLoggers.debug(
                    TAG,
                    "settings snapshot ignored while connected: engine toggle returned to ${state.engineId}",
                )
                return
            }
            if (startupAcceptedSnapshot == snapshot) {
                PersistentLoggers.debug(
                    TAG,
                    "settings snapshot ignored while connected: startup snapshot already applied (${state.engineId})",
                )
                return
            }
            startupAcceptedSnapshot = null
            onRestartConnected(snapshot)
            return
        }
        val currentEngine: EngineId = when (state) {
            is TunnelState.Connecting -> state.engineId
            is TunnelState.Probing -> state.engineId ?: return
            else -> {
                startupAcceptedSnapshot = null
                return
            }
        }
        if (startupAcceptedSnapshot == snapshot) return
        if (
            currentEngine == snapshot.targetEngine() &&
            trigger.previous.targetEngine() != currentEngine &&
            startupAcceptedSnapshot == null
        ) {
            startupAcceptedSnapshot = snapshot
            return
        }
        startupAcceptedSnapshot = null
        onRestartConnected(snapshot)
    }

    private fun Snapshot.targetEngine(): EngineId? = manualEngine ?: engineAutoPriority?.firstOrNull()

    private fun Snapshot.sameRuntimeFor(engineId: EngineId, other: Snapshot): Boolean {
        val commonRuntimeMatches = ipv6Enabled == other.ipv6Enabled &&
            trafficMode == other.trafficMode &&
            customDnsServers == other.customDnsServers
        return when (engineId) {
            EngineId.BYEDPI -> byedpiWinningArgs == other.byedpiWinningArgs && commonRuntimeMatches
            else -> commonRuntimeMatches
        }
    }

    internal companion object {
        const val TAG = "EngineSettingsRestartObserver"
        const val RESTART_DEBOUNCE_MS = 4000L
        const val RESTART_DEBOUNCE_MS_FOR_TESTS = RESTART_DEBOUNCE_MS
    }
}
