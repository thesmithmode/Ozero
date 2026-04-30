package ru.ozero.enginescore

sealed class ChainResult {
    data class Success(val finalSocksPort: Int) : ChainResult()
    data class Failure(
        val failedAtIndex: Int,
        val reason: String,
        val rolledBack: Int,
    ) : ChainResult()
}
