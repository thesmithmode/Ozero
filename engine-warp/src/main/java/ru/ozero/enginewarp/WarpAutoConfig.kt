package ru.ozero.enginewarp

data class RegisteredWarpConfig(
    val config: WarpConfig,
    val rawIni: String,
)

interface WarpAutoConfig {
    suspend fun register(onProgress: ((String) -> Unit)? = null): Result<RegisteredWarpConfig>
    fun remainingCooldownMs(): Long = 0L
}
