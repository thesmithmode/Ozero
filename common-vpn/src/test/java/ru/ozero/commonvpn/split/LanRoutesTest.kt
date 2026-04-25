package ru.ozero.commonvpn.split

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanRoutesTest {

    private fun ipToLong(ip: String): Long {
        val parts = ip.split('.').map { it.toLong() }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }

    private fun cidrCovers(c: LanRoutes.Cidr, ip: String): Boolean {
        val mask = if (c.prefix == 0) 0L else ((-1L) shl (32 - c.prefix)) and 0xFFFFFFFFL
        val net = ipToLong(c.address) and mask
        val target = ipToLong(ip) and mask
        return net == target
    }

    private fun coveredBy(ip: String): Boolean =
        LanRoutes.BYPASS_LAN_IPV4.any { cidrCovers(it, ip) }

    // ---- Excluded ranges should NOT be covered (LAN bypass) ------------

    @Test fun rfc1918_10NotCovered() = assertFalse(coveredBy("10.0.0.1"))
    @Test fun rfc1918_172_16NotCovered() = assertFalse(coveredBy("172.16.5.5"))
    @Test fun rfc1918_172_31NotCovered() = assertFalse(coveredBy("172.31.255.255"))
    @Test fun rfc1918_192_168NotCovered() = assertFalse(coveredBy("192.168.1.100"))
    @Test fun cgnatNotCovered() = assertFalse(coveredBy("100.96.42.7"))
    @Test fun loopbackNotCovered() = assertFalse(coveredBy("127.0.0.1"))
    @Test fun linkLocalNotCovered() = assertFalse(coveredBy("169.254.5.5"))
    @Test fun multicastNotCovered() = assertFalse(coveredBy("224.0.0.1"))
    @Test fun reservedNotCovered() = assertFalse(coveredBy("240.0.0.1"))
    @Test fun thisNetworkNotCovered() = assertFalse(coveredBy("0.0.0.5"))

    // ---- Public IPs SHOULD be covered (go through VPN) ------------------

    @Test fun googleDnsCovered() = assertTrue(coveredBy("8.8.8.8"))
    @Test fun cloudflareDnsCovered() = assertTrue(coveredBy("1.1.1.1"))
    @Test fun youtubeCovered() = assertTrue(coveredBy("142.250.190.46"))
    @Test fun edgeOf172Covered() = assertTrue(coveredBy("172.15.255.255"))
    @Test fun edgeOf172AfterCovered() = assertTrue(coveredBy("172.32.0.1"))
    @Test fun edgeOfCgnatLowerCovered() = assertTrue(coveredBy("100.63.255.255"))
    @Test fun edgeOfCgnatUpperCovered() = assertTrue(coveredBy("100.128.0.1"))

    @Test
    fun listIsNonEmpty() {
        assertTrue(LanRoutes.BYPASS_LAN_IPV4.isNotEmpty())
    }

    @Test
    fun allPrefixesAreValid() {
        for (c in LanRoutes.BYPASS_LAN_IPV4) {
            assertTrue(c.prefix in 0..32, "prefix invalid: ${c.address}/${c.prefix}")
        }
    }

    @Test
    fun cidrsHaveAddressOnNetworkBoundary() {
        for (c in LanRoutes.BYPASS_LAN_IPV4) {
            val mask = if (c.prefix == 0) 0L else ((-1L) shl (32 - c.prefix)) and 0xFFFFFFFFL
            val a = ipToLong(c.address)
            assertEquals(a and mask, a, "address не на границе сети: ${c.address}/${c.prefix}")
        }
    }
}
