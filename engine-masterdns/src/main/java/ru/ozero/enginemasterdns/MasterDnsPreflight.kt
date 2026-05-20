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
        return first to FALLBACK_PORT
    }

    private companion object {
        const val FALLBACK_HOST = "8.8.8.8"
        const val FALLBACK_PORT = 53
        const val TIMEOUT_MS = 5_000L
    }
}
