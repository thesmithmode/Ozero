package ru.ozero.enginewarp

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.net.Socket

class WarpEndpointProber(private val connectTimeoutMs: Int = 1500) {

    data class ProbeResult(val endpoint: String, val rttMs: Long)

    suspend fun probe(endpoints: List<String>): List<ProbeResult> = coroutineScope {
        endpoints.map { endpoint ->
            async { ProbeResult(endpoint, measureTcpRtt(endpoint)) }
        }.awaitAll().sortedBy { it.rttMs }
    }

    private fun measureTcpRtt(endpoint: String): Long {
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: return Long.MAX_VALUE
        val host = endpoint.substringBeforeLast(':')
        return try {
            val t = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress(host, port), connectTimeoutMs) }
            System.currentTimeMillis() - t
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
