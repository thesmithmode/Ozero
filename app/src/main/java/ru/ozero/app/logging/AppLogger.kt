package ru.ozero.app.logging

object AppLogger {

    @Volatile private var buffer: LogBuffer? = null

    fun attach(b: LogBuffer) {
        buffer = b
    }

    fun v(tag: String, msg: String) {
        UnifiedLogger.log("TRACE", tag, msg)
        emit(LogLevel.TRACE, tag, msg)
    }

    fun d(tag: String, msg: String) {
        UnifiedLogger.log("DEBUG", tag, msg)
        emit(LogLevel.DEBUG, tag, msg)
    }

    fun i(tag: String, msg: String) {
        UnifiedLogger.log("INFO", tag, msg)
        emit(LogLevel.INFO, tag, msg)
    }

    fun w(tag: String, msg: String) {
        UnifiedLogger.log("WARN", tag, msg)
        emit(LogLevel.WARN, tag, msg)
    }

    fun e(tag: String, msg: String) {
        UnifiedLogger.log("ERROR", tag, msg)
        emit(LogLevel.ERROR, tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable) {
        UnifiedLogger.log("WARN", tag, msg, t)
        emit(LogLevel.WARN, tag, "$msg — ${t.javaClass.simpleName}: ${t.message}")
    }

    fun e(tag: String, msg: String, t: Throwable) {
        UnifiedLogger.log("ERROR", tag, msg, t)
        val cause = t.cause?.let { " caused by ${it.javaClass.simpleName}: ${it.message}" } ?: ""
        emit(LogLevel.ERROR, tag, "$msg — ${t.javaClass.simpleName}: ${t.message}$cause")
    }

    private fun emit(level: LogLevel, tag: String, msg: String) {
        val buf = buffer ?: return
        buf.append(LogEntry.now(level, tag, msg))
    }
}
