package ru.ozero.enginemasterdns

import ru.ozero.commonvpn.probe.TcpProbe
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class MasterDnsPreflight(
    private val resolversProvider: () -> List<String> = { emptyList() },
) : EnginePreflight {

    override suspend fun probe(protector: SocketProtector): EnginePreflight.Result {
        val target = resolveTarget()
        return TcpProbe.probe(host = target.first, port = target.second, timeoutMs = TIMEOUT_MS, protector = protector)
    }

    internal fun resolveTarget(): Pair<String, Int> {
        val first = resolversProvider().firstOrNull { it.isNotBlank() }?.trim()
            ?: return FALLBACK_HOST to FALLBACK_PORT
        return parseHostPort(first)
    }

    private fun parseHostPort(raw: String): Pair<String, Int> {
        if (raw.startsWith("[")) {
            val close = raw.indexOf(']')
            if (close > 0) {
                val host = raw.substring(1, close)
                val rest = raw.substring(close + 1)
                val port = rest.takeIf { it.startsWith(":") }
                    ?.substring(1)
                    ?.toIntOrNull()
                    ?.takeIf { it in PORT_RANGE }
                    ?: FALLBACK_PORT
                return host to port
            }
        }
        val idx = raw.lastIndexOf(':')
        if (idx > 0 && raw.indexOf(':') == idx) {
            val portCandidate = raw.substring(idx + 1).toIntOrNull()
            if (portCandidate != null && portCandidate in PORT_RANGE) {
                return raw.substring(0, idx) to portCandidate
            }
        }
        return raw to FALLBACK_PORT
    }

    private companion object {
        const val FALLBACK_HOST = "8.8.8.8"
        const val FALLBACK_PORT = 53
        const val TIMEOUT_MS = 5_000L
        val PORT_RANGE = 1..65535
    }
}
