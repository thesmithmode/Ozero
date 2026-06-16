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
    fun `start clears stopping before start`() {
        val order = mutableListOf<String>()
        val calls = Calls(clearStopping = { order += "clear" }, startAction = { order += "start" })

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 12)

        assertEquals(OzeroVpnServiceStartResult.STICKY, result)
        assertEquals(listOf("clear", "start"), order)
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
    fun `repeated actions update latest start id each time and keep callbacks isolated`() {
        val calls = Calls(idle = true)
        val dispatcher = calls.dispatcher()
        dispatcher.dispatch(OzeroVpnService.ACTION_START, 1)
        dispatcher.dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 2)
        dispatcher.dispatch(OzeroVpnService.ACTION_STOP, 3)
        dispatcher.dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 4)
        dispatcher.dispatch(null, 5)

        assertEquals(listOf(1, 2, 3, 4, 5), calls.latestStartIds)
        assertEquals(2, calls.startCalls)
        assertEquals(1, calls.stopCalls)
        assertEquals(0, calls.restartCalls)
        assertEquals(2, calls.stopSelfIds.size)
        assertEquals(2, calls.clearStoppingCalls)
        assertEquals(listOf(2, 4), calls.stopSelfIds)
    }

    @Test
    fun `exception during action maps to not sticky and requests stop`() {
        val calls = Calls(throwOnStart = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 9)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(1, calls.stopCalls)
        assertFalse(calls.stopSelfIds.contains(9))
    }

    @Test
    fun `exception during stop action is caught and stop fallback is attempted`() {
        val calls = Calls(throwOnStop = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_STOP, 10)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(2, calls.stopCalls)
    }

    @Test
    fun `exception during restart action is caught and stop fallback is attempted`() {
        val calls = Calls(throwOnRestart = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 11)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(1, calls.restartCalls)
        assertEquals(1, calls.stopCalls)
    }

    @Test
    fun `exception fallback still returns not sticky when stop also throws`() {
        val calls = Calls(throwOnStart = true, throwOnStop = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 13)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(1, calls.startCalls)
        assertEquals(1, calls.stopCalls)
    }

    @Test
    fun `exception before action dispatch is caught and stop fallback runs`() {
        val calls = Calls(throwOnLatestStartId = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_START, 14)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(listOf(14), calls.latestStartIds)
        assertEquals(0, calls.startCalls)
        assertEquals(1, calls.stopCalls)
    }

    @Test
    fun `exception from readiness guard is caught after foreground promotion`() {
        val calls = Calls(throwOnReadyCheck = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_STOP, 15)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(0, calls.latestStartIds.size)
        assertEquals(1, calls.stopCalls)
    }

    @Test
    fun `null action exception from clearStopping is caught before start`() {
        val calls = Calls(throwOnClearStopping = true)

        val result = calls.dispatcher().dispatch(null, 16)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(listOf(16), calls.latestStartIds)
        assertEquals(1, calls.clearStoppingCalls)
        assertEquals(0, calls.startCalls)
        assertEquals(1, calls.stopCalls)
    }

    @Test
    fun `restart idle stopSelf exception is caught and falls back to stop`() {
        val calls = Calls(idle = true, throwOnStopSelf = true)

        val result = calls.dispatcher().dispatch(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG, 17)

        assertEquals(OzeroVpnServiceStartResult.NOT_STICKY, result)
        assertEquals(listOf(17), calls.latestStartIds)
        assertEquals(listOf(17), calls.stopSelfIds)
        assertEquals(0, calls.restartCalls)
        assertEquals(1, calls.stopCalls)
    }

    private class Calls(
        private val foreground: Boolean = true,
        private val injected: Boolean = true,
        private val idle: Boolean = false,
        private val throwOnStart: Boolean = false,
        private val throwOnStop: Boolean = false,
        private val throwOnRestart: Boolean = false,
        private val throwOnLatestStartId: Boolean = false,
        private val throwOnReadyCheck: Boolean = false,
        private val throwOnClearStopping: Boolean = false,
        private val throwOnStopSelf: Boolean = false,
        private val startAction: () -> Unit = {},
        private val clearStopping: () -> Unit = {},
    ) {
        val latestStartIds = mutableListOf<Int>()
        val stopSelfIds = mutableListOf<Int>()
        var clearStoppingCalls = 0
        var startCalls = 0
        var stopCalls = 0
        var restartCalls = 0

        fun dispatcher() = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = {
                latestStartIds += it
                if (throwOnLatestStartId) {
                    error("latest failed")
                }
            },
            isChainOrchestratorReady = {
                if (throwOnReadyCheck) {
                    error("ready failed")
                }
                injected
            },
            enterForeground = { foreground },
            isTunnelIdle = { idle },
            clearStopping = {
                clearStoppingCalls++
                if (throwOnClearStopping) {
                    error("clear failed")
                }
                clearStopping()
            },
            stopSelf = {
                stopSelfIds += it
                if (throwOnStopSelf) {
                    error("stopSelf failed")
                }
            },
            startVpn = {
                startCalls++
                if (throwOnStart) {
                    error("start failed")
                }
                startAction()
            },
            stopVpn = {
                stopCalls++
                if (throwOnStop && stopCalls == 1) {
                    error("stop failed")
                }
            },
            restartVpn = {
                restartCalls++
                if (throwOnRestart) {
                    error("restart failed")
                }
            },
        )
    }
}
