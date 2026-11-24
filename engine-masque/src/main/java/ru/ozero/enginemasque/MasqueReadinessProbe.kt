package ru.ozero.enginemasque

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal fun interface MasqueReadinessProbe {
    fun check(port: Int): Boolean
}

internal object TlsMasqueReadinessProbe : MasqueReadinessProbe {
    override fun check(port: Int): Boolean {
        val target = "1.1.1.1"
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        return try {
            Socket(proxy).use { socket ->
                socket.soTimeout = 2_000
                socket.connect(InetSocketAddress(target, 443), 2_000)
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                (factory.createSocket(socket, target, 443, true) as SSLSocket).use { tls ->
                    tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    tls.soTimeout = 2_000
                    tls.startHandshake()
                }
            }
            true
        } catch (_: java.io.IOException) {
            false
        }
    }
}
