package ru.ozero.app.vpn

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EngineRuntimeConfigProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeConfigRestartCoordinatorTest {

    @Test
    fun `start subscribes observer once and restarts connected engine on runtime change`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val changes = MutableStateFlow<Any?>("baseline")
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
                launch { tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820)) }
            },
            observer = EngineRuntimeConfigRestartObserver(setOf(runtimeProvider(EngineId.WARP, changes))),
            tunnelController = tunnelController,
        )

        coordinator.start(backgroundScope)
        coordinator.start(backgroundScope)
        runCurrent()
        changes.value = "changed"
        runCurrent()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart returns false and does not start service when tunnel is idle`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val coordinator = coordinator(
            context = recordingContext(startServiceActions),
            tunnelController = TunnelController(),
        )

        val restarted = coordinator.restartVpnIfRunning("settings changed")

        assertFalse(restarted)
        assertTrue(startServiceActions.isEmpty())
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart sends action and clears switching when tunnel starts again`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val context = recordingContext(startServiceActions) {
            tunnelController.setState(TunnelState.Disconnecting)
            launch {
                tunnelController.setState(TunnelState.Probing(EngineId.WARP))
            }
        }
        val coordinator = coordinator(context, tunnelController)

        val restarted = coordinator.restartVpnIfRunning("settings changed")
        runCurrent()

        assertTrue(restarted)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)
    }

    @Test
    fun `restart works from connecting state and preserves pending switch target`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connecting(EngineId.BYEDPI))
        tunnelController.onSwitchingStarted(from = EngineId.WARP, to = EngineId.BYEDPI)
        val context = recordingContext(startServiceActions) {
            tunnelController.setState(TunnelState.Disconnecting)
            launch {
                tunnelController.setState(TunnelState.Connected(EngineId.BYEDPI, 1080))
            }
        }
        val coordinator = coordinator(context, tunnelController)

        val restarted = coordinator.restartVpnIfRunning("profile changed")
        runCurrent()

        assertTrue(restarted)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(tunnelController.switching.value == null)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart works from probing state when restart reaches connecting`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Probing(EngineId.FPTN))
        val context = recordingContext(startServiceActions) {
            tunnelController.setState(TunnelState.Disconnecting)
            launch {
                tunnelController.setState(TunnelState.Connecting(EngineId.FPTN))
            }
        }
        val coordinator = coordinator(context, tunnelController)

        val restarted = coordinator.restartVpnIfRunning("token changed")
        runCurrent()

        assertTrue(restarted)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(tunnelController.switching.value == null)
    }

    @Test
    fun `restart returns false and clears switching when restarted tunnel fails`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val context = recordingContext(startServiceActions) {
            tunnelController.setState(TunnelState.Disconnecting)
            launch {
                tunnelController.setState(TunnelState.Failed(EngineId.WARP, "broken"))
            }
        }
        val coordinator = coordinator(context, tunnelController)

        val restarted = coordinator.restartVpnIfRunning("settings changed")
        runCurrent()

        assertFalse(restarted)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)
    }

    @Test
    fun `restart times out waiting for start after stop signal`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
            },
            tunnelController = tunnelController,
        )

        val restart = launch {
            assertFalse(coordinator.restartVpnIfRunning("settings changed"))
        }
        runCurrent()
        advanceTimeBy(15_001)
        restart.join()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)
    }

    @Test
    fun `restart times out waiting for stop and resets queue state`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = coordinator(recordingContext(startServiceActions), tunnelController)

        val restart = launch {
            assertFalse(coordinator.restartVpnIfRunning("settings changed"))
        }
        advanceTimeBy(11_001)
        restart.join()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)
    }

    @Test
    fun `restart queue clears after exception and accepts the next restart`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startService(intent: Intent): android.content.ComponentName? {
                startServiceActions += intent.action
                throw IllegalStateException("boom")
            }
        }
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        assertIs<TunnelState.Connected>(tunnelController.state.value)
        val coordinator = RuntimeConfigRestartCoordinator(
            context = context,
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = tunnelController,
        )

        val firstRestart = runCatching { coordinator.restartVpnIfRunning("current-request") }
        assertTrue(firstRestart.isFailure, firstRestart.toString())
        assertEquals("boom", firstRestart.exceptionOrNull()?.message)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)

        runCatching { coordinator.restartVpnIfRunning("second-request") }
        assertEquals(2, startServiceActions.size)
        assertEquals(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, startServiceActions.last())
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `queued restart is processed after first restart settles`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val allowRestartStart = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                calls += 1
                tunnelController.setState(TunnelState.Disconnecting)
                launch {
                    allowRestartStart.await()
                    tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
                }
            },
            tunnelController = tunnelController,
        )

        val firstRestart = launch {
            assertTrue(coordinator.restartVpnIfRunning("first-request"))
        }
        runCurrent()
        val secondRestart = launch {
            assertTrue(coordinator.restartVpnIfRunning("second-request"))
        }
        runCurrent()
        allowRestartStart.complete(Unit)
        runCurrent()
        firstRestart.join()
        secondRestart.join()

        assertEquals(2, calls)
        assertEquals(
            listOf<String?>(
                OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG,
                OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG,
            ),
            startServiceActions,
        )
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `second restart coalesces while first restart is in progress`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
            },
            tunnelController = tunnelController,
        )

        val firstRestart = launch {
            assertFalse(coordinator.restartVpnIfRunning("first-request"))
        }
        runCurrent()

        val secondResult = coordinator.restartVpnIfRunning("second-request")
        advanceTimeBy(11_001)
        firstRestart.join()

        assertTrue(secondResult)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart returns false for failed disconnecting and probing without engine`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        val coordinator = coordinator(recordingContext(startServiceActions), tunnelController)

        tunnelController.setState(TunnelState.Failed(EngineId.WARP, "failed"))
        assertFalse(coordinator.restartVpnIfRunning("failed-state"))

        tunnelController.setState(TunnelState.Disconnecting)
        assertFalse(coordinator.restartVpnIfRunning("disconnecting-state"))

        tunnelController.setState(TunnelState.Probing(null))
        assertFalse(coordinator.restartVpnIfRunning("probing-without-engine"))

        assertTrue(startServiceActions.isEmpty())
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart accepts idle after previous false result`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        val coordinator = coordinator(recordingContext(startServiceActions), tunnelController)

        assertFalse(coordinator.restartVpnIfRunning("idle"))
        tunnelController.setState(TunnelState.Connected(EngineId.SINGBOX, 2080))
        val restarted = launch {
            assertTrue(coordinator.restartVpnIfRunning("connected"))
        }
        runCurrent()
        tunnelController.setState(TunnelState.Disconnecting)
        runCurrent()
        tunnelController.setState(TunnelState.Connected(EngineId.SINGBOX, 2080))
        restarted.join()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `queued restart aborts when second processed restart sees failed state`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val allowRestartStart = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                calls += 1
                tunnelController.setState(TunnelState.Disconnecting)
                launch {
                    allowRestartStart.await()
                    if (calls == 1) {
                        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
                    } else {
                        tunnelController.setState(TunnelState.Failed(EngineId.WARP, "second failed"))
                    }
                }
            },
            tunnelController = tunnelController,
        )

        val firstRestart = launch {
            assertFalse(coordinator.restartVpnIfRunning("first-request"))
        }
        runCurrent()
        val secondRestart = launch {
            assertTrue(coordinator.restartVpnIfRunning("second-request"))
        }
        runCurrent()
        allowRestartStart.complete(Unit)
        firstRestart.join()
        secondRestart.join()

        assertEquals(2, calls)
        assertEquals(2, startServiceActions.size)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `queued restart waits for failed settle signal before next request`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val allowFirstStart = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                calls += 1
                tunnelController.setState(TunnelState.Disconnecting)
                launch {
                    allowFirstStart.await()
                    tunnelController.setState(TunnelState.Failed(EngineId.WARP, "settled failed"))
                }
            },
            tunnelController = tunnelController,
        )

        val firstRestart = launch {
            assertFalse(coordinator.restartVpnIfRunning("first-request"))
        }
        runCurrent()
        val secondRestart = launch {
            assertTrue(coordinator.restartVpnIfRunning("second-request"))
        }
        runCurrent()
        allowFirstStart.complete(Unit)
        firstRestart.join()
        secondRestart.join()

        assertEquals(1, calls)
        assertEquals(1, startServiceActions.size)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `start ignores duplicate subscription even when changes keep arriving`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val changes = MutableStateFlow<Any?>("baseline")
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
                launch { tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820)) }
            },
            observer = EngineRuntimeConfigRestartObserver(setOf(runtimeProvider(EngineId.WARP, changes))),
            tunnelController = tunnelController,
        )

        repeat(5) { coordinator.start(backgroundScope) }
        runCurrent()
        changes.value = "changed-1"
        runCurrent()
        changes.value = "changed-2"
        runCurrent()

        assertEquals(2, startServiceActions.size)
    }

    @Test
    fun `start does nothing when already started`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val coordinator = coordinator(
            context = recordingContext(startServiceActions),
            tunnelController = TunnelController(),
        )

        coordinator.start(backgroundScope)
        coordinator.start(backgroundScope)
        coordinator.start(backgroundScope)

        assertTrue(startServiceActions.isEmpty())
    }

    @Test
    fun `restart queues and coalesces while restart in progress`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val allowStop = CompletableDeferred<Unit>()
        val allowRestartStart = CompletableDeferred<Unit>()
        val coordinator = coordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
                launch {
                    allowStop.await()
                    tunnelController.setState(TunnelState.Probing(EngineId.WARP))
                    allowRestartStart.await()
                    tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
                }
            },
            tunnelController = tunnelController,
        )

        val first = launch { assertTrue(coordinator.restartVpnIfRunning("first")) }
        runCurrent()
        val second = launch { assertTrue(coordinator.restartVpnIfRunning("second")) }
        runCurrent()
        allowStop.complete(Unit)
        runCurrent()
        allowRestartStart.complete(Unit)
        first.join()
        second.join()

        assertEquals(2, startServiceActions.size)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart aborts queued work after exception and ignores disconnecting next state`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun startService(intent: Intent): android.content.ComponentName? {
                    startServiceActions += intent.action
                    tunnelController.setState(TunnelState.Disconnecting)
                    throw IllegalStateException("boom")
                }
            },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = tunnelController,
        )

        val first = runCatching { coordinator.restartVpnIfRunning("first") }
        val second = runCatching { coordinator.restartVpnIfRunning("second") }

        assertTrue(first.isFailure)
        assertEquals(false, second.getOrThrow())
        assertEquals(1, startServiceActions.size)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    @Test
    fun `restart sees queued state from observable state flow`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.SINGBOX, 2080))
        val changes = MutableStateFlow<Any?>("baseline")
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
                launch { tunnelController.setState(TunnelState.Connected(EngineId.SINGBOX, 2080)) }
            },
            observer = EngineRuntimeConfigRestartObserver(setOf(runtimeProvider(EngineId.SINGBOX, changes))),
            tunnelController = tunnelController,
        )

        coordinator.start(backgroundScope)
        runCurrent()
        changes.value = "changed"
        runCurrent()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertEquals(TunnelState.Connected(EngineId.SINGBOX, 2080), tunnelController.state.first())
    }

    @Test
    fun `coalesced restart branch returns accepted so observer can advance baseline`() {
        val source = readSource("src/main/java/ru/ozero/app/vpn/RuntimeConfigRestartCoordinator.kt")
        val coalescedBlock = source.substringAfter("if (!shouldProcess)")
            .substringBefore("var completed = false")

        assertTrue(coalescedBlock.contains("return true"))
        assertFalse(coalescedBlock.contains("return false"))
    }

    private fun readSource(path: String): String =
        java.io.File(
            System.getProperty("user.dir") ?: ".",
            path,
        ).readText()

    private fun coordinator(
        context: Context,
        tunnelController: TunnelController,
    ): RuntimeConfigRestartCoordinator = RuntimeConfigRestartCoordinator(
        context = context,
        observer = EngineRuntimeConfigRestartObserver(emptySet()),
        tunnelController = tunnelController,
    )

    private fun recordingContext(
        startServiceActions: MutableList<String?>,
        onStartService: () -> Unit = {},
    ): Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun startService(intent: Intent): android.content.ComponentName? {
            startServiceActions += intent.action
            onStartService()
            return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun RuntimeConfigRestartCoordinator.restartQueue(): ArrayDeque<String> {
        val field = javaClass.getDeclaredField("restartQueue")
        field.isAccessible = true
        return field.get(this) as ArrayDeque<String>
    }

    private fun RuntimeConfigRestartCoordinator.restartInProgress(): Boolean {
        val field = javaClass.getDeclaredField("restartInProgress")
        field.isAccessible = true
        return field.getBoolean(this)
    }

    private fun TunnelController.setState(state: TunnelState) {
        val field = javaClass.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<TunnelState>
        flow.value = state
    }

    private fun runtimeProvider(
        engineId: EngineId,
        changes: MutableStateFlow<Any?>,
    ): EngineRuntimeConfigProvider = object : EngineRuntimeConfigProvider {
        override val engineId: EngineId = engineId
        override val changes = changes
        override val restartReason: String = "runtime changed"
    }
}
