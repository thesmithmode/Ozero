package ru.ozero.engineurnetwork

import ru.ozero.commonvpn.probe.TcpProbe
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class UrnetworkPreflight : EnginePreflight {
    override suspend fun probe(protector: SocketProtector): EnginePreflight.Result =
        TcpProbe.probe(host = HOST, port = PORT, timeoutMs = TIMEOUT_MS, protector = protector)

    private companion object {
        const val HOST = "ssl.bringyour.com"
        const val PORT = 443
        const val TIMEOUT_MS = 5_000L
    }
}
