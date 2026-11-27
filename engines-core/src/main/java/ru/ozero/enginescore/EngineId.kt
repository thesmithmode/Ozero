package ru.ozero.enginescore

enum class EngineId(val displayName: String, val isStub: Boolean, val manualStandaloneOnly: Boolean = false) {
    BYEDPI(displayName = "ByeDPI", isStub = false),
    URNETWORK(displayName = "URnetwork", isStub = false),
    WARP(displayName = "WARP", isStub = false),
    MASTERDNS(displayName = "MasterDNS", isStub = false),
    SINGBOX(displayName = "Sing-box", isStub = false),
    FPTN(displayName = "FPTN", isStub = false),
    OPERA(displayName = "Opera Proxy", isStub = false),
    TGWSS(displayName = "Telegram WSS", isStub = false, manualStandaloneOnly = true),
    MASQUE(displayName = "MASQUE", isStub = false),
    GFVPN(displayName = "Adaptive VPN", isStub = false, manualStandaloneOnly = true),
}
