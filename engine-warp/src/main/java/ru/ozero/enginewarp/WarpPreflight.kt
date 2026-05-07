package ru.ozero.enginewarp

import ru.ozero.commonvpn.probe.TcpProbe
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class WarpPreflight : EnginePreflight {
    override suspend fun probe(protector: SocketProtector): EnginePreflight.Result =
        TcpProbe.probe(host = HOST, port = PORT, timeoutMs = TIMEOUT_MS, protector = protector)

    private companion object {
        const val HOST = "engage.cloudflareclient.com"
        const val PORT = 443
        const val TIMEOUT_MS = 5_000L
    }
}
