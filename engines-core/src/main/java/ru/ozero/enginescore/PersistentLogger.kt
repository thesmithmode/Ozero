package ru.ozero.enginescore

interface PersistentLogger {
    fun info(tag: String, msg: String)
    fun warn(tag: String, msg: String, t: Throwable? = null)
    fun error(tag: String, msg: String, t: Throwable? = null)
}

object PersistentLoggers {
    @Volatile
    var instance: PersistentLogger? = null

    fun info(tag: String, msg: String) {
        instance?.info(tag, msg)
    }

    fun warn(tag: String, msg: String, t: Throwable? = null) {
        instance?.warn(tag, msg, t)
    }

    fun error(tag: String, msg: String, t: Throwable? = null) {
        instance?.error(tag, msg, t)
    }
}
