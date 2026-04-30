package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream

data class StrategyScore(
    val strategy: ByeDpiStrategy,
    val totalProbes: Int,
    val successCount: Int,
    val avgDurationMs: Long,
) {
    val successRate: Double = if (totalProbes == 0) 0.0 else successCount.toDouble() / totalProbes
}

sealed class PickResult {
    data class Success(
        val ranked: List<StrategyScore>,
        val winner: StrategyScore,
    ) : PickResult()

    data class Failed(val reason: String) : PickResult()
    object Cancelled : PickResult()
}

fun interface PickProgress {
    fun onProgress(current: Int, total: Int, lastScore: StrategyScore?)
}

class AutoStrategyPicker(
    private val byeDpiEngine: EnginePlugin,
    private val probeClient: SocksProbeClient,
    private val strategies: List<ByeDpiStrategy>,
    private val sites: List<String>,
    private val socksPort: Int = 1080,
    private val perStrategyStartTimeoutMs: Long = START_TIMEOUT_MS,
    private val betweenDelayMs: Long = BETWEEN_STRATEGY_DELAY_MS,
) {

    suspend fun pickBest(progress: PickProgress = PickProgress { _, _, _ -> }): PickResult =
        coroutineScope {
            if (strategies.isEmpty()) return@coroutineScope PickResult.Failed("no strategies")
            if (sites.isEmpty()) return@coroutineScope PickResult.Failed("no test sites")

            val scores = mutableListOf<StrategyScore>()
            for ((index, strategy) in strategies.withIndex()) {
                if (!currentCoroutineContext().isActive) {
                    runCatching { byeDpiEngine.stop() }
                    return@coroutineScope PickResult.Cancelled
                }

                val score = measureStrategy(strategy)
                scores += score
                progress.onProgress(index + 1, strategies.size, score)
                if (betweenDelayMs > 0L) delay(betweenDelayMs)
            }

            val ranked = scores.sortedByDescending { it.successRate }
            val winner = ranked.firstOrNull { it.successRate > 0.0 }
                ?: return@coroutineScope PickResult.Failed("ни одна стратегия не пробила DPI")
            PickResult.Success(ranked = ranked, winner = winner)
        }

    private suspend fun measureStrategy(strategy: ByeDpiStrategy): StrategyScore {
        val started = withTimeoutOrNull(perStrategyStartTimeoutMs) {
            byeDpiEngine.start(
                config = EngineConfig.ByeDpi(args = strategy.command, socksPort = socksPort),
                upstream = Upstream.None,
            )
        }
        if (started !is StartResult.Success) {
            PersistentLoggers.warn(TAG, "strategy '${strategy.command}' start failed: $started")
            runCatching { byeDpiEngine.stop() }
            return StrategyScore(
                strategy = strategy,
                totalProbes = sites.size,
                successCount = 0,
                avgDurationMs = 0L,
            )
        }

        var success = 0
        var totalDur = 0L
        for (site in sites) {
            if (!currentCoroutineContext().isActive) break
            val result = probeClient.probe(site)
            if (result.success) success++
            totalDur += result.durationMs
        }

        runCatching { byeDpiEngine.stop() }

        return StrategyScore(
            strategy = strategy,
            totalProbes = sites.size,
            successCount = success,
            avgDurationMs = if (sites.isEmpty()) 0L else totalDur / sites.size,
        )
    }

    companion object {
        const val START_TIMEOUT_MS: Long = 6_000L
        const val BETWEEN_STRATEGY_DELAY_MS: Long = 500L
    }
}
