package ru.ozero.commondns

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicDnsServersCoverageTest {

    @Test
    fun `IPV4 contains only IPv4 literals and stable resolver order`() {
        assertEquals("8.8.8.8", PublicDnsServers.IPV4.first())
        assertEquals("149.112.112.112", PublicDnsServers.IPV4.last())
        PublicDnsServers.IPV4.forEach { server ->
            assertEquals(3, server.count { it == '.' })
            assertFalse(server.contains(':'))
        }
    }

    @Test
    fun `IPV6 contains only IPv6 literals and no IPv4 fallback entries`() {
        assertEquals("2001:4860:4860::8888", PublicDnsServers.IPV6.first())
        PublicDnsServers.IPV6.forEach { server ->
            assertTrue(server.contains(':'))
            assertFalse(server.contains('.'))
        }
    }

    @Test
    fun `ALL preserves IPv4 then IPv6 concatenation`() {
        assertEquals(PublicDnsServers.IPV4 + PublicDnsServers.IPV6, PublicDnsServers.ALL)
        assertEquals(PublicDnsServers.IPV4.size + PublicDnsServers.IPV6.size, PublicDnsServers.ALL.size)
    }
}
