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
    fun `singbox profile changes restart only stable connected singbox`() {
        val observerBlock = source.substringAfter("private fun observeSingboxProfileChanges")
            .substringBefore("private suspend fun restartSingboxIfStableConnected")
        assertTrue(
            observerBlock.contains("restartSingboxIfStableConnected") &&
                !observerBlock.contains("restartVpnIfConnected("),
            "singbox profile observer must not call generic restart directly because Connecting/Probing profile writes can self-restart",
        )

        val helperBlock = source.substringAfter("private suspend fun restartSingboxIfStableConnected")
            .substringBefore("private companion object")
        assertTrue(
            helperBlock.contains("TunnelState.Connected") &&
                helperBlock.contains("EngineId.SINGBOX") &&
                !helperBlock.contains("TunnelState.Connecting") &&
                !helperBlock.contains("TunnelState.Probing"),
            "singbox profile restart must be allowed only after stable Connected(SINGBOX), not during Connecting/Probing",
        )
    }
}
