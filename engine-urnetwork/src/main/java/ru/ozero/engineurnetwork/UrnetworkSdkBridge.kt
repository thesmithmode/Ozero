package ru.ozero.engineurnetwork

interface UrnetworkSdkBridge {
    suspend fun start(walletAddress: String, apiUrl: String, connectUrl: String): StartResult
    suspend fun stop()
    fun isRunning(): Boolean

    sealed class StartResult {
        data object Success : StartResult()
        data class Failed(val reason: String) : StartResult()
    }
}
