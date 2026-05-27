package ru.ozero.app.relay

interface DummyPipeFactory {
    data class PipeHandle(val readFd: Int, val writeEnd: AutoCloseable)
    fun create(): PipeHandle
}
