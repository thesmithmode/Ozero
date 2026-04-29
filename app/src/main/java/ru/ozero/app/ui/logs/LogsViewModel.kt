package ru.ozero.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.logging.UnifiedLogger
import javax.inject.Inject

data class LogsUiState(
    val tail: String = "",
    val fileSize: Long = 0L,
    val filePath: String = "",
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    @Suppress("UnusedPrivateMember") private val buffer: LogBuffer,
    private val reader: LogcatReader,
) : ViewModel() {

    private val _refresh = MutableStateFlow(0L)
    val refresh: StateFlow<Long> = _refresh.asStateFlow()

    val uiState: StateFlow<LogsUiState> = pollFile().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LogsUiState(),
    )

    private fun pollFile(): Flow<LogsUiState> = flow {
        while (true) {
            emit(snapshot())
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun snapshot(): LogsUiState = withContext(Dispatchers.IO) {
        LogsUiState(
            tail = UnifiedLogger.readTail(),
            fileSize = UnifiedLogger.fileSize(),
            filePath = UnifiedLogger.file()?.absolutePath.orEmpty(),
        )
    }

    fun copyAll(): String = UnifiedLogger.read()

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            reader.clearAll()
            UnifiedLogger.clear()
            _refresh.value = System.currentTimeMillis()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
