package ru.ozero.commonvpn.split

enum class SplitTunnelMode { ALL, BYPASS_LAN, ALLOWLIST, BLOCKLIST }

data class SplitTunnelConfig(
    val mode: SplitTunnelMode = SplitTunnelMode.ALL,
    val packages: Set<String> = emptySet(),
)
