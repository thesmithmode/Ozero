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
import ru.ozero.desktop.engine.TunFrontend
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.model.VpnMode
import ru.ozero.desktop.platform.PlatformDetector
import ru.ozero.desktop.proxy.SystemProxy
import ru.ozero.desktop.proxy.WindowsSystemProxy
import ru.ozero.desktop.ui.components.PowerDiscState
import java.util.logging.Logger

class DesktopVpnManager(
    private val scope: CoroutineScope,
    private val systemProxy: SystemProxy = WindowsSystemProxy.create(),
    private val platformDetector: PlatformDetectorPort = DefaultPlatformDetectorPort,
    private val warpConfigPort: WarpConfigPort = DefaultWarpConfigPort(),
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

    private val _effectiveVpnMode = MutableStateFlow(VpnMode.TUN)
    val effectiveVpnMode: StateFlow<VpnMode> = _effectiveVpnMode.asStateFlow()

    private var activeEngine: DesktopEngine? = null
    private var tunFrontend: TunFrontend? = null
    private var watchdogJob: Job? = null

    fun toggle(settings: SettingsModel = SettingsModel.DEFAULT) {
        when (_state.value) {
            is TunnelState.Connected -> disconnect()
            is TunnelState.Idle, is TunnelState.Failed -> {
                connect(settings.manualEngine, settings.vpnMode, settings)
            }
            else -> Unit
        }
    }

    fun connect(
        engineId: EngineId? = null,
        vpnMode: VpnMode = VpnMode.TUN,
        settings: SettingsModel = SettingsModel.DEFAULT,
    ) {
        val id = engineId ?: EngineId.SINGBOX
        val engine = DesktopEngineRegistry.get(id)
        if (engine == null) {
            _state.value = TunnelState.Failed(id, "Unknown engine: ${id.displayName}")
            return
        }

        _state.value = TunnelState.Connecting(id)
        _powerDiscState.value = PowerDiscState.Connecting

        scope.launch {
            val effectiveMode = resolveVpnMode(id, vpnMode)
            _effectiveVpnMode.value = effectiveMode

            when (effectiveMode) {
                VpnMode.TUN -> connectTun(id, engine, settings)
                VpnMode.PROXY -> connectProxy(id, engine, settings)
            }
        }
    }

    private suspend fun connectTun(id: EngineId, engine: DesktopEngine, settings: SettingsModel) {
        when (id) {
            EngineId.SINGBOX -> connectSingboxTun(id, engine, settings)
            EngineId.WARP -> connectWarpTun(id, engine)
            EngineId.BYEDPI -> connectWithTunFrontend(id, engine)
            else -> {
                log.info("${id.displayName} no TUN support, falling back to proxy")
                _effectiveVpnMode.value = VpnMode.PROXY
                connectProxy(id, engine, settings)
            }
        }
    }

    private suspend fun connectSingboxTun(id: EngineId, engine: DesktopEngine, settings: SettingsModel) {
        val json = SingboxDesktopConfigResolver.resolve(settings, VpnMode.TUN)
            .getOrElse {
                handleFailedResult(id, EngineStartResult.Failed(it.message ?: "Sing-box config is invalid"))
                return
            }
        val config = EngineConfig(singboxJson = json)
        val result = engine.start(config)
        handleStartResult(id, engine, result)
    }

    private suspend fun connectWarpTun(id: EngineId, engine: DesktopEngine) {
        val warpConfigText = warpConfigPort.loadWarpConfigText()?.trim()
        if (warpConfigText.isNullOrBlank()) {
            log.warning("start failed: WARP config is empty")
            _state.value = TunnelState.Failed(id, "WARP config is empty")
            _powerDiscState.value = PowerDiscState.Off
            return
        }
        val config = EngineConfig(warpConfig = warpConfigText)
        val result = engine.start(config)
        handleStartResult(id, engine, result)
    }

    private suspend fun connectWithTunFrontend(id: EngineId, engine: DesktopEngine) {
        val config = EngineConfig()
        val result = engine.start(config)

        when (result) {
            is EngineStartResult.Success -> {
                val frontend = TunFrontend()
                val tunStarted = frontend.start(result.port)

                if (tunStarted) {
                    activeEngine = engine
                    tunFrontend = frontend
                    _state.value = TunnelState.Connected(id, result.port)
                    _powerDiscState.value = PowerDiscState.Connected
                    startWatchdog(engine, id, frontend)
                    log.info("connected via ${id.displayName} + TUN frontend on port ${result.port}")
                } else {
                    log.warning("TUN frontend failed, falling back to proxy")
                    _effectiveVpnMode.value = VpnMode.PROXY
                    activeEngine = engine
                    _state.value = TunnelState.Connected(id, result.port)
                    _powerDiscState.value = PowerDiscState.Connected
                    runCatching { systemProxy.enable("127.0.0.1:${result.port}") }
                        .onFailure { log.warning("system proxy set failed: ${it.message}") }
                    startWatchdog(engine, id, null)
                    log.info("connected via ${id.displayName} proxy fallback on port ${result.port}")
                }
            }

            else -> handleFailedResult(id, result)
        }
    }

    private suspend fun connectProxy(id: EngineId, engine: DesktopEngine, settings: SettingsModel) {
        val config = if (id == EngineId.SINGBOX) {
            val json = SingboxDesktopConfigResolver.resolve(settings, VpnMode.PROXY)
                .getOrElse {
                    handleFailedResult(id, EngineStartResult.Failed(it.message ?: "Sing-box config is invalid"))
                    return
                }
            EngineConfig(singboxJson = json)
        } else {
            EngineConfig()
        }
        val result = engine.start(config)

        when (result) {
            is EngineStartResult.Success -> {
                activeEngine = engine
                val port = result.port
                _state.value = TunnelState.Connected(id, port)
                _powerDiscState.value = PowerDiscState.Connected

                runCatching { systemProxy.enable("127.0.0.1:$port") }
                    .onFailure { log.warning("system proxy set failed: ${it.message}") }

                startWatchdog(engine, id, null)
                log.info("connected via ${id.displayName} proxy on port $port")
            }

            else -> handleFailedResult(id, result)
        }
    }

    private fun handleStartResult(id: EngineId, engine: DesktopEngine, result: EngineStartResult) {
        when (result) {
            is EngineStartResult.Success -> {
                activeEngine = engine
                _state.value = TunnelState.Connected(id, result.port)
                _powerDiscState.value = PowerDiscState.Connected
                startWatchdog(engine, id, null)
                log.info("connected via ${id.displayName} TUN on port ${result.port}")
            }

            else -> handleFailedResult(id, result)
        }
    }

    private fun handleFailedResult(id: EngineId, result: EngineStartResult) {
        val reason = when (result) {
            is EngineStartResult.BinaryMissing -> "${id.displayName}: ${result.binaryName} not found"
            is EngineStartResult.PlatformUnavailable -> result.reason
            is EngineStartResult.Failed -> result.reason
            is EngineStartResult.Success -> return
        }
        _state.value = TunnelState.Failed(id, reason)
        _powerDiscState.value = PowerDiscState.Off
        log.warning("start failed: $reason")
    }

    fun disconnect() {
        val engine = activeEngine
        val frontend = tunFrontend
        _state.value = TunnelState.Disconnecting
        _powerDiscState.value = PowerDiscState.Off

        scope.launch {
            watchdogJob?.cancel()
            watchdogJob = null

            runCatching { systemProxy.disable() }
                .onFailure { log.warning("system proxy disable failed: ${it.message}") }

            frontend?.let {
                runCatching { it.stop() }
                    .onFailure { log.warning("TUN frontend stop failed: ${it.message}") }
            }
            tunFrontend = null

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

    fun switchEngine(
        newEngineId: EngineId,
        vpnMode: VpnMode = VpnMode.TUN,
        settings: SettingsModel = SettingsModel.DEFAULT,
    ) {
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
            connect(newEngineId, vpnMode, settings)
            _switching.value = null
        }
    }

    private fun resolveVpnMode(engineId: EngineId, requested: VpnMode): VpnMode {
        if (requested == VpnMode.PROXY) return VpnMode.PROXY

        if (engineId == EngineId.WARP) return VpnMode.TUN

        if (!platformDetector.canUseTun()) {
            log.info(
                "TUN not available (admin=${platformDetector.isAdmin()}" +
                    ", wintun=${platformDetector.hasWintun()}), using proxy",
            )
            return VpnMode.PROXY
        }

        return VpnMode.TUN
    }

    private fun startWatchdog(engine: DesktopEngine, engineId: EngineId, frontend: TunFrontend?) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val engineDead = !engine.isRunning()
                val frontendDead = frontend != null && !frontend.isRunning()

                if (engineDead || frontendDead) {
                    val what = if (engineDead) "engine" else "TUN frontend"
                    log.warning("watchdog: $what ${engineId.displayName} died unexpectedly")

                    frontend?.let { runCatching { it.stop() } }
                    tunFrontend = null
                    runCatching { systemProxy.disable() }
                    activeEngine = null
                    _state.value = TunnelState.Failed(engineId, "Process terminated unexpectedly")
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

interface PlatformDetectorPort {
    fun isAdmin(): Boolean
    fun hasWintun(): Boolean
    fun canUseTun(): Boolean
}

object DefaultPlatformDetectorPort : PlatformDetectorPort {
    override fun isAdmin() = PlatformDetector.isAdmin()
    override fun hasWintun() = PlatformDetector.hasWintun()
    override fun canUseTun() = PlatformDetector.canUseTun()
}
