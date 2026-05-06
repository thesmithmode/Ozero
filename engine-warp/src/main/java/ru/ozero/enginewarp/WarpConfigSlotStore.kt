package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow

interface WarpConfigSlotStore {
    fun slots(): Flow<List<WarpConfigSlot>>
    fun activeConfig(): Flow<WarpConfig?>
    suspend fun addSlot(name: String, config: WarpConfig): String
    suspend fun setActive(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun updateSlot(id: String, name: String, config: WarpConfig)
    suspend fun delete(id: String)
    suspend fun clear()
    suspend fun replaceAll(slots: List<WarpConfigSlot>)
    suspend fun migrateIfNeeded() {}
}
