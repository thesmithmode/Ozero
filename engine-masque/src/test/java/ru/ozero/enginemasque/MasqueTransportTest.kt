package ru.ozero.enginemasque

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class MasqueTransportTest {
    @Test
    fun `auto tries h3 then h2 and manual mode never falls back`() {
        assertEquals(listOf(MasqueTransport.H3, MasqueTransport.H2), MasqueTransport.AUTO.attempts())
        assertEquals(listOf(MasqueTransport.H3), MasqueTransport.H3.attempts())
        assertEquals(listOf(MasqueTransport.H2), MasqueTransport.H2.attempts())
    }

    @Test
    fun `commands bind loopback and preserve pinned TLS and tunneled DNS`() {
        val binary = File("usque")
        val config = File("config.json")
        val h3 = MasqueCommand.socks(binary, config, 1080, MasqueTransport.H3)
        val h2 = MasqueCommand.socks(binary, config, 1080, MasqueTransport.H2)
        assertEquals(listOf(binary.absolutePath, "--config", config.absolutePath), h3.take(3))
        assertTrue("127.0.0.1" in h3)
        assertTrue("1080" in h3)
        assertFalse("--http2" in h3)
        assertEquals(h3 + "--http2", h2)
        assertTrue("--no-tunnel-ipv6" in h3)
        assertTrue("--always-reconnect" in h3)
        val dns = h3.zipWithNext().filter { it.first == "--dns" }.map { it.second }
        assertEquals(listOf("9.9.9.9", "149.112.112.112"), dns)
        val v6 = MasqueCommand.socks(binary, config, 1080, MasqueTransport.H3, true, true)
        assertTrue("--ipv6" in v6)
        assertFalse("--no-tunnel-ipv6" in v6)
        for (flag in listOf("--insecure", "--local-dns", "--system-dns")) {
            assertFalse(flag in h3)
            assertFalse(flag in h2)
        }
        assertTrue("--accept-tos" in MasqueCommand.register(binary, config))
    }

    @Test
    fun `commands reject invalid ports and unresolved transport`() {
        for (port in listOf(0, 65536)) {
            assertThrows(IllegalArgumentException::class.java) {
                MasqueCommand.socks(File("usque"), File("config"), port, MasqueTransport.H3)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MasqueCommand.socks(File("usque"), File("config"), 1080, MasqueTransport.AUTO)
        }
    }
}
