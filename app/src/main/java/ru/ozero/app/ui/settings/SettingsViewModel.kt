package ru.ozero.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        repository.settings
            .map<_, SettingsUiState> { SettingsUiState.Content(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SettingsUiState.Loading,
            )

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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
