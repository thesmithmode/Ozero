package ru.ozero.engineopera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
                tls.startHandshake()
                if (!javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(TARGET, tls.session)) {
                    throw IOException("opera probe hostname verification failed")
                }
            }
        }
        (System.nanoTime() - started) / 1_000_000L
    }
}
