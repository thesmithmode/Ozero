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

    fun isRunning(): Boolean

    fun reprotectSockets()

    sealed interface AttachResult {
        data object Success : AttachResult
        data class Failed(val reason: String) : AttachResult
    }
}
