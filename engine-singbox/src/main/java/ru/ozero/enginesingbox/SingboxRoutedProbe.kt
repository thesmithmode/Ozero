package ru.ozero.enginesingbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

fun interface SingboxRoutedProbe {
    suspend fun probeLatencyMs(socksPort: Int): Long
}

class SingboxHttp204RoutedProbe(
    private val probeUrl: URL = URL(PROBE_URL),
    private val fallbackProbeUrls: List<URL> = FALLBACK_PROBE_URLS.map(::URL),
    private val socksHost: String = LOOPBACK,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxProbeUrls: Int = DEFAULT_MAX_PROBE_URLS,
    private val nanoTime: () -> Long = System::nanoTime,
) : SingboxRoutedProbe {

    override suspend fun probeLatencyMs(socksPort: Int): Long = withContext(Dispatchers.IO) {
        probeLatencyMsUntil(socksPort, deadlineNanos = nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong()))
    }

    fun probeLatencyMsUntil(socksPort: Int, deadlineNanos: Long): Long {
        if (maxProbeUrls <= 0) return LATENCY_FAILED
        if (socksPort <= 0) {
            PersistentLoggers.warn(TAG, "routed probe failed: invalid socksPort=$socksPort")
            return LATENCY_FAILED
        }
        if (!isSocksPortReady(socksPort, deadlineNanos)) {
            PersistentLoggers.debug(
                TAG,
                "routed probe failed: socks not ready socksPort=$socksPort timeoutMs=$timeoutMs",
            )
            return LATENCY_FAILED
        }
        val urls = listOf(probeUrl) + fallbackProbeUrls
        val failures = mutableListOf<String>()
        for (url in urls.distinctBy { it.toString() }.take(maxProbeUrls)) {
            val remainingTimeoutMs = remainingTimeoutMs(deadlineNanos) ?: break
            val result = probeSingleUrl(url, socksPort, remainingTimeoutMs)
            val latency = result.latencyMs
            if (latency >= 0) return latency
            failures += "${url.host}:${result.reason}"
        }
        PersistentLoggers.debug(
            TAG,
            "routed probe failed: socksPort=$socksPort timeoutMs=$timeoutMs failures=${failures.joinToString(";")}",
        )
        return LATENCY_FAILED
    }

    private fun isSocksPortReady(socksPort: Int, deadlineNanos: Long): Boolean {
        val connectTimeoutMs = remainingTimeoutMs(deadlineNanos) ?: return false
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(socksHost, socksPort), connectTimeoutMs)
            }
            true
        }.getOrDefault(false)
    }

    private fun probeSingleUrl(url: URL, socksPort: Int, remainingTimeoutMs: Int): ProbeAttempt {
        val start = nanoTime()
        return runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            val connection = url.openConnection(proxy) as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = remainingTimeoutMs
                connection.readTimeout = remainingTimeoutMs
                val code = connection.responseCode
                if (isSuccessfulProbeResponse(url, code)) {
                    ProbeAttempt(TimeUnit.NANOSECONDS.toMillis(nanoTime() - start).coerceAtLeast(1L), "ok")
                } else {
                    ProbeAttempt(LATENCY_FAILED, "http-$code")
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            ProbeAttempt(LATENCY_FAILED, error::class.java.simpleName)
        }
    }

    private fun remainingTimeoutMs(deadlineNanos: Long): Int? {
        val remainingNanos = deadlineNanos - nanoTime()
        if (remainingNanos <= 0) return null
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos)
            .coerceAtLeast(1L)
            .coerceAtMost(timeoutMs.toLong())
            .toInt()
    }

    private fun isSuccessfulProbeResponse(url: URL, code: Int): Boolean {
        return if (url.path.endsWith(GENERATE_204_PATH)) {
            code == HttpURLConnection.HTTP_NO_CONTENT
        } else {
            code in SUCCESS_HTTP_CODES
        }
    }

    companion object {
        private const val TAG = "SingboxRoutedProbe"
        const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
        val FALLBACK_PROBE_URLS = listOf(
            "http://cp.cloudflare.com/generate_204",
            "https://www.gstatic.com/generate_204",
        )
        const val LATENCY_FAILED = -1L
        private const val LOOPBACK = "127.0.0.1"
        private const val DEFAULT_TIMEOUT_MS = 3_000
        private const val DEFAULT_MAX_PROBE_URLS = 3
        private const val GENERATE_204_PATH = "/generate_204"
        private val SUCCESS_HTTP_CODES = 200..299
    }

    private data class ProbeAttempt(val latencyMs: Long, val reason: String)
}
