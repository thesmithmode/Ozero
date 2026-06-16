package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OzeroVpnServiceRuntimeRestartTest {

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(sourceFile.exists(), "OzeroVpnService.kt not found: $sourceFile")
        sourceFile.readText()
    }

    private val dispatcherSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = File(
            moduleRoot,
            "src/main/java/ru/ozero/commonvpn/OzeroVpnServiceActionDispatcher.kt",
        )
        assertTrue(sourceFile.exists(), "OzeroVpnServiceActionDispatcher.kt not found: $sourceFile")
        sourceFile.readText()
    }

    @Test
    fun `ACTION_RESTART_RUNTIME_CONFIG restarts only active tunnel`() {
        val commandBody = sourceBlock(
            text = dispatcherSource,
            startAnchor = "fun dispatch(",
            endAnchor = "} catch",
        )
        val restartCommand = commandBody
            .substringAfter("ACTION_RESTART_RUNTIME_CONFIG -> {")
            .substringBefore("OzeroVpnService.ACTION_START, null")

        assertTrue(restartCommand.contains("if (isTunnelIdle())"))
        assertTrue(restartCommand.contains("stopSelf(startId)"))
        assertTrue(restartCommand.contains("restartVpn()"))
    }

    @Test
    fun `restartVpn relaunches without stopSelf and then requests start after shutdown`() {
        val restartBody = sourceBlock(
            text = serviceSource,
            startAnchor = "private fun restartVpn()",
            endAnchor = "private fun logActiveExternalVpn()",
        )
        val stopCallStart = restartBody.indexOf("shutdownCoord.stopVpn(callStopSelf = false)")
        val shutdownJoinStart = restartBody.indexOf("shutdownJobRef.get()?.let")
        val fgCheckStart = restartBody.indexOf("notificationFactory.enterForeground(this@OzeroVpnService)")
        val startVpnCall = restartBody.indexOf("startVpn()", fgCheckStart)
        val stopSelfCall = restartBody.indexOf("stopSelf(latestStartId.get())")

        assertTrue(stopCallStart >= 0)
        assertTrue(restartBody.contains("runtimeConfigRestartCancelled.set(false)"))
        assertTrue(restartBody.contains("runtimeConfigRestartInProgress.set(true)"))
        assertTrue(shutdownJoinStart > stopCallStart)
        assertTrue(fgCheckStart > shutdownJoinStart)
        assertTrue(startVpnCall > fgCheckStart)
        assertTrue(stopSelfCall > startVpnCall)
        assertTrue(restartBody.contains("startVpn()"))
    }

    @Test
    fun `user stop during runtime restart cancels relaunch and stops service`() {
        val stopBody = sourceBlock(
            text = serviceSource,
            startAnchor = "private fun stopVpn()",
            endAnchor = "private fun restartVpn()",
        )
        val restartBody = sourceBlock(
            text = serviceSource,
            startAnchor = "private fun restartVpn()",
            endAnchor = "private fun logActiveExternalVpn()",
        )
        val abortBody = restartBody.substringAfter("} else {")

        assertTrue(stopBody.contains("runtimeConfigRestartInProgress.get()"))
        assertTrue(stopBody.contains("runtimeConfigRestartCancelled.set(true)"))
        assertTrue(restartBody.contains("val restartCancelled = runtimeConfigRestartCancelled.get()"))
        assertTrue(restartBody.contains("!restartCancelled"))
        assertTrue(abortBody.contains("runtimeConfigRestartInProgress.set(false)"))
        assertTrue(abortBody.contains("runtimeConfigRestartCancelled.set(false)"))
        assertTrue(abortBody.contains("stopSelf(latestStartId.get())"))
    }

    @Test
    fun `runtime failure router replaces active handler and ignores stale callbacks`() {
        val router = RuntimeFailureRouter()
        val staleCalls = mutableListOf<EngineId>()
        val freshCalls = mutableListOf<EngineId>()
        val stale = { engine: EngineId, _: String -> staleCalls += engine }
        val fresh = { engine: EngineId, _: String -> freshCalls += engine }

        router.bind(stale)
        router.handleEngineFailure(EngineId.BYEDPI, "first")
        router.bind(fresh)
        router.handleEngineFailure(EngineId.URNETWORK, "second")
        router.unbind(stale)
        router.unbind(fresh)
        router.handleEngineFailure(EngineId.WARP, "third")

        assertEquals(listOf(EngineId.BYEDPI), staleCalls)
        assertEquals(listOf(EngineId.URNETWORK), freshCalls)
    }

    @Test
    fun `runtime failure router ignores failures without a bound handler`() {
        val router = RuntimeFailureRouter()
        val calls = mutableListOf<EngineId>()

        val handler = { engine: EngineId, _: String -> calls += engine }
        router.bind(handler)
        router.unbind(handler)
        router.handleEngineFailure(EngineId.WARP, "ignored")

        assertEquals(emptyList(), calls)
    }

    private fun sourceBlock(text: String, startAnchor: String, endAnchor: String): String {
        assertTrue(text.contains(startAnchor), "Source anchor '$startAnchor' missing")
        assertTrue(text.contains(endAnchor), "Source anchor '$endAnchor' missing")
        val startIndex = text.indexOf(startAnchor)
        val endIndex = text.indexOf(endAnchor, startIndex + startAnchor.length)
        assertTrue(endIndex > startIndex, "End anchor appears before start anchor")
        return text.substring(startIndex, endIndex)
    }
}
