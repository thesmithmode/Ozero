package ru.ozero.commonvpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers

class TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()
    private val _stats = MutableStateFlow<TunnelStats?>(null)
    val stats: StateFlow<TunnelStats?> = _stats.asStateFlow()
    private val lock = Any()

    fun onProbing() = transition(TunnelState.Probing)

    fun onConnecting(engineId: EngineId) = transition(TunnelState.Connecting(engineId))

    fun onEngineStarted(engineId: EngineId, socksPort: Int) =
        transition(TunnelState.Connected(engineId, socksPort))

    fun onEngineDied(engineId: EngineId, reason: String) =
        transition(TunnelState.Failed(engineId, reason), markError = true)

    fun onDisconnecting() = transition(TunnelState.Disconnecting)

    fun reset() {
        _stats.value = null
        transition(TunnelState.Idle)
    }

    fun updateStats(stats: TunnelStats) {
        _stats.value = stats
    }

    private fun transition(target: TunnelState, markError: Boolean = false) {
        synchronized(lock) {
            val current = _state.value
            if (current == target) return
            if (!isAllowed(current, target)) {
                PersistentLoggers.warn(
                    TAG,
                    "invalid transition ${name(current)} → ${name(target)} ignored",
                )
                return
            }
            if (markError) {
                PersistentLoggers.error(TAG, "${name(current)} → ${name(target)}")
            } else {
                PersistentLoggers.info(TAG, "${name(current)} → ${name(target)}")
            }
            _state.value = target
        }
    }

    private fun isAllowed(from: TunnelState, to: TunnelState): Boolean = when (from) {
        is TunnelState.Idle -> to is TunnelState.Probing
        is TunnelState.Probing ->
            to is TunnelState.Connecting ||
                to is TunnelState.Failed ||
                to is TunnelState.Disconnecting
        is TunnelState.Connecting ->
            to is TunnelState.Connected ||
                to is TunnelState.Failed ||
                to is TunnelState.Disconnecting
        is TunnelState.Connected ->
            to is TunnelState.Disconnecting ||
                to is TunnelState.Failed
        is TunnelState.Disconnecting ->
            to is TunnelState.Idle ||
                to is TunnelState.Failed
        is TunnelState.Failed ->
            to is TunnelState.Probing ||
                to is TunnelState.Idle ||
                to is TunnelState.Disconnecting
    }

    private fun name(s: TunnelState): String = when (s) {
        is TunnelState.Idle -> "Idle"
        is TunnelState.Probing -> "Probing"
        is TunnelState.Connecting -> "Connecting(${s.engineId})"
        is TunnelState.Connected -> "Connected(${s.engineId}, port=${s.socksPort})"
        is TunnelState.Failed -> "Failed(${s.engineId}, ${s.reason})"
        is TunnelState.Disconnecting -> "Disconnecting"
    }

    private companion object {
        const val TAG = "TunnelController"
    }
}
