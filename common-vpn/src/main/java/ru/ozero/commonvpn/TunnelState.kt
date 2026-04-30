package ru.ozero.commonvpn

import ru.ozero.enginescore.EngineId

sealed class TunnelState {
    data object Idle : TunnelState()
    data object Probing : TunnelState()
    data class Connecting(val engineId: EngineId) : TunnelState()
    data class Connected(val engineId: EngineId, val socksPort: Int) : TunnelState()
    data class Failed(val engineId: EngineId, val reason: String) : TunnelState()
    data object Disconnecting : TunnelState()
}
