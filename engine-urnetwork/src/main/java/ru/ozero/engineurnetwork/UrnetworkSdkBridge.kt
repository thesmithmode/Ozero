package ru.ozero.engineurnetwork

interface UrnetworkSdkBridge {
    suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): StartResult
    suspend fun stop()
    fun isRunning(): Boolean
    suspend fun attachTun(tunFd: Int): AttachResult

    sealed class StartResult {
        data object Success : StartResult()
        data class Failed(val reason: String) : StartResult()
    }

    sealed class AttachResult {
        data object Success : AttachResult()
        data class Failed(val reason: String) : AttachResult()
    }
}
