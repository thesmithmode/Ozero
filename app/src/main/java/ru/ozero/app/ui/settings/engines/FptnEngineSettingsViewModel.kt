package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginefptn.FptnBypassMethod
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.FptnConfigStore
import ru.ozero.enginefptn.FptnServer
import ru.ozero.enginefptn.FptnToken
import ru.ozero.enginefptn.FptnTokenData
import javax.inject.Inject

data class FptnSettingsUiState(
    val savedToken: String = "",
    val tokenData: FptnTokenData? = null,
    val tokenInvalid: Boolean = false,
    val selectedServerName: String? = null,
    val autoSelect: Boolean = true,
    val bypassMethod: FptnBypassMethod = FptnBypassMethod.DEFAULT,
    val sniDomain: String = FptnConfig.DEFAULT_SNI_DOMAIN,
    val reconnectOnNetworkChange: Boolean = true,
    val reconnectOnIpChange: Boolean = false,
    val maxReconnectAttempts: Int = 5,
    val reconnectPauseSeconds: Int = 2,
    val resetServerOnDisconnect: Boolean = true,
) {
    val servers: List<FptnServer> get() = tokenData?.servers ?: emptyList()
    val hasToken: Boolean get() = tokenData != null
}

@HiltViewModel
class FptnEngineSettingsViewModel @Inject constructor(
    private val configStore: FptnConfigStore,
) : ViewModel() {

    val uiState: StateFlow<FptnSettingsUiState> = configStore.config().map { cfg ->
        val tokenData = if (cfg.token.isNotBlank()) FptnToken.parse(cfg.token) else null
        FptnSettingsUiState(
            savedToken = cfg.token,
            tokenData = tokenData,
            tokenInvalid = cfg.token.isNotBlank() && tokenData == null,
            selectedServerName = cfg.selectedServerName,
            autoSelect = cfg.autoSelect,
            bypassMethod = FptnBypassMethod.fromStrategyName(cfg.bypassMethod),
            sniDomain = cfg.sniDomain,
            reconnectOnNetworkChange = cfg.reconnectOnNetworkChange,
            reconnectOnIpChange = cfg.reconnectOnIpChange,
            maxReconnectAttempts = cfg.maxReconnectAttempts,
            reconnectPauseSeconds = cfg.reconnectPauseSeconds,
            resetServerOnDisconnect = cfg.resetServerOnDisconnect,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FptnSettingsUiState())

    fun onTokenSave(draft: String) {
        viewModelScope.launch {
            configStore.update { it.copy(token = draft.trim(), selectedServerName = null) }
        }
    }

    fun onServerSelect(name: String?) {
        viewModelScope.launch {
            configStore.update { it.copy(selectedServerName = name, autoSelect = name == null) }
        }
    }

    fun onAutoSelect() {
        viewModelScope.launch {
            configStore.update { it.copy(selectedServerName = null, autoSelect = true) }
        }
    }

    fun onBypassMethodChange(method: FptnBypassMethod) {
        viewModelScope.launch {
            configStore.update { it.copy(bypassMethod = method.strategyName) }
        }
    }

    fun onSniDomainChange(domain: String) {
        val trimmed = domain.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch { configStore.update { it.copy(sniDomain = trimmed) } }
        }
    }

    fun onReconnectNetworkChange(enabled: Boolean) {
        viewModelScope.launch { configStore.update { it.copy(reconnectOnNetworkChange = enabled) } }
    }

    fun onReconnectIpChange(enabled: Boolean) {
        viewModelScope.launch { configStore.update { it.copy(reconnectOnIpChange = enabled) } }
    }

    fun onMaxAttemptsChange(value: Int) {
        viewModelScope.launch { configStore.update { it.copy(maxReconnectAttempts = value) } }
    }

    fun onPauseSecondsChange(value: Int) {
        viewModelScope.launch { configStore.update { it.copy(reconnectPauseSeconds = value) } }
    }

    fun onResetServerChange(enabled: Boolean) {
        viewModelScope.launch { configStore.update { it.copy(resetServerOnDisconnect = enabled) } }
    }
}
