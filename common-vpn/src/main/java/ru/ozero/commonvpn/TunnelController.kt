package ru.ozero.commonvpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers

class TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    fun onProbing() {
        PersistentLoggers.info(TAG, "probing")
        _state.value = TunnelState.Probing
    }

    fun onConnecting(engineId: EngineId) {
        PersistentLoggers.info(TAG, "connecting engine=$engineId")
        _state.value = TunnelState.Connecting(engineId)
    }

    fun onEngineStarted(engineId: EngineId, socksPort: Int) {
        PersistentLoggers.info(TAG, "engineStarted engine=$engineId socksPort=$socksPort")
        _state.value = TunnelState.Connected(engineId, socksPort)
    }

    fun onEngineDied(engineId: EngineId, reason: String) {
        PersistentLoggers.error(TAG, "engineDied engine=$engineId reason=$reason kill-switch активен")
        _state.value = TunnelState.Failed(engineId, reason)
    }

    fun onDisconnecting() {
        PersistentLoggers.info(TAG, "disconnecting")
        _state.value = TunnelState.Disconnecting
    }

    fun reset() {
        PersistentLoggers.info(TAG, "reset")
        _state.value = TunnelState.Idle
    }

    private companion object {
        const val TAG = "TunnelController"
    }
}
