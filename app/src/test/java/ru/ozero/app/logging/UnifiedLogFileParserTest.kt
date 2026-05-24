package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedLogFileParserTest {

    @Test
    fun `parseLine — корректный INFO`() {
        val line = "2026-05-25 12:34:56.789 INFO [main] SomeTag: hello world"
        val entry = UnifiedLogFileParser.parseLine(line)
        assertEquals(LogLevel.INFO, entry?.level)
        assertEquals("SomeTag", entry?.tag)
        assertEquals("hello world", entry?.message)
    }

    @Test
    fun `parseLine — WARN с thread-name с пробелами`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 00:00:00.001 WARN [DefaultDispatcher-worker-1] RealWarpSdkBridge: msg",
        )
        assertEquals(LogLevel.WARN, entry?.level)
        assertEquals("RealWarpSdkBridge", entry?.tag)
    }

    @Test
    fun `parseLine — ERROR`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 10:00:00.000 ERROR [IO] CrashLog: boom",
        )
        assertEquals(LogLevel.ERROR, entry?.level)
    }

    @Test
    fun `parseLine — DEBUG`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 10:00:00.000 DEBUG [Thread-1] Tag: debug msg",
        )
        assertEquals(LogLevel.DEBUG, entry?.level)
    }

    @Test
    fun `parseLine — TRACE`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 10:00:00.000 TRACE [T] Tag: verbose",
        )
        assertEquals(LogLevel.TRACE, entry?.level)
    }

    @Test
    fun `parseLine — VERBOSE маппится в TRACE`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 10:00:00.000 VERBOSE [T] Tag: v",
        )
        assertEquals(LogLevel.TRACE, entry?.level)
    }

    @Test
    fun `parseLine — строка LOGCAT пропускается`() {
        val raw = "LOGCAT 05-25 10:00:00.000  1234  1234 W Tag    : msg"
        assertNull(UnifiedLogFileParser.parseLine(raw))
    }

    @Test
    fun `parseLine — пустая строка`() {
        assertNull(UnifiedLogFileParser.parseLine(""))
    }

    @Test
    fun `parseLine — мусор`() {
        assertNull(UnifiedLogFileParser.parseLine("not a log line at all"))
    }

    @Test
    fun `parseLine — pid всегда 0 (cross-process)`() {
        val entry = UnifiedLogFileParser.parseLine(
            "2026-05-25 10:00:00.000 INFO [main] Tag: msg",
        )
        assertEquals(0, entry?.pid)
    }

    @Test
    fun `parseAll — возвращает только валидные строки`() {
        val text = """
            2026-05-25 10:00:00.000 INFO [main] TagA: first
            not a log line
            LOGCAT 05-25 10:00:00.100  1234  1234 I TagB    : raw
            2026-05-25 10:00:01.000 WARN [IO] TagC: third
        """.trimIndent()
        val entries = UnifiedLogFileParser.parseAll(text)
        assertEquals(2, entries.size)
        assertEquals("TagA", entries[0].tag)
        assertEquals("TagC", entries[1].tag)
    }

    @Test
    fun `parseAll — пустой текст`() {
        assertTrue(UnifiedLogFileParser.parseAll("").isEmpty())
    }
}
