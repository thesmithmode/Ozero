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

class BootFileLoggerPersistenceTest {

    @TempDir lateinit var tmp: File

    @BeforeEach
    fun resetLoggerState() {
        LogFileStore.resetForTest()
    }

    private fun mockContext(filesDir: File): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        every { ctx.getExternalFilesDir(null) } returns null
        return ctx
    }

    @Test
    fun `boot log файл переживает re-init с тем же filesDir (append, не truncate)`() {
        BootFileLogger.init(mockContext(tmp))
        BootFileLogger.warn("Session1", "before-restart-marker")
        val targetBefore = assertNotNull(UnifiedLogger.file())
        val pathBefore = targetBefore.absolutePath
        assertTrue(targetBefore.readText().contains("before-restart-marker"))

        LogFileStore.resetForTest()
        BootFileLogger.init(mockContext(tmp))
        BootFileLogger.warn("Session2", "after-restart-marker")

        val targetAfter = assertNotNull(UnifiedLogger.file())
        assertTrue(
            targetAfter.absolutePath == pathBefore,
            "Re-init обязан использовать тот же путь: было=$pathBefore, стало=${targetAfter.absolutePath}",
        )
        val content = targetAfter.readText()
        assertTrue(
            content.contains("before-restart-marker"),
            "boot.log потерял запись из предыдущей сессии после re-init: $content",
        )
        assertTrue(content.contains("after-restart-marker"))
    }

    @Test
    fun `boot log файл сохраняется на диске после очистки in-memory targetRef`() {
        BootFileLogger.init(mockContext(tmp))
        BootFileLogger.error("Diag", "persistent-payload", IllegalStateException("synthetic"))
        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(target.exists())
        val sizeBefore = target.length()
        assertTrue(sizeBefore > 0L, "файл должен содержать запись после error()")

        LogFileStore.resetForTest()
        assertTrue(
            target.exists(),
            "файл boot.log не должен исчезать при сбросе in-memory указателя",
        )
        assertTrue(
            target.length() == sizeBefore,
            "размер boot.log не должен меняться без явного clear() — было=$sizeBefore, стало=${target.length()}",
        )
    }

    @Test
    fun `файл сохраняется в filesDir-logs если getExternalFilesDir = null (sandbox path)`() {
        BootFileLogger.init(mockContext(tmp))
        BootFileLogger.info("Path", "sandbox-payload")
        val target = assertNotNull(UnifiedLogger.file())
        val expectedDir = File(tmp, "logs")
        assertTrue(
            target.parentFile == expectedDir,
            "ожидали logs под filesDir=$expectedDir, получили parent=${target.parentFile}",
        )
        assertTrue(target.name == "ozero.log")
    }

    @Test
    fun `файл сохраняется в filesDir даже если external storage доступен`() {
        val external = File(tmp, "external")
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns tmp
        every { ctx.getExternalFilesDir(null) } returns external

        BootFileLogger.init(ctx)
        BootFileLogger.info("Path", "internal-only")

        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(
            target.parentFile == File(tmp, "logs"),
            "persistent logs должны лежать во внутреннем filesDir, а не external app-specific storage",
        )
    }

    @Test
    fun `clear очищает файл но re-init после clear не пересоздаёт его пустым повторно`() {
        BootFileLogger.init(mockContext(tmp))
        BootFileLogger.info("Tag", "before-clear-marker")
        val target = assertNotNull(UnifiedLogger.file())
        assertTrue(target.readText().contains("before-clear-marker"))

        BootFileLogger.clear()
        assertTrue(target.exists(), "clear() не должен удалять файл")
        assertTrue(target.readText().isEmpty(), "clear() обязан опустошить содержимое")

        BootFileLogger.info("Tag", "after-clear-marker")
        assertTrue(target.readText().contains("after-clear-marker"))
    }
}
