package ru.ozero.coreapi

fun interface ManualEngineSource {
    suspend fun current(): EngineId?
}
