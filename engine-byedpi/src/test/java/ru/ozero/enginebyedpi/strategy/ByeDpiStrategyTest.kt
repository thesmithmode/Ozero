package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByeDpiStrategyTest {

    @Test
    fun `split toArgs format`() {
        val s = ByeDpiStrategy(DesyncMethod.SPLIT, splitAt = 4)
        assertEquals("--desync split:4", s.toArgs())
    }

    @Test
    fun `disorder toArgs format`() {
        val s = ByeDpiStrategy(DesyncMethod.DISORDER, splitAt = 16)
        assertEquals("--desync disorder:16", s.toArgs())
    }

    @Test
    fun `fake adds fake-ttl arg`() {
        val s = ByeDpiStrategy(DesyncMethod.FAKE, splitAt = 4, fakeTtl = 8)
        assertEquals("--desync fake:4 --fake-ttl 8", s.toArgs())
    }

    @Test
    fun `oob adds oob-byte arg`() {
        val s = ByeDpiStrategy(DesyncMethod.OOB, splitAt = 1, oobByte = 0xFF)
        assertEquals("--desync oob:1 --oob-byte 255", s.toArgs())
    }

    @Test
    fun `fake without ttl skips fake-ttl arg`() {
        val s = ByeDpiStrategy(DesyncMethod.FAKE, splitAt = 4, fakeTtl = null)
        assertEquals("--desync fake:4", s.toArgs())
    }
}

class ByeDpiStrategyMatrixTest {

    @Test
    fun `generate produces ordered list with split first`() {
        val list = ByeDpiStrategyMatrix.generate()
        assertTrue(list.isNotEmpty())
        assertEquals(DesyncMethod.SPLIT, list.first().desyncMethod)
        assertEquals(1, list.first().splitAt)
    }

    @Test
    fun `generate stays under 50 candidates (probe time budget)`() {
        val list = ByeDpiStrategyMatrix.generate()
        assertTrue(list.size <= 50, "matrix=${list.size}")
    }

    @Test
    fun `generate covers all desync methods`() {
        val methods = ByeDpiStrategyMatrix.generate().map { it.desyncMethod }.toSet()
        assertEquals(DesyncMethod.entries.toSet(), methods)
    }

    @Test
    fun `all fake variants have fakeTtl set`() {
        ByeDpiStrategyMatrix.generate()
            .filter { it.desyncMethod == DesyncMethod.FAKE }
            .forEach { assertTrue(it.fakeTtl != null, "fake без fakeTtl: $it") }
    }

    @Test
    fun `all oob variants have oobByte set`() {
        ByeDpiStrategyMatrix.generate()
            .filter { it.desyncMethod == DesyncMethod.OOB }
            .forEach { assertTrue(it.oobByte != null, "oob без oobByte: $it") }
    }
}

class ByeDpiStrategyGeneratorTest {

    @Test
    fun `findWinning returns first passing strategy`() = kotlinx.coroutines.test.runTest {
        val gen = ByeDpiStrategyGenerator(
            matrix = listOf(
                ByeDpiStrategy(DesyncMethod.SPLIT, 1),
                ByeDpiStrategy(DesyncMethod.SPLIT, 2),
                ByeDpiStrategy(DesyncMethod.SPLIT, 4),
            ),
        )
        var calls = 0
        val winner = gen.findWinning { s ->
            calls++
            s.splitAt == 2
        }
        assertEquals(2, winner?.splitAt)
        assertEquals(2, calls) 
    }

    @Test
    fun `findWinning returns null when none pass`() = kotlinx.coroutines.test.runTest {
        val gen = ByeDpiStrategyGenerator(
            matrix = listOf(ByeDpiStrategy(DesyncMethod.SPLIT, 1)),
        )
        val winner = gen.findWinning { false }
        assertEquals(null, winner)
    }

    @Test
    fun `findWinning catches probe exceptions and continues`() = kotlinx.coroutines.test.runTest {
        val gen = ByeDpiStrategyGenerator(
            matrix = listOf(
                ByeDpiStrategy(DesyncMethod.SPLIT, 1),
                ByeDpiStrategy(DesyncMethod.SPLIT, 2),
            ),
        )
        val winner = gen.findWinning { s ->
            if (s.splitAt == 1) error("crash on first") else true
        }
        assertEquals(2, winner?.splitAt)
    }

    @Test
    fun `defaultStrategy returns split 1`() {
        val gen = ByeDpiStrategyGenerator()
        val def = gen.defaultStrategy()
        assertEquals(DesyncMethod.SPLIT, def.desyncMethod)
        assertEquals(1, def.splitAt)
    }
}
