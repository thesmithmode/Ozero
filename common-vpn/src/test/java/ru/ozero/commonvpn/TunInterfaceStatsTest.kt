package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunInterfaceStatsTest {

    private val sample = """
        Inter-|   Receive                                                |  Transmit
         face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
            lo:  150000      300    0    0    0     0          0         0   150000      300    0    0    0     0       0          0
          eth0:12345678     5000    0    0    0     0          0         0  9876543     4000    0    0    0     0       0          0
          tun0:1048576      1000    0    0    0     0          0         0  2097152     1500    0    0    0     0       0          0
          tun1:       0        0    0    0    0     0          0         0        0        0    0    0    0     0       0          0
    """.trimIndent()

    @Test
    fun `parseProcNetDev извлекает rx tx для указанного интерфейса`() {
        val r = TunInterfaceStats.parseProcNetDev(sample, "tun0")
        assertEquals(1048576L to 2097152L, r)
    }

    @Test
    fun `parseProcNetDev возвращает null если интерфейс отсутствует`() {
        val r = TunInterfaceStats.parseProcNetDev(sample, "tun7")
        assertNull(r)
    }

    @Test
    fun `parseProcNetDev игнорирует lo и eth когда ищет tun`() {
        val r = TunInterfaceStats.parseProcNetDev(sample, "lo")
        assertEquals(150000L to 150000L, r)
    }

    @Test
    fun `parseProcNetDev парсит когда несколько tun интерфейсов`() {
        val r0 = TunInterfaceStats.parseProcNetDev(sample, "tun0")
        val r1 = TunInterfaceStats.parseProcNetDev(sample, "tun1")
        assertEquals(1048576L to 2097152L, r0)
        assertEquals(0L to 0L, r1)
    }

    @Test
    fun `parseProcNetDev парсит большие Long значения byte counts`() {
        val big = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
              tun0:9999999999999    1000    0    0    0     0          0         0  8888888888888     1500    0    0    0     0       0          0
        """.trimIndent()
        val r = TunInterfaceStats.parseProcNetDev(big, "tun0")
        assertEquals(9999999999999L to 8888888888888L, r)
    }

    @Test
    fun `snapshotTunInterfaces возвращает только tun префикс`() {
        val tuns = TunInterfaceStats.parseTunInterfaces(sample)
        assertTrue("tun0" in tuns)
        assertTrue("tun1" in tuns)
        assertEquals(2, tuns.size)
    }

    @Test
    fun `pickNewTunInterface выбирает дельту`() {
        val before = setOf("tun0")
        val after = setOf("tun0", "tun1")
        assertEquals("tun1", TunInterfaceStats.pickNewTunInterface(before, after))
    }

    @Test
    fun `pickNewTunInterface fallback на максимальный tun суффикс если diff пустой`() {
        val before = setOf("tun0", "tun5")
        val after = setOf("tun0", "tun5")
        assertEquals("tun5", TunInterfaceStats.pickNewTunInterface(before, after))
    }

    @Test
    fun `pickNewTunInterface возвращает null если after пустой`() {
        assertNull(TunInterfaceStats.pickNewTunInterface(emptySet(), emptySet()))
    }

    @Test
    fun `pickNewTunInterface fallback использует наибольший число а не лекс порядок`() {
        val before = emptySet<String>()
        val after = setOf("tun0", "tun10", "tun2")
        assertEquals("tun10", TunInterfaceStats.pickNewTunInterface(before, after))
    }
}
