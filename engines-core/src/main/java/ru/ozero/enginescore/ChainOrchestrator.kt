package ru.ozero.enginescore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
            if (steps.size > 1 && idx == 0 && !plugin.capabilities.providesLocalSocksWithoutUpstream) {
                PersistentLoggers.warn(TAG, "step $idx ${step.engineId} has no standalone local SOCKS port")
                return@withLock rollback(
                    idx,
                    "engine ${step.engineId} has no standalone local SOCKS port for chain head",
                )
            }
            if (upstream !is Upstream.None && !plugin.capabilities.supportsUpstreamSocks) {
                PersistentLoggers.warn(TAG, "step $idx ${step.engineId} cannot accept upstream — terminal-only engine")
                return@withLock rollback(
                    idx,
                    "engine ${step.engineId} terminal-only (supportsUpstreamSocks=false), " +
                        "can only be head of chain or standalone",
                )
            }
            if (steps.size > 1 && !plugin.capabilities.providesLocalSocks) {
                PersistentLoggers.warn(TAG, "step $idx ${step.engineId} has no local SOCKS capability")
                return@withLock rollback(idx, "engine ${step.engineId} has no local SOCKS port for chain")
            }
            val r = try {
                PersistentLoggers.debug(TAG, "step[$idx] ${step.engineId} start upstream=$upstream")
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
                    synchronized(started) { started.add(plugin) }
                    if (steps.size > 1 && r.socksPort <= 0) {
                        PersistentLoggers.warn(TAG, "step $idx ${step.engineId} has no local SOCKS port for chain")
                        return@withLock rollback(
                            idx,
                            "engine ${step.engineId} has no local SOCKS port for chain",
                        )
                    }
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

    fun activeEngines(): List<EnginePlugin> = synchronized(started) { started.toList() }

    private suspend fun rollback(failedAt: Int, reason: String): ChainResult.Failure {
        val rolledBack = synchronized(started) { started.size }
        stopInternal()
        return ChainResult.Failure(failedAtIndex = failedAt, reason = reason, rolledBack = rolledBack)
    }

    private suspend fun stopInternal() {
        val snapshot = synchronized(started) {
            started.toList().asReversed().also { started.clear() }
        }
        snapshot.forEach { plugin ->
            try {
                val timeoutMs = plugin.stopTimeoutMs()
                val completed = withTimeoutOrNull(timeoutMs) { plugin.stop() }
                if (completed == null) {
                    PersistentLoggers.warn(
                        TAG,
                        "stop ${plugin.id} timed out after ${timeoutMs}ms — mutex освобождён",
                    )
                }
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
