package ru.ozero.coreorchestrator.probe

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.assertTrue

class Socks5HandshakeProbeTest {

    private var server: ServerSocket? = null

    @AfterEach
    fun tearDown() {
        runCatching { server?.close() }
    }

    @Test
    fun returnsLatencyOnValidNoAuthHandshake() = runTest {
        val srv = ServerSocket(0).also { server = it }
        thread(start = true, isDaemon = true) {
            runCatching {
                val client = srv.accept()
                client.use { c ->
                    val ins = c.getInputStream()
                    val ver = ins.read()
                    val n = ins.read()
                    repeat(n) { ins.read() }
                    require(ver == 0x05)
                    val out = c.getOutputStream()
                    out.write(byteArrayOf(0x05, 0x00))
                    out.flush()
                }
            }
        }

        val latency = Socks5HandshakeProbe.probe("127.0.0.1", srv.localPort, timeoutMs = 2_000)
        assertTrue(latency >= 0L, "latency должен быть неотрицательным")
    }

    @Test
    fun throwsWhenServerSendsBogusBytes() = runTest {
        val srv = ServerSocket(0).also { server = it }
        thread(start = true, isDaemon = true) {
            runCatching {
                val client = srv.accept()
                client.use { c ->
                    c.getInputStream().read(ByteArray(8))
                    c.getOutputStream().write(byteArrayOf(0x00, 0x42))
                    c.getOutputStream().flush()
                }
            }
        }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                Socks5HandshakeProbe.probe("127.0.0.1", srv.localPort, timeoutMs = 2_000)
            }
        }
    }

    @Test
    fun throwsWhenServerRejectsAllMethods() = runTest {
        val srv = ServerSocket(0).also { server = it }
        thread(start = true, isDaemon = true) {
            runCatching {
                val client = srv.accept()
                client.use { c ->
                    c.getInputStream().read(ByteArray(8))
                    c.getOutputStream().write(byteArrayOf(0x05, 0xFF.toByte()))
                    c.getOutputStream().flush()
                }
            }
        }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                Socks5HandshakeProbe.probe("127.0.0.1", srv.localPort, timeoutMs = 2_000)
            }
        }
    }

    @Test
    fun throwsOnRefusedConnection() = runTest {
        val free = ServerSocket(0).use { it.localPort }
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                Socks5HandshakeProbe.probe("127.0.0.1", free, timeoutMs = 1_000)
            }
        }
    }

    @Test
    fun throwsWhenServerHangsBeyondTimeout() = runTest {
        val srv = ServerSocket(0).also { server = it }
        thread(start = true, isDaemon = true) {
            runCatching {
                val client: Socket = srv.accept()
                Thread.sleep(2_000)
                client.close()
            }
        }

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                Socks5HandshakeProbe.probe("127.0.0.1", srv.localPort, timeoutMs = 300)
            }
        }
    }
}
