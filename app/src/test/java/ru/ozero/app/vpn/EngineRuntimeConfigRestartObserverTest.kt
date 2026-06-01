package ru.ozero.app.vpn

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.runtimeFingerprint
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineRuntimeConfigRestartObserverTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observeFlow drops initial emission and restarts only active engine`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.WARP, 0))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-2"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        changes.value = "runtime-3"
        runCurrent()

        assertEquals(listOf("warp changed"), restarts)
    }

    @Test
    fun `observeFlow can include starting states for WARP and FPTN`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.WARP))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-2"
        runCurrent()

        assertEquals(listOf("warp changed"), restarts)
    }

    @Test
    fun `observeFlow can include connecting state`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connecting(EngineId.WARP))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-2"
        runCurrent()

        assertEquals(listOf("warp changed"), restarts)
    }

    @Test
    fun `observeFlow excludes starting states for FPTN hydration`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("synthetic-default")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            adoptedBaselineFrom = "synthetic-default",
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "persisted-config"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()
        changes.value = "post-connected-edit"
        runCurrent()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow replays FPTN runtime edit after startup reaches connected`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-edit"
        runCurrent()
        assertTrue(restarts.isEmpty())

        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow replays latest FPTN runtime edit after startup reaches connected`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connecting(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-edit-1"
        runCurrent()
        changes.value = "runtime-edit-2"
        runCurrent()
        assertTrue(restarts.isEmpty())

        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow clears pending FPTN replay when startup edit is reverted`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-edit"
        runCurrent()
        changes.value = "initial"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()

        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `observeFlow drops stale FPTN replay when startup leaves FPTN before connected`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            replayAfterStarting = true,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-edit"
        runCurrent()
        state.value = TunnelState.Failed(EngineId.FPTN, "startup failed")
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()

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
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.FPTN,
            reason = "fptn changed",
            includeStarting = false,
            adoptedBaselineFrom = default,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = persisted
        runCurrent()
        changes.value = edited
        runCurrent()

        assertEquals(listOf("fptn changed"), restarts)
    }

    @Test
    fun `observeFlow excludes starting states for singbox profile writes`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connecting(EngineId.SINGBOX))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.SINGBOX,
            reason = "singbox changed",
            includeStarting = false,
            replayAfterStarting = false,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-2"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.SINGBOX, 1080)
        runCurrent()
        changes.value = "runtime-3"
        runCurrent()

        assertEquals(listOf("singbox changed"), restarts)
    }

    @Test
    fun `start wires provider options into runtime observation`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("default")
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<String>()
        val provider = fakeProvider(
            providerEngineId = EngineId.FPTN,
            configChanges = changes,
            providerIncludeStarting = false,
            providerReplayAfterStarting = true,
            providerAdoptedBaselineFrom = "default",
            providerRestartReason = "provider reason",
        )

        EngineRuntimeConfigRestartObserver(setOf(provider)).start(
            scope = observerScope(),
            lifecycle = null,
            exceptionHandler = CoroutineExceptionHandler { _, _ -> },
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "persisted"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        runCurrent()
        changes.value = "edited"
        runCurrent()

        assertEquals(listOf("provider reason"), restarts)
    }

    @Test
    fun `start observes provider changes without Android lifecycle`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.WARP, 0))
        val restarts = mutableListOf<String>()
        val provider = fakeProvider(
            providerEngineId = EngineId.WARP,
            configChanges = changes,
            providerIncludeStarting = true,
            providerReplayAfterStarting = false,
            providerAdoptedBaselineFrom = null,
            providerRestartReason = "provider reason",
        )

        EngineRuntimeConfigRestartObserver(setOf(provider)).start(
            scope = observerScope(),
            lifecycle = null,
            exceptionHandler = CoroutineExceptionHandler { _, _ -> },
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "runtime-2"
        runCurrent()

        assertEquals(listOf("provider reason"), restarts)
    }

    @Test
    fun `start observes multiple providers independently`() = runTest(dispatcher) {
        val warpChanges = MutableStateFlow<Any?>("warp-initial")
        val fptnChanges = MutableStateFlow<Any?>("fptn-initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.WARP, 0))
        val restarts = mutableListOf<String>()
        val warpProvider = fakeProvider(
            providerEngineId = EngineId.WARP,
            configChanges = warpChanges,
            providerIncludeStarting = true,
            providerReplayAfterStarting = false,
            providerAdoptedBaselineFrom = null,
            providerRestartReason = "warp reason",
        )
        val fptnProvider = fakeProvider(
            providerEngineId = EngineId.FPTN,
            configChanges = fptnChanges,
            providerIncludeStarting = true,
            providerReplayAfterStarting = false,
            providerAdoptedBaselineFrom = null,
            providerRestartReason = "fptn reason",
        )

        EngineRuntimeConfigRestartObserver(setOf(warpProvider, fptnProvider)).start(
            scope = observerScope(),
            lifecycle = null,
            exceptionHandler = CoroutineExceptionHandler { _, _ -> },
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        warpChanges.value = "warp-runtime-2"
        runCurrent()
        fptnChanges.value = "fptn-runtime-2"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        fptnChanges.value = "fptn-runtime-3"
        runCurrent()

        assertEquals(listOf("warp reason", "fptn reason"), restarts)
    }

    @Test
    fun `observeFlow updates baseline while inactive without replay`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.WARP, 0))
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.SINGBOX,
            reason = "singbox changed",
            includeStarting = false,
            replayAfterStarting = false,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "inactive-edit"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.SINGBOX, 1080)
        runCurrent()
        changes.value = "active-edit"
        runCurrent()

        assertEquals(listOf("singbox changed"), restarts)
    }

    @Test
    fun `observeFlow updates baseline while idle without replay`() = runTest(dispatcher) {
        val changes = MutableStateFlow<Any?>("initial")
        val state = MutableStateFlow<TunnelState>(TunnelState.Idle)
        val restarts = mutableListOf<String>()

        newObserver().observeFlow(
            scope = observerScope(),
            lifecycle = null,
            changes = changes,
            engineId = EngineId.WARP,
            reason = "warp changed",
            includeStarting = true,
            replayAfterStarting = false,
            state = state,
            restart = { restarts += it },
        )
        runCurrent()

        changes.value = "idle-edit"
        runCurrent()
        state.value = TunnelState.Connected(EngineId.WARP, 0)
        runCurrent()
        changes.value = "connected-edit"
        runCurrent()

        assertEquals(listOf("warp changed"), restarts)
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

    private fun TestScope.observerScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun fakeProvider(
        providerEngineId: EngineId,
        configChanges: MutableStateFlow<Any?>,
        providerIncludeStarting: Boolean,
        providerReplayAfterStarting: Boolean,
        providerAdoptedBaselineFrom: Any?,
        providerRestartReason: String,
    ): EngineRuntimeConfigProvider = object : EngineRuntimeConfigProvider {
        override val engineId = providerEngineId
        override val changes = configChanges
        override val includeStarting = providerIncludeStarting
        override val replayAfterStarting = providerReplayAfterStarting
        override val adoptedBaselineFrom = providerAdoptedBaselineFrom
        override val restartReason = providerRestartReason
    }
}
