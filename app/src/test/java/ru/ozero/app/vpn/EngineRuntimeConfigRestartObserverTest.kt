package ru.ozero.app.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.runtimeFingerprint
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineRuntimeConfigRestartObserverTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `observeFlow drops initial emission and restarts only active engine`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.WARP, 0))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-2")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        changes.emit("runtime-3")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("warp changed"), restarts)
    }

    @Test
    fun `observeFlow can include starting states for WARP and FPTN`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.WARP))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-2")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("warp changed"), restarts)
    }

    @Test
    fun `observeFlow excludes starting states for FPTN hydration`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("synthetic-default")
        changes.emit("persisted-config")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        changes.emit("post-connected-edit")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow replays FPTN runtime edit after startup reaches connected`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-edit")
        testScheduler.advanceUntilIdle()
        assertTrue(restarts.isEmpty())

        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow clears pending FPTN replay when startup edit is reverted`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-edit")
        changes.emit("initial")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        testScheduler.advanceUntilIdle()

        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `observeFlow drops stale FPTN replay when startup leaves FPTN before connected`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-edit")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Failed(EngineId.FPTN, "startup failed")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        testScheduler.advanceUntilIdle()

        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `observeFlow adopts FPTN synthetic default to persisted baseline without restart`() = runTest(dispatcher) {
        val default = FptnConfig().runtimeFingerprint()
        val persisted = FptnConfig(token = "persisted-token").runtimeFingerprint()
        val edited = FptnConfig(token = "edited-token").runtimeFingerprint()
        val changes = MutableStateFlow<Any?>(default)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.FPTN, 0))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            adoptedBaselineFrom = default,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.value = persisted
        testScheduler.advanceUntilIdle()
        changes.value = edited
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow excludes starting states for singbox profile writes`() = runTest(dispatcher) {
        val changes = MutableSharedFlow<Any?>(extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connecting(EngineId.SINGBOX))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = backgroundScope,
            lifecycle = null,
            changes = changes,
            engineId = EngineId.SINGBOX,
            reason = "singbox changed",
            includeStarting = false,
            replayAfterStarting = false,
            state = state,
            restart = { restarts += it },
        )
        testScheduler.advanceUntilIdle()

        changes.emit("initial")
        changes.emit("runtime-2")
        testScheduler.advanceUntilIdle()
        state.value = TunnelState.Connected(EngineId.SINGBOX, 1080)
        changes.emit("runtime-3")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("singbox changed"), restarts)
    }

    @Test
    fun `runtime observer stays generic while composition root wires engine providers`() {
        val mainActivity = readSource("src/main/java/ru/ozero/app/MainActivity.kt")
        val observer = readSource("src/main/java/ru/ozero/app/vpn/EngineRuntimeConfigRestartObserver.kt")

        assertTrue(mainActivity.contains("EngineRuntimeConfigRestartObserver"))
        assertTrue(observer.contains("EngineRuntimeConfigProvider"))
        assertTrue((mainActivity + observer).contains("WarpConfigSlotStore").not())
        assertTrue((mainActivity + observer).contains("FptnConfigStore").not())
        assertTrue((mainActivity + observer).contains("SingboxProbeService").not())

        val diSources = listOf(
            "src/main/java/ru/ozero/app/di/WarpModule.kt",
            "src/main/java/ru/ozero/app/di/FptnModule.kt",
            "src/main/java/ru/ozero/app/di/SingboxModule.kt",
        ).joinToString("\n") { readSource(it) }
        assertTrue(diSources.contains("EngineRuntimeConfigProvider"))
    }

    private fun readSource(path: String): String =
        java.io.File(
            System.getProperty("user.dir") ?: ".",
            path,
        ).readText()

    private fun newObserver() = EngineRuntimeConfigRestartObserver(
        providers = emptySet(),
    )
}
