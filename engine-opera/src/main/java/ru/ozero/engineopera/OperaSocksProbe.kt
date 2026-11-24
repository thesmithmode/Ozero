package ru.ozero.engineopera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object OperaSocksProbe {
    private const val TARGET = "example.com"
    private const val DNS_HOST = "cloudflare-dns.com"
    private const val TARGET_PORT = 443
    private const val TIMEOUT_MS = 3_000

    suspend fun probe(port: Int): Long = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        connectTls(port, InetSocketAddress.createUnresolved(TARGET, TARGET_PORT), TARGET) { }
        (System.nanoTime() - started) / 1_000_000L
    }

    suspend fun probeDns(port: Int): Unit = withContext(Dispatchers.IO) {
        val target = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), TARGET_PORT)
        connectTls(port, target, DNS_HOST) { tls ->
            val request = "GET /dns-query?dns=AAABAAABAAAAAAAAB2V4YW1wbGUDY29tAAABAAE HTTP/1.0\r\n" +
                "Host: $DNS_HOST\r\nAccept: application/dns-message\r\nConnection: close\r\n\r\n"
            tls.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            tls.outputStream.flush()
            validateDnsResponse(tls.inputStream)
        }
    }

    private fun connectTls(
        port: Int,
        target: InetSocketAddress,
        hostname: String,
        action: (SSLSocket) -> Unit,
    ) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(OperaProxyCommand.LOOPBACK, port))
        Socket(proxy).use { socket ->
            socket.soTimeout = TIMEOUT_MS
            socket.connect(target, TIMEOUT_MS)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(socket, hostname, TARGET_PORT, true) as SSLSocket).use { tls ->
                tls.soTimeout = TIMEOUT_MS
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
                action(tls)
            }
        }
    }

    internal fun validateDnsResponse(input: InputStream) = OperaDnsResponse.validate(input)
}
