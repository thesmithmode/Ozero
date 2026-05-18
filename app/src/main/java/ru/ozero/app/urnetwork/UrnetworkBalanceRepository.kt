package ru.ozero.app.urnetwork

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot

interface UrnetworkBalanceRepository {
    val state: StateFlow<UrnetworkBalanceState>
    suspend fun refresh()
    fun startPolling()
    fun stopPolling()
}

data class UrnetworkBalanceState(
    val snapshot: SubscriptionBalanceSnapshot?,
    val isLoading: Boolean,
    val lastError: String?,
) {
    val availableBytes: Long
        get() = snapshot?.let { (it.balanceBytes - it.pendingBytes - it.usedBytes).coerceAtLeast(0L) } ?: 0L

    companion object {
        val INITIAL = UrnetworkBalanceState(snapshot = null, isLoading = false, lastError = null)
    }
}

class RealUrnetworkBalanceRepository(
    private val bridge: UrnetworkSdkBridge,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UrnetworkBalanceRepository {

    private val _state = MutableStateFlow(UrnetworkBalanceState.INITIAL)
    override val state: StateFlow<UrnetworkBalanceState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var pollJob: Job? = null

    override suspend fun refresh() = refreshMutex.withLock {
        _state.value = _state.value.copy(isLoading = true)
        val result = runCatching { bridge.fetchSubscriptionBalance() }
        _state.value = result.fold(
            onSuccess = { snap ->
                _state.value.copy(snapshot = snap, isLoading = false, lastError = null)
            },
            onFailure = { err ->
                _state.value.copy(isLoading = false, lastError = err.message ?: err.javaClass.simpleName)
            },
        )
    }

    override fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(dispatcher) {
            while (true) {
                refresh()
                delay(pollIntervalMs)
            }
        }
    }

    override fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 30_000L
    }
}
