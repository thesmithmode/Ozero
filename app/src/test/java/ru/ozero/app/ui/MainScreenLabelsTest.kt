package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import ru.ozero.app.R
import ru.ozero.commonvpn.SwitchingTransition
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

class MainScreenLabelsTest {

    @Test
    fun `switching transition overrides everything`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Idle,
            switching = SwitchingTransition(from = EngineId.WARP, to = EngineId.BYEDPI),
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_switching, res)
    }

    @Test
    fun `urnetwork connected with zero peers shows searching`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_urnetwork_searching, res)
    }

    @Test
    fun `urnetwork connected with peers shows connected`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
            switching = null,
            urnetworkPeerCount = 5,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_connected, res)
    }

    @Test
    fun `idle shows disconnected`() {
        val res = pickStatusLabelRes(TunnelState.Idle, null, 0, false)
        assertEquals(R.string.main_status_disconnected, res)
    }

    @Test
    fun `failed not reconnecting shows failed`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Failed(EngineId.WARP, "boom"),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_failed, res)
    }

    @Test
    fun `failed while reconnecting shows reconnecting`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Failed(EngineId.WARP, "boom"),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = true,
        )
        assertEquals(R.string.main_status_reconnecting, res)
    }

    @Test
    fun `connecting not reconnecting shows connecting`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Connecting(EngineId.BYEDPI),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_connecting, res)
    }

    @Test
    fun `connecting while reconnecting shows reconnecting`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Connecting(EngineId.BYEDPI),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = true,
        )
        assertEquals(R.string.main_status_reconnecting, res)
    }

    @Test
    fun `probing reconnecting overrides engine specific label`() {
        for (engine in listOf(EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK, null)) {
            val res = probingLabelRes(engine, isReconnecting = true)
            assertEquals(
                R.string.main_status_reconnecting,
                res,
                "engine=$engine должен показывать reconnecting когда isReconnecting=true",
            )
        }
    }

    @Test
    fun `probing warp shows probing warp`() {
        assertEquals(R.string.main_status_probing_warp, probingLabelRes(EngineId.WARP, false))
    }

    @Test
    fun `probing byedpi shows connecting`() {
        assertEquals(R.string.main_status_connecting, probingLabelRes(EngineId.BYEDPI, false))
    }

    @Test
    fun `probing urnetwork shows probing`() {
        assertEquals(R.string.main_status_probing, probingLabelRes(EngineId.URNETWORK, false))
    }

    @Test
    fun `probing null engine shows generic probing`() {
        assertEquals(R.string.main_status_probing, probingLabelRes(null, false))
    }

    @Test
    fun `disconnecting state`() {
        val res = pickStatusLabelRes(TunnelState.Disconnecting, null, 0, false)
        assertEquals(R.string.main_status_disconnecting, res)
    }

    @Test
    fun `connected non-urnetwork shows connected regardless of peerCount`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Connected(EngineId.WARP, socksPort = 0),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_connected, res)
    }

    @Test
    fun `ui selected engine prefers real tunnel engine over manual selection`() {
        val selected = resolveUiSelectedEngine(
            tunnelState = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
            switching = null,
            manualEngine = EngineId.BYEDPI,
        )
        assertEquals(EngineId.URNETWORK, selected)
    }

    @Test
    fun `ui selected engine prefers switching target while transition is active`() {
        val selected = resolveUiSelectedEngine(
            tunnelState = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
            switching = SwitchingTransition(from = EngineId.URNETWORK, to = EngineId.BYEDPI),
            manualEngine = EngineId.URNETWORK,
        )
        assertEquals(EngineId.BYEDPI, selected)
    }
}
