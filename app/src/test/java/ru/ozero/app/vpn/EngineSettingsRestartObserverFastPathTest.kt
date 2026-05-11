package ru.ozero.app.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineSettingsRestartObserverFastPathTest {

    private fun baseSettings(
        manualEngine: EngineId? = EngineId.BYEDPI,
        ipv6Enabled: Boolean = false,
        customDnsServers: List<String> = emptyList(),
    ): SettingsModel = SettingsModel(
        manualEngine = manualEngine,
        ipv6Enabled = ipv6Enabled,
        customDnsServers = customDnsServers,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `manualEngine change triggers restart instantly without debounce`() = runTest {
        val settings = MutableStateFlow(baseSettings(manualEngine = EngineId.BYEDPI))
        val observer = EngineSettingsRestartObserver(
            settingsFlow = settings,
            vpnStateProvider = { TunnelState.Connected(EngineId.BYEDPI, 1080) },
            onRestartConnected = { },
        )
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.take(1).toList(collected) }
        advanceUntilIdle()

        settings.value = baseSettings(manualEngine = EngineId.WARP)
        advanceTimeBy(50)
        advanceUntilIdle()

        assertEquals(1, collected.size, "manualEngine change должен пройти БЕЗ 4-сек debounce")
        assertEquals(EngineId.WARP, collected.first().manualEngine)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `non-manualEngine change still debounced`() = runTest {
        val settings = MutableStateFlow(baseSettings(ipv6Enabled = false))
        val observer = EngineSettingsRestartObserver(
            settingsFlow = settings,
            vpnStateProvider = { TunnelState.Connected(EngineId.BYEDPI, 1080) },
            onRestartConnected = { },
        )
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.take(1).toList(collected) }
        advanceUntilIdle()

        settings.value = baseSettings(ipv6Enabled = true)
        advanceTimeBy(500)
        assertTrue(collected.isEmpty(), "ipv6 change должен ждать debounce — не мгновенный")

        advanceTimeBy(4_000)
        assertEquals(1, collected.size, "после debounce 4сек ipv6 change должен пройти")
        assertTrue(collected.first().ipv6Enabled)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `manualEngine fast-path не подавляет одновременный non-manual debounce`() = runTest {
        val settings = MutableStateFlow(baseSettings(manualEngine = EngineId.BYEDPI, ipv6Enabled = false))
        val observer = EngineSettingsRestartObserver(
            settingsFlow = settings,
            vpnStateProvider = { TunnelState.Connected(EngineId.BYEDPI, 1080) },
            onRestartConnected = { },
        )
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        settings.value = baseSettings(manualEngine = EngineId.WARP, ipv6Enabled = true)
        advanceTimeBy(100)
        assertEquals(1, collected.size, "manualEngine change должен прийти мгновенно даже когда ipv6 тоже менялся")
        assertEquals(EngineId.WARP, collected[0].manualEngine)

        advanceTimeBy(4_500)
        assertTrue(collected.size >= 2, "ipv6 debounce должен дострелить отдельным emit")
        assertTrue(collected.any { it.ipv6Enabled }, "ipv6=true должен присутствовать среди emit'ов")
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `handle вызывает onRestartConnected только если Connected`() = runTest {
        var calls = 0
        var vpnState: TunnelState = TunnelState.Idle
        val observer = EngineSettingsRestartObserver(
            settingsFlow = MutableStateFlow(baseSettings()),
            vpnStateProvider = { vpnState },
            onRestartConnected = { calls++ },
        )
        val snap = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = null,
            ipv6Enabled = false,
            customDnsServers = emptyList(),
            engineAutoPriority = null,
        )

        observer.handle(snap)
        assertEquals(0, calls, "Idle state — рестарт не должен вызываться")

        vpnState = TunnelState.Connected(EngineId.BYEDPI, 1080)
        observer.handle(snap)
        assertEquals(1, calls, "Connected state — рестарт должен вызваться")
    }
}
