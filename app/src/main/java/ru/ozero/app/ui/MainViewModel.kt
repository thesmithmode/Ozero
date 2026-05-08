package ru.ozero.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tunnelController: TunnelController,
    private val healthMonitor: HealthMonitor,
    private val settingsRepository: SettingsRepository,
    private val urnetworkBridge: UrnetworkSdkBridge,
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

    private val _speedHistory = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Float, Float>>> = _speedHistory.asStateFlow()

    val urnetworkPeerCount: StateFlow<Int> = flow {
        while (true) {
            val s = tunnelController.state.value
            val active = s is TunnelState.Connected && s.engineId == EngineId.URNETWORK
            emit(if (active) urnetworkBridge.peerCount() else 0)
            delay(URNETWORK_PEER_POLL_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(URNETWORK_PEER_POLL_KEEP_MS),
        initialValue = 0,
    )

    val urnetworkPeerSearchSeconds: StateFlow<Int> = flow {
        var seconds = 0
        while (true) {
            val s = tunnelController.state.value
            val active = s is TunnelState.Connected && s.engineId == EngineId.URNETWORK
            val peers = if (active) runCatching { urnetworkBridge.peerCount() }.getOrDefault(0) else 0
            seconds = when {
                !active -> 0
                peers > 0 -> 0
                else -> seconds + 1
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
            tunnelController.stats.collect { s ->
                if (s != null) {
                    val prev = _speedHistory.value
                    _speedHistory.value = (prev + Pair(s.bpsIn.toFloat(), s.bpsOut.toFloat()))
                        .takeLast(SPEED_HISTORY_SIZE)
                } else {
                    if (_speedHistory.value.isNotEmpty()) _speedHistory.value = emptyList()
                }
            }
        }
    }

    fun onConnectClick() = Unit

    fun onVpnPermissionGranted() = Unit

    fun onVpnPermissionDenied() {
        val current = state.value
        if (current is TunnelState.Probing || current is TunnelState.Connecting) {
            tunnelController.onEngineDied(EngineId.BYEDPI, "VPN permission denied")
        }
    }

    fun onManualEngineSelect(engine: EngineId?) {
        viewModelScope.launch { settingsRepository.setManualEngine(engine) }
    }

    private companion object {
        const val SPEED_HISTORY_SIZE = 60
        const val URNETWORK_PEER_POLL_MS = 2_000L
        const val URNETWORK_PEER_POLL_KEEP_MS = 5_000L
        const val URNETWORK_SEARCH_TICK_MS = 1_000L
    }
}
