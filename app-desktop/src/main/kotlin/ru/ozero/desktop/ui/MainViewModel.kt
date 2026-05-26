package ru.ozero.desktop.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.model.SettingsModel
import ru.ozero.desktop.model.SpeedSample
import ru.ozero.desktop.model.SwitchingTransition
import ru.ozero.desktop.model.TunnelState
import ru.ozero.desktop.model.TunnelStats
import ru.ozero.desktop.ui.components.PowerDiscState
import ru.ozero.desktop.vpn.DesktopSettingsStore
import ru.ozero.desktop.vpn.DesktopVpnManager

class MainViewModel(
    private val scope: CoroutineScope,
    private val vpnManager: DesktopVpnManager,
    private val settingsStore: DesktopSettingsStore,
) {
    val state: StateFlow<TunnelState> = vpnManager.state
    val stats: StateFlow<TunnelStats?> = vpnManager.stats
    val switching: StateFlow<SwitchingTransition?> = vpnManager.switching

    private val _stagnant = MutableStateFlow(false)
    val stagnant: StateFlow<Boolean> = _stagnant.asStateFlow()

    private val _killswitchActive = MutableStateFlow(false)
    val killswitchActive: StateFlow<Boolean> = _killswitchActive.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    val appMode: StateFlow<AppMode> = settingsStore.settings
        .map { it.appMode }
        .stateIn(scope, SharingStarted.Eagerly, AppMode.EXPERT)

    val manualEngine: StateFlow<EngineId?> = settingsStore.settings
        .map { it.manualEngine }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val engineAutoPriority: StateFlow<List<EngineId>> = settingsStore.settings
        .map { it.engineAutoPriority }
        .stateIn(scope, SharingStarted.Eagerly, SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY)

    private val _speedHistory = MutableStateFlow<List<SpeedSample>>(emptyList())
    val speedHistory: StateFlow<List<SpeedSample>> = _speedHistory.asStateFlow()

    private val _ipInfo = MutableStateFlow<IpInfoState>(IpInfoState.Idle)
    val ipInfo: StateFlow<IpInfoState> = _ipInfo.asStateFlow()

    val powerDiscState: StateFlow<PowerDiscState> = vpnManager.powerDiscState

    init {
        scope.launch {
            var lastRecordMs = 0L
            vpnManager.stats.collect { s ->
                if (s != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastRecordMs >= 1_000L) {
                        lastRecordMs = now
                        val prev = _speedHistory.value
                        _speedHistory.value = (prev + SpeedSample(now, s.bpsIn.toFloat(), s.bpsOut.toFloat()))
                            .takeLast(3_600)
                    }
                }
            }
        }
    }

    fun onConnectClick() {
        scope.launch { vpnManager.toggle() }
    }

    fun onManualEngineSelect(engine: EngineId?) {
        settingsStore.update { copy(manualEngine = engine) }
    }

    fun onAppModeSelect(mode: AppMode) {
        settingsStore.update { copy(appMode = mode) }
    }

    fun refreshIpInfo() {}
}

sealed class IpInfoState {
    data object Idle : IpInfoState()
    data object Loading : IpInfoState()
    data class Loaded(val ip: String, val country: String?, val countryCode: String?, val city: String?) : IpInfoState()
    data class Error(val message: String) : IpInfoState()
}
