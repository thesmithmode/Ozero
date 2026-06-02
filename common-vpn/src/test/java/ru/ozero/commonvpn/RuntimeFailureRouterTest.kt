package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuntimeFailureRouterTest {

    @Test
    fun `without bound handler routes failure to TunnelController`() {
        val controller = TunnelController()
        val router = RuntimeFailureRouter(controller)

        controller.onProbing(EngineId.WARP)
        router.handleEngineFailure(EngineId.WARP, "binder died")

        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.WARP, state.engineId)
        assertEquals("binder died", state.reason)
    }

    @Test
    fun `bound handler receives failures instead of fallback controller path`() {
        val controller = TunnelController()
        val router = RuntimeFailureRouter(controller)
        val calls = mutableListOf<Pair<EngineId, String>>()

        router.bind { engineId, reason -> calls += engineId to reason }
        router.handleEngineFailure(EngineId.URNETWORK, "io-loop-ended")

        assertEquals(listOf(EngineId.URNETWORK to "io-loop-ended"), calls)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun `unbind only clears the currently bound handler instance`() {
        val controller = TunnelController()
        val router = RuntimeFailureRouter(controller)
        val staleCalls = mutableListOf<Pair<EngineId, String>>()
        val activeCalls = mutableListOf<Pair<EngineId, String>>()
        val staleHandler: (EngineId, String) -> Unit = { engineId, reason -> staleCalls += engineId to reason }
        val activeHandler: (EngineId, String) -> Unit = { engineId, reason -> activeCalls += engineId to reason }

        router.bind(staleHandler)
        router.bind(activeHandler)
        router.unbind(staleHandler)
        router.handleEngineFailure(EngineId.FPTN, "runtime failed")

        assertTrue(staleCalls.isEmpty())
        assertEquals(listOf(EngineId.FPTN to "runtime failed"), activeCalls)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun `unbind current handler restores fallback controller path`() {
        val controller = TunnelController()
        val router = RuntimeFailureRouter(controller)
        val handler: (EngineId, String) -> Unit = { _, _ -> error("handler should be unbound") }

        router.bind(handler)
        router.unbind(handler)
        controller.onProbing(EngineId.SINGBOX)
        router.handleEngineFailure(EngineId.SINGBOX, "binder-died")

        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.SINGBOX, state.engineId)
        assertEquals("binder-died", state.reason)
    }
}
