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
        val sep = endpoint.lastIndexOf(':')
        if (sep <= 0) return FALLBACK_HOST to FALLBACK_PORT
        val host = endpoint.substring(0, sep).trim().trim('[', ']')
        if (!isPlainIp(host)) return FALLBACK_HOST to FALLBACK_PORT
        return host to FALLBACK_PORT
    }

    private fun isPlainIp(host: String): Boolean {
        if (host.isEmpty()) return false
        if (host.contains(':')) return false
        return host.all { it.isDigit() || it == '.' }
    }

    private companion object {
        const val FALLBACK_HOST = "1.1.1.1"
        const val FALLBACK_PORT = 443
        const val TIMEOUT_MS = 5_000L
    }
}
