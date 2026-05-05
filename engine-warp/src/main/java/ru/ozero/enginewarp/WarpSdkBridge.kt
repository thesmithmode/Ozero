package ru.ozero.enginewarp

interface WarpSdkBridge {
    suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
    ): AttachResult

    suspend fun detachTun()

    fun isRunning(): Boolean

    sealed interface AttachResult {
        data object Success : AttachResult
        data class Failed(val reason: String) : AttachResult
    }
}
