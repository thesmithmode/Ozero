package ru.ozero.engineurnetwork

class StubUrnetworkSdkBridge : UrnetworkSdkBridge {

    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byJwt: String?,
    ): UrnetworkSdkBridge.StartResult =
        UrnetworkSdkBridge.StartResult.Failed(
            "URnetwork SDK AAR not yet built — see scripts/build_urnetwork.sh",
        )

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false

    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
        UrnetworkSdkBridge.AttachResult.Failed("Stub bridge — attachTun unsupported")
}
