package ru.ozero.coreapi

sealed class StartResult {
    data class Success(val socksPort: Int) : StartResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : StartResult()
}
