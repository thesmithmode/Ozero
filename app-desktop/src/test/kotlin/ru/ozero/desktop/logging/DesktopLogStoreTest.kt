package ru.ozero.desktop.logging

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class DesktopLogStoreTest {

    @TempDir
    lateinit var tempDir: File

    private var installedHandler: Handler? = null

    @AfterEach
    fun tearDown() {
        installedHandler?.let { Logger.getLogger("").removeHandler(it) }
        installedHandler = null
    }

    @Test
    fun `append copy export and clear preserve diagnostic log contract`() {
        System.setProperty("user.home", tempDir.absolutePath)
        DesktopLogStore.clear()

        DesktopLogStore.append(DesktopLogLevel.INFO, "Warp", "connected")
        DesktopLogStore.append(DesktopLogLevel.ERROR, "Warp", "handshake failed")

        val copied = DesktopLogStore.copyAll()
        assertTrue(copied.contains("[INFO] Warp: connected"))
        assertTrue(copied.contains("[ERROR] Warp: handshake failed"))
        assertEquals(listOf("All", "Warp"), DesktopLogStore.availableTags)

        val export = File(tempDir, "ozero-desktop.log")
        DesktopLogStore.export(export)
        assertEquals(copied, export.readText())

        DesktopLogStore.clear()
        assertTrue(DesktopLogStore.entries.value.isEmpty())
        assertEquals("", DesktopLogStore.copyAll())
    }

    @Test
    fun `availableTags are sorted and deduplicated`() {
        System.setProperty("user.home", tempDir.absolutePath)
        DesktopLogStore.clear()

        DesktopLogStore.append(DesktopLogLevel.DEBUG, "Zeta", "last")
        DesktopLogStore.append(DesktopLogLevel.DEBUG, "Alpha", "first")
        DesktopLogStore.append(DesktopLogLevel.DEBUG, "Zeta", "again")

        assertEquals(listOf("All", "Alpha", "Zeta"), DesktopLogStore.availableTags)
    }

    @Test
    fun `jul handler maps levels and logger names into desktop entries`() {
        System.setProperty("user.home", tempDir.absolutePath)
        DesktopLogStore.clear()
        val before = DesktopLogStore.entries.value.size

        DesktopLogStore.installJulHandler()
        installedHandler = Logger.getLogger("").handlers.last()
        installedHandler?.publish(
            LogRecord(Level.WARNING, "warn from runtime").apply {
                loggerName = "ru.ozero.desktop.Engine"
            },
        )

        val added = DesktopLogStore.entries.value.drop(before)
        assertFalse(added.isEmpty())
        val entry = added.last()
        assertEquals(DesktopLogLevel.WARN, entry.level)
        assertEquals("Engine", entry.tag)
        assertEquals("warn from runtime", entry.message)
    }
}
