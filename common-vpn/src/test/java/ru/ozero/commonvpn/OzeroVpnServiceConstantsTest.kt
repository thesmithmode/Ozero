package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OzeroVpnServiceConstantsTest {
    @Test
    fun actionStartHasCorrectValue() {
        assertEquals("ru.ozero.vpn.ACTION_START", OzeroVpnService.ACTION_START)
    }

    @Test
    fun actionStopHasCorrectValue() {
        assertEquals("ru.ozero.vpn.ACTION_STOP", OzeroVpnService.ACTION_STOP)
    }

    @Test
    fun actionEngineFailureHasCorrectValue() {
        assertEquals("ru.ozero.vpn.ACTION_ENGINE_FAILURE", OzeroVpnService.ACTION_ENGINE_FAILURE)
    }

    @Test
    fun engineFailureExtrasHaveCorrectValues() {
        assertEquals("ru.ozero.vpn.EXTRA_ENGINE_ID", OzeroVpnService.EXTRA_ENGINE_ID)
        assertEquals("ru.ozero.vpn.EXTRA_ENGINE_FAILURE_REASON", OzeroVpnService.EXTRA_ENGINE_FAILURE_REASON)
    }

    @Test
    fun tunAddressIsPrivateRange() {
        assertEquals("10.10.10.10", OzeroVpnService.TUN_ADDRESS)
    }

    @Test
    fun tunPrefixLengthIsHost() {
        assertEquals(32, OzeroVpnService.TUN_PREFIX_LENGTH)
    }

    @Test
    fun tunAddressV6IsUla() {
        assert(OzeroVpnService.TUN_ADDRESS_V6.startsWith("fd"))
    }

    @Test
    fun tunPrefixLengthV6IsValid() {
        assert(OzeroVpnService.TUN_PREFIX_LENGTH_V6 in 1..128)
    }

    @Test
    fun tunDnsServersAllReachableNonLoopback() {
        val servers = OzeroVpnService.TUN_DNS_SERVERS
        assert(servers.isNotEmpty()) { "TUN_DNS_SERVERS must contain at least one DNS" }
        for (s in servers) {
            val addr = java.net.InetAddress.getByName(s)
            assert(!addr.isLoopbackAddress()) { "DNS '$s' must not be a loopback address" }
            assert(!addr.isAnyLocalAddress()) { "DNS '$s' must not be 0.0.0.0/::" }
        }
    }

    @Test
    fun tunDnsServersAreFromPublicDnsServers() {
        assertEquals(ru.ozero.commondns.PublicDnsServers.IPV4, OzeroVpnService.TUN_DNS_SERVERS)
    }

    @Test
    fun notificationIdIsPositive() {
        assert(OzeroNotificationFactory.NOTIFICATION_ID > 0)
    }

    @Test
    fun channelIdIsNonEmpty() {
        assert(OzeroNotificationFactory.CHANNEL_ID.isNotEmpty())
    }
}
