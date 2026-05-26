package ru.ozero.enginescore

import java.util.logging.Level
import java.util.logging.Logger

interface PersistentLogger {
    fun info(tag: String, msg: String)
    fun warn(tag: String, msg: String, t: Throwable? = null)
    fun error(tag: String, msg: String, t: Throwable? = null)
}

object PersistentLoggers {
    @Volatile
    var instance: PersistentLogger? = null

    private val fallback: Logger = Logger.getLogger("OzeroFallback")

    fun info(tag: String, msg: String) {
        val i = instance
        if (i != null) {
            i.info(tag, msg)
        } else {
            runCatching { fallback.log(Level.INFO, "[$tag] $msg") }
        }
    }

    fun warn(tag: String, msg: String, t: Throwable? = null) {
        val i = instance
        if (i != null) {
            i.warn(tag, msg, t)
        } else {
            runCatching { fallback.log(Level.WARNING, "[$tag] $msg", t) }
        }
    }

    fun error(tag: String, msg: String, t: Throwable? = null) {
        val i = instance
        if (i != null) {
            i.error(tag, msg, t)
        } else {
            runCatching { fallback.log(Level.SEVERE, "[$tag] $msg", t) }
        }
    }
}
