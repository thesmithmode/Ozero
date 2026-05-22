package ru.ozero.app.urnetwork

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkSdkBridge.SubscriptionBalanceSnapshot

interface UrnetworkBalanceRepository {
    val state: StateFlow<UrnetworkBalanceState>
    suspend fun refresh()
}

data class UrnetworkBalanceState(
    val snapshot: SubscriptionBalanceSnapshot?,
    val isLoading: Boolean,
    val lastError: String?,
    val meanReliabilityWeight: Double = 0.0,
    val totalReferrals: Long = 0L,
) {
    val availableBytes: Long
        get() = ((snapshot?.balanceBytes ?: 0L).coerceAtLeast(0L) + (snapshot?.pendingBytes ?: 0L).coerceAtLeast(0L))

    companion object {
        val INITIAL = UrnetworkBalanceState(snapshot = null, isLoading = false, lastError = null)
    }
}

class RealUrnetworkBalanceRepository(
    private val bridge: UrnetworkSdkBridge,
    private val cache: UrnetworkBalanceCache? = null,
    private val fetchTimeoutMs: Long = DEFAULT_FETCH_TIMEOUT_MS,
) : UrnetworkBalanceRepository {

    private val _state = MutableStateFlow(
        cache?.load()?.let {
            UrnetworkBalanceState(
                snapshot = it.snapshot,
                isLoading = false,
                lastError = null,
                meanReliabilityWeight = it.meanReliabilityWeight,
                totalReferrals = it.totalReferrals,
            )
        } ?: UrnetworkBalanceState.INITIAL,
    )
    override val state: StateFlow<UrnetworkBalanceState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    override suspend fun refresh() = refreshMutex.withLock {
        _state.value = _state.value.copy(isLoading = true)
        coroutineScope {
            val balanceDeferred = async {
                runCatching { withTimeout(fetchTimeoutMs) { bridge.fetchSubscriptionBalance() } }
            }
            val reliabilityDeferred = async {
                runCatching { withTimeout(fetchTimeoutMs) { bridge.fetchNetworkReliability() } }
            }
            val referralDeferred = async {
                runCatching { withTimeout(fetchTimeoutMs) { bridge.fetchReferralCount() } }
            }
            val balanceResult = balanceDeferred.await()
            val newReliability = reliabilityDeferred.await().getOrNull()
                ?: _state.value.meanReliabilityWeight
            val newReferrals = referralDeferred.await().getOrNull()
                ?: _state.value.totalReferrals
            _state.value = balanceResult.fold(
                onSuccess = { snap ->
                    if (snap != null) {
                        runCatching { cache?.save(snap, newReliability, newReferrals) }
                    }
                    _state.value.copy(
                        snapshot = snap ?: _state.value.snapshot,
                        isLoading = false,
                        lastError = null,
                        meanReliabilityWeight = newReliability,
                        totalReferrals = newReferrals,
                    )
                },
                onFailure = { err ->
                    val msg = when (err) {
                        is TimeoutCancellationException -> "balance fetch timeout (${fetchTimeoutMs}ms)"
                        else -> err.message ?: err.javaClass.simpleName
                    }
                    _state.value.copy(
                        isLoading = false,
                        lastError = msg,
                        meanReliabilityWeight = newReliability,
                        totalReferrals = newReferrals,
                    )
                },
            )
        }
    }

    private companion object {
        const val DEFAULT_FETCH_TIMEOUT_MS: Long = 8_000
    }
}
