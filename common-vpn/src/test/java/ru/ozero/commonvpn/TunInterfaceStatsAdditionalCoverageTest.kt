package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunInterfaceStatsAdditionalCoverageTest {

    @Test
    fun `parseProcNetDev returns null for short and malformed counter rows`() {
        val short = "tun0: 1 2 3"
        val badRx = "tun0: nope 0 0 0 0 0 0 0 20 0 0 0 0 0 0 0"
        val badTx = "tun0: 10 0 0 0 0 0 0 0 nope 0 0 0 0 0 0 0"

        assertNull(TunInterfaceStats.parseProcNetDev(short, "tun0"))
        assertNull(TunInterfaceStats.parseProcNetDev(badRx, "tun0"))
        assertNull(TunInterfaceStats.parseProcNetDev(badTx, "tun0"))
    }

    @Test
    fun `parseTunInterfaces ignores headers blank names and non tun interfaces`() {
        val content = """
            Inter-| Receive | Transmit
             : no-name
             wlan0: 1 2 3
             mytun0: 1 2 3
             tunX: 1 2 3
               tun42: 1 2 3
        """.trimIndent()

        val tuns = TunInterfaceStats.parseTunInterfaces(content)

        assertEquals(setOf("tunX", "tun42"), tuns)
    }

    @Test
    fun `pickNewTunInterface treats non numeric suffix as zero`() {
        assertEquals("tun2", TunInterfaceStats.pickNewTunInterface(emptySet(), setOf("tunX", "tun2")))
        assertEquals("tunX", TunInterfaceStats.pickNewTunInterface(emptySet(), setOf("tunX")))
    }
}
