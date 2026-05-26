package ru.ozero.desktop.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ozero.desktop.engines.ByeDpiSubprocess
import ru.ozero.desktop.engines.SingboxSubprocess
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.ui.components.PowerDiscState
import java.util.logging.Logger

class DesktopVpnManager(private val scope: CoroutineScope) {

    private val log = Logger.getLogger("DesktopVpnManager")

    private val byedpi = ByeDpiSubprocess()
    private val singbox = SingboxSubprocess()
    private val configBuilder = SingboxConfigBuilder()

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<TunnelStats?>(null)
    val stats: StateFlow<TunnelStats?> = _stats.asStateFlow()

    private val _switching = MutableStateFlow<SwitchingTransition?>(null)
    val switching: StateFlow<SwitchingTransition?> = _switching.asStateFlow()

    private val _powerDiscState = MutableStateFlow(PowerDiscState.Off)
    val powerDiscState: StateFlow<PowerDiscState> = _powerDiscState.asStateFlow()

    private var activeEngine: EngineId? = null
    private var sessionStartMs: Long = 0L

    suspend fun toggle() {
        when (_state.value) {
            is TunnelState.Connected -> disconnect()
            is TunnelState.Idle, is TunnelState.Failed -> connect(EngineId.BYEDPI)
            else -> Unit
        }
    }

    suspend fun connect(engineId: EngineId, socksPort: Int = DEFAULT_SOCKS_PORT) {
        _state.value = TunnelState.Connecting(engineId)
        _powerDiscState.value = PowerDiscState.Connecting
        log.info("Connecting with engine $engineId on port $socksPort")

        val engineStarted = when (engineId) {
            EngineId.BYEDPI -> byedpi.start(socksPort)
            else -> {
                log.info("Engine $engineId: using sing-box TUN only")
                true
            }
        }

        if (!engineStarted) {
            _state.value = TunnelState.Failed(engineId, "Engine failed to start")
            _powerDiscState.value = PowerDiscState.Off
            return
        }

        val config = configBuilder.socksUpstream(socksPort).buildTun2Socks()
        val tunStarted = singbox.startWithConfig(config)

        if (!tunStarted) {
            byedpi.stop()
            _state.value = TunnelState.Failed(engineId, "TUN interface failed to start")
            _powerDiscState.value = PowerDiscState.Off
            return
        }

        activeEngine = engineId
        sessionStartMs = System.currentTimeMillis()
        _state.value = TunnelState.Connected(engineId, socksPort)
        _powerDiscState.value = PowerDiscState.Connected
        log.info("Connected via $engineId")

        startStatsPolling()
    }

    fun disconnect() {
        _state.value = TunnelState.Disconnecting
        _powerDiscState.value = PowerDiscState.Off
        singbox.stop()
        byedpi.stop()
        _stats.value = null
        activeEngine = null
        _state.value = TunnelState.Idle
        log.info("Disconnected")
    }

    private fun startStatsPolling() {
        scope.launch(Dispatchers.IO) {
            while (_state.value is TunnelState.Connected) {
                _stats.value = TunnelStats(
                    txPackets = 0,
                    txBytes = 0,
                    rxPackets = 0,
                    rxBytes = 0,
                    timestampMs = System.currentTimeMillis(),
                    sessionStartMs = sessionStartMs,
                )
                delay(1_000)
            }
        }
    }

    companion object {
        private const val DEFAULT_SOCKS_PORT = 1080
    }
}
