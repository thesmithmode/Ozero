package ru.ozero.commonvpn.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector
import java.net.InetSocketAddress
import java.net.Socket

object TcpProbe {

    suspend fun probe(
        host: String,
        port: Int,
        timeoutMs: Long,
        protector: SocketProtector,
    ): EnginePreflight.Result = withContext(Dispatchers.IO) {
        val socket = Socket()
        val protected = runCatching { protector.protect(socket) }.getOrDefault(false)
        if (!protected) {
            runCatching { socket.close() }
            return@withContext EnginePreflight.Result.Fail("VpnService.protect отклонил сокет")
        }
        val outcome: EnginePreflight.Result = withTimeoutOrNull(timeoutMs) {
            runCatching {
                socket.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                EnginePreflight.Result.Ok as EnginePreflight.Result
            }.getOrElse { e ->
                EnginePreflight.Result.Fail(
                    "connect $host:$port — ${e.javaClass.simpleName}: ${e.message}",
                )
            }
        } ?: EnginePreflight.Result.Fail("connect $host:$port — timeout ${timeoutMs}ms")
        runCatching { socket.close() }
        outcome
    }
}
