package ru.ozero.commonvpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    fun onEngineStarted(socksPort: Int) {
        Log.d(TAG, "engineStarted socksPort=$socksPort")
        Log.i(TAG, "engineStarted")
        _state.value = TunnelState.Connected(socksPort)
    }

    fun onEngineDied(reason: String) {
        Log.e(TAG, "engineDied reason=$reason kill-switch активен")
        _state.value = TunnelState.Dead(reason)
    }

    fun onReconnecting() {
        Log.i(TAG, "reconnecting")
        _state.value = TunnelState.Disconnecting
    }

    fun onReconnected(socksPort: Int) {
        Log.d(TAG, "reconnected socksPort=$socksPort")
        Log.i(TAG, "reconnected")
        _state.value = TunnelState.Connected(socksPort)
    }

    fun reset() {
        Log.i(TAG, "reset")
        _state.value = TunnelState.Idle
    }

    private companion object {
        const val TAG = "TunnelController"
    }
}
