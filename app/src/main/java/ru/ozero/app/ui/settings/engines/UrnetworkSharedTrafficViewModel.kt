package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ozero.app.urnetwork.DayBytes
import ru.ozero.app.urnetwork.UrnetworkSharedTrafficHistory
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

@HiltViewModel
class UrnetworkSharedTrafficViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
    private val history: UrnetworkSharedTrafficHistory,
) : ViewModel() {

    private val _unpaidBytes = MutableStateFlow(0L)
    val unpaidBytes: StateFlow<Long> = _unpaidBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dailyBytes = MutableStateFlow(history.loadLast30Days())
    val dailyBytes: StateFlow<List<DayBytes>> = _dailyBytes.asStateFlow()

    private val loadMutex = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = loadMutex.withLock {
        _isLoading.value = true
        runCatching { bridge.fetchTransferStats() }
        val current = runCatching { bridge.unpaidByteCount() }.getOrDefault(0L)
        _unpaidBytes.value = current
        runCatching { history.record(current) }
        _dailyBytes.value = history.loadLast30Days()
        _isLoading.value = false
    }
}
