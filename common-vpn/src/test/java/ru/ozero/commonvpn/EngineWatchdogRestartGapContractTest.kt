package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class EngineWatchdogRestartGapContractTest {

    @Test
    fun `watchdog treats runtime restart as transient blocking state`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/commonvpn/EngineWatchdogCoordinator.kt",
        ).readText()

        assertTrue(
            source.contains("restartInProgressProvider"),
            "EngineWatchdogCoordinator must accept runtime-restart state so restart gaps stay fail-closed.",
        )
        assertTrue(
            source.contains("hasBlockingTunForKillswitch()"),
            "EngineWatchdogCoordinator must use the combined blocking check for killswitch decisions.",
        )
    }
}
