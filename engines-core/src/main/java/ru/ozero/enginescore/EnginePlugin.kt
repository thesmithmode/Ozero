package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow

interface EnginePlugin {
    val id: EngineId
    val capabilities: EngineCapabilities
    suspend fun start(config: EngineConfig, upstream: Upstream = Upstream.None): StartResult
    suspend fun stop()
    suspend fun probe(): ProbeResult
    fun stats(): Flow<EngineStats>
    suspend fun tunSpec(): TunSpec? = null
    fun preflight(): EnginePreflight? = null

    suspend fun awaitReady() = Unit

    suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute = IpProbeRoute.Default

    fun stopTimeoutMs(): Long = DEFAULT_STOP_TIMEOUT_MS

    suspend fun recover(): RecoverResult = RecoverResult.NotSupported

    sealed class RecoverResult {
        data object Success : RecoverResult()
        data class Failed(val reason: String) : RecoverResult()
        data object NotSupported : RecoverResult()
    }

    companion object {
        const val DEFAULT_STOP_TIMEOUT_MS = 2_000L
    }
}
