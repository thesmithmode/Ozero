package ru.ozero.enginescore

import android.util.Log

interface PersistentLogger {
    fun info(tag: String, msg: String)
    fun warn(tag: String, msg: String, t: Throwable? = null)
    fun error(tag: String, msg: String, t: Throwable? = null)
}

object PersistentLoggers {
    @Volatile
    var instance: PersistentLogger? = null

    fun info(tag: String, msg: String) {
        val i = instance
        if (i != null) {
            i.info(tag, msg)
        } else {
            runCatching { Log.i(tag, "[fallback] $msg") }
        }
    }

    fun warn(tag: String, msg: String, t: Throwable? = null) {
        val i = instance
        if (i != null) {
            i.warn(tag, msg, t)
        } else {
            runCatching {
                if (t != null) Log.w(tag, "[fallback] $msg", t) else Log.w(tag, "[fallback] $msg")
            }
        }
    }

    fun error(tag: String, msg: String, t: Throwable? = null) {
        val i = instance
        if (i != null) {
            i.error(tag, msg, t)
        } else {
            runCatching {
                if (t != null) Log.e(tag, "[fallback] $msg", t) else Log.e(tag, "[fallback] $msg")
            }
        }
    }
}
