package ru.ozero.app.logging

import android.util.Log

object AppLogger {

    @Volatile private var buffer: LogBuffer? = null

    fun attach(b: LogBuffer) {
        buffer = b
    }

    fun v(tag: String, msg: String) { Log.v(tag, msg); emit(LogLevel.TRACE, tag, msg) }
    fun d(tag: String, msg: String) { Log.d(tag, msg); emit(LogLevel.DEBUG, tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg); emit(LogLevel.INFO, tag, msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg); emit(LogLevel.WARN, tag, msg) }
    fun e(tag: String, msg: String) { Log.e(tag, msg); emit(LogLevel.ERROR, tag, msg) }

    fun w(tag: String, msg: String, t: Throwable) {
        Log.w(tag, msg, t)
        emit(LogLevel.WARN, tag, "$msg — ${t.javaClass.simpleName}: ${t.message}")
    }

    fun e(tag: String, msg: String, t: Throwable) {
        Log.e(tag, msg, t)
        val cause = t.cause?.let { " caused by ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        emit(LogLevel.ERROR, tag, "$msg — ${t.javaClass.simpleName}: ${t.message}$cause")
    }

    private fun emit(level: LogLevel, tag: String, msg: String) {
        val buf = buffer
        if (buf == null) {
            Log.w(TAG, "emit() before attach() — dropped: [$tag] $msg")
            return
        }
        buf.append(LogEntry.now(level, tag, msg))
    }

    private const val TAG = "AppLogger"
}
