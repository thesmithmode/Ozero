package ru.ozero.app.logging

import android.content.Context
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.ByteArrayInputStream
import java.io.IOException
import java.lang.ProcessBuilder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogcatReaderTest {

    @TempDir
    lateinit var tmp: File

    @BeforeEach
    fun setUp() {
        LogFileStore.resetForTest()
    }

    @Test
    fun `clearAll clears buffer even when logcat reset command fails`() {
        val buffer = LogBuffer()
        buffer.append(entry("before-clear"))
        val reader = LogcatReader(buffer)

        reader.clearAll()

        assertEquals(0, buffer.size())
    }

    @Test
    fun `backoffMs grows exponentially and caps at max delay`() {
        val reader = LogcatReader(LogBuffer())
        val method = LogcatReader::class.java.getDeclaredMethod("backoffMs", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }

        assertEquals(250L, method.invoke(reader, 0))
        assertEquals(8_000L, method.invoke(reader, 10))
        assertEquals(8_000L, method.invoke(reader, 99))
    }

    @Test
    fun `diagnostic helper creates logcat reader entry`() {
        BootFileLogger.init(mockContext(tmp))
        val reader = LogcatReader(LogBuffer())
        val method = LogcatReader::class.java.getDeclaredMethod(
            "diagnostic",
            LogLevel::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        val entry = method.invoke(reader, LogLevel.WARN, "offline") as LogEntry

        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("LogcatReader", entry.tag)
        assertEquals("offline", entry.message)
        assertTrue(entry.timestampMs > 0L)
    }

    @Test
    fun `start appends parsed logcat lines and raw log sync output`() {
        BootFileLogger.init(mockContext(tmp))
        mockkConstructor(ProcessBuilder::class)
        val process = mockk<java.lang.Process>()
        every { process.inputStream } returns ByteArrayInputStream(
            "04-27 10:32:18.421  1234  5678 I OzeroVpn: startVpn\n".toByteArray(),
        )
        justRun { process.destroy() }
        every { anyConstructed<ProcessBuilder>().start() } returns process

        val buffer = LogBuffer()
        val reader = LogcatReader(buffer)

        reader.start()
        waitFor { buffer.entries.value.any { it.tag == "OzeroVpn" && it.message == "startVpn" } }
        waitFor { BootFileLogger.read().contains("LOGCAT 04-27 10:32:18.421  1234  5678 I OzeroVpn: startVpn") }
        reader.stop()
        reader.shutdown()

        val entries = buffer.entries.value
        assertTrue(entries.any { it.tag == "LogcatReader" && it.message.startsWith("LogcatReader started pid=") })
        assertTrue(entries.any { it.tag == "OzeroVpn" && it.message == "startVpn" && it.level == LogLevel.INFO })
        assertTrue(BootFileLogger.read().contains("LOGCAT 04-27 10:32:18.421  1234  5678 I OzeroVpn: startVpn"))
    }

    @Test
    fun `start emits warning when process returns zero parsable lines`() {
        mockkConstructor(ProcessBuilder::class)
        val process = mockk<java.lang.Process>()
        every { process.inputStream } returns ByteArrayInputStream("not a logcat line\n".toByteArray())
        justRun { process.destroy() }
        every { anyConstructed<ProcessBuilder>().start() } returns process

        val buffer = LogBuffer()
        val reader = LogcatReader(buffer)

        reader.start()
        waitFor {
            buffer.entries.value.any {
                it.level == LogLevel.WARN &&
                    it.message.startsWith("logcat exited with 0 lines")
            }
        }
        reader.stop()
        reader.shutdown()

        val entries = buffer.entries.value
        assertTrue(entries.any { it.tag == "LogcatReader" && it.level == LogLevel.INFO })
        assertTrue(entries.any { it.level == LogLevel.WARN && it.message.startsWith("logcat exited with 0 lines") })
    }

    @Test
    fun `start reports spawn failure before any lines are read`() {
        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().start() } throws IOException("missing logcat")

        val buffer = LogBuffer()
        val reader = LogcatReader(buffer)

        reader.start()
        waitFor {
            buffer.entries.value.any { it.level == LogLevel.ERROR && it.message.startsWith("logcat spawn failed:") }
        }
        reader.stop()
        reader.shutdown()

        val entries = buffer.entries.value
        assertTrue(entries.any { it.level == LogLevel.ERROR && it.message.startsWith("logcat spawn failed:") })
    }

    @AfterEach
    fun cleanupMocks() {
        runCatching { unmockkConstructor(ProcessBuilder::class) }
        LogFileStore.resetForTest()
    }

    private fun mockContext(filesDir: File): Context {
        val ctx = io.mockk.mockk<Context>(relaxed = true)
        io.mockk.every { ctx.filesDir } returns filesDir
        io.mockk.every { ctx.getExternalFilesDir(null) } returns null
        return ctx
    }

    private fun entry(tag: String): LogEntry =
        LogEntry(
            timestampMs = 0L,
            level = LogLevel.INFO,
            tag = tag,
            pid = 1,
            message = "seed",
        )

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        error("condition was not met")
    }
}
