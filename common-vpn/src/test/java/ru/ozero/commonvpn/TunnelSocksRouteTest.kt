package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TunnelSocksRouteTest {
    @Test
    fun `route is available only on an eligible connected tunnel`() {
        assertNull(TunnelController().activeSocksRoute())
        for (port in listOf(0, -1, 65536)) {
            assertNull(connected(port).activeSocksRoute())
        }
        val controller = connected(1080)
        val route = assertNotNull(controller.activeSocksRoute())
        assertEquals(1080, route.port)
        assertSame(route, controller.activeSocksRoute())
        controller.onSwitchingStarted(EngineId.BYEDPI, EngineId.WARP)
        assertNull(controller.activeSocksRoute())
        controller.onSwitchingFinished("fixture")
        assertNotSame(route, controller.activeSocksRoute())
        assertFalse(route.register {})
    }

    @Test
    fun `disconnect failure switch and reset invalidate the route once`() {
        val transitions: List<(TunnelController) -> Unit> = listOf(
            { it.onDisconnecting() },
            { it.onEngineDied(EngineId.BYEDPI, "fixture") },
            { it.onKillswitchEngaged(EngineId.BYEDPI, "fixture") },
            { it.onSwitchingStarted(EngineId.BYEDPI, EngineId.WARP) },
            { it.reset() },
        )
        for (transition in transitions) {
            val controller = connected(1080)
            val route = assertNotNull(controller.activeSocksRoute())
            var cancelled = 0
            var completed = 0
            val done: () -> Unit = { completed++ }
            assertTrue(route.register(done))
            route.unregister(done)
            assertTrue(route.register { cancelled++ })
            transition(controller)
            controller.reset()
            assertEquals(1, cancelled)
            assertEquals(0, completed)
            assertFalse(route.register { cancelled++ })
        }
    }

    @Test
    fun `connected recovery cannot acquire route until kill switch releases`() {
        val controller = connected(1080)
        controller.onKillswitchEngaged(EngineId.BYEDPI, "fixture")
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertTrue(controller.killswitchActive.value)
        assertNull(controller.activeSocksRoute())
        controller.onKillswitchReleased()
        assertNotNull(controller.activeSocksRoute())
    }

    @Test
    fun `one failing cancellation cannot prevent other calls or tunnel transition`() {
        val controller = connected(1080)
        val route = assertNotNull(controller.activeSocksRoute())
        var cancelled = false
        assertTrue(route.register { error("fixture") })
        assertTrue(route.register { cancelled = true })
        controller.onDisconnecting()
        assertTrue(cancelled)
        assertEquals(TunnelState.Disconnecting, controller.state.value)
    }

    private fun connected(port: Int): TunnelController = TunnelController().apply {
        onProbing()
        onConnecting(EngineId.BYEDPI)
        onEngineStarted(EngineId.BYEDPI, port)
    }
}
