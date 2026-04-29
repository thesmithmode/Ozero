package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HevTunnelConfigTest {

    private fun pfd(fd: Int): ParcelFileDescriptor = mockk {
        every { this@mockk.fd } returns fd
    }

    private fun base() = HevTunnelConfig(tunPfd = pfd(7), socksAddress = "127.0.0.1", socksPort = 1080)

    @Test
    fun `toYaml содержит обязательные поля upstream формата`() {
        val yaml = base().toYaml()
        assertTrue(yaml.contains("tunnel:"))
        assertTrue(yaml.contains("socks5:"))
        assertTrue(yaml.contains("address: 127.0.0.1"))
        assertTrue(yaml.contains("port: 1080"))
        assertTrue(yaml.contains("mtu: 1500"))
    }

    @Test
    fun `toYaml содержит IPv4 и IPv6 для tunnel секции`() {
        val yaml = base().toYaml()
        assertTrue(yaml.contains("ipv4: 10.10.10.10"))
        assertTrue(yaml.contains("ipv6: 'fd00:ffff:ffff:ffff::1'"))
    }

    @Test
    fun `toYaml НЕ содержит несуществующих в upstream полей`() {
        val yaml = base().toYaml()
        assertFalse(yaml.contains("fd:"), "tunnel.fd не существует в upstream — fd передаётся JNI-параметром")
        assertFalse(yaml.contains("dns:"), "секции dns нет в upstream conf — DNS resolve делает socks5 server")
    }

    @Test
    fun `toYaml содержит udp mode`() {
        val yaml = base().toYaml()
        assertTrue(yaml.contains("udp: 'udp'"))
    }

    @Test
    fun `default IPv6 в одинарных кавычках для libyaml`() {
        val yaml = base().toYaml()
        assertTrue(yaml.contains("ipv6: 'fd00"))
    }

    @Test
    fun `copy preserves fields`() {
        val original = base()
        val copy = original.copy(socksPort = 2080)
        assertEquals(2080, copy.socksPort)
        assertEquals(7, copy.tunPfd.fd)
    }

    @Test
    fun `rejects negative tunFd`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(-1), socksAddress = "127.0.0.1", socksPort = 1080)
        }
    }

    @Test
    fun `rejects invalid port`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 0)
        }
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 65536)
        }
    }

    @Test
    fun `rejects yaml injection in address`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(
                tunPfd = pfd(5),
                socksAddress = "127.0.0.1\n  evil: true",
                socksPort = 1080,
            )
        }
    }

    @Test
    fun `rejects invalid mtu`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 1080, tunMtu = 100)
        }
    }

    @Test
    fun `rejects invalid udpMode`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 1080, udpMode = "invalid")
        }
    }

    @Test
    fun `rejects out of range mtu high`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 1080, tunMtu = 70000)
        }
    }

    @Test
    fun `rejects yaml injection in ipv4`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(
                tunPfd = pfd(5),
                socksAddress = "127.0.0.1",
                socksPort = 1080,
                tunIpv4 = "10.0.0.1\nevil: y",
            )
        }
    }

    @Test
    fun `rejects yaml injection in ipv6`() {
        assertThrows<IllegalArgumentException> {
            HevTunnelConfig(
                tunPfd = pfd(5),
                socksAddress = "127.0.0.1",
                socksPort = 1080,
                tunIpv6 = "fd00:: ' \nevil: y",
            )
        }
    }

    @Test
    fun `accepts tcp udpMode`() {
        val cfg = HevTunnelConfig(tunPfd = pfd(5), socksAddress = "127.0.0.1", socksPort = 1080, udpMode = "tcp")
        assertTrue(cfg.toYaml().contains("udp: 'tcp'"))
    }
}
