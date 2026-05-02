package ru.ozero.enginescore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChainOrchestrator(
    private val engines: Set<EnginePlugin>,
) {
    private val started = mutableListOf<EnginePlugin>()
    private val mutex = Mutex()

    suspend fun start(steps: List<ChainStep>): ChainResult = mutex.withLock {
        require(steps.isNotEmpty()) { "ChainOrchestrator.start: steps empty — single-engine = chain of 1" }

        var upstream: Upstream = Upstream.None
        var lastSocksPort = 0

        steps.forEachIndexed { idx, step ->
            val plugin = engines.firstOrNull { it.id == step.engineId }
                ?: return@withLock rollback(idx, "engine ${step.engineId} not found in registry")
            if (upstream !is Upstream.None && !plugin.capabilities.supportsUpstreamSocks) {
                PersistentLoggers.warn(TAG, "step $idx ${step.engineId} cannot accept upstream — terminal-only engine")
                return@withLock rollback(
                    idx,
                    "engine ${step.engineId} terminal-only (supportsUpstreamSocks=false), " +
                        "can only be head of chain or standalone",
                )
            }
            val r = try {
                PersistentLoggers.info(TAG, "step[$idx] ${step.engineId} start upstream=$upstream")
                plugin.start(step.config, upstream)
            } catch (ce: CancellationException) {
                PersistentLoggers.warn(TAG, "step $idx ${step.engineId} cancelled, rollback")
                stopInternal()
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "step $idx ${step.engineId} threw: ${t.message}")
                return@withLock rollback(idx, "step $idx ${step.engineId} threw: ${t.message}")
            }
            when (r) {
                is StartResult.Failure -> {
                    PersistentLoggers.warn(TAG, "step $idx ${step.engineId} failed: ${r.reason}")
                    return@withLock rollback(idx, r.reason)
                }
                is StartResult.Success -> {
                    started.add(plugin)
                    lastSocksPort = r.socksPort
                    upstream = Upstream.Socks5(host = LOOPBACK, port = r.socksPort)
                    PersistentLoggers.info(TAG, "step[$idx] ${step.engineId} success socksPort=${r.socksPort}")
                }
            }
        }
        ChainResult.Success(finalSocksPort = lastSocksPort)
    }

    suspend fun stop() = mutex.withLock {
        stopInternal()
    }

    private suspend fun rollback(failedAt: Int, reason: String): ChainResult.Failure {
        val rolledBack = started.size
        stopInternal()
        return ChainResult.Failure(failedAtIndex = failedAt, reason = reason, rolledBack = rolledBack)
    }

    private suspend fun stopInternal() {
        val snapshot = started.toList().asReversed()
        started.clear()
        snapshot.forEach { plugin ->
            try {
                plugin.stop()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stop ${plugin.id} threw: ${t.message}")
            }
        }
    }

    private companion object {
        const val TAG = "ChainOrchestrator"
        const val LOOPBACK = "127.0.0.1"
    }
}
