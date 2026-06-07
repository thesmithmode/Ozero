package ru.ozero.app.vpn

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
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
