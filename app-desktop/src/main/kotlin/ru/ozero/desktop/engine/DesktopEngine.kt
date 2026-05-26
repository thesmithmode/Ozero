package ru.ozero.desktop.engine

import ru.ozero.desktop.model.EngineId

interface DesktopEngine {
    val id: EngineId
    val binaryName: String
    val isAvailableOnPlatform: Boolean

    suspend fun start(config: EngineConfig): EngineStartResult
    suspend fun stop()
    fun isRunning(): Boolean
    fun listeningPort(): Int
}

data class EngineConfig(
    val socksPort: Int = 0,
    val httpPort: Int = 0,
    val extraArgs: List<String> = emptyList(),
    val singboxJson: String? = null,
    val warpConfig: String? = null,
)

sealed class EngineStartResult {
    data class Success(val port: Int) : EngineStartResult()
    data class BinaryMissing(val binaryName: String) : EngineStartResult()
    data class PlatformUnavailable(val reason: String) : EngineStartResult()
    data class Failed(val reason: String, val cause: Throwable? = null) : EngineStartResult()
}
