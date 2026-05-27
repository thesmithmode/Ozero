package ru.ozero.desktop.logging

enum class DesktopLogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

data class DesktopLogEntry(
    val timestampMs: Long,
    val level: DesktopLogLevel,
    val tag: String,
    val message: String,
)
