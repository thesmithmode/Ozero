package ru.ozero.enginetgwss

import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal object WarpRouteProbe {
    private val resolver = byteArrayOf(1, 1, 1, 1)
    private const val TIMEOUT_MS = 3_000

    fun check(port: Int): Boolean = try {
        checkTcp(port) && checkUdp(port)
    } catch (_: IOException) {
        false
    }

    private fun checkTcp(port: Int): Boolean = Socket().use { socket ->
        val reply = negotiate(socket, port, SocksRequest(1, resolver, 443, 1))
        if (reply[1].toInt() != 0) return false
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        (factory.createSocket(socket, "1.1.1.1", 443, true) as SSLSocket).use { tls ->
            tls.soTimeout = TIMEOUT_MS
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.startHandshake()
            true
        }
    }

    private fun checkUdp(port: Int): Boolean = Socket().use { control ->
        val reply = negotiate(control, port, SocksRequest(3, ByteArray(4), 0, 1))
        if (reply[1].toInt() != 0) return false
        val address = InetAddress.getByAddress(reply.copyOfRange(4, 8))
        val udpPort = ((reply[8].toInt() and 255) shl 8) or (reply[9].toInt() and 255)
        DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { udp ->
            udp.soTimeout = TIMEOUT_MS
            udp.connect(address, udpPort)
            val query = byteArrayOf(
                0x61, 0x27, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0, 0, 1, 0, 1,
            )
            val packet = byteArrayOf(0, 0, 0, 1) + resolver + byteArrayOf(0, 53) + query
            udp.send(DatagramPacket(packet, packet.size))
            val received = DatagramPacket(ByteArray(4096), 4096)
            udp.receive(received)
            val data = received.data
            val expectedHeader = byteArrayOf(0, 0, 0, 1) + resolver + byteArrayOf(0, 53)
            val validAddress = received.length >= 22 && data.copyOfRange(0, 10).contentEquals(expectedHeader)
            val validId = data[10] == query[0] && data[11] == query[1]
            val isResponse = (data[12].toInt() and 128) != 0
            validAddress && validId && isResponse
        }
    }

    private fun negotiate(socket: Socket, port: Int, request: SocksRequest): ByteArray {
        socket.connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS)
        socket.soTimeout = TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        output.write(byteArrayOf(5, 1, 0))
        output.flush()
        if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != 0) {
            throw IOException("WARP SOCKS authentication failed")
        }
        output.write(request.encode())
        output.flush()
        return WarpSocksRelay.readResponse(input, request.command)
    }
}
