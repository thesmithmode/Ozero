package ru.ozero.enginescore

import java.net.Socket

fun interface SocketProtector {
    fun protect(socket: Socket): Boolean
}

fun interface EnginePreflight {
    suspend fun probe(protector: SocketProtector): Result

    sealed interface Result {
        data object Ok : Result
        data class Fail(val reason: String) : Result
    }
}
