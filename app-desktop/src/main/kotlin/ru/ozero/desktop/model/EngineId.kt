package ru.ozero.desktop.model

enum class EngineId(val displayName: String, val isStub: Boolean) {
    BYEDPI(displayName = "ByeDPI", isStub = false),
    URNETWORK(displayName = "URnetwork", isStub = false),
    WARP(displayName = "WARP", isStub = false),
    MASTERDNS(displayName = "MasterDNS", isStub = false),
    SINGBOX(displayName = "Sing-box", isStub = false),
    FPTN(displayName = "FPTN", isStub = false),
}
