package ru.ozero.enginescore

interface PersistentLogger {
    fun info(tag: String, msg: String)
    fun warn(tag: String, msg: String, t: Throwable? = null)
    fun error(tag: String, msg: String, t: Throwable? = null)
}

object PersistentLoggers {
    @Volatile
    var instance: PersistentLogger? = null
}
