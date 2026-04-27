package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogBufferTest {

    private fun entry(i: Int, level: LogLevel = LogLevel.INFO) = LogEntry(
        timestampMs = i.toLong(),
        level = level,
        tag = "T",
        pid = 1,
        message = "m$i",
    )

    @Test
    fun `circular buffer drops oldest at capacity`() {
        val buf = LogBuffer(capacity = 3)
        repeat(5) { buf.append(entry(it)) }
        val list = buf.entries.value
        assertEquals(3, list.size)
        assertEquals("m2", list.first().message)
        assertEquals("m4", list.last().message)
    }

    @Test
    fun `clear empties buffer`() {
        val buf = LogBuffer(capacity = 10)
        repeat(3) { buf.append(entry(it)) }
        buf.clear()
        assertTrue(buf.entries.value.isEmpty())
        assertEquals(0, buf.size())
    }

    @Test
    fun `entries flow updates atomically on append`() {
        val buf = LogBuffer(capacity = 100)
        buf.append(entry(1))
        val s1 = buf.entries.value
        buf.append(entry(2))
        val s2 = buf.entries.value
        // Snapshot s1 не мутирует после второго append
        assertEquals(1, s1.size)
        assertEquals(2, s2.size)
    }
}
