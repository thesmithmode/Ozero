package ru.ozero.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tunnelController: TunnelController,
    private val healthMonitor: HealthMonitor,
    private val settingsRepository: SettingsRepository,
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

    init {
        viewModelScope.launch {
            while (true) {
                delay(SPEED_SAMPLE_INTERVAL_MS)
                val s = stats.value
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
        const val SPEED_SAMPLE_INTERVAL_MS = 100L
        const val SPEED_HISTORY_SIZE = 60
    }
}
