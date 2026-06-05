package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class TunnelRestartCoalescingSentinelTest {

    @Test
    fun `restartVpnIfConnected coalesces concurrent restart requests`() {
        val source = mainActivitySource()
        val block = source.substringAfter("private suspend fun restartVpnIfConnected")
            .substringBefore("private fun observeLiveEngineSettingsChanges")
        assertTrue(
            source.contains("private val restartMutex = Mutex()") &&
                source.contains("private val restartQueue = ArrayDeque<String>()") &&
                source.contains("private var restartInProgress = false") &&
                block.contains("restartMutex.withLock") &&
                block.contains("restartQueue.addLast(reason)") &&
                block.contains("restartQueue.removeFirstOrNull()") &&
                block.contains("restartInProgress = false") &&
                block.contains("restartQueue.isNotEmpty()"),
            "restartVpnIfConnected must coalesce through a mutex-guarded queue so enqueue and exit checks stay consistent. Block:\n$block",
        )
    }

    private fun mainActivitySource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(file.exists(), "MainActivity.kt not found: $file")
        return file.readText()
    }
}
