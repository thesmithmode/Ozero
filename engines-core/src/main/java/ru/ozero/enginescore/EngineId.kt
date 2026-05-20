package ru.ozero.enginescore

enum class EngineId(val displayName: String, val isStub: Boolean) {
    BYEDPI(displayName = "ByeDPI", isStub = false),
    URNETWORK(displayName = "URnetwork", isStub = false),
    WARP(displayName = "WARP", isStub = false),
    MASTERDNS(displayName = "MasterDNS", isStub = false),
    XRAY(displayName = "Xray", isStub = true),
    AMNEZIA(displayName = "AmneziaWG 2.0", isStub = true),
    HYSTERIA2(displayName = "Hysteria2", isStub = true),
    NAIVE(displayName = "NaiveProxy", isStub = true),
    TOR(displayName = "Tor", isStub = true),
    FPTN(displayName = "FPTN", isStub = true),
}
