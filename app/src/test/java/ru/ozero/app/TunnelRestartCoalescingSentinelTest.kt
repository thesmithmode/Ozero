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
        val tryLockIdx = block.indexOf("restartMutex.tryLock()")
        val switchingIdx = block.indexOf("onSwitchingStarted")
        assertTrue(
            source.contains("private val restartMutex = Mutex()") &&
                source.contains("private var restartPending = false") &&
                tryLockIdx >= 0 &&
                tryLockIdx < switchingIdx &&
                block.contains("restartPending = true") &&
                block.contains("while (restartPending)") &&
                block.contains("restartMutex.unlock()"),
            "restartVpnIfConnected must take restartMutex.tryLock() before onSwitchingStarted, " +
                "preserve a pending restart instead of dropping it, and unlock in finally. Block:\n$block",
        )
    }

    private fun mainActivitySource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(file.exists(), "MainActivity.kt not found: $file")
        return file.readText()
    }
}
