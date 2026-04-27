package ru.ozero.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogExporter
import ru.ozero.app.logging.LogLevel
import ru.ozero.app.logging.LogcatReader
import java.io.File
import javax.inject.Inject

/**
 * UI-state экрана логов. [filtered] — буфер уже отрезан по [minLevel].
 */
data class LogsUiState(
    val minLevel: LogLevel = LogLevel.INFO,
    val filtered: List<LogEntry> = emptyList(),
    val totalCaptured: Int = 0,
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val buffer: LogBuffer,
    private val reader: LogcatReader,
    private val exporter: LogExporter,
) : ViewModel() {

    private val _minLevel = MutableStateFlow(LogLevel.INFO)
    val minLevel: StateFlow<LogLevel> = _minLevel.asStateFlow()

    val uiState: StateFlow<LogsUiState> = combine(buffer.entries, _minLevel) { entries, level ->
        LogsUiState(
            minLevel = level,
            filtered = entries.asSequence()
                .filter { it.level.ordinal >= level.ordinal }
                .toList(),
            totalCaptured = entries.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LogsUiState(),
    )

    fun setLevel(level: LogLevel) {
        _minLevel.value = level
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) { reader.clearAll() }
    }

    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        exporter.export(buffer.entries.value, _minLevel.value)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
