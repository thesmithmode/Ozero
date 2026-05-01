package ru.ozero.enginewarp

interface WarpAutoConfig {
    suspend fun register(): Result<WarpConfig>
}
