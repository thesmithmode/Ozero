package ru.ozero.enginesingbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit

fun interface SingboxRoutedProbe {
    suspend fun probeLatencyMs(socksPort: Int): Long
}

class SingboxHttp204RoutedProbe(
    private val probeUrl: URL = URL(PROBE_URL),
    private val socksHost: String = LOOPBACK,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val nanoTime: () -> Long = System::nanoTime,
) : SingboxRoutedProbe {

    override suspend fun probeLatencyMs(socksPort: Int): Long = withContext(Dispatchers.IO) {
        if (socksPort <= 0) {
            PersistentLoggers.warn(TAG, "routed probe failed: invalid socksPort=$socksPort")
            return@withContext LATENCY_FAILED
        }
        val start = nanoTime()
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            val connection = probeUrl.openConnection(proxy) as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                    TimeUnit.NANOSECONDS.toMillis(nanoTime() - start).coerceAtLeast(1L)
                } else {
                    PersistentLoggers.warn(
                        TAG,
                        "routed probe failed: httpCode=$code urlHost=${probeUrl.host} socksPort=$socksPort",
                    )
                    LATENCY_FAILED
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            PersistentLoggers.warn(
                TAG,
                "routed probe failed: ${error::class.java.simpleName}: ${error.message} " +
                    "urlHost=${probeUrl.host} socksPort=$socksPort timeoutMs=$timeoutMs",
            )
            LATENCY_FAILED
        }
    }

    companion object {
        private const val TAG = "SingboxRoutedProbe"
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
        const val LATENCY_FAILED = -1L
        private const val LOOPBACK = "127.0.0.1"
        private const val DEFAULT_TIMEOUT_MS = 3_000
    }
}
