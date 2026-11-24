package ru.ozero.enginemasque

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal fun interface MasqueReadinessProbe {
    fun check(port: Int): Boolean
}

internal class TlsMasqueReadinessProbe(
    private val createSocket: (Proxy) -> Socket = { Socket(it) },
    private val createTlsFactory: () -> SSLSocketFactory = {
        SSLSocketFactory.getDefault() as SSLSocketFactory
    },
) : MasqueReadinessProbe {
    override fun check(port: Int): Boolean = try {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
        createSocket(proxy).use { socket ->
            socket.soTimeout = TIMEOUT_MS
            socket.connect(InetSocketAddress(TARGET, 443), TIMEOUT_MS)
            handshake(socket)
        }
        true
    } catch (_: IOException) {
        false
    }

    private fun handshake(socket: Socket) {
        val factory = createTlsFactory()
        (factory.createSocket(socket, TARGET, 443, true) as SSLSocket).use { tls ->
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            tls.soTimeout = TIMEOUT_MS
            tls.startHandshake()
        }
    }

    private companion object {
        const val TARGET = "1.1.1.1"
        const val TIMEOUT_MS = 2_000
    }
}
