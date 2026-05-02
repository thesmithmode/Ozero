package ru.ozero.enginescore

enum class EngineId(val displayName: String, val isStub: Boolean) {
    BYEDPI(displayName = "ByeDPI", isStub = false),

    // v0.0.2-4: bridge готов, AAR rebuild требуется (libgojni.so collision между двумя
    // gomobile bind проектами URnetworkSdk + userwireguard) — пока StubUrnetworkSdkBridge
    URNETWORK(displayName = "URnetwork", isStub = true),
    XRAY(displayName = "Xray", isStub = true),
    AMNEZIA(displayName = "AmneziaWG 2.0", isStub = true),
    HYSTERIA2(displayName = "Hysteria2", isStub = true),
    NAIVE(displayName = "NaiveProxy", isStub = true),
    TOR(displayName = "Tor", isStub = true),
    WARP(displayName = "WARP", isStub = true),
    FPTN(displayName = "FPTN", isStub = true),
}
