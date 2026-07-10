package ru.ozero.app.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

class LogBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val deque = ArrayDeque<LogEntry>(capacity)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun append(entry: LogEntry) {
        val snapshot = synchronized(lock) {
            if (deque.size >= capacity) deque.pollFirst()
            deque.addLast(entry.bounded())
            deque.toList()
        }
        _entries.value = snapshot
    }

    fun appendAll(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        val snapshot = synchronized(lock) {
            for (entry in entries) {
                if (deque.size >= capacity) deque.pollFirst()
                deque.addLast(entry.bounded())
            }
            deque.toList()
        }
        _entries.value = snapshot
    }

    fun clear() {
        synchronized(lock) { deque.clear() }
        _entries.value = emptyList()
    }

    fun size(): Int = synchronized(lock) { deque.size }

    companion object {
        const val DEFAULT_CAPACITY: Int = 1000
        const val MAX_MESSAGE_CHARS: Int = 2_000
    }
}

private fun LogEntry.bounded(): LogEntry {
    if (message.length <= LogBuffer.MAX_MESSAGE_CHARS) return this
    return copy(message = message.take(LogBuffer.MAX_MESSAGE_CHARS) + "...[truncated]")
}
