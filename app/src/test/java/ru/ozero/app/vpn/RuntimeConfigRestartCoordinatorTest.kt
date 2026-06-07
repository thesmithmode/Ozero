package ru.ozero.app.vpn

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeConfigRestartCoordinatorTest {

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

        assertFalse(secondResult)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

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
}
