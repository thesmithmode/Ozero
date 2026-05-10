package ru.ozero.enginebyedpi

import ru.ozero.commonvpn.probe.TcpProbe
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class ByeDpiPreflight : EnginePreflight {
    override suspend fun probe(protector: SocketProtector): EnginePreflight.Result =
        TcpProbe.probe(host = HOST, port = PORT, timeoutMs = TIMEOUT_MS, protector = protector)

    internal companion object {
        const val HOST = "1.1.1.1"
        const val PORT = 443
        const val TIMEOUT_MS = 5_000L
    }
}
