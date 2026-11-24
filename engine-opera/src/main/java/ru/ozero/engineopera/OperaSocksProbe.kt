package ru.ozero.engineopera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object OperaSocksProbe {
    private const val TARGET = "example.com"
    private const val TARGET_PORT = 443
    private const val TIMEOUT_MS = 3_000

    suspend fun probe(port: Int): Long = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(OperaProxyCommand.LOOPBACK, port))
        Socket(proxy).use { socket ->
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress.createUnresolved(TARGET, TARGET_PORT), TIMEOUT_MS)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(socket, TARGET, TARGET_PORT, true) as SSLSocket).use { tls ->
                tls.soTimeout = TIMEOUT_MS
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                tls.startHandshake()
            }
        }
        (System.nanoTime() - started) / 1_000_000L
    }
}
