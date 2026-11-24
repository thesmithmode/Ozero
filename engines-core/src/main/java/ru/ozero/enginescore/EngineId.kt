package ru.ozero.enginescore

enum class EngineId(val displayName: String, val isStub: Boolean, val proxyOnly: Boolean = false) {
    BYEDPI(displayName = "ByeDPI", isStub = false),
    URNETWORK(displayName = "URnetwork", isStub = false),
    WARP(displayName = "WARP", isStub = false),
    MASTERDNS(displayName = "MasterDNS", isStub = false),
    SINGBOX(displayName = "Sing-box", isStub = false),
    FPTN(displayName = "FPTN", isStub = false),
    TGWSS(displayName = "Telegram WSS", isStub = false, proxyOnly = true),
}
