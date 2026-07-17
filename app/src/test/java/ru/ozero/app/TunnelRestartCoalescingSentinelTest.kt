package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunnelRestartCoalescingSentinelTest {

    @Test
    fun `MainActivity has no lifecycle scoped restart queue`() {
        val source = mainActivitySource()
        assertFalse(source.contains("restartMutex"))
        assertFalse(source.contains("restartQueue"))
        assertFalse(source.contains("restartInProgress"))
        assertFalse(source.contains("ACTION_STOP"))
        assertFalse(source.contains("ACTION_START"))
    }

    private fun mainActivitySource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(file.exists(), "MainActivity.kt not found: $file")
        return file.readText()
    }
}
