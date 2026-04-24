package ru.ozero.commonvpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    fun onEngineStarted(socksPort: Int) {
        // Не логируем порт в info — читается через READ_LOGS/ADB. Только debug.
        Log.d(TAG, "engineStarted socksPort=$socksPort")
        Log.i(TAG, "engineStarted")
        _state.value = TunnelState.Connected(socksPort)
    }

    // TUN fd не закрывается — kill-switch активен, трафик блокируется на уровне TUN
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

    /**
     * Предусловие: вызывающий должен закрыть TUN fd ДО вызова reset().
     * Иначе kill-switch сломается: Idle предполагает что TUN закрыт и трафик уходит наружу без VPN.
     * Гарантия инварианта — ответственность OzeroVpnService.stopVpn.
     */
    fun reset() {
        Log.i(TAG, "reset")
        _state.value = TunnelState.Idle
    }

    private companion object {
        const val TAG = "TunnelController"
    }
}
