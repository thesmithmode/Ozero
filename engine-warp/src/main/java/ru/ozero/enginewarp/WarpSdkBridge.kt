package ru.ozero.enginewarp

import ru.ozero.enginescore.VpnSocketProtector

interface WarpSdkBridge {
    suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): AttachResult

    suspend fun detachTun()

    suspend fun startProxy(
        tunnelName: String,
        iniConfig: String,
        uapiPath: String,
        socksPort: Int,
        protector: VpnSocketProtector,
    ): ProxyResult = ProxyResult.Failed("WARP proxy mode is not supported by this bridge")

    suspend fun stopProxy() = Unit

    fun isRunning(): Boolean

    fun reprotectSockets()

    sealed interface AttachResult {
        data object Success : AttachResult
        data class Failed(val reason: String) : AttachResult
    }

    sealed interface ProxyResult {
        data object Success : ProxyResult
        data class Failed(val reason: String) : ProxyResult
    }
}
