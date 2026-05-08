package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByeDpiStrategiesParserTest {

    @Test
    fun `parse пустой content returns empty list`() {
        assertTrue(ByeDpiStrategiesParser.parse("").isEmpty())
    }

    @Test
    fun `parse single line returns single strategy`() {
        val result = ByeDpiStrategiesParser.parse("-d1 -s3+s -a1")
        assertEquals(1, result.size)
        assertEquals("-d1 -s3+s -a1", result[0].command)
    }

    @Test
    fun `parse multiline returns multiple strategies`() {
        val result = ByeDpiStrategiesParser.parse(
            """
            -d1 -s3+s -a1
            -o3 -d7 -a1
            -d7 -s2 -a1
            """.trimIndent(),
        )
        assertEquals(3, result.size)
        assertEquals("-d1 -s3+s -a1", result[0].command)
        assertEquals("-o3 -d7 -a1", result[1].command)
        assertEquals("-d7 -s2 -a1", result[2].command)
    }

    @Test
    fun `parse trims whitespace`() {
        val result = ByeDpiStrategiesParser.parse("   -d1 -a1   \n   -o2 -a1   ")
        assertEquals(2, result.size)
        assertEquals("-d1 -a1", result[0].command)
        assertEquals("-o2 -a1", result[1].command)
    }

    @Test
    fun `parse skips blank lines`() {
        val result = ByeDpiStrategiesParser.parse("-d1 -a1\n\n\n-o2 -a1")
        assertEquals(2, result.size)
    }

    @Test
    fun `parse заменяет sni placeholder на дефолт google_com`() {
        val result = ByeDpiStrategiesParser.parse("-n {sni} -Qr -a1")
        assertEquals("-n \"google.com\" -Qr -a1", result[0].command)
    }

    @Test
    fun `parse заменяет sni placeholder на custom`() {
        val result = ByeDpiStrategiesParser.parse(
            "-n {sni} -Qr -a1",
            sniValue = "youtube.com",
        )
        assertEquals("-n \"youtube.com\" -Qr -a1", result[0].command)
    }

    @Test
    fun `parse 75 строк из embedded asset content`() {
        val asset = ByeDpiStrategiesParserTest::class.java.classLoader!!
            .getResourceAsStream("byedpi_strategies.list")
        if (asset == null) {
            // skip — asset не подцепился через test resource
            return
        }
        val content = asset.bufferedReader().readText()
        val strategies = ByeDpiStrategiesParser.parse(content)
        assertEquals(ByeDpiStrategiesParser.EXPECTED_COUNT, strategies.size)
        assertTrue(strategies.all { it.command.isNotBlank() })
        val unique = strategies.map { it.command }.toSet()
        assertEquals(strategies.size, unique.size, "стратегии должны быть уникальными")
    }

    @Test
    fun `parse skips comment lines starting with hash`() {
        val result = ByeDpiStrategiesParser.parse("# comment\n-d1 -a1\n# another comment\n-o2 -a1")
        assertEquals(2, result.size)
        assertEquals("-d1 -a1", result[0].command)
        assertEquals("-o2 -a1", result[1].command)
    }

    @Test
    fun `parse skips hash-only line`() {
        val result = ByeDpiStrategiesParser.parse("#\n-d1 -a1")
        assertEquals(1, result.size)
    }

    @Test
    fun `default constants`() {
        assertEquals("google.com", ByeDpiStrategiesParser.DEFAULT_SNI)
        assertEquals(75, ByeDpiStrategiesParser.EXPECTED_COUNT)
    }

    @Test
    fun `commands не пустые после parse`() {
        val result = ByeDpiStrategiesParser.parse(
            """
            -d1 -a1

            -o2 -a1
            """.trimIndent(),
        )
        assertFalse(result.any { it.command.isBlank() })
    }
}
