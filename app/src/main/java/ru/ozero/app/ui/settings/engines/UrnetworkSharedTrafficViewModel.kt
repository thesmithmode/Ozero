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
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import javax.inject.Inject

@HiltViewModel
class UrnetworkSharedTrafficViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
) : ViewModel() {

    private val _balance = MutableStateFlow<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?>(null)
    val balance: StateFlow<UrnetworkSdkBridge.SubscriptionBalanceSnapshot?> = _balance.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val loadMutex = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() = loadMutex.withLock {
        _isLoading.value = true
        _balance.value = runCatching { bridge.fetchSubscriptionBalance() }.getOrNull()
        _isLoading.value = false
    }
}
