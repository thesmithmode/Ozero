package ru.ozero.enginewarp

interface WarpSdkBridge {
    suspend fun start(config: WarpConfig): StartResult
    suspend fun stop()
    fun isRunning(): Boolean

    sealed interface StartResult {
        data object Success : StartResult
        data class Failed(val reason: String) : StartResult
    }
}
