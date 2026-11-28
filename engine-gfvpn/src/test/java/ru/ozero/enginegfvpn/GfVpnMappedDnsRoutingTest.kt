package ru.ozero.enginegfvpn

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.split.LanRoutes

class GfVpnMappedDnsRoutingTest {
    @Test
    fun `synthetic DNS range is routed in LAN bypass without capturing carrier LAN`() {
        val mappedNetwork = ipv4(GfVpnTun.MAP_DNS_NETWORK)
        val mappedPrefix = GfVpnTun.MAP_DNS_PREFIX
        val routes = LanRoutes.BYPASS_LAN_IPV4
        assertTrue(
            routes.any { route ->
                route.prefix <= mappedPrefix &&
                    mappedNetwork and mask(route.prefix) == ipv4(route.address) and mask(route.prefix)
            },
        )
        assertFalse(
            routes.any { route ->
                ipv4("100.64.0.1") and mask(route.prefix) == ipv4(route.address) and mask(route.prefix)
            },
        )
        assertFalse(
            routes.any { route ->
                ipv4("192.168.1.1") and mask(route.prefix) == ipv4(route.address) and mask(route.prefix)
            },
        )
    }

    private fun ipv4(address: String): Long =
        address.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun mask(prefix: Int): Long = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
}
