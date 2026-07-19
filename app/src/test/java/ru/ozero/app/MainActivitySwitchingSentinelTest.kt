package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivitySwitchingSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(f.exists(), "MainActivity.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `MainActivity does not auto stop-start VPN on settings changes`() {
        assertFalse(source.contains("observeLiveEngineSettingsChanges"))
        assertFalse(source.contains("restartVpnIfConnected"))
        assertFalse(source.contains("vpnIntentLauncher.stop()"))
        assertFalse(source.contains("vpnIntentLauncher.start()"))
    }

    @Test
    fun `runtime config observer owns engine-specific restart rules`() {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val app = File(moduleRoot, "src/main/java/ru/ozero/app/OzeroApp.kt").readText()
        val coordinator = File(
            moduleRoot,
            "src/main/java/ru/ozero/app/vpn/RuntimeConfigRestartCoordinator.kt",
        ).readText()
        assertTrue(
            source.contains("EngineRuntimeConfigRestartObserver").not() &&
                app.contains("runtimeConfigRestartCoordinator.start(appScope)") &&
                coordinator.contains("observer.start") &&
                coordinator.contains("tunnelController.state"),
            "Runtime config restarts must be process-wide, not gated by MainActivity STARTED lifecycle.",
        )
    }
}
