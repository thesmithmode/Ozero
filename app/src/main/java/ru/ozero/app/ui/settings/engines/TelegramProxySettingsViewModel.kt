package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.enginetelegram.TelegramConfigStore
import ru.ozero.enginetelegram.TelegramProxyConfig
import ru.ozero.enginetelegram.TelegramProxyService
import ru.ozero.enginetelegram.TelegramProxyState
import javax.inject.Inject

sealed interface TelegramProxyUiState {
    data object Loading : TelegramProxyUiState
    data class Content(
        val enabled: Boolean,
        val port: String,
        val domain: String,
        val secret: String,
        val proxyState: TelegramProxyState,
        val generatingSecret: Boolean = false,
        val generateError: Boolean = false,
    ) : TelegramProxyUiState {
        val tgLink: String?
            get() = if (secret.isNotBlank()) {
                "tg://proxy?server=127.0.0.1&port=$port&secret=$secret"
            } else {
                null
            }
    }
}

@HiltViewModel
class TelegramProxySettingsViewModel @Inject constructor(
    private val configStore: TelegramConfigStore,
    private val proxyService: TelegramProxyService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TelegramProxyUiState>(TelegramProxyUiState.Loading)
    val uiState: StateFlow<TelegramProxyUiState> = _uiState.asStateFlow()

    init {
        configStore.config()
            .onEach { config ->
                val previous = _uiState.value as? TelegramProxyUiState.Content
                _uiState.value = TelegramProxyUiState.Content(
                    enabled = config.enabled,
                    port = previous?.port ?: config.port.toString(),
                    domain = previous?.domain ?: config.domain,
                    secret = config.secret,
                    proxyState = previous?.proxyState ?: TelegramProxyState.Idle,
                    generatingSecret = previous?.generatingSecret ?: false,
                )
            }
            .launchIn(viewModelScope)

        proxyService.state
            .onEach { proxyState ->
                val s = _uiState.value as? TelegramProxyUiState.Content ?: return@onEach
                _uiState.value = s.copy(proxyState = proxyState)
            }
            .launchIn(viewModelScope)
    }

    fun onEnabledToggle(value: Boolean) {
        viewModelScope.launch { configStore.setEnabled(value) }
    }

    fun onPortChange(value: String) {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        _uiState.value = s.copy(port = value)
    }

    fun onDomainChange(value: String) {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        _uiState.value = s.copy(domain = value)
    }

    fun onSavePort() {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        val port = s.port.toIntOrNull() ?: return
        viewModelScope.launch { configStore.setPort(port) }
    }

    fun onSaveDomain() {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        viewModelScope.launch { configStore.setDomain(s.domain) }
    }

    fun onRegenerateSecret() {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        if (s.generatingSecret) return
        _uiState.value = s.copy(generatingSecret = true, generateError = false)
        viewModelScope.launch {
            val result = proxyService.generateAndSaveSecret(s.domain.ifBlank { TelegramProxyConfig.DEFAULT_DOMAIN })
            val current = _uiState.value as? TelegramProxyUiState.Content ?: return@launch
            _uiState.value = current.copy(generatingSecret = false, generateError = result == null)
        }
    }

    fun onDismissGenerateError() {
        val s = _uiState.value as? TelegramProxyUiState.Content ?: return
        _uiState.value = s.copy(generateError = false)
    }
}
