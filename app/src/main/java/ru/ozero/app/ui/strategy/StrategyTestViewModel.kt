package ru.ozero.app.ui.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginebyedpi.strategy.EvolutionEngine
import ru.ozero.enginebyedpi.strategy.GeneMemory
import ru.ozero.enginebyedpi.strategy.GenePool
import ru.ozero.enginebyedpi.strategy.ProbeResult
import ru.ozero.enginebyedpi.strategy.SocksProbeClient
import ru.ozero.enginebyedpi.strategy.StrategyEvolver
import ru.ozero.enginebyedpi.strategy.toCommand
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import javax.inject.Inject
import kotlin.math.max

sealed interface StrategyTestError {
    data object VpnRunning : StrategyTestError
    data object NoSites : StrategyTestError
}

data class EvolutionUiState(
    val generation: Int = 0,
    val maxGenerations: Int = 0,
    val bestFitness: Double = 0.0,
    val topChromosomes: List<Pair<String, Double>> = emptyList(),
    val isInitializing: Boolean = true,
    val evaluatingIndex: Int = 0,
    val evaluatingTotal: Int = 0,
    val evaluatingCommand: String? = null,
    val stagnationCount: Int = 0,
)

@HiltViewModel
class StrategyTestViewModel @Inject constructor(
    private val repository: ru.ozero.enginescore.settings.SettingsRepository,
    private val assetSource: StrategyAssetSource,
    private val resultsStore: StrategyResultsStore,
    private val settingsStore: StrategyTestSettingsStore,
    private val domainListManager: DomainListManager,
    private val savedStrategyStore: SavedStrategyStore,
    private val byeDpiEngine: EnginePlugin,
    private val probeFactory: StrategyProbeClientFactory,
    private val tunnelController: TunnelController,
    private val geneMemory: GeneMemory,
    private val usageHistoryStore: UsageHistoryStore,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _strategies = MutableStateFlow<List<StrategyResult>>(emptyList())
    val strategies: StateFlow<List<StrategyResult>> = _strategies.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _domainLists = MutableStateFlow<List<DomainList>>(emptyList())
    val domainLists: StateFlow<List<DomainList>> = _domainLists.asStateFlow()

    private val _runSummary = MutableStateFlow("")
    val runSummary: StateFlow<String> = _runSummary.asStateFlow()

    private val _errorMessage = MutableStateFlow<StrategyTestError?>(null)
    val errorMessage: StateFlow<StrategyTestError?> = _errorMessage.asStateFlow()

    private val _settings = MutableStateFlow(StrategyTestSettings())
    val settings: StateFlow<StrategyTestSettings> = _settings.asStateFlow()

    private val _savedStrategies = MutableStateFlow<List<SavedStrategy>>(emptyList())
    val savedStrategies: StateFlow<List<SavedStrategy>> = _savedStrategies.asStateFlow()

    private val _usageHistory = MutableStateFlow<List<UsageEntry>>(emptyList())
    val usageHistory: StateFlow<List<UsageEntry>> = _usageHistory.asStateFlow()

    private val _evolutionState = MutableStateFlow<EvolutionUiState?>(null)
    val evolutionState: StateFlow<EvolutionUiState?> = _evolutionState.asStateFlow()

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
            _domainLists.value = withContext(ioDispatcher) {
                runCatching { domainListManager.load() }.getOrDefault(emptyList())
            }
            _settings.value = withContext(ioDispatcher) {
                runCatching { settingsStore.load() }.getOrDefault(StrategyTestSettings())
            }
            _savedStrategies.value = withContext(ioDispatcher) {
                runCatching { savedStrategyStore.load() }.getOrDefault(emptyList())
            }
            _usageHistory.value = withContext(ioDispatcher) {
                runCatching { usageHistoryStore.load() }.getOrDefault(emptyList())
            }
        }
    }

    fun onToggleDomainList(id: String) {
        val updated = domainListManager.toggle(_domainLists.value, id)
        _domainLists.value = updated
        viewModelScope.launch(ioDispatcher) { runCatching { domainListManager.save(updated) } }
    }

    fun onAddDomainList(name: String, domains: List<String>) {
        val updated = domainListManager.addCustom(_domainLists.value, name, domains)
        _domainLists.value = updated
        viewModelScope.launch(ioDispatcher) { runCatching { domainListManager.save(updated) } }
    }

    fun onDeleteDomainList(id: String) {
        val updated = domainListManager.delete(_domainLists.value, id)
        _domainLists.value = updated
        viewModelScope.launch(ioDispatcher) { runCatching { domainListManager.save(updated) } }
    }

    fun onResetDomainLists() {
        val updated = domainListManager.resetToDefaults(_domainLists.value)
        _domainLists.value = updated
        viewModelScope.launch(ioDispatcher) { runCatching { domainListManager.save(updated) } }
    }

    fun onSettingsChange(newSettings: StrategyTestSettings) {
        _settings.value = newSettings
        viewModelScope.launch(ioDispatcher) {
            runCatching { settingsStore.save(newSettings) }
        }
    }

    private fun List<StrategyResult>.sortedForUi(): List<StrategyResult> =
        sortedWith(
            compareByDescending<StrategyResult> { it.isCompleted }
                .thenByDescending { it.successPercentage }
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
            val sites = domainListManager.getActiveDomains(_domainLists.value)
            if (sites.isEmpty()) {
                _errorMessage.value = StrategyTestError.NoSites
                _isRunning.value = false
                return@launch
            }
            val snap = _settings.value
            val commands: List<String> = if (snap.useCustomStrategies && snap.customStrategies.isNotBlank()) {
                snap.customStrategies.lines().map(String::trim).filter(String::isNotEmpty)
            } else {
                _strategies.value.map { it.command }
            }
            _strategies.value = commands.map { cmd ->
                _strategies.value.find { it.command == cmd } ?: StrategyResult(command = cmd)
            }.map {
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
            val useEvolution = _settings.value.evolutionMode
            try {
                if (useEvolution) {
                    runEvolution(sites)
                } else {
                    runLoop(sites)
                }
            } finally {
                _evolutionState.update { it?.copy(evaluatingCommand = null, isInitializing = false) }
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

    fun onSave(command: String) {
        viewModelScope.launch(ioDispatcher) {
            val updated = runCatching { savedStrategyStore.add(command) }.getOrElse { savedStrategyStore.load() }
            _savedStrategies.value = updated
        }
    }

    fun onDeleteSaved(id: String) {
        viewModelScope.launch(ioDispatcher) {
            val updated = runCatching { savedStrategyStore.delete(id) }.getOrElse { savedStrategyStore.load() }
            _savedStrategies.value = updated
        }
    }

    fun onPinSaved(id: String, pin: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val updated = runCatching {
                if (pin) savedStrategyStore.pin(id) else savedStrategyStore.unpin(id)
            }.getOrElse { savedStrategyStore.load() }
            _savedStrategies.value = updated
        }
    }

    fun onRename(id: String, name: String) {
        viewModelScope.launch(ioDispatcher) {
            val updated = runCatching { savedStrategyStore.rename(id, name) }.getOrElse { savedStrategyStore.load() }
            _savedStrategies.value = updated
        }
    }

    fun onApply(command: String) {
        viewModelScope.launch {
            runCatching { repository.setByedpiWinningArgs(command) }
                .onFailure { PersistentLoggers.warn(TAG, "onApply failed: ${it.message}") }
            withContext(ioDispatcher) {
                val updated = runCatching { savedStrategyStore.add(command) }.getOrElse { savedStrategyStore.load() }
                _savedStrategies.value = updated
                val savedName = updated.find { it.command == command }?.name
                runCatching { usageHistoryStore.record(command, savedName) }
                _usageHistory.value = runCatching { usageHistoryStore.load() }.getOrDefault(_usageHistory.value)
            }
        }
    }

    private suspend fun runEvolution(sites: List<String>) {
        val snap = _settings.value
        val seedCommands = _strategies.value.map { it.command }
        val genePool = GenePool(seedCommands)
        val evolver = StrategyEvolver(genePool)
        val maxGen = snap.evolutionMaxGenerations
        val timeoutMs = snap.timeoutSeconds * 1_000L
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = byeDpiEngine,
            probeFactory = { port, timeout -> probeFactory.create(port, timeout.toInt()) },
            evolver = evolver,
            pool = genePool,
            sites = sites,
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = snap.evolutionPopulationSize,
                maxGenerations = maxGen,
                mutationRate = snap.evolutionMutationRate,
                eliteCount = snap.evolutionEliteCount,
                concurrentProbes = max(1, snap.concurrentLimit),
                timeoutMs = timeoutMs,
            ),
            memory = geneMemory,
        )
        _evolutionState.value = EvolutionUiState(
            maxGenerations = maxGen,
            evaluatingTotal = snap.evolutionPopulationSize,
            isInitializing = true,
        )
        evolutionEngine.evolve(
            seedStrategies = seedCommands,
            onGeneration = { result ->
                _evolutionState.update { current ->
                    EvolutionUiState(
                        generation = result.generation,
                        maxGenerations = maxGen,
                        bestFitness = result.bestFitness,
                        topChromosomes = result.population
                            .sortedByDescending { it.second }
                            .take(5)
                            .map { it.first.toCommand() to it.second },
                        isInitializing = false,
                        evaluatingIndex = current?.evaluatingIndex ?: 0,
                        evaluatingTotal = snap.evolutionPopulationSize,
                        evaluatingCommand = current?.evaluatingCommand,
                        stagnationCount = result.stagnationCount,
                    )
                }
                _runSummary.value = "Gen ${result.generation}/$maxGen · best ${(result.bestFitness * 100).toInt()}%"
            },
            onChromosomeEval = { index, total, command ->
                _evolutionState.update { current ->
                    current?.copy(
                        evaluatingIndex = index + 1,
                        evaluatingTotal = total,
                        evaluatingCommand = command,
                    )
                }
            },
        )
    }

    private suspend fun runLoop(sites: List<String>) = coroutineScope {
        val snap = _settings.value
        val startTimeoutMs = snap.timeoutSeconds * 1_000L
        val concurrentLimit = max(1, snap.concurrentLimit)
        val requestsPerDomain = snap.requestsPerDomain.coerceAtLeast(1)
        val commands = _strategies.value.map { it.command }
        for ((loopIdx, command) in commands.withIndex()) {
            if (!currentCoroutineContext().isActive) return@coroutineScope
            _runSummary.value = "Testing ${loopIdx + 1}/${commands.size}: $command"
            val started = withTimeoutOrNull(startTimeoutMs) {
                byeDpiEngine.start(
                    config = EngineConfig.ByeDpi(args = command, socksPort = SOCKS_PORT),
                    upstream = Upstream.None,
                )
            }
            if (started !is StartResult.Success) {
                runCatching { byeDpiEngine.stop() }
                applyEngineStartFailure(command, sites, started)
                continue
            }
            val probe: SocksProbeClient = probeFactory.create(SOCKS_PORT, startTimeoutMs.toInt())
            val semaphore = Semaphore(concurrentLimit)
            try {
                coroutineScope {
                    sites.map { site ->
                        async {
                            semaphore.withPermit {
                                var result = probe.probe(site)
                                var attempt = 1
                                while (!result.success && attempt < requestsPerDomain) {
                                    result = probe.probe(site)
                                    attempt++
                                }
                                applyProbeResult(command, site, result)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                runCatching { byeDpiEngine.stop() }
            }
            markStrategyCompleted(command)
            if (snap.delayBetweenMs > 0L) delay(snap.delayBetweenMs)
        }
    }

    private fun applyEngineStartFailure(command: String, sites: List<String>, started: StartResult?) =
        _strategies.update { list ->
            list.map { s ->
                if (s.command == command) {
                    s.copy(
                        currentProgress = sites.size,
                        successCount = 0,
                        isCompleted = true,
                        lastError = started?.toString() ?: "start timeout",
                    )
                } else {
                    s
                }
            }.sortedForUi()
        }

    private fun applyProbeResult(command: String, site: String, result: ProbeResult) =
        _strategies.update { list ->
            list.map { s ->
                if (s.command == command) {
                    val newProgress = s.currentProgress + 1
                    val newSuccess = s.successCount + if (result.success) 1 else 0
                    val totalDur = s.avgDurationMs * s.currentProgress + result.durationMs
                    s.copy(
                        currentProgress = newProgress,
                        successCount = newSuccess,
                        avgDurationMs = totalDur / newProgress.coerceAtLeast(1),
                        lastSite = site,
                        lastError = if (result.success) null else "probe failed",
                    )
                } else {
                    s
                }
            }.sortedForUi()
        }

    private fun markStrategyCompleted(command: String) =
        _strategies.update { list ->
            list.map { s ->
                if (s.command == command) {
                    s.copy(isCompleted = true)
                } else {
                    s
                }
            }.sortedForUi()
        }

    private companion object {
        const val TAG: String = "StrategyTestVM"
        const val SOCKS_PORT: Int = 1080
    }
}
