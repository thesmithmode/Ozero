package ru.ozero.desktop.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ozero.desktop.engine.DesktopEngine
import ru.ozero.desktop.engine.DesktopEngineRegistry
import ru.ozero.desktop.engine.EngineConfig
import ru.ozero.desktop.engine.EngineStartResult
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.proxy.SystemProxy
import ru.ozero.desktop.proxy.WindowsSystemProxy
import ru.ozero.desktop.ui.components.PowerDiscState
import java.util.logging.Logger

class DesktopVpnManager(
    private val scope: CoroutineScope,
    private val systemProxy: SystemProxy = WindowsSystemProxy.create(),
) {
    private val log = Logger.getLogger("DesktopVpnManager")

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<TunnelStats?>(null)
    val stats: StateFlow<TunnelStats?> = _stats.asStateFlow()

    private val _switching = MutableStateFlow<SwitchingTransition?>(null)
    val switching: StateFlow<SwitchingTransition?> = _switching.asStateFlow()

    private val _powerDiscState = MutableStateFlow(PowerDiscState.Off)
    val powerDiscState: StateFlow<PowerDiscState> = _powerDiscState.asStateFlow()

    private var activeEngine: DesktopEngine? = null
    private var watchdogJob: Job? = null

    fun toggle() {
        when (_state.value) {
            is TunnelState.Connected -> disconnect()
            is TunnelState.Idle, is TunnelState.Failed -> connect()
            else -> Unit
        }
    }

    fun connect(engineId: EngineId? = null) {
        val id = engineId ?: EngineId.SINGBOX
        val engine = DesktopEngineRegistry.get(id)
        if (engine == null) {
            _state.value = TunnelState.Failed(id, "Unknown engine: ${id.displayName}")
            return
        }

        _state.value = TunnelState.Connecting(id)
        _powerDiscState.value = PowerDiscState.Connecting

        scope.launch {
            val config = EngineConfig()
            val result = engine.start(config)

            when (result) {
                is EngineStartResult.Success -> {
                    activeEngine = engine
                    val port = result.port
                    _state.value = TunnelState.Connected(id, port)
                    _powerDiscState.value = PowerDiscState.On

                    runCatching { systemProxy.enable("127.0.0.1:$port") }
                        .onFailure { log.warning("system proxy set failed: ${it.message}") }

                    startWatchdog(engine, id)
                    log.info("connected via ${id.displayName} on port $port")
                }

                is EngineStartResult.BinaryMissing -> {
                    _state.value = TunnelState.Failed(id, "${id.displayName}: ${result.binaryName} not found")
                    _powerDiscState.value = PowerDiscState.Off
                    log.warning("binary missing: ${result.binaryName}")
                }

                is EngineStartResult.PlatformUnavailable -> {
                    _state.value = TunnelState.Failed(id, result.reason)
                    _powerDiscState.value = PowerDiscState.Off
                    log.warning("platform unavailable: ${result.reason}")
                }

                is EngineStartResult.Failed -> {
                    _state.value = TunnelState.Failed(id, result.reason)
                    _powerDiscState.value = PowerDiscState.Off
                    log.warning("start failed: ${result.reason}")
                }
            }
        }
    }

    fun disconnect() {
        val engine = activeEngine
        _state.value = TunnelState.Disconnecting
        _powerDiscState.value = PowerDiscState.Off

        scope.launch {
            watchdogJob?.cancel()
            watchdogJob = null

            runCatching { systemProxy.disable() }
                .onFailure { log.warning("system proxy disable failed: ${it.message}") }

            engine?.let {
                runCatching { it.stop() }
                    .onFailure { log.warning("engine stop failed: ${it.message}") }
            }

            activeEngine = null
            _state.value = TunnelState.Idle
            _stats.value = null
            log.info("disconnected")
        }
    }

    fun switchEngine(newEngineId: EngineId) {
        val currentState = _state.value
        val currentEngineId = when (currentState) {
            is TunnelState.Connected -> currentState.engineId
            else -> null
        }

        if (currentEngineId == newEngineId) return

        _switching.value = SwitchingTransition(currentEngineId, newEngineId)

        scope.launch {
            if (currentState is TunnelState.Connected) {
                disconnect()
                delay(500)
            }
            connect(newEngineId)
            _switching.value = null
        }
    }

    private fun startWatchdog(engine: DesktopEngine, engineId: EngineId) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!engine.isRunning()) {
                    log.warning("watchdog: engine ${engineId.displayName} died unexpectedly")
                    runCatching { systemProxy.disable() }
                    activeEngine = null
                    _state.value = TunnelState.Failed(engineId, "Engine process terminated unexpectedly")
                    _powerDiscState.value = PowerDiscState.Off
                    break
                }
            }
        }
    }

    private companion object {
        const val WATCHDOG_INTERVAL_MS = 2_000L
    }
}
