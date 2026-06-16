package ru.ozero.app.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogEntryAndTailReaderTest {

    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("log_tail_reader_test").toFile()
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `fromShort maps fatal Android levels to error`() {
        assertEquals(LogLevel.ERROR, LogLevel.fromShort('E'))
        assertEquals(LogLevel.ERROR, LogLevel.fromShort('F'))
        assertEquals(LogLevel.ERROR, LogLevel.fromShort('A'))
    }

    @Test
    fun `fromShort maps unknown level to info`() {
        assertEquals(LogLevel.INFO, LogLevel.fromShort('?'))
        assertEquals(LogLevel.INFO, LogLevel.fromShort(' '))
    }

    @Test
    fun `read concatenates previous and current files when both exist`() {
        val prev = file("prev.log", "old\n")
        val current = file("current.log", "new\n")

        assertEquals("old\nnew\n", LogTailReader.read(current, prev))
    }

    @Test
    fun `read ignores missing files`() {
        val missing = File(tempDir, "missing.log")
        val current = file("current.log", "new\n")

        assertEquals("new\n", LogTailReader.read(current, missing))
        assertEquals("", LogTailReader.read(missing, null))
    }

    @Test
    fun `readTail returns empty for null or missing current file`() {
        assertEquals("", LogTailReader.readTail(null, null, maxBytes = 10))
        assertEquals("", LogTailReader.readTail(File(tempDir, "missing.log"), null, maxBytes = 10))
    }

    @Test
    fun `readTail returns current tail when current file exceeds limit`() {
        val current = file("current.log", "0123456789")

        assertEquals("56789", LogTailReader.readTail(current, null, maxBytes = 5))
    }

    @Test
    fun `readTail prepends previous tail when current file is below limit`() {
        val prev = file("prev.log", "previous-")
        val current = file("current.log", "current")

        assertEquals("ous-current", LogTailReader.readTail(current, prev, maxBytes = 11))
    }

    @Test
    fun `readTail ignores previous file when current alone reaches limit`() {
        val prev = file("prev.log", "old")
        val current = file("current.log", "1234567890")

        assertEquals("67890", LogTailReader.readTail(current, prev, maxBytes = 5))
    }

    @Test
    fun `now creates entry with requested level tag and message`() {
        val before = System.currentTimeMillis()
        val entry = LogEntry.now(LogLevel.WARN, "Tag", "message")
        val after = System.currentTimeMillis()

        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("Tag", entry.tag)
        assertEquals("message", entry.message)
        assertTrue(entry.timestampMs in before..after)
        assertTrue(entry.pid >= 0)
    }

    private fun file(name: String, text: String): File =
        File(tempDir, name).also { it.writeText(text) }
}
