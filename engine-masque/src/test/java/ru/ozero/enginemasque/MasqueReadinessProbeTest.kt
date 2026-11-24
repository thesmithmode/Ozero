package ru.ozero.enginemasque

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class MasqueReadinessProbeTest {
    @Test
    fun `only TLS over the loopback SOCKS endpoint establishes readiness`() {
        val socket = mockk<Socket>(relaxed = true)
        val tls = mockk<SSLSocket>(relaxed = true)
        val factory = mockk<SSLSocketFactory>()
        every { factory.createSocket(socket, "1.1.1.1", 443, true) } returns tls
        every { tls.sslParameters } returns SSLParameters()
        val probe = TlsMasqueReadinessProbe(
            createSocket = { proxy ->
                assertEquals(Proxy.Type.SOCKS, proxy.type())
                assertEquals(InetSocketAddress("127.0.0.1", 1080), proxy.address())
                socket
            },
            createTlsFactory = { factory },
        )
        assertTrue(probe.check(1080))
        verify { socket.connect(InetSocketAddress("1.1.1.1", 443), 2_000) }
        verify { tls.sslParameters = match { it.endpointIdentificationAlgorithm == "HTTPS" } }
        verify { tls.startHandshake() }
        verify { tls.close() }
        verify { socket.close() }
        every { tls.startHandshake() } throws IOException("fixture")
        assertFalse(probe.check(1080))
    }

    @Test
    fun `unreachable proxy is not ready`() {
        val socket = mockk<Socket>(relaxed = true)
        every { socket.connect(any(), any()) } throws IOException("fixture")
        val probe = TlsMasqueReadinessProbe({ socket }) { error("Must not reach TLS") }
        assertFalse(probe.check(1080))
        verify { socket.close() }
    }
}
