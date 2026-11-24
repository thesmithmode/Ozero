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
    private val mutex = Mutex()
    private var process: OperaProxyProcess? = null
    private var port = 0

    suspend fun start(config: OperaProxyConfig): StartResult = mutex.withLock {
        try {
            stopProcess()
            val selectedPort = withContext(Dispatchers.IO) {
                allocatePort().also { process = launcher.launch(config, it) }
            }
            val ready = withTimeoutOrNull(timeoutMs) {
                awaitRoute(selectedPort)
                true
            } ?: false
            if (ready) {
                port = selectedPort
                StartResult.Success(selectedPort)
            } else {
                stopProcess()
                StartResult.Failure("opera readiness timeout")
            }
        } catch (e: CancellationException) {
            stopProcess()
            throw e
        } catch (e: Exception) {
            stopProcess()
            StartResult.Failure("opera startup failed")
        }
    }

    suspend fun stop() = mutex.withLock { stopProcess() }

    suspend fun probe(): ProbeResult = mutex.withLock {
        if (port == 0 || process?.alive != true) {
            return@withLock ProbeResult.Failure("opera process unavailable")
        }
        try {
            val latency = routedProbe(port)
            if (process?.alive != true) return@withLock ProbeResult.Failure("opera process exited")
            ProbeResult.Success(latency)
        } catch (e: IOException) {
            ProbeResult.Failure("opera routed probe failed", code = ProbeResult.Failure.Code.ROUTED_PROBE_FAILED)
        }
    }

    private suspend fun awaitRoute(selectedPort: Int) {
        while (true) {
            if (process?.alive != true) throw IOException("opera process exited")
            try {
                routedProbe(selectedPort)
                if (process?.alive != true) throw IOException("opera process exited")
                return
            } catch (e: IOException) {
                if (process?.alive != true) throw e
                delay(retryDelayMs)
            }
        }
    }

    private suspend fun stopProcess() = withContext(NonCancellable + Dispatchers.IO) {
        port = 0
        process?.stop()
        process = null
    }

    private companion object {
        fun freePort(): Int = ServerSocket(0, 1, InetAddress.getByName(OperaProxyCommand.LOOPBACK)).use {
            it.localPort
        }
    }
}
