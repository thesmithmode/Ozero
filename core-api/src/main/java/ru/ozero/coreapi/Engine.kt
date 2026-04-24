package ru.ozero.coreapi

import kotlinx.coroutines.flow.Flow

interface Engine {
    val id: EngineId
    val capabilities: EngineCapabilities
    suspend fun start(config: EngineConfig): StartResult
    suspend fun stop()
    suspend fun probe(): ProbeResult
    fun stats(): Flow<EngineStats>
}
