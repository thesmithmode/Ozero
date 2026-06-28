package ru.ozero.enginescore

sealed class StartResult {
    data class Success(
        val socksPort: Int,
        val socksUsername: String? = null,
        val socksPassword: String? = null,
    ) : StartResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : StartResult()
}
