package ru.ozero.app.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * In-memory циркулярный буфер логов. Не пишет на диск — место не засирается.
 * При превышении [capacity] выкидывает самые старые записи.
 *
 * Thread-safe: операции под `synchronized(lock)`. UI читает через [entries].
 */
class LogBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val deque = ArrayDeque<LogEntry>(capacity)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun append(entry: LogEntry) {
        val snapshot = synchronized(lock) {
            if (deque.size >= capacity) deque.pollFirst()
            deque.addLast(entry)
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
        // 5000 строк ≈ 1-3 МБ RAM. Сбрасывается при kill процесса — диск не трогаем.
        const val DEFAULT_CAPACITY: Int = 5000
    }
}
