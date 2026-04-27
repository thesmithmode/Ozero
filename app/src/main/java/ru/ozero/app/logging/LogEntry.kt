package ru.ozero.app.logging

/**
 * Уровни логов в порядке возрастания серьёзности. Сортировка по [ordinal]
 * используется для фильтрации `>= selected`.
 */
enum class LogLevel(val short: Char) {
    TRACE('V'),
    DEBUG('D'),
    INFO('I'),
    WARN('W'),
    ERROR('E');

    companion object {
        /** Маппинг символа из logcat -v threadtime в [LogLevel]. F (fatal) → ERROR. */
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

/**
 * Одна строка лога. [timestampMs] — wall-clock в миллисекундах. [tag] и [message]
 * взяты из logcat без модификации, [pid] помогает отличать наш процесс от чужого.
 */
data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val pid: Int,
    val message: String,
)
