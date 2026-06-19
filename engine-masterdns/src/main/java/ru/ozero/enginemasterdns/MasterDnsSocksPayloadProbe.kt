package ru.ozero.enginemasterdns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

object MasterDnsSocksPayloadProbe {

    suspend fun probe(
        socksHost: String,
        socksPort: Int,
        targetHost: String = DEFAULT_TARGET_HOST,
        targetPort: Int = DEFAULT_TARGET_PORT,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Long = withContext(Dispatchers.IO) {
        require(socksPort in PORT_RANGE) { "socksPort out of range: $socksPort" }
        require(targetPort in PORT_RANGE) { "targetPort out of range: $targetPort" }
        require(timeoutMs > 0) { "timeoutMs must be > 0" }
        val started = System.currentTimeMillis()
        Socket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(socksHost, socksPort), timeoutMs)
            val out = socket.getOutputStream()
            val input = socket.getInputStream()
            out.write(byteArrayOf(SOCKS_VERSION, 0x01, METHOD_NO_AUTH))
            out.flush()
            val version = input.read()
            val method = input.read()
            if (version != SOCKS_VERSION.toInt()) throw IOException("bad SOCKS version in response: $version")
            if (method != METHOD_NO_AUTH.toInt()) throw IOException("unsupported SOCKS auth method: $method")
            val hostBytes = targetHost.encodeToByteArray()
            if (hostBytes.size > MAX_DOMAIN_LENGTH) throw IOException("target host too long")
            out.write(byteArrayOf(SOCKS_VERSION, COMMAND_CONNECT, RESERVED, ADDRESS_DOMAIN, hostBytes.size.toByte()))
            out.write(hostBytes)
            out.write(byteArrayOf((targetPort shr 8).toByte(), targetPort.toByte()))
            out.flush()
            val replyVersion = input.read()
            val reply = input.read()
            input.read()
            val addressType = input.read()
            if (replyVersion != SOCKS_VERSION.toInt()) throw IOException("bad SOCKS connect version: $replyVersion")
            if (reply != REPLY_SUCCESS.toInt()) throw IOException("SOCKS connect failed reply=$reply")
            readBoundAddress(input, addressType)
            System.currentTimeMillis() - started
        }
    }

    private fun readBoundAddress(input: java.io.InputStream, addressType: Int) {
        val addressLength = when (addressType) {
            ADDRESS_IPV4.toInt() -> IPV4_LENGTH
            ADDRESS_DOMAIN.toInt() -> {
                input.read()
                    .takeIf { it >= 0 }
                    ?: throw IOException("missing bound domain length")
            }
            ADDRESS_IPV6.toInt() -> IPV6_LENGTH
            else -> throw IOException("unsupported bound address type: $addressType")
        }
        repeat(addressLength + PORT_LENGTH) {
            if (input.read() < 0) throw IOException("truncated SOCKS connect reply")
        }
    }

    private const val DEFAULT_TARGET_HOST = "1.1.1.1"
    private const val DEFAULT_TARGET_PORT = 443
    private const val DEFAULT_TIMEOUT_MS = 8_000
    private const val SOCKS_VERSION: Byte = 0x05
    private const val METHOD_NO_AUTH: Byte = 0x00
    private const val COMMAND_CONNECT: Byte = 0x01
    private const val RESERVED: Byte = 0x00
    private const val ADDRESS_IPV4: Byte = 0x01
    private const val ADDRESS_DOMAIN: Byte = 0x03
    private const val ADDRESS_IPV6: Byte = 0x04
    private const val REPLY_SUCCESS: Byte = 0x00
    private const val MAX_DOMAIN_LENGTH = 255
    private const val IPV4_LENGTH = 4
    private const val IPV6_LENGTH = 16
    private const val PORT_LENGTH = 2
    private val PORT_RANGE = 1..65535
}
