package ru.ozero.commonnet

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkDiagnosticsTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test
    fun `reports tcp and udp success with observed mapping`() = runBlocking {
        ServerSocket(0, 1, loopback).use { tcp ->
            DatagramSocket(0, loopback).use { udp ->
                udp.soTimeout = 3_000
                val responder = thread(isDaemon = true) {
                    val packet = DatagramPacket(ByteArray(256), 256)
                    udp.receive(packet)
                    val response = stunResponse(packet.data.copyOfRange(8, 20), packet.address, packet.port)
                    udp.send(DatagramPacket(response, response.size, packet.socketAddress))
                }
                val diagnostics = NetworkDiagnostics("127.0.0.1", tcp.localPort, "127.0.0.1", udp.localPort)

                val result = requireNotNull(diagnostics.run(1500) { true })

                responder.join(3_000)
                assertTrue(result.tcpReachable)
                assertTrue(result.udpReachable)
                assertEquals(NatObservation.UNCHANGED, result.nat)
                assertEquals(1500, result.linkMtu)
            }
        }
    }

    @Test
    fun `missing udp response does not imply a nat type`() = runBlocking {
        ServerSocket(0, 1, loopback).use { tcp ->
            DatagramSocket(0, loopback).use { udp ->
                val diagnostics = NetworkDiagnostics("127.0.0.1", tcp.localPort, "127.0.0.1", udp.localPort, 100)

                val result = requireNotNull(diagnostics.run(null) { true })

                assertTrue(result.tcpReachable)
                assertFalse(result.udpReachable)
                assertNull(result.nat)
                assertNull(result.linkMtu)
            }
        }
    }

    @Test
    fun `closed tcp port is not reachable`() = runBlocking {
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        DatagramSocket(0, loopback).use { udp ->
            val diagnostics = NetworkDiagnostics("127.0.0.1", port, "127.0.0.1", udp.localPort, 100)

            val result = requireNotNull(diagnostics.run(null) { true })

            assertFalse(result.tcpReachable)
        }
    }

    @Test
    fun `reports changed address or port without assigning nat type`() = runBlocking {
        ServerSocket(0, 1, loopback).use { tcp ->
            DatagramSocket(0, loopback).use { udp ->
                udp.soTimeout = 3_000
                val responder = thread(isDaemon = true) {
                    val packet = DatagramPacket(ByteArray(256), 256)
                    udp.receive(packet)
                    val response = stunResponse(
                        packet.data.copyOfRange(8, 20),
                        InetAddress.getByName("192.0.2.1"),
                        packet.port,
                    )
                    udp.send(DatagramPacket(response, response.size, packet.socketAddress))
                }
                val diagnostics = NetworkDiagnostics("127.0.0.1", tcp.localPort, "127.0.0.1", udp.localPort)

                val result = requireNotNull(diagnostics.run(1400) { true })

                responder.join(3_000)
                assertTrue(result.udpReachable)
                assertEquals(NatObservation.TRANSLATED, result.nat)
            }
        }
    }

    @Test
    fun `guard change discards result before next probe`() = runBlocking {
        ServerSocket(0, 1, loopback).use { tcp ->
            val diagnostics = NetworkDiagnostics("127.0.0.1", tcp.localPort, "127.0.0.1", 1)
            var checks = 0

            assertNull(diagnostics.run(1400) { ++checks < 3 })
            assertEquals(3, checks)
        }
    }

    @Test
    fun `denied diagnostics are not started`() = runBlocking {
        val diagnostics = NetworkDiagnostics("127.0.0.1", 1, "127.0.0.1", 1)

        assertNull(diagnostics.run(1400) { false })
    }
}
