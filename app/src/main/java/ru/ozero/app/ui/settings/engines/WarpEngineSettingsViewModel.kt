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
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigStore
import javax.inject.Inject

data class WarpSettingsUiState(
    val currentConfig: WarpConfig? = null,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class WarpEngineSettingsViewModel @Inject constructor(
    private val store: WarpConfigStore,
    private val autoConfig: WarpAutoConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarpSettingsUiState())
    val uiState: StateFlow<WarpSettingsUiState> = _uiState.asStateFlow()

    private var autoTriggered: Boolean = false

    init {
        store.current()
            .onEach { cfg ->
                _uiState.value = _uiState.value.copy(currentConfig = cfg)
                if (cfg == null && !autoTriggered &&
                    !_uiState.value.isRegistering &&
                    _uiState.value.errorMessage == null
                ) {
                    autoTriggered = true
                    onGenerate()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onGenerate() {
        if (_uiState.value.isRegistering) return
        _uiState.value = _uiState.value.copy(isRegistering = true, errorMessage = null)
        viewModelScope.launch {
            val result = autoConfig.register()
            result.fold(
                onSuccess = { cfg ->
                    store.save(cfg)
                    _uiState.value = _uiState.value.copy(isRegistering = false, errorMessage = null)
                },
                onFailure = { t ->
                    _uiState.value = _uiState.value.copy(
                        isRegistering = false,
                        errorMessage = t.message ?: "register failed",
                    )
                },
            )
        }
    }

    fun onClear() {
        viewModelScope.launch {
            store.clear()
        }
    }
}
