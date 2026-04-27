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
    fun tunDnsIsLocalhost() {
        assertEquals("127.0.0.1", OzeroVpnService.TUN_DNS)
    }

    @Test
    fun tunMtuIsStandard() {
        assertEquals(1500, OzeroVpnService.TUN_MTU)
    }

    @Test
    fun notificationIdIsPositive() {
        assert(OzeroVpnService.NOTIFICATION_ID > 0)
    }

    @Test
    fun channelIdIsNonEmpty() {
        assert(OzeroVpnService.CHANNEL_ID.isNotEmpty())
    }
}
