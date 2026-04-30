package ru.ozero.commonvpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.enginescore.EngineId

class TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    fun onProbing() {
        Log.i(TAG, "probing")
        _state.value = TunnelState.Probing
    }

    fun onConnecting(engineId: EngineId) {
        Log.i(TAG, "connecting engine=$engineId")
        _state.value = TunnelState.Connecting(engineId)
    }

    fun onEngineStarted(engineId: EngineId, socksPort: Int) {
        Log.d(TAG, "engineStarted engine=$engineId socksPort=$socksPort")
        Log.i(TAG, "engineStarted")
        _state.value = TunnelState.Connected(engineId, socksPort)
    }

    fun onEngineDied(engineId: EngineId, reason: String) {
        Log.e(TAG, "engineDied engine=$engineId reason=$reason kill-switch активен")
        _state.value = TunnelState.Failed(engineId, reason)
    }

    fun onDisconnecting() {
        Log.i(TAG, "disconnecting")
        _state.value = TunnelState.Disconnecting
    }

    fun reset() {
        Log.i(TAG, "reset")
        _state.value = TunnelState.Idle
    }

    private companion object {
        const val TAG = "TunnelController"
    }
}
