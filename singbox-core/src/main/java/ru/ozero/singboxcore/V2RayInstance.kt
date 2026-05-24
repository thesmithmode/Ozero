package ru.ozero.singboxcore

class V2RayInstance {
    internal val native = libcore.V2RayInstance()

    fun loadConfig(config: String) = native.loadConfig(config)

    fun start() = native.start()

    fun stop() = native.close()

    fun queryStats(tag: String, direction: String): Long = native.queryStats(tag, direction)

    fun withProtect(socketPath: String) = native.withProtect(socketPath)
}
