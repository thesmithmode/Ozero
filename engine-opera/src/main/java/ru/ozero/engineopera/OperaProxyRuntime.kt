package ru.ozero.engineopera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class OperaProxyRuntime(
    private val launcher: OperaProxyLauncher,
    private val routedProbe: suspend (Int) -> Long = OperaSocksProbe::probe,
    private val allocatePort: () -> Int = ::freePort,
    private val timeoutMs: Long = 25_000L,
    private val retryDelayMs: Long = 250L,
) {
    private class Session(val process: OperaProxyProcess, val port: Int) {
        var ready: Boolean = false
    }

    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun start(config: OperaProxyConfig): StartResult {
        var launched: Session? = null
        try {
            mutex.withLock {
                stopCurrent()
                withContext(Dispatchers.IO) {
                    val port = allocatePort()
                    Session(launcher.launch(config, port), port).also {
                        launched = it
                        session = it
                    }
                }
            }
            var alternate = config.alternateBootstrap
            while (true) {
                val current = checkNotNull(launched)
                val ready = withTimeoutOrNull(timeoutMs) { awaitRoute(current) } == true
                val result: StartResult? = mutex.withLock {
                    if (ready && session === current && current.process.alive) {
                        current.ready = true
                        StartResult.Success(current.port)
                    } else {
                        val code = current.process.exitCode
                        val reason = when {
                            session !== current -> "opera startup interrupted"
                            !current.process.alive -> "opera process exited before readiness"
                            else -> "opera routed readiness timed out"
                        }
                        if (session === current && code != null) {
                            PersistentLoggers.warn("OperaRuntime", "native startup exit code=$code")
                        }
                        // Only the owner of the failed session may replace it.
                        // 9/10/12 are upstream registration/discovery/selection failures.
                        if (session === current && !alternate && code in RETRYABLE_EXIT_CODES) {
                            stopIfCurrent(current)
                            alternate = true
                            withContext(Dispatchers.IO) {
                                val port = allocatePort()
                                Session(launcher.launch(config.copy(alternateBootstrap = true), port), port).also {
                                    launched = it
                                    session = it
                                }
                            }
                            null
                        } else {
                            stopIfCurrent(current)
                            StartResult.Failure(reason)
                        }
                    }
                }
                if (result != null) return result
            }
        } catch (e: CancellationException) {
            cleanup(launched)
            throw e
        } catch (e: Exception) {
            cleanup(launched)
            PersistentLoggers.warn("OperaRuntime", "startup failed: ${e.javaClass.simpleName}")
            return StartResult.Failure("opera startup failed")
        }
    }

    suspend fun stop() = mutex.withLock { stopCurrent() }

    suspend fun probe(): ProbeResult {
        val current = mutex.withLock { session?.takeIf { it.ready && it.process.alive } }
            ?: return ProbeResult.Failure("opera process unavailable")
        return try {
            val latency = routedProbe(current.port)
            if (isCurrent(current)) {
                ProbeResult.Success(latency)
            } else {
                ProbeResult.Failure("opera process exited")
            }
        } catch (e: IOException) {
            ProbeResult.Failure(
                "opera routed probe failed",
                cause = e,
                code = ProbeResult.Failure.Code.ROUTED_PROBE_FAILED,
            )
        }
    }

    private suspend fun awaitRoute(current: Session): Boolean {
        while (isCurrent(current)) {
            try {
                routedProbe(current.port)
                return isCurrent(current)
            } catch (_: IOException) {
                if (!isCurrent(current)) return false
                delay(retryDelayMs)
            }
        }
        return false
    }

    private suspend fun isCurrent(current: Session): Boolean =
        mutex.withLock { session === current && current.process.alive }

    private suspend fun cleanup(current: Session?) = withContext(NonCancellable) {
        mutex.withLock {
            if (current != null) stopIfCurrent(current)
        }
    }

    private suspend fun stopIfCurrent(current: Session) {
        if (session === current) stopCurrent()
    }

    private suspend fun stopCurrent() = withContext(NonCancellable + Dispatchers.IO) {
        session?.process?.stop()
        session = null
    }

    private companion object {
        val RETRYABLE_EXIT_CODES = setOf(9, 10, 12)

        fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(OperaProxyCommand.LOOPBACK)).use {
            it.localPort
        }
    }
}
