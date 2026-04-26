package ru.ozero.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import ru.ozero.enginetor.dynamicmod.InstallResult
import ru.ozero.enginetor.dynamicmod.SplitInstallClient
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val torClient: SplitInstallClient,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        repository.settings
            .map<_, SettingsUiState> { SettingsUiState.Content(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SettingsUiState.Loading,
            )

    private val _torInstall = MutableStateFlow<TorInstallUiState>(
        if (TOR_MODULE_NAME in torClient.installedModules) {
            TorInstallUiState.Installed
        } else {
            TorInstallUiState.NotInstalled
        },
    )
    val torInstall: StateFlow<TorInstallUiState> = _torInstall.asStateFlow()

    private var installJob: Job? = null

    fun onSplitModeChange(mode: SplitTunnelMode) {
        viewModelScope.launch { repository.setSplitMode(mode) }
    }

    fun onIpv6Toggle(enabled: Boolean) {
        viewModelScope.launch { repository.setIpv6Enabled(enabled) }
    }

    fun onAutoStartToggle(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoStart(enabled) }
    }

    fun onManualEngineSelect(engine: EngineId?) {
        viewModelScope.launch { repository.setManualEngine(engine) }
    }

    fun onInstallTor() {
        if (_torInstall.value is TorInstallUiState.Installed) return
        installJob?.cancel()
        installJob = viewModelScope.launch {
            torClient.requestInstall(TOR_MODULE_NAME).collect { result ->
                _torInstall.value = when (result) {
                    InstallResult.AlreadyInstalled, InstallResult.Installed ->
                        TorInstallUiState.Installed
                    is InstallResult.Installing ->
                        TorInstallUiState.Installing(percent = result.percent)
                    is InstallResult.Failed ->
                        TorInstallUiState.Failed(reason = result.reason)
                }
            }
        }
    }

    fun onCancelTor() {
        installJob?.cancel()
        installJob = null
        _torInstall.value = TorInstallUiState.NotInstalled
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TOR_MODULE_NAME = "dynamic_tor"
    }
}
