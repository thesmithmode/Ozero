package ru.ozero.enginescore

sealed class ChainResult {
    data class Success(
        val finalSocksPort: Int,
        val finalSocksUsername: String? = null,
        val finalSocksPassword: String? = null,
    ) : ChainResult()
    data class Failure(
        val failedAtIndex: Int,
        val reason: String,
        val rolledBack: Int,
    ) : ChainResult()
}
