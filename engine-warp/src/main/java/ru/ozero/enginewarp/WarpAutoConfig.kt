package ru.ozero.enginewarp

interface WarpAutoConfig {
    suspend fun register(onProgress: ((String) -> Unit)? = null): Result<WarpConfig>
    fun remainingCooldownMs(): Long = 0L
}
