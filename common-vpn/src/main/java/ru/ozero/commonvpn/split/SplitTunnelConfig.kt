package ru.ozero.commonvpn.split

import ru.ozero.enginescore.settings.SplitTunnelMode

data class SplitTunnelConfig(
    val mode: SplitTunnelMode = SplitTunnelMode.ALL,
    val packages: Set<String> = emptySet(),
)
