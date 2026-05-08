package ru.ozero.commonvpn

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelControllerTest {
    private lateinit var controller: TunnelController

    @BeforeEach
    fun setUp() {
        controller = TunnelController()
    }

    @Test
    fun initialStateIsIdle() {
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun fullHappyPath_idleProbingConnectingConnectedDisconnectingIdle() {
        controller.onProbing()
        assertIs<TunnelState.Probing>(controller.state.value)

        controller.onConnecting(EngineId.BYEDPI)
        val connecting = controller.state.value
        assertIs<TunnelState.Connecting>(connecting)
        assertEquals(EngineId.BYEDPI, connecting.engineId)

        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        val connected = controller.state.value
        assertIs<TunnelState.Connected>(connected)
        assertEquals(1080, connected.socksPort)

        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)

        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun engineDeathFromConnectedTransitionsToFailed() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "process exited with code 1")
        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
        assertEquals("process exited with code 1", state.reason)
    }

    @Test
    fun failedStateIsNotIdleKillSwitchInvariant() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        assertTrue(controller.state.value is TunnelState.Failed)
        assertTrue(controller.state.value !is TunnelState.Idle)
    }

    @Test
    fun failedCanRetryViaProbing() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        assertIs<TunnelState.Failed>(controller.state.value)

        controller.onProbing()
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun invalidIdleToConnectedIgnored() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidIdleToConnectingIgnored() {
        controller.onConnecting(EngineId.BYEDPI)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidIdleToDisconnectingIgnored() {
        controller.onDisconnecting()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidConnectedToConnectingIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onConnecting(EngineId.BYEDPI)
        val state = controller.state.value
        assertIs<TunnelState.Connected>(state)
        assertEquals(1080, state.socksPort)
    }

    @Test
    fun invalidConnectedToProbingIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onProbing()
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun invalidProbingToConnectedIgnored() {
        controller.onProbing()
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun idempotentDuplicateConnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onConnecting(EngineId.BYEDPI)
        val state = controller.state.value
        assertIs<TunnelState.Connecting>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
    }

    @Test
    fun idempotentDuplicateDisconnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onDisconnecting()
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun probingCanGoToFailedDirectly() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "no engines available")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun connectingCanGoToFailedDirectly() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineDied(EngineId.BYEDPI, "engine start timeout")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun disconnectingCanResetToIdle() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onDisconnecting()
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidConnectedToIdleIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.reset()
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun failedCanGoToIdleViaReset() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun connectedCanGoDirectToFailed() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "engine died after connected")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun probingCanCancelToDisconnecting() {
        controller.onProbing()
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun connectingCanCancelToDisconnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun statsInitiallyNull() {
        assertNull(controller.stats.value)
    }

    @Test
    fun updateStatsPublishesSnapshot() {
        val snapshot = TunnelStats(
            txPackets = 10,
            txBytes = 1024,
            rxPackets = 20,
            rxBytes = 4096,
            timestampMs = 1000,
        )
        controller.updateStats(snapshot)
        assertEquals(snapshot, controller.stats.value)
    }

    @Test
    fun updateStatsCalculatesBpsFromDelta() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.updateStats(
            TunnelStats(
                txPackets = 0, txBytes = 0, rxPackets = 0, rxBytes = 0, timestampMs = 1000,
            ),
        )
        controller.updateStats(
            TunnelStats(
                txPackets = 10, txBytes = 1024, rxPackets = 20, rxBytes = 2048, timestampMs = 2000,
            ),
        )
        val snapshot = controller.stats.value
        assertNotNull(snapshot)
        assertTrue(snapshot.bpsIn > 0.0, "bpsIn должен быть > 0 после второго sample")
        assertTrue(snapshot.bpsOut > 0.0, "bpsOut должен быть > 0 после второго sample")
        assertEquals(2048L, snapshot.rxBytes)
    }

    @Test
    fun onEngineStartedSetsSessionStartMs() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        val before = System.currentTimeMillis()
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.updateStats(
            TunnelStats(txPackets = 0, txBytes = 0, rxPackets = 0, rxBytes = 0, timestampMs = before + 100),
        )
        val snapshot = controller.stats.value
        assertNotNull(snapshot)
        assertTrue(snapshot.sessionStartMs >= before, "sessionStartMs должен быть установлен в onEngineStarted")
    }

    @Test
    fun stagnantInitiallyFalse() {
        assertEquals(false, controller.stagnant.value)
    }

    @Test
    fun stagnantTrueWhenConnectedAndStatsFlatPastThreshold() {
        var clock = 1_000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        val ctl = TunnelController(stagnationMonitor = mon)
        ctl.onProbing()
        ctl.onConnecting(EngineId.BYEDPI)
        ctl.onEngineStarted(EngineId.BYEDPI, 1080)
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        clock += 30_001L
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        assertEquals(true, ctl.stagnant.value)
    }

    @Test
    fun stagnantFalseWhenStatsChange() {
        var clock = 1_000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        val ctl = TunnelController(stagnationMonitor = mon)
        ctl.onProbing()
        ctl.onConnecting(EngineId.BYEDPI)
        ctl.onEngineStarted(EngineId.BYEDPI, 1080)
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        clock += 30_001L
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        assertEquals(true, ctl.stagnant.value)
        clock += 1_000L
        ctl.updateStats(TunnelStats(0, 150, 0, 200, clock))
        assertEquals(false, ctl.stagnant.value)
    }

    @Test
    fun stagnantNotEmittedWhenNotConnected() {
        var clock = 1_000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        val ctl = TunnelController(stagnationMonitor = mon)
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        clock += 30_001L
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        assertEquals(false, ctl.stagnant.value, "не Connected — stagnation не emit")
    }

    @Test
    fun resetClearsStagnantFlag() {
        var clock = 1_000L
        val mon = StatsStagnationMonitor(thresholdMs = 30_000L, nowMs = { clock })
        val ctl = TunnelController(stagnationMonitor = mon)
        ctl.onProbing()
        ctl.onConnecting(EngineId.BYEDPI)
        ctl.onEngineStarted(EngineId.BYEDPI, 1080)
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        clock += 30_001L
        ctl.updateStats(TunnelStats(0, 100, 0, 200, clock))
        assertEquals(true, ctl.stagnant.value)
        ctl.onDisconnecting()
        ctl.reset()
        assertEquals(false, ctl.stagnant.value)
    }

    @Test
    fun killswitchActiveInitiallyFalse() {
        assertEquals(false, controller.killswitchActive.value)
    }

    @Test
    fun killswitchEngagedSetsFlagAndTransitionsToFailed() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onKillswitchEngaged(EngineId.BYEDPI, "engine died, killswitch on")
        assertIs<TunnelState.Failed>(controller.state.value)
        assertEquals(true, controller.killswitchActive.value)
    }

    @Test
    fun killswitchReleasedClearsFlagOnly() {
        controller.onProbing()
        controller.onKillswitchEngaged(EngineId.BYEDPI, "x")
        assertEquals(true, controller.killswitchActive.value)
        controller.onKillswitchReleased()
        assertEquals(false, controller.killswitchActive.value)
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun resetClearsKillswitchFlag() {
        controller.onProbing()
        controller.onKillswitchEngaged(EngineId.BYEDPI, "x")
        controller.reset()
        assertEquals(false, controller.killswitchActive.value)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun killswitchReleasedNoOpWhenInactive() {
        controller.onKillswitchReleased()
        assertEquals(false, controller.killswitchActive.value)
    }

    @Test
    fun failedToProbingDoesNotAutoClearKillswitch() {
        controller.onProbing()
        controller.onKillswitchEngaged(EngineId.BYEDPI, "x")
        controller.onProbing()
        assertEquals(true, controller.killswitchActive.value)
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun killswitchEngagedFromProbingSurvives() {
        controller.onProbing()
        controller.onKillswitchEngaged(EngineId.WARP, "preflight failed")
        assertEquals(true, controller.killswitchActive.value)
        val s = controller.state.value
        assertIs<TunnelState.Failed>(s)
        assertEquals(EngineId.WARP, s.engineId)
    }

    @Test
    fun resetClearsStats() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.updateStats(
            TunnelStats(
                txPackets = 1,
                txBytes = 100,
                rxPackets = 2,
                rxBytes = 200,
                timestampMs = 500,
            ),
        )
        controller.onDisconnecting()
        controller.reset()
        assertNull(controller.stats.value, "reset() обязан очистить stats — иначе stale значения мелькают на reconnect")
    }
}
