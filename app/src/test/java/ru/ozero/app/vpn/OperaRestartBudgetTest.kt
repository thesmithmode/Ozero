package ru.ozero.app.vpn

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import io.mockk.mockk
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
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperaRestartBudgetTest {
    @Test
    fun `slow Opera restart can connect after the normal startup deadline`() = runTest {
        val actions = mutableListOf<String?>()
        val controller = TunnelController().apply {
            onProbing()
            onConnecting(EngineId.OPERA)
            onEngineStarted(EngineId.OPERA, 1080)
        }
        val context: Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startService(intent: Intent): android.content.ComponentName? {
                actions += intent.action
                controller.onDisconnecting()
                return null
            }
        }
        val coordinator = RuntimeConfigRestartCoordinator(
            context = context,
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = controller,
            settingsRepository = mockk<SettingsRepository>(),
        )
        val restart = launch {
            assertTrue(coordinator.restartVpnIfRunning("settings changed"))
        }
        runCurrent()
        advanceTimeBy(25_001)
        assertTrue(restart.isActive)
        controller.reset()
        controller.onProbing()
        controller.onConnecting(EngineId.OPERA)
        controller.onEngineStarted(EngineId.OPERA, 1081)
        restart.join()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), actions)
        assertNull(controller.switching.value)
    }
}
