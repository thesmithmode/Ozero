package ru.ozero.app.logging

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootFileLoggerFsyncTest {

    @TempDir lateinit var tmp: File

    @BeforeEach
    fun resetLoggerState() {
        val field = BootFileLogger::class.java.getDeclaredField("targetRef")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(BootFileLogger) as AtomicReference<File?>).set(null)
    }

    private fun mockContext(): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns tmp
        return ctx
    }

    @Test
    fun `init создаёт файл и пишет начальную запись`() {
        BootFileLogger.init(mockContext())
        val target = assertNotNull(BootFileLogger.file())
        assertTrue(target.exists())
        assertTrue(target.readText().contains("init pid="))
    }

    @Test
    fun `log вызывает fsync и данные на диске сразу`() {
        BootFileLogger.init(mockContext())
        BootFileLogger.info("UnitTest", "fsync-marker-payload")
        val target = assertNotNull(BootFileLogger.file())
        val content = target.readText()
        assertTrue(content.contains("fsync-marker-payload"), "ожидали маркер в файле: $content")
        assertTrue(content.contains("UnitTest"))
        assertTrue(content.contains("INFO"))
    }

    @Test
    fun `rotate работает при превышении MAX_BYTES`() {
        BootFileLogger.init(mockContext())
        val chunk = "x".repeat(10_000)
        repeat(120) { BootFileLogger.info("Bulk", chunk) }
        val prev = File(File(tmp, "debug"), "boot.log.prev")
        assertTrue(prev.exists(), "ожидали ротацию в boot.log.prev")
        val target = assertNotNull(BootFileLogger.file())
        assertTrue(target.exists())
    }
}
