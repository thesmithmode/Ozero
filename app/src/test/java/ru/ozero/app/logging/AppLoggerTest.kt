package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLoggerTest {

    @Test
    fun `plain log methods append entries with matching levels`() {
        val buffer = LogBuffer()
        AppLogger.attach(buffer)

        AppLogger.v("Tag", "trace")
        AppLogger.d("Tag", "debug")
        AppLogger.i("Tag", "info")
        AppLogger.w("Tag", "warn")
        AppLogger.e("Tag", "error")

        val entries = buffer.entries.value
        assertEquals(
            listOf(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),
            entries.map { it.level },
        )
        assertEquals(listOf("trace", "debug", "info", "warn", "error"), entries.map { it.message })
        assertTrue(entries.all { it.tag == "Tag" })
    }

    @Test
    fun `throwable overloads include exception and cause details`() {
        val buffer = LogBuffer()
        AppLogger.attach(buffer)

        AppLogger.w("WarnTag", "warning", IllegalArgumentException("bad"))
        AppLogger.e(
            "ErrorTag",
            "failed",
            IllegalStateException("outer", IllegalArgumentException("inner")),
        )

        val entries = buffer.entries.value
        assertEquals(LogLevel.WARN, entries[0].level)
        assertEquals("WarnTag", entries[0].tag)
        assertTrue(entries[0].message.contains("IllegalArgumentException: bad"))
        assertEquals(LogLevel.ERROR, entries[1].level)
        assertEquals("ErrorTag", entries[1].tag)
        assertTrue(entries[1].message.contains("IllegalStateException: outer"))
        assertTrue(entries[1].message.contains("caused by IllegalArgumentException: inner"))
    }

    @Test
    fun `error throwable without cause omits caused by suffix`() {
        val buffer = LogBuffer()
        AppLogger.attach(buffer)

        AppLogger.e("ErrorTag", "failed", IllegalStateException("outer"))

        val entry = buffer.entries.value.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertTrue(entry.message.contains("IllegalStateException: outer"))
        assertTrue(!entry.message.contains("caused by"))
    }
}
