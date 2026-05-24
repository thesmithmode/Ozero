package ru.ozero.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.logging.UnifiedLogFileParser
import ru.ozero.app.logging.UnifiedLogger
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

internal const val FILTER_ALL = "Все"

data class LogsUiState(
    val fileSize: Long = 0L,
    val filePath: String = "",
    val entries: List<LogEntry> = emptyList(),
    val tagFilter: String = FILTER_ALL,
    val levelFilter: String = FILTER_ALL,
) {
    val availableTags: List<String> = buildList {
        add(FILTER_ALL)
        entries.map { it.tag }.distinct().sorted().forEach { add(it) }
    }

    val filteredEntries: List<LogEntry> = entries.filter { entry ->
        (tagFilter == FILTER_ALL || entry.tag == tagFilter) &&
            (levelFilter == FILTER_ALL || entry.level.name == levelFilter)
    }
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    @Suppress("UnusedPrivateMember") private val buffer: LogBuffer,
    private val reader: LogcatReader,
) : ViewModel() {

    internal var ioContext: CoroutineContext = Dispatchers.IO

    private val _refresh = MutableStateFlow(0L)
    val refresh: StateFlow<Long> = _refresh.asStateFlow()

    private val _tagFilter = MutableStateFlow(FILTER_ALL)
    private val _levelFilter = MutableStateFlow(FILTER_ALL)

    val uiState: StateFlow<LogsUiState> = combine(
        fileEntriesFlow(),
        _tagFilter,
        _levelFilter,
        fileStateFlow(),
    ) { entries: List<LogEntry>, tag: String, level: String, fs: Pair<Long, String> ->
        LogsUiState(
            fileSize = fs.first,
            filePath = fs.second,
            entries = entries,
            tagFilter = tag,
            levelFilter = level,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LogsUiState(),
    )

    fun onTagFilter(tag: String) {
        _tagFilter.value = tag
    }

    fun onLevelFilter(level: String) {
        _levelFilter.value = level
    }

    fun copyAll(): String = UnifiedLogger.read()

    fun clear() {
        viewModelScope.launch(ioContext) {
            reader.clearAll()
            UnifiedLogger.clear()
            _refresh.value = System.currentTimeMillis()
        }
    }

    private fun fileEntriesFlow() = flow {
        var lastSize = -1L
        var cached = emptyList<LogEntry>()
        while (true) {
            val size = withContext(ioContext) { UnifiedLogger.fileSize() }
            if (size != lastSize) {
                lastSize = size
                cached = withContext(ioContext) {
                    UnifiedLogFileParser.parseAll(UnifiedLogger.readTail())
                }
            }
            emit(cached)
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun fileStateFlow() = flow {
        while (true) {
            val pair = withContext(ioContext) {
                Pair(UnifiedLogger.fileSize(), UnifiedLogger.file()?.absolutePath.orEmpty())
            }
            emit(pair)
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
