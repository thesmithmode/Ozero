package ru.ozero.enginewarp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import ru.ozero.enginescore.VpnSocketProtector

internal class FakeWarpSdkBridge(
    private val attachResult: WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success,
    private val proxyResult: WarpSdkBridge.ProxyResult = WarpSdkBridge.ProxyResult.Failed("proxy disabled"),
    private val stopProxyDelayMs: Long = 0L,
    private val blockStopUntilForceTermination: Boolean = false,
    private val releaseCleanupOnForceTermination: Boolean = true,
) : WarpSdkBridge {
    var attachCalls: Int = 0
    var startProxyCalls: Int = 0
    var detachCalls: Int = 0
    var stopProxyCalls: Int = 0
    var forceTerminateCalls: Int = 0
    var lastFd: Int = -1
    var lastIni: String? = null
    var lastUapi: String? = null
    var lastProxyPort: Int = -1
    private var running = false
    private val forcedTermination = CompletableDeferred<Unit>()

    override suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.AttachResult {
        attachCalls++
        lastFd = tunFd
        lastIni = iniConfig
        lastUapi = uapiPath
        if (attachResult is WarpSdkBridge.AttachResult.Success) running = true
        return attachResult
    }

    override suspend fun startProxy(
        tunnelName: String,
        iniConfig: String,
        uapiPath: String,
        socksPort: Int,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.ProxyResult {
        startProxyCalls++
        lastProxyPort = socksPort
        if (proxyResult is WarpSdkBridge.ProxyResult.Success) running = true
        return proxyResult
    }

    override suspend fun detachTun() {
        detachCalls++
        running = false
    }

    override suspend fun stopProxy() {
        stopProxyCalls++
        if (blockStopUntilForceTermination) forcedTermination.await()
        if (stopProxyDelayMs > 0) delay(stopProxyDelayMs)
    }

    override fun forceTerminate() {
        forceTerminateCalls++
        if (releaseCleanupOnForceTermination) forcedTermination.complete(Unit)
    }

    fun releaseCleanup() {
        forcedTermination.complete(Unit)
    }

    override fun isRunning(): Boolean = running

    override fun reprotectSockets() = Unit
}
