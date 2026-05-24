package ru.ozero.enginescore

enum class EngineId(val displayName: String, val isStub: Boolean) {
    BYEDPI(displayName = "ByeDPI", isStub = false),
    URNETWORK(displayName = "URnetwork", isStub = false),
    WARP(displayName = "WARP", isStub = false),
    MASTERDNS(displayName = "MasterDNS", isStub = false),
    SINGBOX(displayName = "Sing-box", isStub = false),
    XRAY(displayName = "Xray", isStub = true),
    HYSTERIA2(displayName = "Hysteria2", isStub = true),
    NAIVE(displayName = "NaiveProxy", isStub = true),
    TOR(displayName = "Tor", isStub = true),
    FPTN(displayName = "FPTN", isStub = false),
}
