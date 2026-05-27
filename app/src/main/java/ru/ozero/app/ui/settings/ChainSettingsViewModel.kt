package ru.ozero.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.settings.ChainStepConfig
import ru.ozero.enginescore.settings.SettingsRepository
import javax.inject.Inject

data class ChainUiState(
    val steps: List<ChainStepConfig> = emptyList(),
    val availableEngines: List<ChainableEngine> = emptyList(),
)

data class ChainableEngine(
    val id: EngineId,
    val displayName: String,
    val capabilities: EngineCapabilities,
)

@HiltViewModel
class ChainSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    engines: Set<@JvmSuppressWildcards EnginePlugin>,
) : ViewModel() {

    private val chainableEngines: List<ChainableEngine> = engines
        .filter { !it.id.isStub }
        .map { ChainableEngine(it.id, it.id.displayName, it.capabilities) }
        .sortedBy { it.displayName }

    private val stepsFlow = MutableStateFlow<List<ChainStepConfig>>(emptyList())

    val state: StateFlow<ChainUiState> = combine(
        stepsFlow,
        MutableStateFlow(chainableEngines),
    ) { steps, available ->
        ChainUiState(steps = steps, availableEngines = available)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChainUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { model ->
                stepsFlow.value = model.proxyChain
            }
        }
    }

    fun addStep(engineId: EngineId) {
        val current = stepsFlow.value.toMutableList()
        current.add(ChainStepConfig(engineId))
        stepsFlow.value = current
        persist(current)
    }

    fun removeStep(index: Int) {
        val current = stepsFlow.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            stepsFlow.value = current
            persist(current)
        }
    }

    fun clearChain() {
        stepsFlow.value = emptyList()
        persist(emptyList())
    }

    private fun persist(chain: List<ChainStepConfig>) {
        viewModelScope.launch { settingsRepository.setProxyChain(chain) }
    }
}
