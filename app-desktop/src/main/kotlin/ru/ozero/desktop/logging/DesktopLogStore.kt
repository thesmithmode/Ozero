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
    private const val MAX_FILE_BYTES = 2_000_000
    private val lock = Any()
    private val deque = ArrayDeque<DesktopLogEntry>(CAPACITY)
    private val _entries = MutableStateFlow<List<DesktopLogEntry>>(emptyList())
    val entries: StateFlow<List<DesktopLogEntry>> = _entries.asStateFlow()
    private val logFile = resolveLogFile()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val lines = readLogLines()
        if (lines.isEmpty()) return
        deque.clear()
        deque.addAll(lines.mapNotNull { parseLogLine(it) })
        _entries.value = deque.toList()
    }

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
        writeLine(formatEntry(entry))
    }

    fun clear() {
        synchronized(lock) {
            deque.clear()
            _entries.value = emptyList()
        }
        runCatching { logFile.writeText("") }
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
            listOf("All") + deque.map { it.tag }.distinct().sorted()
        }

    private fun formatTime(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = ms
        }
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

    private fun writeLine(line: String) {
        runCatching {
            logFile.parentFile?.mkdirs()
            if (logFile.exists() && logFile.length() > MAX_FILE_BYTES) {
                val lines = logFile.readLines()
                val keep = lines.drop((lines.size * 0.6).toInt())
                logFile.writeText(keep.joinToString("\n"))
            }
            logFile.appendText(if (logFile.exists() && logFile.length() > 0L) "\n$line" else line)
        }
    }

    private fun readLogLines(): List<String> {
        return runCatching { logFile.readLines() }.getOrElse { emptyList() }
    }

    private fun parseLogLine(line: String): DesktopLogEntry? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null
        val split = trimmed.split(" ", limit = 4)
        if (split.size < 3) return null

        val time = split[0]
        val levelPart = split[1].trim().removePrefix("[").removeSuffix("]")
        val tagAndMessage = split.getOrNull(2) ?: return null
        val separatorIndex = tagAndMessage.indexOf(":")
        if (separatorIndex <= 0) return null

        val tag = tagAndMessage.substring(0, separatorIndex)
        val messageTail = tagAndMessage.substring(separatorIndex + 1)
        val message = if (split.size >= 4) {
            "$messageTail ${split[3]}"
        } else {
            messageTail
        }.trim()

        val ts = parseTimeToMillis(time)
        val lvl = when (levelPart) {
            DesktopLogLevel.ERROR.name -> DesktopLogLevel.ERROR
            DesktopLogLevel.WARN.name -> DesktopLogLevel.WARN
            DesktopLogLevel.INFO.name -> DesktopLogLevel.INFO
            DesktopLogLevel.DEBUG.name -> DesktopLogLevel.DEBUG
            else -> DesktopLogLevel.TRACE
        }
        return DesktopLogEntry(ts, lvl, tag, message)
    }

    private fun parseTimeToMillis(time: String): Long {
        return runCatching {
            val parts = time.split(":")
            val today = java.time.LocalDate.now()
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            val secAndMs = parts[2].split(".")
            val sec = secAndMs[0].toInt()
            val ms = secAndMs.getOrNull(1)?.toIntOrNull() ?: 0
            java.time.LocalDateTime.of(
                today.year,
                today.month,
                today.dayOfMonth,
                hour,
                minute,
                sec,
                ms * 1_000_000,
            ).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }

    private fun formatEntry(entry: DesktopLogEntry): String {
        val time = formatTime(entry.timestampMs)
        return "$time [${entry.level.name}] ${entry.tag}: ${entry.message}"
    }

    private fun resolveLogFile(): File {
        val base = System.getenv("LOCALAPPDATA")
            ?: System.getenv("APPDATA")
            ?: System.getProperty("user.home")
        return File(base, "Ozero/logs/ozero-desktop.log")
    }
}
