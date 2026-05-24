package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow

class WarpConfigDuplicateException(val existingSlotId: String, val existingSlotName: String) :
    IllegalStateException("WARP config duplicate of slot '$existingSlotName'")

interface WarpConfigSlotStore {
    fun slots(): Flow<List<WarpConfigSlot>>
    fun activeSlot(): Flow<WarpConfigSlot?>
    fun activeConfig(): Flow<WarpConfig?>
    suspend fun addSlot(name: String, config: WarpConfig, rawIni: String? = null, endpointList: List<String> = emptyList()): String
    suspend fun setActive(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String? = null, endpointList: List<String> = emptyList())
    suspend fun delete(id: String)
    suspend fun clear()
    suspend fun replaceAll(slots: List<WarpConfigSlot>)
    suspend fun migrateIfNeeded() {}
}

internal fun WarpConfig.dedupFingerprint(): String =
    listOf(privateKey, peerPublicKey, peerEndpoint).joinToString("|")
