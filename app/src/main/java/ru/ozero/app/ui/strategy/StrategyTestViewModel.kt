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

    private val _sitesText = MutableStateFlow("")
    val sitesText: StateFlow<String> = _sitesText.asStateFlow()

    private val _runSummary = MutableStateFlow("")
    val runSummary: StateFlow<String> = _runSummary.asStateFlow()

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
            }.sortedForUi()
            _sitesText.value = withContext(ioDispatcher) {
                runCatching { assetSource.loadSites().joinToString("\n") }.getOrDefault("")
            }
        }
    }

    fun onSitesTextChange(text: String) {
        _sitesText.value = text
    }

    private fun parseSites(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()

    private fun List<StrategyResult>.sortedForUi(): List<StrategyResult> =
        sortedWith(
            compareByDescending<StrategyResult> { it.successPercentage }
                .thenByDescending { it.successCount }
                .thenBy { if (it.avgDurationMs > 0) it.avgDurationMs else Long.MAX_VALUE }
                .thenBy { it.command },
        )

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
            val sites = parseSites(_sitesText.value)
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
                    avgDurationMs = 0L,
                    lastSite = null,
                    lastError = null,
                )
            }
            _runSummary.value = "0/${_strategies.value.size} strategies, ${sites.size} sites"
            try {
                runLoop(sites)
            } finally {
                _strategies.value = _strategies.value.sortedForUi()
                withContext(ioDispatcher) {
                    runCatching { resultsStore.save(_strategies.value) }
                }
                _runSummary.value = ""
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
            _runSummary.value = "Testing ${index + 1}/${_strategies.value.size}: ${strategy.command}"
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
                        lastError = started?.toString() ?: "start timeout",
                    )
                }
                continue
            }
            val probe: SocksProbeClient = probeFactory.create(SOCKS_PORT)
            var successCount = 0
            var totalDuration = 0L
            for (site in sites) {
                if (!currentCoroutineContext().isActive) {
                    runCatching { byeDpiEngine.stop() }
                    return@coroutineScope
                }
                val result = probe.probe(site)
                if (result.success) successCount++
                totalDuration += result.durationMs
                _strategies.value = _strategies.value.toMutableList().also { list ->
                    val cur = list[index]
                    val processed = cur.currentProgress + 1
                    list[index] = cur.copy(
                        currentProgress = processed,
                        successCount = successCount,
                        avgDurationMs = totalDuration / processed.coerceAtLeast(1),
                        lastSite = site,
                        lastError = if (result.success) null else "probe failed",
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
