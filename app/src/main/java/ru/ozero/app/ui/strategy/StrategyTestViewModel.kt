package ru.ozero.app.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginebyedpi.strategy.SocksProbeClient
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import javax.inject.Inject

sealed interface StrategyTestError {
    data object VpnRunning : StrategyTestError
    data object NoSites : StrategyTestError
}

@HiltViewModel
class StrategyTestViewModel @Inject constructor(
    private val repository: ru.ozero.enginescore.settings.SettingsRepository,
    private val assetSource: StrategyAssetSource,
    private val resultsStore: StrategyResultsStore,
    private val byeDpiEngine: EnginePlugin,
    private val probeFactory: StrategyProbeClientFactory,
    private val tunnelController: TunnelController,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _strategies = MutableStateFlow<List<StrategyResult>>(emptyList())
    val strategies: StateFlow<List<StrategyResult>> = _strategies.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _errorMessage = MutableStateFlow<StrategyTestError?>(null)
    val errorMessage: StateFlow<StrategyTestError?> = _errorMessage.asStateFlow()

    private var testJob: Job? = null

    init {
        viewModelScope.launch {
            val previous = withContext(ioDispatcher) {
                runCatching { resultsStore.load() }.getOrDefault(emptyList())
            }
            val commands = withContext(ioDispatcher) {
                runCatching { assetSource.loadStrategies() }.getOrDefault(emptyList())
            }
            val byCmd = previous.associateBy { it.command }
            _strategies.value = commands.map { cmd ->
                byCmd[cmd] ?: StrategyResult(command = cmd)
            }
        }
    }

    fun onErrorDismiss() {
        _errorMessage.value = null
    }

    fun onStart() {
        if (_isRunning.value) return
        if (tunnelController.state.value !is TunnelState.Idle) {
            _errorMessage.value = StrategyTestError.VpnRunning
            return
        }
        _isRunning.value = true
        testJob = viewModelScope.launch {
            val sites = withContext(ioDispatcher) {
                runCatching { assetSource.loadSites() }.getOrDefault(emptyList())
            }
            if (sites.isEmpty()) {
                _errorMessage.value = StrategyTestError.NoSites
                _isRunning.value = false
                return@launch
            }
            _strategies.value = _strategies.value.map {
                it.copy(
                    successCount = 0,
                    totalRequests = sites.size,
                    currentProgress = 0,
                    isCompleted = false,
                )
            }
            try {
                runLoop(sites)
            } finally {
                _strategies.value = _strategies.value.sortedByDescending { it.successPercentage }
                withContext(ioDispatcher) {
                    runCatching { resultsStore.save(_strategies.value) }
                }
                _isRunning.value = false
            }
        }
    }

    fun onStop() {
        testJob?.cancel()
        testJob = null
        _isRunning.value = false
    }

    fun onApply(command: String) {
        viewModelScope.launch {
            runCatching { repository.setByedpiWinningArgs(command) }
                .onFailure { PersistentLoggers.warn(TAG, "onApply failed: ${it.message}") }
        }
    }

    private suspend fun runLoop(sites: List<String>) = coroutineScope {
        val current = _strategies.value
        for (index in current.indices) {
            if (!currentCoroutineContext().isActive) return@coroutineScope
            val strategy = _strategies.value[index]
            val started = withTimeoutOrNull(START_TIMEOUT_MS) {
                byeDpiEngine.start(
                    config = EngineConfig.ByeDpi(args = strategy.command, socksPort = SOCKS_PORT),
                    upstream = Upstream.None,
                )
            }
            if (started !is StartResult.Success) {
                runCatching { byeDpiEngine.stop() }
                _strategies.value = _strategies.value.toMutableList().also { list ->
                    list[index] = list[index].copy(
                        currentProgress = sites.size,
                        successCount = 0,
                        isCompleted = true,
                    )
                }
                continue
            }
            val probe: SocksProbeClient = probeFactory.create(SOCKS_PORT)
            var successCount = 0
            for (site in sites) {
                if (!currentCoroutineContext().isActive) {
                    runCatching { byeDpiEngine.stop() }
                    return@coroutineScope
                }
                val result = probe.probe(site)
                if (result.success) successCount++
                _strategies.value = _strategies.value.toMutableList().also { list ->
                    val cur = list[index]
                    list[index] = cur.copy(
                        currentProgress = cur.currentProgress + 1,
                        successCount = successCount,
                    )
                }
            }
            runCatching { byeDpiEngine.stop() }
            _strategies.value = _strategies.value.toMutableList().also { list ->
                list[index] = list[index].copy(isCompleted = true)
            }
        }
    }

    private companion object {
        const val TAG: String = "StrategyTestVM"
        const val SOCKS_PORT: Int = 1080
        const val START_TIMEOUT_MS: Long = 6_000L
    }
}
