package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HevTunnelConfigTest {
    @Test
    fun defaultDnsValues() {
        val config = HevTunnelConfig(tunFd = 5, socksAddress = "127.0.0.1", socksPort = 1080)
        assertEquals("127.0.0.1", config.dnsAddress)
        assertEquals(53, config.dnsPort)
    }

    @Test
    fun toYamlContainsAllFields() {
        val config = HevTunnelConfig(
            tunFd = 7,
            socksAddress = "127.0.0.1",
            socksPort = 1080,
        )
        val yaml = config.toYaml()
        assertTrue(yaml.contains("fd: 7"))
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("port: 1080"))
    }

    @Test
    fun toYamlHasTunnelSection() {
        val yaml = HevTunnelConfig(tunFd = 3, socksAddress = "127.0.0.1", socksPort = 9050).toYaml()
        assertTrue(yaml.contains("tunnel:"))
        assertTrue(yaml.contains("socks5:"))
        assertTrue(yaml.contains("dns:"))
    }

    @Test
    fun copyPreservesFields() {
        val original = HevTunnelConfig(tunFd = 5, socksAddress = "127.0.0.1", socksPort = 1080)
        val copy = original.copy(socksPort = 2080)
        assertEquals(2080, copy.socksPort)
        assertEquals(5, copy.tunFd)
    }

    @Test
    fun rejectsInvalidTunFd() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunFd = -1, socksAddress = "127.0.0.1", socksPort = 1080)
        }
    }

    @Test
    fun rejectsInvalidPort() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunFd = 5, socksAddress = "127.0.0.1", socksPort = 0)
        }
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunFd = 5, socksAddress = "127.0.0.1", socksPort = 65536)
        }
    }

    @Test
    fun rejectsYamlInjectionInAddress() {
        // YAML injection: newline + новое поле
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(
                tunFd = 5,
                socksAddress = "127.0.0.1\n  evil: true",
                socksPort = 1080,
            )
        }
    }

    @Test
    fun rejectsSpaceInAddress() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunFd = 5, socksAddress = "127.0.0.1 evil", socksPort = 1080)
        }
    }
}
