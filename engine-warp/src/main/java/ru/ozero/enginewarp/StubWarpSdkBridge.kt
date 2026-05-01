package ru.ozero.enginewarp

class StubWarpSdkBridge : WarpSdkBridge {

    override suspend fun start(config: WarpConfig): WarpSdkBridge.StartResult =
        WarpSdkBridge.StartResult.Failed(
            "WireGuard AAR not yet built — see scripts/build_wireguard_android.sh",
        )

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false
}
