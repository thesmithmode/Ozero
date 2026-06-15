package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceRuntimeRestartTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(file.exists(), "OzeroVpnService.kt не найден: $file")
        file.readText()
    }

    private val dispatcherSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnServiceActionDispatcher.kt")
        assertTrue(file.exists(), "OzeroVpnServiceActionDispatcher.kt не найден: $file")
        file.readText()
    }

    @Test
    fun `ACTION_RESTART_RUNTIME_CONFIG делает restart внутри живого service`() {
        val commandBody = dispatcherSource
            .substringAfter("fun dispatch(")
            .substringBefore("} catch")
        assertTrue(commandBody.contains("ACTION_RESTART_RUNTIME_CONFIG -> {"))
        assertTrue(commandBody.contains("isTunnelIdle()"))
        assertTrue(commandBody.contains("stopSelf(startId)"))
        assertTrue(commandBody.contains("restartVpn()"))

        val restartBody = source
            .substringAfter("private fun restartVpn()")
            .substringBefore("private fun logActiveExternalVpn()")
        assertTrue(restartBody.contains("shutdownCoord.stopVpn(callStopSelf = false)"))
        assertTrue(restartBody.contains("if (!shutdownStarted) return"))
        assertTrue(restartBody.contains("runtimeConfigRestartInProgress.set(true)"))
        assertTrue(restartBody.contains("shutdownJobRef.get()?.let"))
        assertTrue(restartBody.contains("notificationFactory.enterForeground(this@OzeroVpnService)"))
        assertTrue(restartBody.contains("startVpn()"))
        assertTrue(restartBody.contains("runtimeConfigRestartInProgress.set(false)"))
    }

    @Test
    fun `runtime restart respects user stop before relaunch`() {
        val startBody = File(
            File(System.getProperty("user.dir") ?: "."),
            "src/main/java/ru/ozero/commonvpn/OzeroVpnServiceStartCoordinator.kt",
        ).readText()
        assertTrue(startBody.contains("runtimeConfigRestartCancelled.set(false)"))

        val stopBody = source.substringAfter("private fun stopVpn()").substringBefore("private fun restartVpn()")
        assertTrue(stopBody.contains("runtimeConfigRestartInProgress.get()"))
        assertTrue(stopBody.contains("runtimeConfigRestartCancelled.set(true)"))

        val restartBody = source
            .substringAfter("private fun restartVpn()")
            .substringBefore("private fun logActiveExternalVpn()")
        assertTrue(restartBody.contains("val restartCancelled = runtimeConfigRestartCancelled.get()"))
        assertTrue(restartBody.contains("!restartCancelled"))
        assertTrue(restartBody.contains("runtimeConfigRestartCancelled.set(false)"))
        assertTrue(restartBody.contains("stopSelf(latestStartId.get())"))
    }

    @Test
    fun `runtime restart stops service on every aborted relaunch path`() {
        val restartBody = source
            .substringAfter("private fun restartVpn()")
            .substringBefore("private fun logActiveExternalVpn()")
        val abortBody = restartBody.substringAfter("} else {")

        assertTrue(abortBody.contains("runtimeConfigRestartInProgress.set(false)"))
        assertTrue(abortBody.contains("runtimeConfigRestartCancelled.set(false)"))
        assertTrue(abortBody.contains("stopSelf(latestStartId.get())"))
        assertTrue(
            !abortBody.contains("if (restartCancelled)"),
            "stopSelf должен выполняться при любом abort restart: " +
                "cancel, stopping или enterForeground=false",
        )
    }

    @Test
    fun `runtime restart ignores stale intent after tunnel became idle`() {
        val commandBody = dispatcherSource
            .substringAfter("fun dispatch(")
            .substringBefore("} catch")
        val restartCommand = commandBody
            .substringAfter("ACTION_RESTART_RUNTIME_CONFIG -> {")
            .substringBefore("ACTION_START, null")

        assertTrue(restartCommand.contains("isTunnelIdle()"))
        assertTrue(restartCommand.contains("stopSelf(startId)"))
        assertTrue(restartCommand.contains("} else {"))
        assertTrue(restartCommand.contains("restartVpn()"))
    }
}
