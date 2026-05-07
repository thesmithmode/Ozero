package ru.ozero.commonvpn.probe

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TcpProbeTest {

    private val server: ServerSocket = ServerSocket(0)
    private val protectorOk = SocketProtector { true }
    private val protectorReject = SocketProtector { false }

    @AfterEach
    fun tearDown() {
        runCatching { server.close() }
    }

    @Test
    fun `probe возвращает Ok когда порт открыт`() = runTest {
        val result = TcpProbe.probe("127.0.0.1", server.localPort, timeoutMs = 2_000, protector = protectorOk)
        assertEquals(EnginePreflight.Result.Ok, result)
    }

    @Test
    fun `probe возвращает Fail когда protector отклонил сокет`() = runTest {
        val result = TcpProbe.probe("127.0.0.1", server.localPort, timeoutMs = 2_000, protector = protectorReject)
        val fail = assertIs<EnginePreflight.Result.Fail>(result)
        assertTrue(fail.reason.contains("protect"))
    }

    @Test
    fun `probe возвращает Fail когда порт закрыт`() = runTest {
        val closed = ServerSocket(0)
        val port = closed.localPort
        closed.close()
        val result = TcpProbe.probe("127.0.0.1", port, timeoutMs = 2_000, protector = protectorOk)
        val fail = assertIs<EnginePreflight.Result.Fail>(result)
        assertTrue(fail.reason.contains("connect"))
    }

    @Test
    fun `probe возвращает Fail timeout когда host не отвечает`() = runTest {
        val result = TcpProbe.probe("10.255.255.1", 65000, timeoutMs = 200, protector = protectorOk)
        val fail = assertIs<EnginePreflight.Result.Fail>(result)
        assertTrue(fail.reason.contains("timeout") || fail.reason.contains("connect"))
    }
}
