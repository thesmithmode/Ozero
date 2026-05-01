package ru.ozero.enginescore

interface TunFdAcceptor {
    suspend fun attachTun(tunFd: Int): TunAttachResult
}

sealed class TunAttachResult {
    data object Success : TunAttachResult()
    data class Failure(val reason: String) : TunAttachResult()
}
