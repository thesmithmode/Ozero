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
                tryLockIdx >= 0 &&
                tryLockIdx < switchingIdx &&
                block.contains("restartMutex.unlock()"),
            "restartVpnIfConnected обязан брать restartMutex.tryLock() до onSwitchingStarted и unlock в finally. " +
                "Иначе несколько observers одновременно запускают stop/start и спамят switching transitions. Block:\n$block",
        )
    }

    private fun mainActivitySource(): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(file.exists(), "MainActivity.kt не найден: $file")
        return file.readText()
    }
}
