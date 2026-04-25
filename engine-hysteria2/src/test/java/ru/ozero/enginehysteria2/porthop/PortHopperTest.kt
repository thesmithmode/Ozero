package ru.ozero.enginehysteria2.porthop

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PortHopperTest {

    private val range = 20000..50000

    @Test
    fun portInRange() {
        val hopper = PortHopper(authKey = "auth-secret", range = range, intervalSeconds = 30)
        repeat(1000) { i ->
            val p = hopper.portAt(epochSeconds = i * 7L)
            assertTrue(p in range, "port=$p out of $range at i=$i")
        }
    }

    @Test
    fun deterministicForSameInputs() {
        val a = PortHopper("k", range, 30)
        val b = PortHopper("k", range, 30)
        assertEquals(a.portAt(123_000L), b.portAt(123_000L))
    }

    @Test
    fun differentKeysProduceDifferentSequences() {
        val a = PortHopper("alpha", range, 30)
        val b = PortHopper("bravo", range, 30)
        // С достаточным числом точек последовательности должны различаться
        val matches = (0 until 200).count { i ->
            a.portAt(i * 30L) == b.portAt(i * 30L)
        }
        assertTrue(matches < 200, "ключи дают идентичные последовательности — HMAC не работает")
    }

    @Test
    fun stableWithinSameInterval() {
        val h = PortHopper("k", range, 30)
        // slot = 90/30 = 3 → epoch ∈ [90..119] в одном окне
        val p1 = h.portAt(90L)
        val p2 = h.portAt(105L)
        val p3 = h.portAt(119L)
        assertEquals(p1, p2)
        assertEquals(p2, p3)
    }

    @Test
    fun changesAtIntervalBoundary() {
        val h = PortHopper("k", range, 30)
        val before = h.portAt(29L)
        val after = h.portAt(30L)
        // С вероятностью 1/30000 совпадут — для конкретного ключа проверим, что хотя бы
        // одна из ближайших границ даёт смену.
        val a90 = h.portAt(60L)
        val a120 = h.portAt(90L)
        val changed = (before != after) || (after != a90) || (a90 != a120)
        assertTrue(changed, "порт не меняется на границах интервалов")
    }

    @Test
    fun nextHopAtReturnsUpcomingBoundary() {
        val h = PortHopper("k", range, 30)
        assertEquals(30L, h.nextHopAt(0L))
        assertEquals(30L, h.nextHopAt(15L))
        assertEquals(60L, h.nextHopAt(30L))
        assertEquals(60L, h.nextHopAt(45L))
        assertEquals(90L, h.nextHopAt(60L))
    }

    @Test
    fun rejectsInvalidRange() {
        val ex = runCatching { PortHopper("k", 50000..20000, 30) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsRangeOutsidePortSpace() {
        val ex1 = runCatching { PortHopper("k", 0..100, 30) }.exceptionOrNull()
        val ex2 = runCatching { PortHopper("k", 100..70000, 30) }.exceptionOrNull()
        assertTrue(ex1 is IllegalArgumentException)
        assertTrue(ex2 is IllegalArgumentException)
    }

    @Test
    fun rejectsNonPositiveInterval() {
        val ex = runCatching { PortHopper("k", range, 0) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun rejectsBlankAuthKey() {
        val ex = runCatching { PortHopper("", range, 30) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun distributionCoversRangeBroadly() {
        val h = PortHopper("k", range, 30)
        val seen = HashSet<Int>()
        repeat(5000) { i -> seen += h.portAt(i * 30L) }
        // 5000 точек на диапазон 30001 — ожидаем покрытие хотя бы 4000 уникальных
        assertTrue(seen.size > 4000, "покрытие слишком узкое: ${seen.size}")
    }

    @Test
    fun differentRangesProduceDifferentPorts() {
        val a = PortHopper("k", 20000..30000, 30)
        val b = PortHopper("k", 40000..50000, 30)
        val pa = a.portAt(60L)
        val pb = b.portAt(60L)
        assertNotEquals(pa, pb)
        assertTrue(pa in 20000..30000)
        assertTrue(pb in 40000..50000)
    }
}
