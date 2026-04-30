package ru.ozero.coreorchestrator.chain

import kotlinx.coroutines.CancellationException
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.PersistentLoggers
import ru.ozero.coreapi.StartResult
import ru.ozero.coreapi.Upstream

class ChainOrchestrator(
    private val engines: Map<EngineId, Engine>,
) {
    private val started = mutableListOf<Engine>()

    suspend fun start(steps: List<ChainStep>): ChainResult {
        require(steps.isNotEmpty()) { "ChainOrchestrator.start: steps пустой — single-engine = chain длины 1" }

        var upstream: Upstream = Upstream.None
        var lastSocksPort = 0

        steps.forEachIndexed { idx, step ->
            val engine = engines[step.engineId]
                ?: return rollback(idx, "engine ${step.engineId} не найден в registry")
            if (upstream !is Upstream.None && !engine.capabilities.supportsUpstreamSocks) {
                PersistentLoggers.instance?.warn(
                    TAG,
                    "step $idx ${step.engineId} не принимает upstream — terminal-only engine",
                )
                return rollback(
                    idx,
                    "engine ${step.engineId} terminal-only (supportsUpstreamSocks=false), " +
                        "может быть только head of chain или standalone",
                )
            }
            val r = try {
                engine.start(step.config, upstream)
            } catch (ce: CancellationException) {
                PersistentLoggers.instance?.warn(TAG, "step $idx ${step.engineId} cancelled, rollback")
                stop()
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.instance?.error(TAG, "step $idx ${step.engineId} threw", t)
                return rollback(idx, "step $idx ${step.engineId} threw: ${t.message}")
            }
            when (r) {
                is StartResult.Failure -> {
                    PersistentLoggers.instance?.warn(TAG, "step $idx ${step.engineId} failed: ${r.reason}")
                    return rollback(idx, r.reason)
                }
                is StartResult.Success -> {
                    started.add(engine)
                    lastSocksPort = r.socksPort
                    upstream = Upstream.Socks5(host = LOOPBACK, port = r.socksPort)
                    PersistentLoggers.instance?.info(
                        TAG,
                        "step $idx ${step.engineId} started → socksPort=${r.socksPort}",
                    )
                }
            }
        }
        return ChainResult.Success(finalSocksPort = lastSocksPort)
    }

    suspend fun stop() {
        val snapshot = started.toList().asReversed()
        started.clear()
        snapshot.forEach { engine ->
            try {
                engine.stop()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.instance?.warn(TAG, "stop ${engine.id} threw", t)
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
