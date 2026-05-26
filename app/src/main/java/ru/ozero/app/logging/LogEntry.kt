package ru.ozero.app.logging

enum class LogLevel(val short: Char, val severity: Int) {
    TRACE('V', 0),
    DEBUG('D', 1),
    INFO('I', 2),
    WARN('W', 3),
    ERROR('E', 4);

    companion object {
        fun fromShort(c: Char): LogLevel = when (c) {
            'V' -> TRACE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E', 'F', 'A' -> ERROR
            else -> INFO
        }
    }
}

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val pid: Int,
    val message: String,
) {
    companion object {
        fun now(level: LogLevel, tag: String, message: String) = LogEntry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            pid = android.os.Process.myPid(),
            message = message,
        )
    }
}
