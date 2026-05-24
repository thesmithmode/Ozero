package ru.ozero.app.logging

import java.text.SimpleDateFormat
import java.util.Locale

internal object UnifiedLogFileParser {

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Format written by UnifiedLogger.log():
    //   2026-05-25 10:23:45.123 WARN [thread-name] SomeTag: message text
    private val LINE = Regex(
        """^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) (VERBOSE|TRACE|DEBUG|INFO|WARN|ERROR) \[[^\]]*\] ([^:]+): (.*)$""",
    )

    fun parseLine(line: String): LogEntry? {
        val trimmed = line.trim()
        if (trimmed.startsWith("LOGCAT ")) return null
        val m = LINE.matchEntire(trimmed) ?: return null
        val (ts, levelStr, tag, msg) = m.destructured
        val tsMs = runCatching { synchronized(tsFmt) { tsFmt.parse(ts)?.time } }.getOrNull() ?: return null
        val level = when (levelStr) {
            "VERBOSE", "TRACE" -> LogLevel.TRACE
            "DEBUG" -> LogLevel.DEBUG
            "WARN" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        return LogEntry(tsMs, level, tag.trim(), 0, msg)
    }

    fun parseAll(text: String): List<LogEntry> =
        text.lineSequence().mapNotNull { parseLine(it) }.toList()
}
