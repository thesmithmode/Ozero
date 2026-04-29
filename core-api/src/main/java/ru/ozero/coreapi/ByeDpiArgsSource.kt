package ru.ozero.coreapi

fun interface ByeDpiArgsSource {
    suspend fun winningArgs(): String?
}
