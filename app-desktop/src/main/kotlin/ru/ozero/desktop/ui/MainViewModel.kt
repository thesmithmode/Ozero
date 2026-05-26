package ru.ozero.desktop.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val stagnant: StateFlow<Boolean> = MutableStateFlow(false)
    val killswitchActive: StateFlow<Boolean> = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = MutableStateFlow(false)
    val speedHistory: StateFlow<List<SpeedSample>> = MutableStateFlow(emptyList())
    val powerDiscState: StateFlow<PowerDiscState> = vpnManager.powerDiscState

    val appMode: StateFlow<AppMode> = settingsStore.settings
        .map { it.appMode }
        .stateIn(scope, SharingStarted.Eagerly, AppMode.EXPERT)

    val manualEngine: StateFlow<EngineId?> = settingsStore.settings
        .map { it.manualEngine }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val engineAutoPriority: StateFlow<List<EngineId>> = settingsStore.settings
        .map { it.engineAutoPriority }
        .stateIn(scope, SharingStarted.Eagerly, SettingsModel.DEFAULT_ENGINE_AUTO_PRIORITY)

    fun onConnectClick() {
        vpnManager.toggle()
    }

    fun onManualEngineSelect(engine: EngineId?) {
        settingsStore.update { copy(manualEngine = engine) }
    }

    fun onAppModeSelect(mode: AppMode) {
        settingsStore.update { copy(appMode = mode) }
    }
}
