package ru.ozero.engineopera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class OperaProxyRuntime(
    private val launcher: OperaProxyLauncher,
    private val routedProbe: suspend (Int) -> Long = OperaSocksProbe::probe,
    private val allocatePort: () -> Int = ::freePort,
    private val timeoutMs: Long = 45_000L,
    private val retryDelayMs: Long = 250L,
) {
    private class Session(val process: OperaProxyProcess, val port: Int) {
        var ready: Boolean = false
    }

    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun start(config: OperaProxyConfig): StartResult {
        var launched: Session? = null
        return try {
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
            val current = checkNotNull(launched)
            val ready = withTimeoutOrNull(timeoutMs) {
                awaitRoute(current)
                true
            } ?: false
            mutex.withLock {
                if (ready && session === current && current.process.alive) {
                    current.ready = true
                    StartResult.Success(current.port)
                } else {
                    stopIfCurrent(current)
                    StartResult.Failure("opera readiness timeout or process stopped")
                }
            }
        } catch (e: CancellationException) {
            cleanup(launched)
            throw e
        } catch (e: Exception) {
            cleanup(launched)
            StartResult.Failure("opera startup failed")
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
            ProbeResult.Failure("opera routed probe failed", code = ProbeResult.Failure.Code.ROUTED_PROBE_FAILED)
        }
    }

    private suspend fun awaitRoute(current: Session) {
        while (isCurrent(current)) {
            try {
                routedProbe(current.port)
                if (isCurrent(current)) return
            } catch (e: IOException) {
                if (!isCurrent(current)) throw e
                delay(retryDelayMs)
            }
        }
        throw IOException("opera process stopped")
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
        fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(OperaProxyCommand.LOOPBACK)).use {
            it.localPort
        }
    }
}
