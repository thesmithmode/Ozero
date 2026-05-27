package ru.ozero.desktop.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

object DesktopLogStore {

    private const val CAPACITY = 5000
    private val lock = Any()
    private val deque = ArrayDeque<DesktopLogEntry>(CAPACITY)
    private val _entries = MutableStateFlow<List<DesktopLogEntry>>(emptyList())
    val entries: StateFlow<List<DesktopLogEntry>> = _entries.asStateFlow()

    fun append(level: DesktopLogLevel, tag: String, message: String) {
        val entry = DesktopLogEntry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        synchronized(lock) {
            if (deque.size >= CAPACITY) deque.removeFirst()
            deque.addLast(entry)
            _entries.value = deque.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            deque.clear()
            _entries.value = emptyList()
        }
    }

    fun copyAll(): String = synchronized(lock) {
        deque.joinToString("\n") { e ->
            val time = formatTime(e.timestampMs)
            "$time [${e.level.name}] ${e.tag}: ${e.message}"
        }
    }

    fun export(file: File) {
        file.writeText(copyAll())
    }

    val availableTags: List<String>
        get() = synchronized(lock) {
            listOf("Все") + deque.map { it.tag }.distinct().sorted()
        }

    private fun formatTime(ms: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ms
        return "%02d:%02d:%02d.%03d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND),
            cal.get(java.util.Calendar.MILLISECOND),
        )
    }

    fun installJulHandler() {
        val root = Logger.getLogger("")
        root.addHandler(object : Handler() {
            override fun publish(record: LogRecord) {
                val level = when {
                    record.level.intValue() >= Level.SEVERE.intValue() -> DesktopLogLevel.ERROR
                    record.level.intValue() >= Level.WARNING.intValue() -> DesktopLogLevel.WARN
                    record.level.intValue() >= Level.INFO.intValue() -> DesktopLogLevel.INFO
                    record.level.intValue() >= Level.FINE.intValue() -> DesktopLogLevel.DEBUG
                    else -> DesktopLogLevel.TRACE
                }
                val tag = record.loggerName?.substringAfterLast('.') ?: "System"
                append(level, tag, record.message ?: "")
            }

            override fun flush() {}
            override fun close() {}
        })
    }
}
