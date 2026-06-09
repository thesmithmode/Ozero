package ru.ozero.commonvpn

import android.net.TrafficStats
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunnelStatsHelpersCoverageTest {

    @Test
    fun `parseProcNetDev returns rx and tx for requested interface only`() {
        val header = "Inter-| Receive | Transmit\n face |bytes packets|bytes packets"
        val content = """
            $header
                lo: 10 1 0 0 0 0 0 0 20 2 0 0 0 0 0 0
              tun0: 100 2 0 0 0 0 0 0 900 3 0 0 0 0 0 0
              tun1: 200 2 0 0 0 0 0 0 800 3 0 0 0 0 0 0
        """.trimIndent()

        assertEquals(100L to 900L, TunInterfaceStats.parseProcNetDev(content, "tun0"))
        assertEquals(200L to 800L, TunInterfaceStats.parseProcNetDev(content, "tun1"))
        assertNull(TunInterfaceStats.parseProcNetDev(content, "tun2"))
    }

    @Test
    fun `parseProcNetDev rejects short and malformed interface rows`() {
        assertNull(TunInterfaceStats.parseProcNetDev("tun0: 1 2 3", "tun0"))
        assertNull(
            TunInterfaceStats.parseProcNetDev(
                "tun0: nope 2 0 0 0 0 0 0 9 0 0 0 0 0 0 0",
                "tun0",
            ),
        )
        assertNull(
            TunInterfaceStats.parseProcNetDev(
                "tun0: 1 2 0 0 0 0 0 0 nope 0 0 0 0 0 0 0",
                "tun0",
            ),
        )
    }

    @Test
    fun `parseTunInterfaces trims names and keeps only tun interfaces`() {
        val parsed = TunInterfaceStats.parseTunInterfaces(
            """
                lo: 1 2
                  tun0: 1 2
                wlan0: 1 2
                  tun12: 1 2
                notun: 1 2
                broken row
            """.trimIndent(),
        )

        assertEquals(setOf("tun0", "tun12"), parsed)
    }

    @Test
    fun `pickNewTunInterface prefers new highest tun suffix and falls back to highest existing`() {
        assertEquals("tun12", TunInterfaceStats.pickNewTunInterface(setOf("tun0"), setOf("tun0", "tun2", "tun12")))
        assertEquals("tun9", TunInterfaceStats.pickNewTunInterface(setOf("tun0", "tun9"), setOf("tun0", "tun9")))
        assertNull(TunInterfaceStats.pickNewTunInterface(setOf("tun0"), emptySet()))
        assertEquals("tunX", TunInterfaceStats.pickNewTunInterface(emptySet(), setOf("tunX", "tun0")))
    }

    @Test
    fun `uid traffic stats returns snapshot only for supported non-negative counters`() {
        mockkStatic(TrafficStats::class)
        try {
            every { TrafficStats.getUidRxBytes(100) } returns 11L
            every { TrafficStats.getUidTxBytes(100) } returns 22L
            assertEquals(UidTrafficStats.Snapshot(rxBytes = 11, txBytes = 22), UidTrafficStats.read(100))

            every { TrafficStats.getUidRxBytes(101) } returns TrafficStats.UNSUPPORTED.toLong()
            every { TrafficStats.getUidTxBytes(101) } returns 22L
            assertNull(UidTrafficStats.read(101))

            every { TrafficStats.getUidRxBytes(102) } returns 11L
            every { TrafficStats.getUidTxBytes(102) } returns TrafficStats.UNSUPPORTED.toLong()
            assertNull(UidTrafficStats.read(102))

            every { TrafficStats.getUidRxBytes(103) } returns -2L
            every { TrafficStats.getUidTxBytes(103) } returns 22L
            assertNull(UidTrafficStats.read(103))

            every { TrafficStats.getUidRxBytes(104) } returns 11L
            every { TrafficStats.getUidTxBytes(104) } returns -2L
            assertNull(UidTrafficStats.read(104))
        } finally {
            unmockkStatic(TrafficStats::class)
        }
    }
}
