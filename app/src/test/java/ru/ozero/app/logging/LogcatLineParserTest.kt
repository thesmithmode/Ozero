package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LogcatLineParserTest {

    @Test
    fun `parses standard threadtime line`() {
        val line = "04-27 10:32:18.421  1234  5678 I OzeroVpn: startVpn"
        val entry = assertNotNull(LogcatLineParser.parse(line, nowYear = 2026))
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("OzeroVpn", entry.tag)
        assertEquals(1234, entry.pid)
        assertEquals("startVpn", entry.message)
    }

    @Test
    fun `maps levels including fatal`() {
        val cases = mapOf(
            "V" to LogLevel.TRACE,
            "D" to LogLevel.DEBUG,
            "I" to LogLevel.INFO,
            "W" to LogLevel.WARN,
            "E" to LogLevel.ERROR,
            "F" to LogLevel.ERROR,
        )
        cases.forEach { (sym, expected) ->
            val entry = assertNotNull(
                LogcatLineParser.parse("01-15 00:00:00.000  1  2 $sym Tag: msg", nowYear = 2026),
            )
            assertEquals(expected, entry.level, "for $sym")
        }
    }

    @Test
    fun `tag with spaces preserved by trim`() {
        val line = "04-27 10:32:18.421  1  2 W My Tag: hello"
        val entry = assertNotNull(LogcatLineParser.parse(line, nowYear = 2026))
        assertEquals("My Tag", entry.tag)
        assertEquals("hello", entry.message)
    }

    @Test
    fun `returns null for non-matching line`() {
        assertNull(LogcatLineParser.parse("--------- beginning of main", nowYear = 2026))
        assertNull(LogcatLineParser.parse("", nowYear = 2026))
        assertNull(LogcatLineParser.parse("garbage line", nowYear = 2026))
    }

    @Test
    fun `empty message OK`() {
        val line = "04-27 10:32:18.421  1  2 I Tag: "
        val entry = assertNotNull(LogcatLineParser.parse(line, nowYear = 2026))
        assertEquals("", entry.message)
    }
}
