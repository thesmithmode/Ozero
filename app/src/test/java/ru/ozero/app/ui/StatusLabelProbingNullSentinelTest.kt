package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import ru.ozero.app.R
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

class StatusLabelProbingNullSentinelTest {

    @Test
    fun `pickStatusLabelRes — Probing с engineId=null возвращает main_status_probing`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Probing(engineId = null),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_probing, res)
    }

    @Test
    fun `pickStatusLabelRes — Probing WARP возвращает main_status_probing_warp`() {
        val res = pickStatusLabelRes(
            state = TunnelState.Probing(engineId = EngineId.WARP),
            switching = null,
            urnetworkPeerCount = 0,
            isReconnecting = false,
        )
        assertEquals(R.string.main_status_probing_warp, res)
    }

    @Test
    fun `probingLabelRes — null engineId fallback на main_status_probing`() {
        assertEquals(R.string.main_status_probing, probingLabelRes(null, isReconnecting = false))
    }

    @Test
    fun `probingLabelRes — null engineId reconnecting возвращает main_status_reconnecting`() {
        assertEquals(R.string.main_status_reconnecting, probingLabelRes(null, isReconnecting = true))
    }

    @Test
    fun `probingLabelRes — BYEDPI reconnecting возвращает main_status_reconnecting`() {
        assertEquals(
            R.string.main_status_reconnecting,
            probingLabelRes(EngineId.BYEDPI, isReconnecting = true),
        )
        assertEquals(
            R.string.main_status_connecting,
            probingLabelRes(EngineId.BYEDPI, isReconnecting = false),
        )
    }
}
