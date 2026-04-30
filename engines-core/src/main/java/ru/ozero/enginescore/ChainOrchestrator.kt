package ru.ozero.enginescore

import android.util.Log
import kotlinx.coroutines.CancellationException

class ChainOrchestrator(
    private val engines: Set<EnginePlugin>,
) {
    private val started = mutableListOf<EnginePlugin>()

    suspend fun start(steps: List<ChainStep>): ChainResult {
        require(steps.isNotEmpty()) { "ChainOrchestrator.start: steps empty — single-engine = chain of 1" }

        var upstream: Upstream = Upstream.None
        var lastSocksPort = 0

        steps.forEachIndexed { idx, step ->
            val plugin = engines.firstOrNull { it.id == step.engineId }
                ?: return rollback(idx, "engine ${step.engineId} not found in registry")
            if (upstream !is Upstream.None && !plugin.capabilities.supportsUpstreamSocks) {
                Log.w(TAG, "step $idx ${step.engineId} cannot accept upstream — terminal-only engine")
                return rollback(
                    idx,
                    "engine ${step.engineId} terminal-only (supportsUpstreamSocks=false), " +
                        "can only be head of chain or standalone",
                )
            }
            val r = try {
                Log.i(TAG, "step[$idx] ${step.engineId} start upstream=$upstream")
                plugin.start(step.config, upstream)
            } catch (ce: CancellationException) {
                Log.w(TAG, "step $idx ${step.engineId} cancelled, rollback")
                stop()
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "step $idx ${step.engineId} threw: ${t.message}")
                return rollback(idx, "step $idx ${step.engineId} threw: ${t.message}")
            }
            when (r) {
                is StartResult.Failure -> {
                    Log.w(TAG, "step $idx ${step.engineId} failed: ${r.reason}")
                    return rollback(idx, r.reason)
                }
                is StartResult.Success -> {
                    started.add(plugin)
                    lastSocksPort = r.socksPort
                    upstream = Upstream.Socks5(host = LOOPBACK, port = r.socksPort)
                    Log.i(TAG, "step[$idx] ${step.engineId} success socksPort=${r.socksPort}")
                }
            }
        }
        return ChainResult.Success(finalSocksPort = lastSocksPort)
    }

    suspend fun stop() {
        val snapshot = started.toList().asReversed()
        started.clear()
        snapshot.forEach { plugin ->
            try {
                plugin.stop()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "stop ${plugin.id} threw: ${t.message}")
            }
        }
    }

    private suspend fun rollback(failedAt: Int, reason: String): ChainResult.Failure {
        val rolledBack = started.size
        stop()
        return ChainResult.Failure(failedAtIndex = failedAt, reason = reason, rolledBack = rolledBack)
    }

    private companion object {
        const val TAG = "ChainOrchestrator"
        const val LOOPBACK = "127.0.0.1"
    }
}