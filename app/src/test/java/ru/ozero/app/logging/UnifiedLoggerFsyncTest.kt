package ru.ozero.app.logging

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UnifiedLoggerFsyncTest {

    @TempDir lateinit var tmp: File

    @BeforeEach
    fun resetLoggerState() {
        LogFileStore.resetForTest()
    }

    private fun mockContext(): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns tmp
        every { ctx.getExternalFilesDir(null) } returns null
        return ctx
    }

    @Test
    fun `init создаёт файл и пишет начальную запись`() {
        UnifiedLogger.init(mockContext())
        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(target.exists())
        assertTrue(target.readText().contains("init pid="))
    }

    @Test
    fun `log вызывает fsync и данные на диске сразу`() {
        UnifiedLogger.init(mockContext())
        UnifiedLogger.info("UnitTest", "fsync-marker-payload")
        val target = assertNotNull(UnifiedLogger.file())
        val content = target.readText()
        assertTrue(content.contains("fsync-marker-payload"), "ожидали маркер в файле: $content")
        assertTrue(content.contains("UnitTest"))
        assertTrue(content.contains("INFO"))
    }

    @Test
    fun `rotate работает при превышении MAX_BYTES`() {
        UnifiedLogger.init(mockContext())
        val chunk = "x".repeat(50_000)
        repeat(120) { UnifiedLogger.info("Bulk", chunk) }
        val prev = File(File(tmp, "logs"), "ozero.log.prev")
        assertTrue(prev.exists(), "ожидали ротацию в ozero.log.prev")
        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(target.exists())
    }

    @Test
    fun `readTail возвращает хвост при превышении лимита`() {
        UnifiedLogger.init(mockContext())
        val unique = "TAIL-MARKER-XYZ"
        repeat(50) { UnifiedLogger.info("Bulk", "fill-".repeat(1000)) }
        UnifiedLogger.info("Tail", unique)
        val tail = UnifiedLogger.readTail(maxBytes = 5_000)
        assertTrue(tail.contains(unique), "tail должен содержать последнюю запись")
    }

    @Test
    fun `BootFileLogger фасад делегирует на UnifiedLogger`() {
        BootFileLogger.init(mockContext())
        BootFileLogger.info("Facade", "facade-payload")
        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(target.readText().contains("facade-payload"))
    }
}
