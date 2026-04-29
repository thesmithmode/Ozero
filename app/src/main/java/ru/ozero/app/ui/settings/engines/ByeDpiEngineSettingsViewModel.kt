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
import ru.ozero.app.settings.SettingsRepository
import ru.ozero.coreapi.EngineConfig
import javax.inject.Inject

sealed interface ByeDpiSettingsUiState {
    data object Loading : ByeDpiSettingsUiState

    data class Content(
        val args: String,
        val savedArgs: String?,
        val defaultArgs: String,
    ) : ByeDpiSettingsUiState {
        val dirty: Boolean get() = args != (savedArgs ?: defaultArgs)
        val usingDefault: Boolean get() = savedArgs.isNullOrBlank()
    }
}

@HiltViewModel
class ByeDpiEngineSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val defaultArgs = EngineConfig.ByeDpi().args

    private val _uiState = MutableStateFlow<ByeDpiSettingsUiState>(ByeDpiSettingsUiState.Loading)
    val uiState: StateFlow<ByeDpiSettingsUiState> = _uiState.asStateFlow()

    init {
        repository.settings
            .onEach { model ->
                val saved = model.byedpiWinningArgs
                val current = (_uiState.value as? ByeDpiSettingsUiState.Content)?.args
                _uiState.value = ByeDpiSettingsUiState.Content(
                    args = current ?: saved ?: defaultArgs,
                    savedArgs = saved,
                    defaultArgs = defaultArgs,
                )
            }
            .launchIn(viewModelScope)
    }

    fun onArgsChange(text: String) {
        val state = _uiState.value as? ByeDpiSettingsUiState.Content ?: return
        _uiState.value = state.copy(args = text)
    }

    fun onSave() {
        val state = _uiState.value as? ByeDpiSettingsUiState.Content ?: return
        viewModelScope.launch {
            val toSave = state.args.takeIf { it.isNotBlank() && it != defaultArgs }
            repository.setByedpiWinningArgs(toSave)
        }
    }

    fun onResetToDefault() {
        viewModelScope.launch {
            repository.setByedpiWinningArgs(null)
            val state = _uiState.value as? ByeDpiSettingsUiState.Content
            if (state != null) {
                _uiState.value = state.copy(args = defaultArgs)
            }
        }
    }
}
