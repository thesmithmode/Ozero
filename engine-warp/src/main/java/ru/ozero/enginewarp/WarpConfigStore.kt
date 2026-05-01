package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow

interface WarpConfigStore {
    fun current(): Flow<WarpConfig?>
    suspend fun save(config: WarpConfig)
    suspend fun clear()
}
