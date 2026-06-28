package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.engineurnetwork.allowDirect
import ru.ozero.engineurnetwork.fixedIpSize
import ru.ozero.engineurnetwork.provideControlMode
import ru.ozero.engineurnetwork.provideEnabled
import ru.ozero.engineurnetwork.provideNetworkMode
import ru.ozero.engineurnetwork.setAllowDirect
import ru.ozero.engineurnetwork.setFixedIpSize
import ru.ozero.engineurnetwork.setProvideControlMode
import ru.ozero.engineurnetwork.setProvideNetworkMode
import ru.ozero.engineurnetwork.setWindowType
import ru.ozero.engineurnetwork.windowType
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UrnetworkEngineSettingsViewModel @Inject constructor(
    private val bridge: UrnetworkSdkBridge,
    private val configStore: UrnetworkConfigStore,
    private val tunnelController: TunnelController,
    private val balanceRepository: UrnetworkBalanceRepository,
) : ViewModel() {

    val isUrnetworkActive: StateFlow<Boolean> = tunnelController.state
        .map { s ->
            when (s) {
                is TunnelState.Connecting -> s.engineId == EngineId.URNETWORK
                is TunnelState.Connected -> s.engineId == EngineId.URNETWORK
                else -> false
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val windowType: StateFlow<UrnetworkWindowType> = configStore.windowType()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UrnetworkWindowType.AUTO)

    val fixedIpSize: StateFlow<Boolean> = configStore.fixedIpSize()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allowDirect: StateFlow<Boolean> = configStore.allowDirect()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val provideControlMode: StateFlow<UrnetworkProvideControlMode> = configStore.provideControlMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UrnetworkProvideControlMode.AUTO)

    val provideNetworkMode: StateFlow<UrnetworkProvideNetworkMode> = configStore.provideNetworkMode()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UrnetworkProvideNetworkMode.WIFI)

    val providePaused: StateFlow<Boolean> = configStore.provideEnabled()
        .map { !it }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val insufficientBalance: StateFlow<Boolean> = bridge.contractStatus()
        .map { it.insufficientBalance && !it.premium }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(POLLER_KEEP_ALIVE_MS), false)

    val sharedTrafficBytes: StateFlow<Long> = isUrnetworkActive.flatMapLatest { active ->
        if (active) {
            flow {
                var last = 0L
                while (true) {
                    last = runCatching { bridge.unpaidByteCount() }.getOrDefault(last)
                    emit(last)
                    delay(SHARED_TRAFFIC_POLL_MS)
                }
            }.distinctUntilChanged()
        } else {
            flowOf(0L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(POLLER_KEEP_ALIVE_MS), 0L)

    private val balancePollTicker = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(BALANCE_POLL_INTERVAL_MS)
        }
    }.onEach { runCatching { balanceRepository.refresh() } }

    val balanceState: StateFlow<UrnetworkBalanceState> = combine(
        balanceRepository.state,
        balancePollTicker,
    ) { state, _ -> state }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(POLLER_KEEP_ALIVE_MS),
            initialValue = balanceRepository.state.value,
        )

    fun selectProvideControlMode(value: UrnetworkProvideControlMode) {
        viewModelScope.launch {
            configStore.setProvideControlMode(value)
            if (isUrnetworkActive.value) {
                runCatching { bridge.setProvideControlMode(value) }
            }
        }
    }

    fun selectProvideNetworkMode(value: UrnetworkProvideNetworkMode) {
        viewModelScope.launch {
            configStore.setProvideNetworkMode(value)
            if (isUrnetworkActive.value) {
                runCatching { bridge.setProvideNetworkMode(value) }
            }
        }
    }

    fun selectWindowType(value: UrnetworkWindowType) {
        viewModelScope.launch {
            configStore.setWindowType(value)
            if (isUrnetworkActive.value) {
                runCatching { bridge.applyPerformanceProfile(value, fixedIpSize.value, allowDirect.value) }
            }
        }
    }

    fun toggleFixedIpSize(value: Boolean) {
        viewModelScope.launch {
            configStore.setFixedIpSize(value)
            if (isUrnetworkActive.value) {
                runCatching { bridge.applyPerformanceProfile(windowType.value, value, allowDirect.value) }
            }
        }
    }

    fun toggleAllowDirect(value: Boolean) {
        viewModelScope.launch {
            configStore.setAllowDirect(value)
            if (isUrnetworkActive.value) {
                runCatching { bridge.applyPerformanceProfile(windowType.value, fixedIpSize.value, value) }
            }
        }
    }

    companion object {
        private const val POLLER_KEEP_ALIVE_MS = 5_000L
        private const val SHARED_TRAFFIC_POLL_MS = 10_000L
        private const val BALANCE_POLL_INTERVAL_MS = 30_000L
    }
}
