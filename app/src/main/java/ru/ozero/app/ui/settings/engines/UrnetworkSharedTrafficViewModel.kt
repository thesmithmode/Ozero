package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

@HiltViewModel
class UrnetworkSharedTrafficViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
    private val balanceRepository: UrnetworkBalanceRepository,
) : ViewModel() {

    private val _unpaidBytes = MutableStateFlow(0L)
    val unpaidBytes: StateFlow<Long> = _unpaidBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val balanceState: StateFlow<UrnetworkBalanceState> = balanceRepository.state

    private val loadMutex = Mutex()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            while (isActive) {
                balanceRepository.refresh()
                delay(BALANCE_POLL_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
        viewModelScope.launch { balanceRepository.refresh() }
    }

    private suspend fun load() = loadMutex.withLock {
        _isLoading.value = true
        runCatching { bridge.fetchTransferStats() }
        _unpaidBytes.value = runCatching { bridge.unpaidByteCount() }.getOrDefault(0L)
        _isLoading.value = false
    }

    companion object {
        private const val BALANCE_POLL_INTERVAL_MS = 30_000L
    }
}
