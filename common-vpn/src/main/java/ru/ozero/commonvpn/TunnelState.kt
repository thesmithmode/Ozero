package ru.ozero.commonvpn

sealed class TunnelState {
    data object Idle : TunnelState()
    data class Connected(val socksPort: Int) : TunnelState()
    data object Disconnecting : TunnelState()

    // Kill-switch активен: TUN fd остаётся открытым, трафик не протекает наружу
    data class Dead(val reason: String) : TunnelState()
}
