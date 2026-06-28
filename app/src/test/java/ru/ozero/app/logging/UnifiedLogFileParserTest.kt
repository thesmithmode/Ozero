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

    @Test
    fun `filterByLevel — WARN фильтрует только warn и error`() {
        val text = """
            2026-05-25 10:00:00.000 TRACE [T] A: trace msg
            2026-05-25 10:00:01.000 DEBUG [T] B: debug msg
            2026-05-25 10:00:02.000 INFO [T] C: info msg
            2026-05-25 10:00:03.000 WARN [T] D: warn msg
            2026-05-25 10:00:04.000 ERROR [T] E: error msg
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.WARN)
        assertTrue(filtered.contains("warn msg"))
        assertTrue(filtered.contains("error msg"))
        assertTrue(!filtered.contains("trace msg"))
        assertTrue(!filtered.contains("debug msg"))
        assertTrue(!filtered.contains("info msg"))
    }

    @Test
    fun `filterByLevel — TRACE возвращает всё`() {
        val text = """
            2026-05-25 10:00:00.000 TRACE [T] A: t
            2026-05-25 10:00:01.000 INFO [T] B: i
            2026-05-25 10:00:02.000 ERROR [T] C: e
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.TRACE)
        assertTrue(filtered.contains("A: t"))
        assertTrue(filtered.contains("B: i"))
        assertTrue(filtered.contains("C: e"))
    }

    @Test
    fun `filterByLevel — ERROR возвращает только error`() {
        val text = """
            2026-05-25 10:00:00.000 WARN [T] A: w
            2026-05-25 10:00:01.000 ERROR [T] B: e
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.ERROR)
        assertTrue(!filtered.contains("A: w"))
        assertTrue(filtered.contains("B: e"))
    }

    @Test
    fun `filterByLevel — многострочная запись включается вместе с заголовком`() {
        val text = """
            2026-05-25 10:00:00.000 INFO [T] A: start
            2026-05-25 10:00:01.000 ERROR [T] B: crash
            java.lang.RuntimeException: boom
                at com.example.Foo.bar(Foo.kt:42)
            2026-05-25 10:00:02.000 DEBUG [T] C: after
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.ERROR)
        assertTrue(filtered.contains("crash"))
        assertTrue(filtered.contains("RuntimeException"))
        assertTrue(filtered.contains("at com.example"))
        assertTrue(!filtered.contains("start"))
        assertTrue(!filtered.contains("after"))
    }

    @Test
    fun `filterByLevel — LOGCAT строки исключаются из фильтрованного вывода`() {
        val text = """
            2026-05-25 10:00:00.000 WARN [T] A: warning
            LOGCAT 05-25 10:00:00.100  1234  1234 I Tag    : raw logcat line
            2026-05-25 10:00:01.000 DEBUG [T] B: debug
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.WARN)
        assertTrue(filtered.contains("warning"))
        assertTrue(!filtered.contains("raw logcat line"))
        assertTrue(!filtered.contains("debug"))
    }

    @Test
    fun `filterByLevel — spoofed level token in continuation does not change filter`() {
        val text = """
            2026-05-25 10:00:00.000 DEBUG [T] A: debug start
            continuation 2026-05-25 10:00:01.000 ERROR fake token
            debug continuation secret
            2026-05-25 10:00:02.000 ERROR [T] B: real error
        """.trimIndent()
        val filtered = UnifiedLogFileParser.filterByLevel(text, LogLevel.ERROR)
        assertTrue(!filtered.contains("debug start"))
        assertTrue(!filtered.contains("fake token"))
        assertTrue(!filtered.contains("debug continuation secret"))
        assertTrue(filtered.contains("real error"))
    }

    @Test
    fun `LogLevel severity order is correct`() {
        assertTrue(LogLevel.TRACE.severity < LogLevel.DEBUG.severity)
        assertTrue(LogLevel.DEBUG.severity < LogLevel.INFO.severity)
        assertTrue(LogLevel.INFO.severity < LogLevel.WARN.severity)
        assertTrue(LogLevel.WARN.severity < LogLevel.ERROR.severity)
    }
}
