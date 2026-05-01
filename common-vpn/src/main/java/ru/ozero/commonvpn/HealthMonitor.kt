package ru.ozero.commonvpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.probe.Socks5HandshakeProbe

class HealthMonitor(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
    private val failuresBeforeDegraded: Int = DEFAULT_FAILURES_BEFORE_DEGRADED,
    private val proxyHost: String = "127.0.0.1",
    private val probe: suspend (host: String, port: Int, timeoutMs: Int) -> Long = { h, p, t ->
        Socks5HandshakeProbe.probe(h, p, t)
    },
) {

    enum class Status { UNKNOWN, HEALTHY, DEGRADED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(Status.UNKNOWN)
    val status: StateFlow<Status> = _status.asStateFlow()
    private var job: Job? = null
    private var consecutiveFailures: Int = 0

    fun start(socksPort: Int) {
        stop()
        consecutiveFailures = 0
        _status.value = Status.UNKNOWN
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (!isActive) return@launch
                runProbe(socksPort)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        consecutiveFailures = 0
        _status.value = Status.UNKNOWN
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun runProbe(socksPort: Int) {
        val ok = runCatching { probe(proxyHost, socksPort, probeTimeoutMs) }.isSuccess
        if (ok) {
            consecutiveFailures = 0
            if (_status.value != Status.HEALTHY) {
                _status.value = Status.HEALTHY
                PersistentLoggers.info(TAG, "engine healthy on port=$socksPort")
            }
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= failuresBeforeDegraded && _status.value != Status.DEGRADED) {
                _status.value = Status.DEGRADED
                PersistentLoggers.warn(
                    TAG,
                    "engine degraded — $consecutiveFailures consecutive probe failures on port=$socksPort",
                )
            }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 30_000L
        const val DEFAULT_PROBE_TIMEOUT_MS: Int = 3_000
        const val DEFAULT_FAILURES_BEFORE_DEGRADED: Int = 3
        private const val TAG: String = "HealthMonitor"
    }
}
