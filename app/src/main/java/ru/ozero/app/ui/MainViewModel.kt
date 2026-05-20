package ru.ozero.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.commonnet.IpInfo
import ru.ozero.commonnet.IpInfoProvider
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.SwitchingTransition
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.IpProbeRoute
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

sealed class IpInfoState {
    data object Idle : IpInfoState()
    data object Loading : IpInfoState()
    data object AutoSelected : IpInfoState()
    data class Loaded(val info: IpInfo) : IpInfoState()
    data class Error(val message: String) : IpInfoState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tunnelController: TunnelController,
    private val healthMonitor: HealthMonitor,
    private val settingsRepository: SettingsRepository,
    private val urnetworkBridge: UrnetworkSdkBridge,
    private val ipInfoProvider: IpInfoProvider,
    private val enginePlugins: Set<@JvmSuppressWildcards EnginePlugin>,
) : ViewModel() {

    val state: StateFlow<TunnelState> =
        tunnelController.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TunnelState.Idle,
        )

    val stats: StateFlow<TunnelStats?> =
        tunnelController.stats.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val stagnant: StateFlow<Boolean> =
        tunnelController.stagnant.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val killswitchActive: StateFlow<Boolean> =
        tunnelController.killswitchActive.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val switching: StateFlow<SwitchingTransition?> =
        tunnelController.switching.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    val isReconnecting: StateFlow<Boolean> = tunnelController.state
        .runningFold<TunnelState, Pair<Boolean, TunnelState>>(false to TunnelState.Idle) { acc, cur ->
            val (reconn, prev) = acc
            val next = when (cur) {
                is TunnelState.Idle,
                is TunnelState.Disconnecting,
                is TunnelState.Connected,
                -> false
                else -> prev is TunnelState.Connected || reconn
            }
            next to cur
        }
        .map { it.first }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val healthStatus: StateFlow<HealthMonitor.Status> =
        healthMonitor.status.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HealthMonitor.Status.UNKNOWN,
        )

    val appMode: StateFlow<AppMode> =
        settingsRepository.settings
            .map { it.appMode }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppMode.SIMPLE,
            )

    val manualEngine: StateFlow<EngineId?> =
        settingsRepository.settings
            .map { it.manualEngine }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    private val _speedHistory = MutableStateFlow<List<SpeedSample>>(emptyList())
    val speedHistory: StateFlow<List<SpeedSample>> = _speedHistory.asStateFlow()

    private val _ipInfo = MutableStateFlow<IpInfoState>(IpInfoState.Idle)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val urnetworkLocationOverride: StateFlow<IpInfoState?> =
        tunnelController.state
            .map { (it as? TunnelState.Connected)?.engineId == EngineId.URNETWORK }
            .distinctUntilChanged()
            .flatMapLatest { isUrnetwork ->
                if (!isUrnetwork) {
                    flowOf<IpInfoState?>(null)
                } else {
                    flow<IpInfoState?> {
                        while (true) {
                            delay(URNETWORK_LOCATION_POLL_MS)
                            val r = resolveOnce(EngineId.URNETWORK, 0)
                            if (r is IpInfoState.Loaded || r is IpInfoState.AutoSelected) emit(r)
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(0),
                initialValue = null,
            )

    val ipInfo: StateFlow<IpInfoState> = combine(
        _ipInfo,
        urnetworkLocationOverride,
    ) { base, override -> override ?: base }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(0),
            initialValue = IpInfoState.Idle,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentEngineDegraded: StateFlow<Boolean> = tunnelController.state
        .flatMapLatest { s ->
            if (s is TunnelState.Connected && s.engineId in DEGRADATION_TRACKED_ENGINES) {
                val plugin = enginePlugins.firstOrNull { it.id == s.engineId }
                plugin?.stats()?.map { it.activeConnections == 0 } ?: flowOf(false)
            } else {
                flowOf(false)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    val urnetworkPeerCount: StateFlow<Int> = flow {
        while (true) {
            val s = tunnelController.state.value
            val active = s is TunnelState.Connected &&
                s.engineId == EngineId.URNETWORK &&
                runCatching { urnetworkBridge.isRunning() }.getOrDefault(false)
            emit(if (active) runCatching { urnetworkBridge.peerCount() }.getOrDefault(0) else 0)
            delay(URNETWORK_PEER_POLL_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(URNETWORK_PEER_POLL_KEEP_MS),
        initialValue = 0,
    )

    val urnetworkPeerSearchSeconds: StateFlow<Int> = flow {
        var seconds = 0
        var graceTicks = 0
        while (true) {
            val s = tunnelController.state.value
            val active = s is TunnelState.Connected &&
                s.engineId == EngineId.URNETWORK &&
                runCatching { urnetworkBridge.isRunning() }.getOrDefault(false)
            val peers = if (active) runCatching { urnetworkBridge.peerCount() }.getOrDefault(0) else 0
            when {
                !active -> {
                    seconds = 0
                    graceTicks = 0
                }
                peers > 0 -> {
                    seconds = 0
                }
                else -> {
                    graceTicks++
                    if (graceTicks > URNETWORK_STARTUP_GRACE_TICKS) seconds++ else seconds = 0
                }
            }
            emit(seconds)
            delay(URNETWORK_SEARCH_TICK_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(URNETWORK_PEER_POLL_KEEP_MS),
        initialValue = 0,
    )

    init {
        viewModelScope.launch {
            var lastRecordMs = 0L
            tunnelController.stats.collect { s ->
                if (s != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastRecordMs >= SPEED_SAMPLE_INTERVAL_MS) {
                        lastRecordMs = now
                        val prev = _speedHistory.value
                        _speedHistory.value = (prev + SpeedSample(now, s.bpsIn.toFloat(), s.bpsOut.toFloat()))
                            .takeLast(MAX_SPEED_HISTORY_POINTS)
                    }
                } else {
                    val switchingNow = tunnelController.switching.value != null
                    if (!switchingNow && _speedHistory.value.isNotEmpty()) {
                        _speedHistory.value = emptyList()
                    }
                }
            }
        }
        viewModelScope.launch {
            var lastSessionKey: String? = null
            tunnelController.state.collectLatest { s ->
                if (s is TunnelState.Connected) {
                    val key = "${s.engineId}:${s.socksPort}"
                    if (key != lastSessionKey) {
                        lastSessionKey = key
                        _ipInfo.value = IpInfoState.Loading
                        Log.i(
                            IP_TAG,
                            "warmup begin engine=${s.engineId} port=${s.socksPort} delay=${IP_INFO_WARMUP_MS}ms",
                        )
                        delay(IP_INFO_WARMUP_MS)
                        Log.i(IP_TAG, "warmup done — resolve IP")
                        val result = resolveIpInfoWithRetry(s.engineId, s.socksPort)
                        when (result) {
                            is IpInfoState.Loaded ->
                                Log.i(IP_TAG, "resolve ok ip='${result.info.ip}' country=${result.info.country}")
                            is IpInfoState.Error ->
                                PersistentLoggers.warn(IP_TAG, "resolve error: ${result.message}")
                            else -> Unit
                        }
                        _ipInfo.value = result
                    }
                } else {
                    lastSessionKey = null
                    if (_ipInfo.value !is IpInfoState.Idle) _ipInfo.value = IpInfoState.Idle
                }
            }
        }
    }

    fun refreshIpInfo() {
        viewModelScope.launch {
            val s = tunnelController.state.value
            _ipInfo.value = IpInfoState.Loading
            _ipInfo.value = if (s is TunnelState.Connected) {
                resolveIpInfoWithRetry(s.engineId, s.socksPort)
            } else {
                resolveOnce(engineId = null, socksPort = 0)
            }
        }
    }

    private suspend fun resolveIpInfoWithRetry(engineId: EngineId, socksPort: Int): IpInfoState {
        var lastError: String? = null
        repeat(IP_INFO_RETRY_ATTEMPTS) { attempt ->
            when (val s = resolveOnce(engineId, socksPort)) {
                is IpInfoState.Loaded -> return s
                is IpInfoState.AutoSelected -> return s
                is IpInfoState.Error -> lastError = s.message
                else -> Unit
            }
            if (attempt < IP_INFO_RETRY_ATTEMPTS - 1) {
                delay(IP_INFO_RETRY_DELAY_MS)
            }
        }
        return IpInfoState.Error(lastError ?: "unknown")
    }

    private suspend fun resolveOnce(engineId: EngineId?, socksPort: Int): IpInfoState {
        val plugin = engineId?.let { id -> enginePlugins.firstOrNull { it.id == id } }
        val route = runCatching { plugin?.ipProbeRoute(socksPort) ?: IpProbeRoute.Default }
            .getOrElse { return IpInfoState.Error(it.message ?: it.javaClass.simpleName) }
        return when (route) {
            IpProbeRoute.Default -> ipInfoProvider.fetch().toState()
            IpProbeRoute.AutoSelected -> IpInfoState.AutoSelected
            is IpProbeRoute.Socks -> ipInfoProvider.fetchVia(route.host, route.port).toState()
            is IpProbeRoute.StaticLocation -> IpInfoState.Loaded(
                IpInfo(
                    ip = "",
                    country = route.country,
                    countryCode = route.countryCode,
                    city = null,
                    fetchedAtMs = System.currentTimeMillis(),
                ),
            )
            is IpProbeRoute.Unavailable -> IpInfoState.Error(route.reason)
        }
    }

    private fun Result<IpInfo>.toState(): IpInfoState = fold(
        onSuccess = { IpInfoState.Loaded(it) },
        onFailure = {
            if (it is kotlinx.coroutines.CancellationException) throw it
            IpInfoState.Error(it.message ?: it.javaClass.simpleName)
        },
    )

    fun onConnectClick() = Unit

    fun onVpnPermissionGranted() = Unit

    fun onVpnPermissionDenied() {
        val current = state.value
        if (current is TunnelState.Probing || current is TunnelState.Connecting) {
            tunnelController.onEngineDied(EngineId.BYEDPI, "VPN permission denied")
        }
    }

    fun onManualEngineSelect(engine: EngineId?) {
        val current = tunnelController.state.value
        if (current is TunnelState.Connected && current.engineId != engine) {
            tunnelController.onSwitchingStarted(from = current.engineId, to = engine)
        }
        viewModelScope.launch { settingsRepository.setManualEngine(engine) }
    }

    private companion object {
        const val IP_TAG = "MainViewModel.ip"
        const val MAX_SPEED_HISTORY_POINTS = 3_600
        const val SPEED_SAMPLE_INTERVAL_MS = 1_000L
        const val URNETWORK_PEER_POLL_MS = 2_000L
        const val URNETWORK_PEER_POLL_KEEP_MS = 5_000L
        const val URNETWORK_SEARCH_TICK_MS = 1_000L
        const val URNETWORK_STARTUP_GRACE_TICKS = 10
        const val IP_INFO_WARMUP_MS = 500L
        const val IP_INFO_RETRY_ATTEMPTS = 3
        const val IP_INFO_RETRY_DELAY_MS = 1_500L
        const val URNETWORK_LOCATION_POLL_MS = 4_000L
        val DEGRADATION_TRACKED_ENGINES = setOf(EngineId.WARP, EngineId.URNETWORK)
    }
}
