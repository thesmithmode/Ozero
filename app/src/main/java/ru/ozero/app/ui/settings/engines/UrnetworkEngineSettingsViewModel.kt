package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkDefaults
import javax.inject.Inject

sealed interface UrnetworkSettingsUiState {
    data object Loading : UrnetworkSettingsUiState

    data class Content(
        val currentWallet: String,
        val editedWallet: String,
        val isUsingPreset: Boolean,
        val consentGranted: Boolean,
        val errorMessage: String? = null,
    ) : UrnetworkSettingsUiState
}

@HiltViewModel
class UrnetworkEngineSettingsViewModel @Inject constructor(
    private val store: UrnetworkConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UrnetworkSettingsUiState>(UrnetworkSettingsUiState.Loading)
    val uiState: StateFlow<UrnetworkSettingsUiState> = _uiState.asStateFlow()

    init {
        combine(store.walletOverride(), store.consentGranted()) { override, consent ->
            override to consent
        }
            .onEach { (override, consent) ->
                val prev = _uiState.value as? UrnetworkSettingsUiState.Content
                _uiState.value = UrnetworkSettingsUiState.Content(
                    currentWallet = override.orEmpty(),
                    editedWallet = prev?.editedWallet ?: override.orEmpty(),
                    isUsingPreset = override == null,
                    consentGranted = consent,
                    errorMessage = prev?.errorMessage,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onWalletChange(text: String) {
        val s = _uiState.value as? UrnetworkSettingsUiState.Content ?: return
        _uiState.value = s.copy(editedWallet = text, errorMessage = null)
    }

    fun onSaveWallet(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == UrnetworkDefaults.PRESET_WALLET) {
            viewModelScope.launch { store.setWalletOverride(null) }
            return
        }
        if (!isValidSolanaAddress(trimmed)) {
            val s = _uiState.value as? UrnetworkSettingsUiState.Content ?: return
            _uiState.value = s.copy(errorMessage = ERROR_INVALID_ADDRESS)
            return
        }
        viewModelScope.launch { store.setWalletOverride(trimmed) }
    }

    fun onResetWallet() {
        viewModelScope.launch { store.setWalletOverride(null) }
    }

    fun onGrantConsent() {
        viewModelScope.launch { store.markConsentGranted() }
    }

    fun onRevokeConsent() {
        viewModelScope.launch { store.revokeConsent() }
    }

    private fun isValidSolanaAddress(s: String): Boolean = SOLANA_REGEX.matches(s)

    private companion object {
        val SOLANA_REGEX = Regex(UrnetworkDefaults.SOLANA_BASE58_REGEX)
        const val ERROR_INVALID_ADDRESS = "invalid"
    }
}
