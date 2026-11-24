package ru.ozero.engineopera

interface OperaTunBridge {
    suspend fun start(tunFd: Int, config: String): Boolean
    suspend fun isRunning(): Boolean
    suspend fun stop()
}
