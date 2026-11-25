package ru.ozero.enginetgwss

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarpRouteProbeTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test
    fun `readiness checks TLS hostname and actual UDP response`() {
        val (factory, tls) = tlsFactory()
        assertTrue(probeWithUdp(factory, valid = true))
        verify(exactly = 1) { tls.startHandshake() }
        verify { tls.sslParameters = match { it.endpointIdentificationAlgorithm == "HTTPS" } }
    }

    @Test
    fun `invalid DNS response does not report readiness`() {
        val (factory, _) = tlsFactory()
        assertFalse(probeWithUdp(factory, valid = false))
    }

    @Test
    fun `TCP SOCKS refusal prevents TLS and UDP probe`() {
        val (factory, tls) = tlsFactory()
        withServer({ server ->
            server.accept().use { socket ->
                greet(socket)
                assertRequest(socket, 1)
                reply(socket, 5, 1)
            }
        }) { port ->
            assertFalse(WarpRouteProbe.check(port) { factory })
        }
        verify(exactly = 0) { tls.startHandshake() }
    }

    @Test
    fun `UDP SOCKS refusal prevents connected state`() {
        val (factory, _) = tlsFactory()
        withServer({ server ->
            server.accept().use { socket ->
                greet(socket)
                assertRequest(socket, 1)
                reply(socket, 0, 1)
            }
            server.accept().use { socket ->
                greet(socket)
                assertRequest(socket, 3)
                reply(socket, 7, 1)
            }
        }) { port ->
            assertFalse(WarpRouteProbe.check(port) { factory })
        }
    }

    @Test
    fun `authentication or TLS failure prevents connected state`() {
        val (factory, tls) = tlsFactory()
        withServer({ server ->
            server.accept().use { socket ->
                DataInputStream(socket.getInputStream()).readFully(ByteArray(3))
                socket.getOutputStream().write(byteArrayOf(5, 2))
            }
        }) { port ->
            assertFalse(WarpRouteProbe.check(port) { factory })
        }
        every { tls.startHandshake() } throws IOException("test TLS failure")
        withServer({ server ->
            server.accept().use { socket ->
                greet(socket)
                assertRequest(socket, 1)
                reply(socket, 0, 1)
            }
        }) { port ->
            assertFalse(WarpRouteProbe.check(port) { factory })
        }
    }

    private fun probeWithUdp(factory: SSLSocketFactory, valid: Boolean): Boolean {
        DatagramSocket(InetSocketAddress(loopback, 0)).use { udp ->
            udp.soTimeout = 3000
            return withServer({ server ->
                server.accept().use { socket ->
                    greet(socket)
                    assertRequest(socket, 1)
                    reply(socket, 0, 1)
                }
                server.accept().use { socket ->
                    greet(socket)
                    assertRequest(socket, 3)
                    reply(socket, 0, udp.localPort)
                    val request = DatagramPacket(ByteArray(128), 128)
                    udp.receive(request)
                    val response = request.data.copyOf(request.length)
                    response[12] = if (valid) 0x81.toByte() else 1
                    udp.send(DatagramPacket(response, response.size, request.socketAddress))
                }
            }) { port ->
                WarpRouteProbe.check(port) { factory }
            }
        }
    }

    private fun tlsFactory(): Pair<SSLSocketFactory, SSLSocket> {
        val factory = mockk<SSLSocketFactory>()
        val tls = mockk<SSLSocket>(relaxed = true)
        every { tls.sslParameters } returns SSLParameters()
        every { factory.createSocket(any<Socket>(), "1.1.1.1", 443, true) } returns tls
        return factory to tls
    }

    private fun greet(socket: Socket) {
        socket.soTimeout = 3000
        val greeting = ByteArray(3).also { DataInputStream(socket.getInputStream()).readFully(it) }
        assertContentEquals(byteArrayOf(5, 1, 0), greeting)
        socket.getOutputStream().write(byteArrayOf(5, 0))
    }

    private fun assertRequest(socket: Socket, command: Int) {
        val request = ByteArray(10).also { DataInputStream(socket.getInputStream()).readFully(it) }
        val expected = if (command == 1) {
            byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, 187.toByte())
        } else {
            byteArrayOf(5, 3, 0, 1) + ByteArray(6)
        }
        assertContentEquals(expected, request)
    }

    private fun reply(socket: Socket, status: Int, port: Int) {
        socket.getOutputStream().write(
            byteArrayOf(5, status.toByte(), 0, 1, 127, 0, 0, 1, (port shr 8).toByte(), port.toByte()),
        )
    }

    private fun <T> withServer(action: (ServerSocket) -> Unit, test: (Int) -> T): T {
        ServerSocket(0, 2, loopback).use { server ->
            server.soTimeout = 3000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val job = executor.submit(Callable { action(server) })
                val result = test(server.localPort)
                job.get(5, TimeUnit.SECONDS)
                return result
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
