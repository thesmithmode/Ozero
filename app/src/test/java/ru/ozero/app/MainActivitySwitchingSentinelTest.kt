package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MainActivitySwitchingSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/MainActivity.kt")
        assertTrue(f.exists(), "MainActivity.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `restartVpnIfConnected обязан помечать switching ДО stop`() {
        val block = source.substringAfter("private suspend fun restartVpnIfConnected")
            .substringBefore("private fun observeLiveEngineSettingsChanges")
        val swStartIdx = block.indexOf("onSwitchingStarted")
        val stopIdx = block.indexOf("vpnIntentLauncher.stop()")
        assertTrue(
            swStartIdx in 0 until stopIdx,
            "onSwitchingStarted обязан вызываться ДО stop, иначе UI покажет Idle-вспышку. " +
                "Block:\n$block",
        )
    }

    @Test
    fun `restartVpnIfConnected обязан очищать switching в catch при ошибке`() {
        val block = source.substringAfter("private suspend fun restartVpnIfConnected")
            .substringBefore("private fun observeLiveEngineSettingsChanges")
        assertTrue(
            block.contains("onSwitchingFinished") && block.contains("catch"),
            "При throw в restart пути switching marker должен очищаться явно — иначе UI зависнет в 'переключении'. " +
                "Block:\n$block",
        )
    }

    @Test
    fun `restartVpnIfConnected обязан передавать pendingTarget из switching а не null`() {
        val block = source.substringAfter("private suspend fun restartVpnIfConnected")
            .substringBefore("private fun observeLiveEngineSettingsChanges")
        assertTrue(
            block.contains("switching.value?.to"),
            "to=null в onSwitchingStarted перетирает ранее установленный target от onManualEngineSelect — " +
                "диск показывает Connected пока chip уже другой движок (UI desync для любой пары движков). " +
                "Обязан читать tunnelController.switching.value?.to как pendingTarget. Block:\n$block",
        )
    }

    @Test
    fun `restartVpnIfConnected waits for stop before start`() {
        val block = source.substringAfter("private suspend fun restartVpnIfConnected")
            .substringBefore("private fun observeLiveEngineSettingsChanges")
        val stoppedIdx = block.indexOf("val stopped = withTimeoutOrNull(RESTART_STOP_TIMEOUT_MS)")
        val guardIdx = block.indexOf("if (stopped == null)")
        val startIdx = block.indexOf("vpnIntentLauncher.start()")
        assertTrue(
            stoppedIdx >= 0 && guardIdx in stoppedIdx until startIdx,
            "restart must not send ACTION_START until stop published Idle/Failed. Block:\n$block",
        )
    }

    @Test
    fun `coalesced restart settle wait ignores stale Idle after start`() {
        val block = source.substringAfter("if (restartPending) {")
            .substringBefore("} while (restartPending)")
        assertTrue(
            block.contains("TunnelState.Connected") &&
                block.contains("TunnelState.Failed") &&
                !block.contains("TunnelState.Idle"),
            "coalesced restart must not treat stale Idle from the previous stop as post-start settlement. " +
                "Block:\n$block",
        )
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
