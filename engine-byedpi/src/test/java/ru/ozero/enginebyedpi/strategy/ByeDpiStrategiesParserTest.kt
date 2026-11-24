package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `parse bundled strategies without duplicates`() {
        val asset = assertNotNull(
            ByeDpiStrategiesParserTest::class.java.classLoader!!.getResourceAsStream("byedpi_strategies.list"),
        )
        val content = asset.bufferedReader().use { it.readText() }
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
        assertEquals(89, ByeDpiStrategiesParser.EXPECTED_COUNT)
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

    @Test
    fun `bundled strategy assets and test fixture stay synchronized`() {
        val module = File(System.getProperty("user.dir") ?: ".")
        val engineAsset = module.resolve("src/main/assets/byedpi_strategies.list").readText()
        val appAsset = module.resolve("../app/src/main/assets/proxytest_strategies.list").readText()
        val fixture = assertNotNull(
            ByeDpiStrategiesParserTest::class.java.classLoader!!.getResourceAsStream("byedpi_strategies.list"),
        ).bufferedReader().use { it.readText() }

        assertEquals(engineAsset, appAsset)
        assertEquals(engineAsset, fixture)
        val strategies = ByeDpiStrategiesParser.parse(engineAsset)
        assertEquals(ByeDpiStrategiesParser.EXPECTED_COUNT, strategies.size)
        strategies.forEach { strategy ->
            assertTrue(ByeDpiArgvValidator.isValid(strategy.command), strategy.command)
        }
    }

    @Test
    fun `additional bundled strategies are available to evolution as valid seeds`() {
        val module = File(System.getProperty("user.dir") ?: ".")
        val asset = module.resolve("src/main/assets/byedpi_strategies.list").readText()
        val additional = ByeDpiStrategiesParser.parse(asset).takeLast(11)
        val seeds = additional.map { it.command.replace("-n \"google.com\" ", "") }

        assertEquals(11, seeds.distinct().size)
        seeds.forEach { seed ->
            assertTrue(seed in ByeDpiKnownSeeds.commands, seed)
            assertTrue(ByeDpiArgvValidator.isValid(seed), seed)
        }
    }
}
