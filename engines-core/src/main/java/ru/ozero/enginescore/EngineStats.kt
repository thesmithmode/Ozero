package ru.ozero.enginescore

data class EngineStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val connectedSince: Long = 0L,
    val activeConnections: Int = 0,
)
