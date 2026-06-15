package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OzeroVpnServiceActionDispatcherTest {

    @Test
    fun `foreground failure stops self and returns not sticky`() {
        val calls = Calls(foreground = false)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 7)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(listOf(7), calls.stopSelfIds)
        assertEquals(0, calls.startCalls)
    }

    @Test
    fun `missing injection stops self after foreground`() {
        val calls = Calls(injected = false)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 8)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(listOf(8), calls.stopSelfIds)
        assertEquals(0, calls.startCalls)
    }

    @Test
    fun `start and null action clear stopping and start vpn`() {
        val explicit = Calls()
        val explicitResult = explicit.dispatcher().dispatch(OzeroVpnService.ACTION_START, 1)

        assertEquals(OzeroVpnServiceStartResult.STICKY, explicitResult)
        assertEquals(listOf(1), explicit.latestStartIds)
        assertEquals(1, explicit.clearStoppingCalls)
        assertEquals(1, explicit.startCalls)

        val nullAction = Calls()
        val nullResult = nullAction.dispatcher().dispatch(null, 2)

        assertEquals(OzeroVpnServiceStartResult.STICKY, nullResult)
        assertEquals(listOf(2), nullAction.latestStartIds)
        assertEquals(1, nullAction.clearStoppingCalls)
        assertEquals(1, nullAction.startCalls)
    }

    @Test
    fun `stop action delegates to stop vpn without clearing start state`() {
        val calls = Calls()

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_STOP, 3)

        assertEquals(OzeroVpnServiceStartResult.STICKY, result)
        assertEquals(listOf(3), calls.latestStartIds)
        assertEquals(1, calls.stopCalls)
        assertEquals(0, calls.clearStoppingCalls)
        assertEquals(0, calls.startCalls)
    }

    @Test
    fun `restart action stops self when tunnel idle and restarts when active`() {
        val idle = Calls(idle = true)
        val idleResult = idle.dispatcher().dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 4)

        assertEquals(OzeroVpnServiceStartResult.STICKY, idleResult)
        assertEquals(listOf(4), idle.stopSelfIds)
        assertEquals(0, idle.restartCalls)

        val active = Calls(idle = false)
        val activeResult = active.dispatcher().dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 5)

        assertEquals(OzeroVpnServiceStartResult.STICKY, activeResult)
        assertTrue(active.stopSelfIds.isEmpty())
        assertEquals(1, active.restartCalls)
    }

    @Test
    fun `unknown action only records latest start id and stays sticky`() {
        val calls = Calls()

        val result = calls.dispatcher().dispatch("unknown", 6)

        assertEquals(OzeroVpnServiceStartResult.STICKY, result)
        assertEquals(listOf(6), calls.latestStartIds)
        assertEquals(0, calls.startCalls + calls.stopCalls + calls.restartCalls)
    }

    @Test
    fun `exception during action maps to not sticky and requests stop`() {
        val calls = Calls(throwOnStart = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 9)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(1, calls.stopCalls)
        assertFalse(calls.stopSelfIds.contains(9))
    }

    private class Calls(
        private val foreground: Boolean = true,
        private val injected: Boolean = true,
        private val idle: Boolean = false,
        private val throwOnStart: Boolean = false,
    ) {
        val latestStartIds = mutableListOf<Int>()
        val stopSelfIds = mutableListOf<Int>()
        var clearStoppingCalls = 0
        var startCalls = 0
        var stopCalls = 0
        var restartCalls = 0

        fun dispatcher() = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = { latestStartIds += it },
            isChainOrchestratorReady = { injected },
            enterForeground = { foreground },
            isTunnelIdle = { idle },
            clearStopping = { clearStoppingCalls++ },
            stopSelf = { stopSelfIds += it },
            startVpn = {
                startCalls++
                if (throwOnStart) error("start failed")
            },
            stopVpn = { stopCalls++ },
            restartVpn = { restartCalls++ },
        )
    }
}
