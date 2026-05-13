package ru.ozero.enginewarp

import ru.ozero.commonvpn.probe.TcpProbe
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class WarpPreflight(
    private val peerEndpointProvider: () -> String? = { null },
) : EnginePreflight {
    override suspend fun probe(protector: SocketProtector): EnginePreflight.Result {
        val (host, port) = resolveTarget()
        return TcpProbe.probe(host = host, port = port, timeoutMs = TIMEOUT_MS, protector = protector)
    }

    internal fun resolveTarget(): Pair<String, Int> {
        val endpoint = peerEndpointProvider()?.trim().orEmpty()
        if (endpoint.isEmpty()) return FALLBACK_HOST to FALLBACK_PORT
        val host = parseHost(endpoint) ?: return FALLBACK_HOST to FALLBACK_PORT
        return host to FALLBACK_PORT
    }

    private fun parseHost(endpoint: String): String? {
        if (endpoint.startsWith('[')) {
            val close = endpoint.indexOf(']')
            if (close < 2) return null
            val host = endpoint.substring(1, close)
            return if (isIpv6(host)) host else null
        }
        val sep = endpoint.lastIndexOf(':')
        if (sep <= 0) return null
        val host = endpoint.substring(0, sep)
        return if (isIpv4(host)) host else null
    }

    private fun isIpv4(host: String): Boolean =
        host.isNotEmpty() && host.all { it.isDigit() || it == '.' }

    private fun isIpv6(host: String): Boolean =
        host.isNotEmpty() && host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }

    private companion object {
        const val FALLBACK_HOST = "1.1.1.1"
        const val FALLBACK_PORT = 443
        const val TIMEOUT_MS = 5_000L
    }
}
