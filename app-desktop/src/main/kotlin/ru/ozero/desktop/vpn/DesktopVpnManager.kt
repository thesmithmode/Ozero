package ru.ozero.desktop.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.ui.components.PowerDiscState
import java.io.File

class DesktopVpnManager(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<TunnelStats?>(null)
    val stats: StateFlow<TunnelStats?> = _stats.asStateFlow()

    private val _switching = MutableStateFlow<SwitchingTransition?>(null)
    val switching: StateFlow<SwitchingTransition?> = _switching.asStateFlow()

    private val _powerDiscState = MutableStateFlow(PowerDiscState.Off)
    val powerDiscState: StateFlow<PowerDiscState> = _powerDiscState.asStateFlow()

    fun toggle() {
        when (_state.value) {
            is TunnelState.Connected -> disconnect()
            is TunnelState.Idle, is TunnelState.Failed -> connect()
            else -> Unit
        }
    }

    private fun connect() {
        val engine = EngineId.BYEDPI
        _state.value = TunnelState.Connecting(engine)
        _powerDiscState.value = PowerDiscState.Connecting

        scope.launch {
            delay(800)
            if (!hasBinaries()) {
                _state.value = TunnelState.Failed(
                    engine,
                    "Движки не установлены",
                )
                _powerDiscState.value = PowerDiscState.Off
                return@launch
            }
            _state.value = TunnelState.Failed(engine, "VPN подключение пока не реализовано")
            _powerDiscState.value = PowerDiscState.Off
        }
    }

    private fun disconnect() {
        _state.value = TunnelState.Idle
        _powerDiscState.value = PowerDiscState.Off
        _stats.value = null
    }

    private fun hasBinaries(): Boolean {
        val binDir = File(System.getProperty("app.dir", "."), "binaries")
        return File(binDir, "byedpi.exe").exists() || File(binDir, "sing-box.exe").exists()
    }
}
