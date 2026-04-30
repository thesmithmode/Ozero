package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.app.di.AutoStrategyPickerFactory
import ru.ozero.enginebyedpi.strategy.PickResult
import ru.ozero.enginebyedpi.strategy.StrategyScore
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.EngineConfig
import javax.inject.Inject

sealed interface AutoTestUiState {
    data object Idle : AutoTestUiState
    data class Running(val current: Int, val total: Int, val lastCommand: String?) : AutoTestUiState
    data class Done(val ranked: List<StrategyScore>) : AutoTestUiState
    data class Failed(val reason: String) : AutoTestUiState
}

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
    private val pickerFactory: AutoStrategyPickerFactory,
) : ViewModel() {

    private val defaultArgs = EngineConfig.ByeDpi().args

    private val _uiState = MutableStateFlow<ByeDpiSettingsUiState>(ByeDpiSettingsUiState.Loading)
    val uiState: StateFlow<ByeDpiSettingsUiState> = _uiState.asStateFlow()

    private val _autoTestState = MutableStateFlow<AutoTestUiState>(AutoTestUiState.Idle)
    val autoTestState: StateFlow<AutoTestUiState> = _autoTestState.asStateFlow()

    private var autoTestJob: Job? = null

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

    fun onStartAutoTest() {
        if (_autoTestState.value is AutoTestUiState.Running) return
        _autoTestState.value = AutoTestUiState.Running(current = 0, total = 0, lastCommand = null)
        autoTestJob = viewModelScope.launch {
            val picker = runCatching { pickerFactory.create() }.getOrElse {
                _autoTestState.value = AutoTestUiState.Failed(it.message ?: "factory threw")
                return@launch
            }
            val result = runCatching {
                picker.pickBest { current, total, score ->
                    _autoTestState.value = AutoTestUiState.Running(
                        current = current,
                        total = total,
                        lastCommand = score?.strategy?.command,
                    )
                }
            }.getOrElse {
                _autoTestState.value = AutoTestUiState.Failed(it.message ?: "auto-test threw")
                return@launch
            }
            _autoTestState.value = when (result) {
                is PickResult.Success -> AutoTestUiState.Done(result.ranked)
                is PickResult.Failed -> AutoTestUiState.Failed(result.reason)
                PickResult.Cancelled -> AutoTestUiState.Idle
            }
        }
    }

    fun onCancelAutoTest() {
        autoTestJob?.cancel()
        autoTestJob = null
        _autoTestState.value = AutoTestUiState.Idle
    }

    fun onApplyAutoTestStrategy(strategy: StrategyScore) {
        viewModelScope.launch {
            repository.setByedpiWinningArgs(strategy.strategy.command)
            val state = _uiState.value as? ByeDpiSettingsUiState.Content
            if (state != null) {
                _uiState.value = state.copy(args = strategy.strategy.command)
            }
            _autoTestState.value = AutoTestUiState.Idle
        }
    }
}
