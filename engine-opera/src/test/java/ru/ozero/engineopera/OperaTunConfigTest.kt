package ru.ozero.engineopera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperaTunConfigTest {
    @Test
    fun `config uses only TCP SOCKS with proxied DoH and no direct fallback`() {
        for (ipv6 in listOf(false, true)) {
            val config = OperaTunConfig.build(1080, ipv6)
            assertTrue(config.contains("\"server\":\"127.0.0.1\",\"server_port\":1080"))
            assertTrue(config.contains("\"network\":\"tcp\""))
            assertTrue(config.contains("\"detour\":\"opera\""))
            assertTrue(config.contains("\"server_name\":\"cloudflare-dns.com\""))
            assertTrue(config.contains("\"final\":\"opera\""))
            assertTrue(config.indexOf("hijack-dns") < config.indexOf("\"network\":\"udp\""))
            assertTrue(config.contains("\"network\":\"udp\",\"action\":\"reject\""))
            assertFalse(config.contains("\"type\":\"direct\""))
            assertFalse(config.contains("\"insecure\":true"))
            assertEquals(ipv6, config.contains("fdfe:dcba:9876::1/126"))
        }
    }

    @Test
    fun `invalid ports rejected and spec matches generated addresses`() {
        for (port in listOf(0, -1, 65536)) {
            assertThrows(IllegalArgumentException::class.java) { OperaTunConfig.build(port, false) }
        }
        val spec = OperaTunConfig.spec()
        assertEquals("172.19.0.1", spec.ipv4Address)
        assertEquals(30, spec.ipv4PrefixLength)
        assertEquals("fdfe:dcba:9876::1", spec.ipv6Address)
        assertEquals(listOf("172.19.0.2", "fdfe:dcba:9876::2"), spec.dnsServers)
        assertTrue(spec.routeAllV4)
        assertTrue(spec.routeAllV6)
        assertFalse(spec.blocking)
    }
}
