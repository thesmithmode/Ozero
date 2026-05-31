package ru.ozero.enginesingbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) : SingboxRoutedProbe {

    override suspend fun probeLatencyMs(socksPort: Int): Long = withContext(Dispatchers.IO) {
        if (socksPort <= 0) return@withContext LATENCY_FAILED
        val start = System.nanoTime()
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
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).coerceAtLeast(1L)
                } else {
                    LATENCY_FAILED
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(LATENCY_FAILED)
    }

    companion object {
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
        const val LATENCY_FAILED = -1L
        private const val LOOPBACK = "127.0.0.1"
        private const val DEFAULT_TIMEOUT_MS = 3_000
    }
}
