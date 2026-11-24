package ru.ozero.enginetgwss

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WarpSocksRelayTest {
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val success = byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 1)

    @Test
    fun `non Telegram TCP domain request goes only to configured WARP endpoint`() {
        val name = "example.test".encodeToByteArray()
        val request = byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf(1, 187.toByte())
        val payload = byteArrayOf(11, 12, 13)
        withUpstream({ upstream ->
            greet(upstream)
            val input = DataInputStream(upstream.getInputStream())
            assertContentEquals(request, ByteArray(request.size).also { input.readFully(it) })
            upstream.getOutputStream().apply {
                write(success)
                flush()
            }
            assertContentEquals(payload, ByteArray(payload.size).also { input.readFully(it) })
            upstream.getOutputStream().write(payload)
        }) { client ->
            request(client, request)
            val input = DataInputStream(client.getInputStream())
            assertContentEquals(success, ByteArray(10).also { input.readFully(it) })
            client.getOutputStream().write(payload)
            assertContentEquals(payload, ByteArray(payload.size).also { input.readFully(it) })
        }
    }

    @Test
    fun `UDP association returns only a loopback relay and preserves datagrams`() {
        DatagramSocket(InetSocketAddress(loopback, 0)).use { udp ->
            udp.soTimeout = 3000
            val port = udp.localPort
            val req = byteArrayOf(5, 3, 0, 1, 0, 0, 0, 0, 0, 0)
            val reply = byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, (port shr 8).toByte(), port.toByte())
            val payload = byteArrayOf(0, 0, 0, 1, 1, 1, 1, 1, 0, 53, 42)
            withUpstream({ upstream ->
                greet(upstream)
                val input = DataInputStream(upstream.getInputStream())
                assertContentEquals(req, ByteArray(10).also { input.readFully(it) })
                upstream.getOutputStream().write(reply)
                val received = DatagramPacket(ByteArray(128), 128)
                udp.receive(received)
                assertContentEquals(payload, received.data.copyOf(received.length))
                udp.send(DatagramPacket(payload, payload.size, received.socketAddress))
                assertEquals(-1, input.read())
            }) { client ->
                request(client, req)
                val response = ByteArray(10).also { DataInputStream(client.getInputStream()).readFully(it) }
                assertContentEquals(byteArrayOf(127, 0, 0, 1), response.copyOfRange(4, 8))
                DatagramSocket(InetSocketAddress(loopback, 0)).use { local ->
                    local.soTimeout = 3000
                    local.send(DatagramPacket(payload, payload.size, InetSocketAddress(loopback, port)))
                    val received = DatagramPacket(ByteArray(128), 128)
                    local.receive(received)
                    assertContentEquals(payload, received.data.copyOf(received.length))
                }
            }
        }
    }

    @Test
    fun `upstream failure is reported without connecting directly`() {
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        TelegramWssProxy(FakeTelegramWebSockets(), WarpSocksRelay(port)) {}.use { proxy ->
            Socket(loopback, proxy.start()).use { client ->
                client.soTimeout = 3000
                request(client, byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, 187.toByte()))
                val reply = ByteArray(10).also { DataInputStream(client.getInputStream()).readFully(it) }
                assertEquals(1, reply[1].toInt())
            }
        }
    }

    @Test
    fun `nonlocal and zero-port UDP endpoints are rejected`() {
        val nonlocal = byteArrayOf(5, 0, 0, 1, 1, 1, 1, 1, 0, 53)
        val noPort = success.copyOf().apply { this[9] = 0 }
        for (response in listOf(nonlocal, noPort)) {
            assertFailsWith<IOException> {
                WarpSocksRelay.readResponse(DataInputStream(ByteArrayInputStream(response)), 3)
            }
        }
        assertFailsWith<IOException> {
            WarpSocksRelay.readResponse(DataInputStream(ByteArrayInputStream(byteArrayOf(4, 0, 0, 1))), 1)
        }
    }

    private fun greet(upstream: Socket) {
        val input = DataInputStream(upstream.getInputStream())
        assertContentEquals(byteArrayOf(5, 1, 0), ByteArray(3).also { input.readFully(it) })
        upstream.getOutputStream().write(byteArrayOf(5, 0))
    }

    private fun request(client: Socket, request: ByteArray) {
        client.getOutputStream().apply {
            write(byteArrayOf(5, 1, 0))
            flush()
        }
        val reply = ByteArray(2).also { DataInputStream(client.getInputStream()).readFully(it) }
        assertContentEquals(byteArrayOf(5, 0), reply)
        client.getOutputStream().apply {
            write(request)
            flush()
        }
    }

    private fun withUpstream(serverAction: (Socket) -> Unit, clientAction: (Socket) -> Unit) {
        ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 3000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val task = worker.submit(Callable {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        serverAction(socket)
                    }
                })
                TelegramWssProxy(FakeTelegramWebSockets(), WarpSocksRelay(server.localPort)) {}.use { proxy ->
                    Socket(loopback, proxy.start()).use { client ->
                        client.soTimeout = 3000
                        clientAction(client)
                    }
                    task.get(5, TimeUnit.SECONDS)
                    assertTrue(task.isDone)
                }
            } finally {
                worker.shutdownNow()
            }
        }
    }
}
