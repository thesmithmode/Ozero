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

    @Test
    fun `ACTION_RESTART_RUNTIME_CONFIG делает restart внутри живого service`() {
        val commandBody = source
            .substringAfter("override fun onStartCommand")
            .substringBefore("private fun startVpn()")
        assertTrue(commandBody.contains("ACTION_RESTART_RUNTIME_CONFIG -> restartVpn()"))

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
        val startBody = source.substringAfter("private fun startVpn()").substringBefore("private fun engineExtras(")
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
        assertTrue(restartBody.contains("if (restartCancelled)"))
        assertTrue(restartBody.contains("stopSelf(latestStartId.get())"))
    }
}
