package ru.ozero.app.logging

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedLoggerRotationVisibilityTest {

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

    private fun forceRotation() {
        val target = UnifiedLogger.file() ?: error("logger must be initialized before rotation")
        val filler = "prefill "
        target.writeText(filler.repeat((LogFileStore.MAX_BYTES / filler.length).toInt()) + "x")
        LogFileStore.rotateIfTooLarge(target)
    }

    @Test
    fun `read возвращает содержимое prev и current после ротации`() {
        UnifiedLogger.init(mockContext())
        UnifiedLogger.info("Pre", "PRE-ROTATION-MARKER")
        forceRotation()
        UnifiedLogger.info("Post", "POST-ROTATION-MARKER")

        val all = UnifiedLogger.read()

        assertTrue(all.contains("PRE-ROTATION-MARKER"), "read должен включать prev: $all")
        assertTrue(all.contains("POST-ROTATION-MARKER"), "read должен включать current: $all")
    }

    @Test
    fun `readTail после ротации добивает из prev если current короче лимита`() {
        UnifiedLogger.init(mockContext())
        UnifiedLogger.info("Pre", "PRE-TAIL-MARKER-AAA")
        forceRotation()
        UnifiedLogger.info("Post", "POST-TAIL-MARKER-BBB")

        val tail = UnifiedLogger.readTail(maxBytes = 10_000_000L)

        assertTrue(tail.contains("PRE-TAIL-MARKER-AAA"), "tail должен видеть prev: ${tail.take(200)}")
        assertTrue(tail.contains("POST-TAIL-MARKER-BBB"), "tail должен видеть current: ${tail.takeLast(200)}")
    }

    @Test
    fun `readTail с маленьким лимитом отдаёт хвост current и не падает на prev`() {
        UnifiedLogger.init(mockContext())
        forceRotation()
        UnifiedLogger.info("Tail", "TINY-TAIL-MARKER-ZZZ")

        val tail = UnifiedLogger.readTail(maxBytes = 1_000L)

        assertTrue(tail.contains("TINY-TAIL-MARKER-ZZZ"), "малый tail = последняя запись current")
    }

    @Test
    fun `fileSize суммирует prev и current после ротации`() {
        UnifiedLogger.init(mockContext())
        forceRotation()
        UnifiedLogger.info("Post", "small")

        val total = UnifiedLogger.fileSize()
        val current = UnifiedLogger.file()?.length() ?: 0L

        assertTrue(total > current, "fileSize должен включать prev: total=$total current=$current")
    }

    @Test
    fun `clear удаляет prev файл а не только обнуляет current`() {
        UnifiedLogger.init(mockContext())
        forceRotation()
        val prev = File(File(tmp, "logs"), "ozero.log.prev")
        assertTrue(prev.exists(), "prev должен быть после ротации")

        UnifiedLogger.clear()

        assertFalse(prev.exists(), "clear должен удалить prev")
    }

    @Test
    fun `read без ротации возвращает только current и не падает`() {
        UnifiedLogger.init(mockContext())
        UnifiedLogger.info("Solo", "SOLO-MARKER")

        val all = UnifiedLogger.read()

        assertTrue(all.contains("SOLO-MARKER"))
        val prev = File(File(tmp, "logs"), "ozero.log.prev")
        assertFalse(prev.exists(), "prev не должен создаваться без ротации")
    }
}
